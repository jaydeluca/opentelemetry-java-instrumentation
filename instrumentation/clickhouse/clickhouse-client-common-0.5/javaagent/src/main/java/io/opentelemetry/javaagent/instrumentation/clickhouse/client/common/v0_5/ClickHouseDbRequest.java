/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import javax.annotation.Nullable;

/**
 * Carries the state of a single ClickHouse query. The host/port are mutable because the endpoint
 * actually used is only known once the client selects (and possibly fails over between) endpoints
 * while the query is in flight.
 */
public final class ClickHouseDbRequest {

  @Nullable private volatile String host;
  @Nullable private volatile Integer port;
  @Nullable private final String namespace;
  private final String sql;

  private ClickHouseDbRequest(
      @Nullable String host, @Nullable Integer port, @Nullable String namespace, String sql) {
    this.host = host;
    this.port = port;
    this.namespace = namespace;
    this.sql = sql;
  }

  public static ClickHouseDbRequest create(
      @Nullable String host, @Nullable Integer port, @Nullable String namespace, String sql) {
    return new ClickHouseDbRequest(host, port, namespace, sql);
  }

  @Nullable
  public String getHost() {
    return host;
  }

  @Nullable
  public Integer getPort() {
    return port;
  }

  /** Updates the endpoint to the one the client actually selected for this query. */
  public void setEndpoint(@Nullable String host, @Nullable Integer port) {
    this.host = host;
    this.port = port;
  }

  @Nullable
  public String getNamespace() {
    return namespace;
  }

  public String getSql() {
    return sql;
  }
}
