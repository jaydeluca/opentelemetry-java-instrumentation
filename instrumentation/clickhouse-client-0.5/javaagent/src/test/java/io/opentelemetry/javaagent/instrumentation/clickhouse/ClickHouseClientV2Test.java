/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemIncubatingValues.CLICKHOUSE;
import static io.opentelemetry.semconv.incubating.ServerIncubatingAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.incubating.ServerIncubatingAttributes.SERVER_PORT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.data_formats.ClickHouseBinaryFormatReader;
import com.clickhouse.client.api.query.QueryResponse;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

@TestInstance(Lifecycle.PER_CLASS)
@SuppressWarnings("deprecation") // using deprecated semconv
class ClickHouseClientV2Test {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final GenericContainer<?> clickhouseServer =
      new GenericContainer<>("clickhouse/clickhouse-server:24.4.2").withExposedPorts(8123);

  private static final String dbName = "default";
  private static final String user = "default";
  private static int port;
  private static String host;
  private Client client;

  @BeforeAll
  void setup() throws Exception {
    clickhouseServer.start();
    port = clickhouseServer.getMappedPort(8123);
    host = clickhouseServer.getHost();

    client =
        new Client.Builder()
            .addEndpoint("http://" + host + ":" + port)
            .setUsername(user)
            .setPassword("")
            .setDefaultDatabase(dbName)
            .build();

    byte[] dbInit =
        ("create table if not exists my_metrics ("
                + "id Nullable(Float64), "
                + "value Nullable(Float64), "
                + "type Nullable(String)) engine = MergeTree order by ();")
            .getBytes(UTF_8);

    BufferedReader reader =
        new BufferedReader(new InputStreamReader(new ByteArrayInputStream(dbInit), UTF_8));
    String sql = reader.lines().collect(Collectors.joining());
    client.query(sql).get(3, TimeUnit.SECONDS);

    // wait for CREATE operation and clear
    testing.waitForTraces(1);
    testing.clearData();
  }

  @AfterAll
  void cleanup() {
    if (client != null) {
      client.close();
    }
    clickhouseServer.stop();
  }

  @Test
  void testInsertAndReadQuery() throws Exception {

    String insertSQL =
        ("insert into my_metrics (id, value, type) values"
            + "(1.0, 100.0, 'type1'),"
            + "(2.0, 200.0, 'type1'),"
            + "(3.0, 300.0, 'type1')");

    client.query(insertSQL).get(3, TimeUnit.SECONDS);

    String readSql = "select * from my_metrics limit 10";

    try (QueryResponse response = client.query(readSql).get(3, TimeUnit.SECONDS)) {
      ClickHouseBinaryFormatReader newReader = client.newBinaryFormatReader(response);

      while (newReader.hasNext()) {
        newReader.next();
        double id = newReader.getDouble("id");
        String title = newReader.getString("type");
        double value = newReader.getDouble("value");
        System.out.println("id: " + id + ", title: " + title + ", value: " + value);
      }
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("INSERT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "insert into my_metrics (id, value, type) values(?, ?, ?),(?, ?, ?),(?, ?, ?)",
                                "INSERT"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions("select * from my_metrics limit ?", "SELECT"))));
  }

  private static List<AttributeAssertion> attributeAssertions(String statement, String operation) {
    return asList(
        equalTo(DB_SYSTEM, CLICKHOUSE),
        equalTo(maybeStable(DB_NAME), dbName),
        equalTo(SERVER_ADDRESS, host),
        equalTo(SERVER_PORT, port),
        equalTo(maybeStable(DB_STATEMENT), statement),
        equalTo(maybeStable(DB_OPERATION), operation));
  }
}
