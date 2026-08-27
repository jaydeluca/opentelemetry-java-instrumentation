/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import javax.annotation.Nullable;

/**
 * Publishes the in-flight query's request so the endpoint-selection advice can report the endpoint
 * actually selected. Endpoint selection happens synchronously on the calling thread (the default;
 * {@code useAsyncRequests} is off), so a thread local is sufficient to bridge the query advice and
 * the selection advice. With async requests enabled the selection runs on another thread, the
 * tracker yields nothing there, and the seeded endpoint is reported instead.
 */
public final class ClickHouseEndpointTracker {

  private static final ThreadLocal<ClickHouseDbRequest> currentRequest = new ThreadLocal<>();

  public static void set(ClickHouseDbRequest request) {
    currentRequest.set(request);
  }

  @Nullable
  public static ClickHouseDbRequest get() {
    return currentRequest.get();
  }

  public static void clear() {
    currentRequest.remove();
  }

  private ClickHouseEndpointTracker() {}
}
