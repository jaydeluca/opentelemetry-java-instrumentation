/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.clickhouse.client.api.transport.Endpoint;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Reports the endpoint the client actually selected for the in-flight query. {@code getEndpoint()}
 * returns the initial endpoint and, since {@code getNextAliveNode()} ends by delegating to it, also
 * the endpoint chosen after a failover; capturing it here covers both cases.
 */
class ClientNodeSelectorInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.transport.ClientNodeSelector");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic().and(named("getEndpoint")).and(takesArguments(0)),
        getClass().getName() + "$GetEndpointAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetEndpointAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Return @Nullable Endpoint endpoint) {
      ClickHouseDbRequest request = ClickHouseEndpointTracker.get();
      if (request != null && endpoint != null) {
        request.setEndpoint(endpoint.getHost(), endpoint.getPort());
      }
    }
  }
}
