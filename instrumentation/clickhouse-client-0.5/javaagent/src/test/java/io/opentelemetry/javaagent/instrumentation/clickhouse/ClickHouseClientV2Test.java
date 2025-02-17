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
import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.client.api.Client;
import com.clickhouse.client.api.command.CommandSettings;
import com.clickhouse.client.api.data_formats.ClickHouseBinaryFormatReader;
import com.clickhouse.client.api.insert.InsertResponse;
import com.clickhouse.client.api.insert.InsertSettings;
import com.clickhouse.client.api.query.QueryResponse;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseFormat;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.sdk.testing.assertj.AttributeAssertion;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.commons.text.StringEscapeUtils;
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
      new GenericContainer<>("clickhouse/clickhouse-server:24.9.3").withExposedPorts(8123);

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

    String insertSql =
        ("insert into my_metrics (id, value, type) values"
            + "(1.0, 100.0, 'type1'),"
            + "(2.0, 200.0, 'type1'),"
            + "(3.0, 300.0, 'type2')");

    client.query(insertSql).get(3, TimeUnit.SECONDS);

    String readSql = "select * from my_metrics limit 10";

    try (QueryResponse response = client.query(readSql).get(3, TimeUnit.SECONDS)) {
      ClickHouseBinaryFormatReader newReader = client.newBinaryFormatReader(response);

      List<Double> expectedIds = asList(1.0, 2.0, 3.0);
      List<String> expectedTitles = asList("type1", "type1", "type2");
      List<Double> expectedValues = asList(100.0, 200.0, 300.0);

      int index = 0;
      while (newReader.hasNext()) {
        newReader.next();
        double id = newReader.getDouble("id");
        String title = newReader.getString("type");
        double value = newReader.getDouble("value");

        assertThat(expectedIds.get(index)).isEqualTo(id);
        assertThat(expectedTitles.get(index)).isEqualTo(title);
        assertThat(expectedValues.get(index)).isEqualTo(value);

        index++;
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

  @Test
  void testExecute() throws Exception {

    String insertSQL =
        ("insert into my_metrics (id, value, type) values"
            + "(1.0, 100.0, 'type1'),"
            + "(2.0, 200.0, 'type1'),"
            + "(3.0, 300.0, 'type2')");

    client.execute(insertSQL).get(3, TimeUnit.SECONDS);

    String readSql = "select * from my_metrics limit 10";

    try (QueryResponse response = client.query(readSql).get(3, TimeUnit.SECONDS)) {
      ClickHouseBinaryFormatReader newReader = client.newBinaryFormatReader(response);

      List<Double> expectedIds = asList(1.0, 2.0, 3.0);
      List<String> expectedTitles = asList("type1", "type1", "type2");
      List<Double> expectedValues = asList(100.0, 200.0, 300.0);

      int index = 0;
      while (newReader.hasNext()) {
        newReader.next();
        double id = newReader.getDouble("id");
        String title = newReader.getString("type");
        double value = newReader.getDouble("value");

        assertThat(expectedIds.get(index)).isEqualTo(id);
        assertThat(expectedTitles.get(index)).isEqualTo(title);
        assertThat(expectedValues.get(index)).isEqualTo(value);

        index++;
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

  @Test
  void testPojoInsert() throws Exception {
    String tableName = "pojo_with_json_table";
    String createSQL =
        "CREATE TABLE "
            + tableName
            + " (eventPayload String) ENGINE = MergeTree() ORDER BY tuple()";
    String originalJsonStr = "{\"a\":{\"b\":\"42\"},\"c\":[\"1\",\"2\",\"3\"]}";

    CommandSettings commandSettings = new CommandSettings();
    client.execute("DROP TABLE IF EXISTS " + tableName, commandSettings).get(5, TimeUnit.SECONDS);
    client.execute(createSQL, commandSettings).get(10, TimeUnit.SECONDS);

    client.register(PojoWithJSON.class, client.getTableSchema(tableName, "default"));
    PojoWithJSON pojo = new PojoWithJSON();
    pojo.setEventPayload(originalJsonStr);
    List<Object> data = Collections.singletonList(pojo);

    InsertSettings insertSettings = new InsertSettings();
    InsertResponse response =
        client.insert(tableName, data, insertSettings).get(5, TimeUnit.SECONDS);
    assertThat(response.getWrittenRows()).isEqualTo(1);

    QuerySettings settings = new QuerySettings().setFormat(ClickHouseFormat.CSV);
    try (QueryResponse resp =
        client.query("SELECT * FROM " + tableName, settings).get(5, TimeUnit.SECONDS)) {
      BufferedReader reader =
          new BufferedReader(new InputStreamReader(resp.getInputStream(), UTF_8));
      String jsonStr = StringEscapeUtils.unescapeCsv(reader.lines().findFirst().get());
      assertThat(jsonStr).isEqualTo(originalJsonStr);
    }

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("DROP TABLE " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "DROP TABLE IF EXISTS " + tableName, "DROP TABLE"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("CREATE TABLE " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(createSQL, "CREATE TABLE"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName(dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "DESCRIBE TABLE pojo_with_json_table FORMAT TSKV", null))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("INSERT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions(
                                "insert into pojo_with_json_table (id, value, type) values(?, ?, ?),(?, ?, ?),(?, ?, ?)",
                                "INSERT"))),
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("SELECT " + dbName)
                        .hasKind(SpanKind.CLIENT)
                        .hasNoParent()
                        .hasAttributesSatisfyingExactly(
                            attributeAssertions("SELECT * FROM pojo_with_json_table", "SELECT"))));
  }

  @SuppressWarnings("unused")
  public static class PojoWithJSON {
    private String eventPayload;

    public String getEventPayload() {
      return eventPayload;
    }

    public void setEventPayload(String eventPayload) {
      this.eventPayload = eventPayload;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof PojoWithJSON)) {
        return false;
      }
      PojoWithJSON that = (PojoWithJSON) o;
      return Objects.equals(eventPayload, that.eventPayload);
    }

    @Override
    public int hashCode() {
      return Objects.hash(eventPayload);
    }

    @Override
    public String toString() {
      return "PojoWithJSON{" + "eventPayload='" + eventPayload + '\'' + '}';
    }
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
