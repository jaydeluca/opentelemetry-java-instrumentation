/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.v5_3;

import static java.util.Collections.emptyIterator;

import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.instrumentation.spring.webflux.v5_3.internal.HeaderUtil;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Set;
import javax.annotation.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

enum WebfluxTextMapGetter implements TextMapGetter<ServerWebExchange> {
  INSTANCE;

  // Cache the method to avoid repeated reflection lookups
  private static final Method HEADER_NAMES_METHOD;

  static {
    Method method = null;
    try {
      // Try Spring 7+ method first
      method = HttpHeaders.class.getMethod("headerNames");
    } catch (NoSuchMethodException e) {
      // Fall back to Spring 6 and earlier - keySet() from MultiValueMap
      try {
        method = HttpHeaders.class.getMethod("keySet");
      } catch (NoSuchMethodException ex) {
        // This should not happen in any supported Spring version
        throw new IllegalStateException(
            "Neither headerNames() nor keySet() found on HttpHeaders", ex);
      }
    }
    HEADER_NAMES_METHOD = method;
  }

  @Override
  public Iterable<String> keys(ServerWebExchange exchange) {
    HttpHeaders headers = exchange.getRequest().getHeaders();
    try {
      // Safe cast: both HttpHeaders.headerNames() and HttpHeaders.keySet() return Set<String>
      @SuppressWarnings("unchecked")
      Set<String> keys = (Set<String>) HEADER_NAMES_METHOD.invoke(headers);
      return keys;
    } catch (Exception e) {
      // If reflection fails, return empty iterable to avoid breaking the instrumentation
      throw new IllegalStateException("Failed to get header names from HttpHeaders", e);
    }
  }

  @Nullable
  @Override
  public String get(@Nullable ServerWebExchange exchange, String key) {
    if (exchange == null) {
      return null;
    }
    return exchange.getRequest().getHeaders().getFirst(key);
  }

  @Override
  public Iterator<String> getAll(@Nullable ServerWebExchange exchange, String key) {
    if (exchange == null) {
      return emptyIterator();
    }
    return HeaderUtil.getHeader(exchange.getRequest().getHeaders(), key).iterator();
  }
}
