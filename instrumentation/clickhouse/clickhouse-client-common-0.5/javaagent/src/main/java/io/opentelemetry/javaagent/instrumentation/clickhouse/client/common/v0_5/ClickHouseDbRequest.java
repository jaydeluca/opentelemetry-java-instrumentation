/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import javax.annotation.Nullable;

/**
 * Carries the state of a single ClickHouse query. The endpoint is mutable because the one that is
 * actually contacted is only known once the client has picked it, which happens after the query has
 * already started.
 */
public final class ClickHouseDbRequest {

  // Written by the transport advice on the thread that runs the query (see
  // ClickHouseEndpointTracker) and read when the span ends. The reference is swapped as a whole so
  // that host and port can never be read from two different endpoints; volatile so that the write
  // stays visible if the client ever moves the transport call off the calling thread.
  private volatile ClickHouseEndpoint endpoint;

  @Nullable private final String namespace;
  private final String sql;

  private ClickHouseDbRequest(ClickHouseEndpoint endpoint, @Nullable String namespace, String sql) {
    this.endpoint = endpoint;
    this.namespace = namespace;
    this.sql = sql;
  }

  public static ClickHouseDbRequest create(
      @Nullable String host, @Nullable Integer port, @Nullable String namespace, String sql) {
    return new ClickHouseDbRequest(ClickHouseEndpoint.create(host, port), namespace, sql);
  }

  @Nullable
  public String getHost() {
    return endpoint.getHost();
  }

  @Nullable
  public Integer getPort() {
    return endpoint.getPort();
  }

  /** Updates the endpoint to the one the client actually contacted for this query. */
  public void setEndpoint(@Nullable String host, @Nullable Integer port) {
    this.endpoint = ClickHouseEndpoint.create(host, port);
  }

  @Nullable
  public String getNamespace() {
    return namespace;
  }

  public String getSql() {
    return sql;
  }
}
