/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

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

public class ClickHouseClientV2Instrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.Client");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("query")).and(takesArgument(0, named(String.class.getName()))),
        this.getClass().getName() + "$ClientQueryAdvice");
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

      Context parentContext = currentContext();

      //      String host =
      // clickhouseClient.getEndpoints().stream().findFirst().orElse("localhost");
      //      String serverAddress = host + ":" + clickhouseClient.getConfiguration();
      //      System.out.println("endpoints: " + clickhouseClient.getEndpoints());
      //      System.out.println("first:" + clickhouseClient.getEndpoints().stream().findFirst());

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
        System.out.println("Host: " + host);
        System.out.println("Port: " + port);
      } else {
        System.out.println("No match found");
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
