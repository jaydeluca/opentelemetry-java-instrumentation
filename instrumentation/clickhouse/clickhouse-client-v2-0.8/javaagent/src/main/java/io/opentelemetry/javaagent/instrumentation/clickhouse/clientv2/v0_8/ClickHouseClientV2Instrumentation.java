/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8.ClickHouseClientV2Singletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isSubTypeOf;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.clickhouse.client.api.Client;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseDbRequest;
import io.opentelemetry.javaagent.instrumentation.clickhouse.client.common.v0_5.ClickHouseScope;
import java.util.Map;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class ClickHouseClientV2Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.Client");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("query"))
            .and(takesArgument(0, String.class))
            .and(takesArgument(1, isSubTypeOf(Map.class)))
            .and(takesArgument(2, named("com.clickhouse.client.api.query.QuerySettings"))),
        getClass().getName() + "$QueryAdvice");
  }

  // getEndpoints() is deprecated in 0.10.0+ but is still the only public way to obtain a seed
  // endpoint before the query runs; the endpoint-selection advice overrides it with the endpoint
  // actually used.
  @SuppressWarnings({"unused", "OtelDeprecatedApiUsage"})
  public static class QueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static ClickHouseScope onEnter(
        @Advice.This Client client, @Advice.Argument(0) @Nullable String sqlQuery) {
      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.getAndIncrement() > 0 || sqlQuery == null) {
        return null;
      }

      // Seed a best-effort endpoint. getEndpoints() returns an unordered set, and clientv2 may
      // fail over between endpoints while the query is in flight, so the endpoint actually used is
      // reported by ClickHouseEndpointSelectionInstrumentation (on clients that expose it) and
      // finalized when the span ends.
      String endpoint = client.getEndpoints().stream().findFirst().orElse(null);
      String database = client.getConfiguration().get("database");
      ClickHouseDbRequest request =
          ClickHouseClientV2Singletons.createRequest(endpoint, database, sqlQuery);
      ClickHouseEndpointTracker.set(request);

      Context parentContext = currentContext();
      return ClickHouseScope.start(instrumenter(), parentContext, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Thrown @Nullable Throwable throwable,
        @Advice.Enter @Nullable ClickHouseScope scope) {
      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      ClickHouseEndpointTracker.clear();
      if (scope != null) {
        scope.end(throwable);
      }
    }
  }
}
