/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import com.clickhouse.client.api.ServerException;
import io.opentelemetry.instrumentation.api.incubator.semconv.net.internal.UrlParser;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseInstrumenterFactory;
import javax.annotation.Nullable;

public class ClickHouseClientV2Singletons {

  private static final String INSTRUMENTER_NAME = "io.opentelemetry.clickhouse-client-v2-0.8";
  private static final Instrumenter<ClickHouseDbRequest, Void> instrumenter;

  static {
    instrumenter =
        ClickHouseInstrumenterFactory.createInstrumenter(
            INSTRUMENTER_NAME,
            error -> {
              if (error instanceof ServerException) {
                int errorCode = ((ServerException) error).getCode();
                return errorCode == 0 ? null : Integer.toString(errorCode);
              }
              return null;
            });
  }

  public static Instrumenter<ClickHouseDbRequest, Void> instrumenter() {
    return instrumenter;
  }

  /**
   * Builds a request seeded with a best-effort endpoint. On clients that expose the endpoint the
   * transport advice overrides this with the endpoint actually contacted before the span ends; on
   * older clients it remains the reported endpoint.
   */
  public static ClickHouseDbRequest createRequest(
      @Nullable String endpoint, @Nullable String database, String sqlQuery) {
    String host = endpoint == null ? null : UrlParser.getHost(endpoint);
    Integer port = endpoint == null ? null : UrlParser.getPort(endpoint);
    return ClickHouseDbRequest.create(host, port, database, sqlQuery);
  }

  private ClickHouseClientV2Singletons() {}
}
