/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.semconv.db;

import javax.annotation.Nullable;

/** An interface for getting attributes common to database clients. */
public interface DbClientCommonAttributesGetter<REQUEST, RESPONSE> {

  @Deprecated
  @Nullable
  default String getSystem(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  @Nullable
  default String getDbSystem(REQUEST request) {
    return getSystem(request);
  }

  @Deprecated
  @Nullable
  default String getUser(REQUEST request) {
    return null;
  }

  /**
   * @deprecated Use {@link #getDbNamespace(Object)} instead.
   */
  @Deprecated
  @Nullable
  default String getName(REQUEST request) {
    return null;
  }

  // TODO: make this required to implement
  @Nullable
  default String getDbNamespace(REQUEST request) {
    return getName(request);
  }

  @Deprecated
  @Nullable
  default String getConnectionString(REQUEST request) {
    return null;
  }

  @Nullable
  default String getResponseStatus(@Nullable RESPONSE response, @Nullable Throwable error) {
    return null;
  }
}
