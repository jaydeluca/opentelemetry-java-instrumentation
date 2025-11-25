/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import io.opentelemetry.instrumentation.thread.internal.ThreadDetailsAttributesExtractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
@ConditionalOnEnabledInstrumentation(module = "common.thread-details", enabledByDefault = false)
@Configuration
public class ThreadDetailsAutoConfiguration {

  @Bean
  public InstrumenterCustomizerProvider threadDetailsInstrumenterCustomizer() {
    return customizer -> {
      customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>());
    };
  }
}
