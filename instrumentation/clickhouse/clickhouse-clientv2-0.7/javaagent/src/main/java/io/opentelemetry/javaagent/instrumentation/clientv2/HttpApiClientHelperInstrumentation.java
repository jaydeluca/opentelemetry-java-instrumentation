/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clientv2;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.clickhouse.client.api.internal.HttpAPIClientHelper;
import io.opentelemetry.javaagent.bootstrap.CallDepth;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.io.IOCallback;

public class HttpApiClientHelperInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.clickhouse.client.api.internal.HttpAPIClientHelper");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(named("executeRequest")),
        this.getClass().getName() + "$ExecuteRequestAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteRequestAdvice {

    @Advice.OnMethodEnter
    public static void onEnter(
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Argument(value = 3, readOnly = false) IOCallback<OutputStream> writeCallback) {
      callDepth = CallDepth.forClass(HttpAPIClientHelper.class);
      if (callDepth.getAndIncrement() > 0) {
        return;
      }

      System.out.println("made it to helper enter advice");
      OutputStreamWrapper capturingStream = new OutputStreamWrapper(new ByteArrayOutputStream());
      writeCallback = new OutputStreamWrapperCallback(writeCallback, capturingStream);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelCallDepth") CallDepth callDepth,
        @Advice.Argument(2) Object settings,
        @Advice.Enter OutputStreamWrapper capturingStream) {
      if (callDepth.decrementAndGet() > 0) {
        return;
      }

      System.out.println("made it to exit of helper");
      if (capturingStream != null) {
        String capturedSql = capturingStream.getCapturedData();
        System.out.println("Captured SQL: " + capturedSql);
      }
    }
  }
}
