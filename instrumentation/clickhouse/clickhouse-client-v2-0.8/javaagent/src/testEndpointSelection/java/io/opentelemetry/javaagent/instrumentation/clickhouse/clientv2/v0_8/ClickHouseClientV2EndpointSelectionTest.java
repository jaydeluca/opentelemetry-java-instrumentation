/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.testing.junit.db.SemconvStabilityUtil.maybeStable;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.DbAttributes.DB_QUERY_SUMMARY;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_NAME;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_OPERATION;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_STATEMENT;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DB_SYSTEM;
import static io.opentelemetry.semconv.incubating.DbIncubatingAttributes.DbSystemNameIncubatingValues.CLICKHOUSE;
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.enums.Protocol;
import com.clickhouse.client.api.query.GenericRecord;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.internal.AutoCleanupExtension;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.GenericContainer;

@SuppressWarnings("deprecation") // using deprecated semconv
class ClickHouseClientV2EndpointSelectionTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  @RegisterExtension static final AutoCleanupExtension cleanup = AutoCleanupExtension.create();

  private static final String DATABASE_NAME = "default";
  private static final String TABLE_NAME = "test_table";
  private static final String USERNAME = "default";
  private static final String PASSWORD = "";

  private static final GenericContainer<?> firstServer =
      new GenericContainer<>("clickhouse/clickhouse-server:24.4.2").withExposedPorts(8123);
  private static final GenericContainer<?> secondServer =
      new GenericContainer<>("clickhouse/clickhouse-server:24.4.2").withExposedPorts(8123);

  private static String firstHost;
  private static int firstPort;
  private static String secondHost;
  private static int secondPort;

  @BeforeAll
  static void setup() {
    firstServer.start();
    cleanup.deferAfterAll(firstServer::stop);
    firstHost = firstServer.getHost();
    firstPort = firstServer.getMappedPort(8123);

    secondServer.start();
    cleanup.deferAfterAll(secondServer::stop);
    secondHost = secondServer.getHost();
    secondPort = secondServer.getMappedPort(8123);

    // each server stores the value that identifies it, so that the row returned by the query tells
    // which server served it
    createTable(firstHost, firstPort, "first");
    createTable(secondHost, secondPort, "second");

    testing.clearData();
  }

  private static void createTable(String host, int port, String value) {
    Client client =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, host, port, false)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .build();
    cleanup.deferAfterAll(client);

    client.queryAll("create table " + TABLE_NAME + "(value String) engine=Memory");
    client.queryAll("insert into " + TABLE_NAME + " values('" + value + "')");
  }

  @Test
  void testEndpointThatServedTheQueryIsReported() {
    // Client#getEndpoints() returns the configured endpoints as an unordered set, so the endpoint
    // the client picks for a query is not the first one that was added. The span has to report the
    // endpoint that actually served the query, which is the server that returned the row below.
    Client client =
        new Client.Builder()
            .addEndpoint(Protocol.HTTP, firstHost, firstPort, false)
            .addEndpoint(Protocol.HTTP, secondHost, secondPort, false)
            .setDefaultDatabase(DATABASE_NAME)
            .setUsername(USERNAME)
            .setPassword(PASSWORD)
            .setOption("compress", "false")
            .build();
    cleanup.deferCleanup(client);

    List<GenericRecord> records = client.queryAll("select * from " + TABLE_NAME + " limit 1");
    assertThat(records).hasSize(1);
    boolean servedByFirstServer = "first".equals(records.get(0).getString(1));

    String expectedHost = servedByFirstServer ? firstHost : secondHost;
    int expectedPort = servedByFirstServer ? firstPort : secondPort;

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(
                            emitStableDatabaseSemconv()
                                ? "select test_table"
                                : "SELECT " + DATABASE_NAME)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            equalTo(maybeStable(DB_SYSTEM), CLICKHOUSE),
                            equalTo(maybeStable(DB_NAME), DATABASE_NAME),
                            equalTo(SERVER_ADDRESS, expectedHost),
                            equalTo(SERVER_PORT, expectedPort),
                            equalTo(
                                maybeStable(DB_STATEMENT),
                                "select * from " + TABLE_NAME + " limit ?"),
                            equalTo(
                                DB_QUERY_SUMMARY,
                                emitStableDatabaseSemconv() ? "select test_table" : null),
                            equalTo(
                                maybeStable(DB_OPERATION),
                                emitStableDatabaseSemconv() ? null : "SELECT"))));
  }
}
