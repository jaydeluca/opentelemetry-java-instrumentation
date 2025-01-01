package io.opentelemetry.javaagent.instrumentation.clickhouse;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.io.IOCallback;

import java.io.OutputStream;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;

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
    public static void onEnter(@Advice.Argument(value = 2, readOnly = false) IOCallback<OutputStream> writeCallback,
        @Advice.Local("capturingStream") OutputStreamWrapper capturingStream) {

//      capturingStream = new OutputStreamWrapper(writeCallback.execute());
//      IOCallback<OutputStream> finalCapturingStream = capturingStream::execute;
//      writeCallback = finalCapturingStream;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Argument(2) Object settings,
        @Advice.Local("capturingStream") OutputStreamWrapper capturingStream) {

      System.out.println("made it to exit");
      if (capturingStream != null) {
        String capturedSql = capturingStream.getCapturedData();
        // Add the captured SQL to your trace
        System.out.println("Captured SQL: " + capturedSql);
        // Add logic to attach captured SQL to OpenTelemetry trace
      }

    }
  }

}
