/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.thread.internal.ThreadDetailsAttributesExtractor;

/**
 * {@link InstrumenterCustomizerProvider} that adds thread details (thread id and name) to all
 * instrumented spans when enabled via Spring Boot configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 *
 * <p>This provider checks the {@code otel.instrumentation.common.thread-details.enabled} system
 * property (or environment variable) at runtime to determine if thread details should be added. The
 * property is controlled by Spring Boot's {@link ThreadDetailsAutoConfiguration} via the
 * {@code @ConditionalOnEnabledInstrumentation} annotation.
 */
public final class ThreadDetailsInstrumenterCustomizerProvider
    implements InstrumenterCustomizerProvider {

  private static final String THREAD_DETAILS_ENABLED =
      "otel.instrumentation.common.thread_details.enabled";

  public ThreadDetailsInstrumenterCustomizerProvider() {
    // Public no-arg constructor required for ServiceLoader
  }

  @Override
  public void customize(InstrumenterCustomizer customizer) {
    // Check if Spring Boot has enabled thread details via configuration
    // Default is false for Spring Boot (unlike the agent which defaults to true)
    if (ConfigPropertiesUtil.getBoolean(THREAD_DETAILS_ENABLED, false)) {
      customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>());
    }
  }
}
