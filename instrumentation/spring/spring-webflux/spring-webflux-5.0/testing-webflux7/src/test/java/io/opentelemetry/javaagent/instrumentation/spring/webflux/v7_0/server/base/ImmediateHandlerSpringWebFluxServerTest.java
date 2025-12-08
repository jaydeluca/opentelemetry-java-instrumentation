/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v7_0.server.base;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.spring.webflux.server.AbstractImmediateHandlerSpringWebFluxServerTest;
import io.opentelemetry.instrumentation.testing.junit.http.ServerEndpoint;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.reactor.netty.NettyReactiveWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Tests the case where {@code Mono<ServerResponse>} from a router function is already a fully
 * constructed response with no deferred actions. For exception endpoint, the exception is thrown
 * within router function scope.
 */
class ImmediateHandlerSpringWebFluxServerTest
    extends AbstractImmediateHandlerSpringWebFluxServerTest {
  @Override
  protected Class<?> getApplicationClass() {
    return Application.class;
  }

  @Configuration
  @EnableAutoConfiguration
  static class Application {

    @Bean
    RouterFunction<ServerResponse> router() {
      return new RouteFactory().createRoutes();
    }

    @Bean
    NettyReactiveWebServerFactory nettyFactory() {
      return new NettyReactiveWebServerFactory();
    }
  }

  static class RouteFactory extends ServerTestRouteFactory {
    @Override
    protected Mono<ServerResponse> wrapResponse(
        ServerEndpoint endpoint, Mono<ServerResponse> response, Runnable spanAction) {
      Tracer tracer = GlobalOpenTelemetry.getTracer("test");
      return Mono.defer(
          () -> {
            Span span = tracer.spanBuilder("controller").setSpanKind(SpanKind.INTERNAL).startSpan();
            Scope scope = span.makeCurrent();
            try {
              spanAction.run();
              return response
                  .doOnError(
                      error -> {
                        span.recordException(error);
                        span.setStatus(StatusCode.ERROR);
                      })
                  .doFinally(
                      signalType -> {
                        scope.close();
                        span.end();
                      });
            } catch (Throwable error) {
              span.recordException(error);
              span.setStatus(StatusCode.ERROR);
              scope.close();
              span.end();
              return Mono.error(error);
            }
          });
    }
  }
}
