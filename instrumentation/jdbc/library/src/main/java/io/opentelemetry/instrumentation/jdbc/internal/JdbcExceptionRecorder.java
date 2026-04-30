/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jdbc.internal;

import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_MESSAGE;
import static io.opentelemetry.semconv.ExceptionAttributes.EXCEPTION_TYPE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import java.sql.SQLException;
import java.util.function.BiConsumer;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
final class JdbcExceptionRecorder {

  static final BiConsumer<Span, Throwable> SANITIZING =
      (span, error) -> {
        if (error instanceof SQLException) {
          SQLException se = (SQLException) error;
          span.addEvent(
              "exception",
              Attributes.of(
                  EXCEPTION_TYPE,
                  se.getClass().getName(),
                  EXCEPTION_MESSAGE,
                  "SQL error [" + se.getErrorCode() + "/" + se.getSQLState() + "]"));
        } else {
          span.recordException(error);
        }
      };

  private JdbcExceptionRecorder() {}
}
