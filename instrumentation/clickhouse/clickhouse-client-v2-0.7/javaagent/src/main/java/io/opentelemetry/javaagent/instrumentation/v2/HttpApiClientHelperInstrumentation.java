/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.v2;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

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
    public static OutputStreamWrapper onEnter(
        @Advice.Argument(value = 2, readOnly = false) IOCallback<OutputStream> writeCallback) {

      System.out.println("made it to enter advice again");
      OutputStreamWrapper capturingStream = new OutputStreamWrapper(new ByteArrayOutputStream());
      writeCallback = new OutputStreamWrapperCallback(writeCallback, capturingStream);
      return capturingStream;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Argument(2) Object settings,
        @Advice.Enter OutputStreamWrapper capturingStream) {

      System.out.println("made it to exit");
      if (capturingStream != null) {
        String capturedSql = capturingStream.getCapturedData();
        System.out.println("Captured SQL: " + capturedSql);
      }
    }
  }
}
