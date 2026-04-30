/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.jdbc.datasource.JdbcTelemetry;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class JdbcExceptionSanitizationTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  private static final String SENSITIVE_SQL = "SELECT * FROM users WHERE password = 'mysecret'";

  @Test
  void exceptionMessageSanitizedWhenQuerySanitizationEnabled() throws Exception {
    JdbcTelemetry telemetry = JdbcTelemetry.builder(testing.getOpenTelemetry()).build();
    Connection conn = buildThrowingConnection(telemetry);

    assertThatCode(() -> conn.createStatement().execute(SENSITIVE_SQL))
        .isInstanceOf(SQLException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfying(
                                    satisfies(
                                        EXCEPTION_MESSAGE,
                                        msg -> msg.doesNotContain("mysecret"))))));
  }

  @Test
  void exceptionMessageNotSanitizedWhenQuerySanitizationDisabled() throws Exception {
    JdbcTelemetry telemetry =
        JdbcTelemetry.builder(testing.getOpenTelemetry())
            .setQuerySanitizationEnabled(false)
            .build();
    Connection conn = buildThrowingConnection(telemetry);

    assertThatCode(() -> conn.createStatement().execute(SENSITIVE_SQL))
        .isInstanceOf(SQLException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfying(
                                    satisfies(
                                        EXCEPTION_MESSAGE, msg -> msg.contains("mysecret"))))));
  }

  @Test
  void exceptionTypePreservedAfterSanitization() throws Exception {
    JdbcTelemetry telemetry = JdbcTelemetry.builder(testing.getOpenTelemetry()).build();
    Connection conn = buildThrowingConnection(telemetry);

    assertThatCode(() -> conn.createStatement().execute(SENSITIVE_SQL))
        .isInstanceOf(SQLException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfying(
                                    equalTo(EXCEPTION_TYPE, "java.sql.SQLException")))));
  }

  @Test
  void nonSqlExceptionUnaffected() throws Exception {
    JdbcTelemetry telemetry = JdbcTelemetry.builder(testing.getOpenTelemetry()).build();
    TestConnection underlyingConn = spy(new TestConnection());
    Statement mockStatement = mock(Statement.class);
    when(underlyingConn.createStatement()).thenReturn(mockStatement);
    doAnswer(
            invoc -> {
              throw new RuntimeException("boom");
            })
        .when(mockStatement)
        .execute(anyString());
    Connection conn = ConnectionWrapper.wrap(underlyingConn, telemetry);

    assertThatCode(() -> conn.createStatement().execute("SELECT 1"))
        .isInstanceOf(RuntimeException.class);

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasEventsSatisfyingExactly(
                        event ->
                            event
                                .hasName("exception")
                                .hasAttributesSatisfying(
                                    satisfies(EXCEPTION_MESSAGE, msg -> msg.contains("boom"))))));
  }

  private static Connection buildThrowingConnection(JdbcTelemetry telemetry) throws Exception {
    TestConnection underlyingConn = spy(new TestConnection());
    Statement mockStatement = mock(Statement.class);
    when(underlyingConn.createStatement()).thenReturn(mockStatement);
    doAnswer(
            invoc -> {
              String executedSql = invoc.getArgument(0);
              throw new SQLException("Error in query: " + executedSql, "42P01", 1054);
            })
        .when(mockStatement)
        .execute(anyString());
    return ConnectionWrapper.wrap(underlyingConn, telemetry);
  }
}
