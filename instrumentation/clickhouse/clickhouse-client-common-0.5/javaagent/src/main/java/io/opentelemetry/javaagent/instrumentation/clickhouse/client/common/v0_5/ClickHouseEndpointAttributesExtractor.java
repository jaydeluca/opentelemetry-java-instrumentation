/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import static io.opentelemetry.semconv.ServerAttributes.SERVER_ADDRESS;
import static io.opentelemetry.semconv.ServerAttributes.SERVER_PORT;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * Records the endpoint that was actually contacted to serve the query, overriding the best-effort
 * value captured when the span started. The client picks the endpoint after the query has started,
 * so the endpoint is not known yet when the span is created. If a query ever contacts more than one
 * endpoint, the last one contacted is the one reported.
 */
final class ClickHouseEndpointAttributesExtractor
    implements AttributesExtractor<ClickHouseDbRequest, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes, Context parentContext, ClickHouseDbRequest request) {}

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      ClickHouseDbRequest request,
      @Nullable Void response,
      @Nullable Throwable error) {
    if (request.getHost() != null) {
      attributes.put(SERVER_ADDRESS, request.getHost());
    }
    if (request.getPort() != null) {
      attributes.put(SERVER_PORT, request.getPort());
    }
  }
}
