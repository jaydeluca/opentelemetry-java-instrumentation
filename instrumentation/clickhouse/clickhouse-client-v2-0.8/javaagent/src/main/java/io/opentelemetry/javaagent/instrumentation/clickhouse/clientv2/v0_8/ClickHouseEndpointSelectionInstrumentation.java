/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.clickhouse.client.api.transport.Endpoint;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Reports the endpoint the client actually selected for the in-flight query. {@code
 * getNextAliveNode()} is where the client picks an endpoint, it is called when the query starts and
 * again when the query is retried, so the last value it returns is the endpoint that served the
 * query.
 */
class ClickHouseEndpointSelectionInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.Client");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    // matched by return type rather than arity: the method is package-private/private and its
    // signature has changed across versions (no-arg in 0.9.x/0.10.0, taking the failed endpoint in
    // later ones).
    transformer.applyAdviceToMethod(
        named("getNextAliveNode")
            .and(returns(named("com.clickhouse.client.api.transport.Endpoint"))),
        getClass().getName() + "$GetNextAliveNodeAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetNextAliveNodeAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Return @Nullable Endpoint endpoint) {
      ClickHouseDbRequest request = ClickHouseEndpointTracker.get();
      if (request != null && endpoint != null) {
        request.setEndpoint(endpoint.getHost(), endpoint.getPort());
      }
    }
  }
}
