/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.v2;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.clickhouse.client.api.Client;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class ClickHouseClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.Client");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("query")).and(takesArgument(0, named(String.class.getName()))),
        this.getClass().getName() + "$ClientQueryAdvice");

    transformer.applyAdviceToMethod(
        isMethod().and(named("insert")).and(takesArgument(0, named(String.class.getName()))),
        this.getClass().getName() + "$ClientInsertAdvice");
  }

  @SuppressWarnings("unused")
  public static class ClientQueryAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ClickHouseScope onEnter(
        @Advice.This Client clickhouseClient, @Advice.Argument(0) String query) {

      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.getAndIncrement() > 0 || query == null) {
        return null;
      }

      System.out.println("got to query advice");

      Context parentContext = currentContext();

      String regex = "http://([^:]+):(\\d+)";

      String endpoint = clickhouseClient.getEndpoints().stream().findFirst().orElse(null);
      if (endpoint == null) {
        return null;
      }
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(endpoint);

      String host = "";
      int port = 8123;

      if (matcher.find()) {
        host = matcher.group(1);
        port = Integer.parseInt(matcher.group(2));
      }

      ClickHouseDbRequest request =
          ClickHouseDbRequest.create(host, port, clickhouseClient.getDefaultDatabase(), query);

      return ClickHouseScope.start(parentContext, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable, @Advice.Enter ClickHouseScope scope) {

      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.end(throwable);
    }
  }

  @SuppressWarnings("unused")
  public static class ClientInsertAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static ClickHouseScope onEnter(
        @Advice.This Client clickhouseClient, @Advice.Argument(0) String query) {

      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.getAndIncrement() > 0 || query == null) {
        return null;
      }

      System.out.println("got to insert advice");

      Context parentContext = currentContext();

      String endpoint = clickhouseClient.getEndpoints().stream().findFirst().orElse(null);
      if (endpoint == null) {
        return null;
      }
      String regex = "http://([^:]+):(\\d+)";
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(endpoint);

      String host = "";
      int port = 8123;

      if (matcher.find()) {
        host = matcher.group(1);
        port = Integer.parseInt(matcher.group(2));
      }

      ClickHouseDbRequest request =
          ClickHouseDbRequest.create(host, port, clickhouseClient.getDefaultDatabase(), query);

      return ClickHouseScope.start(parentContext, request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable, @Advice.Enter ClickHouseScope scope) {

      CallDepth callDepth = CallDepth.forClass(Client.class);
      if (callDepth.decrementAndGet() > 0 || scope == null) {
        return;
      }

      scope.end(throwable);
    }
  }
}
