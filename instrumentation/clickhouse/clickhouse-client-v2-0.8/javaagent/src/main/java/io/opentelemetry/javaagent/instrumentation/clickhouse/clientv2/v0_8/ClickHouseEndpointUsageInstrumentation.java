/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.clickhouse.client.api.transport.Endpoint;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Reports the endpoint the client actually contacted for the in-flight query.
 *
 * <p>The obvious hook is {@code Client#getNextAliveNode()}, where the client picks an endpoint, but
 * it picks more often than it connects: the retry loop picks again after a failed attempt and only
 * then re-checks whether any attempts remain, so a query that exhausts its retries picks one last
 * endpoint it never contacts. Advising the transport reports only endpoints that an attempt was
 * actually made against.
 *
 * <p>Both hooks agree today, because {@code getNextAliveNode()} is a stub that always returns the
 * first configured endpoint (verified through client-v2 0.10.0) — the client has no failover. What
 * this module adds today is the identity of that endpoint, which {@code Client#getEndpoints()}
 * cannot give because it returns an unordered {@code Set}. Advising the transport is what keeps the
 * reported endpoint correct if the client ever starts living up to the method's name.
 *
 * <p>Matched by parameter type rather than by method name because the transport entry point was
 * renamed across versions ({@code executeRequest(Endpoint, ...)} in 0.9.x, {@code
 * createRequest(Endpoint, ...)} in 0.10.0+). In both, the endpoint that is about to be used is the
 * first argument.
 */
class ClickHouseEndpointUsageInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.internal.HttpAPIClientHelper");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(takesArgument(0, named("com.clickhouse.client.api.transport.Endpoint"))),
        getClass().getName() + "$EndpointUsedAdvice");
  }

  @SuppressWarnings("unused")
  public static class EndpointUsedAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static void onEnter(@Advice.Argument(0) @Nullable Endpoint endpoint) {
      ClickHouseDbRequest request = ClickHouseEndpointTracker.get();
      if (request != null && endpoint != null) {
        request.setEndpoint(endpoint.getHost(), endpoint.getPort());
      }
    }
  }
}
