/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import javax.annotation.Nullable;

/**
 * Publishes the in-flight query's request so the transport advice can report the endpoint that is
 * actually contacted. The transport runs synchronously on the calling thread (the default; {@code
 * useAsyncRequests} is off), so a thread local is sufficient to bridge the query advice and the
 * transport advice. With async requests enabled the transport runs on another thread, the tracker
 * yields nothing there, and the seeded endpoint is reported instead.
 *
 * <p>Written by {@link ClickHouseClientV2Instrumentation} in the base module and read by {@link
 * ClickHouseEndpointUsageInstrumentation} in the endpoint module. Both resolve the same injected
 * copy of this class — and therefore the same thread local — because helper classes are cached per
 * application class loader. Moving either advice to a different helper package would silently break
 * that.
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
