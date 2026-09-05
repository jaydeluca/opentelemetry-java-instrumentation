/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5;

import javax.annotation.Nullable;

/** Immutable host/port pair, swapped as a whole on {@link ClickHouseDbRequest}. */
final class ClickHouseEndpoint {

  static final ClickHouseEndpoint UNKNOWN = new ClickHouseEndpoint(null, null);

  @Nullable private final String host;
  @Nullable private final Integer port;

  private ClickHouseEndpoint(@Nullable String host, @Nullable Integer port) {
    this.host = host;
    this.port = port;
  }

  static ClickHouseEndpoint create(@Nullable String host, @Nullable Integer port) {
    return host == null && port == null ? UNKNOWN : new ClickHouseEndpoint(host, port);
  }

  @Nullable
  String getHost() {
    return host;
  }

  @Nullable
  Integer getPort() {
    return port;
  }
}
