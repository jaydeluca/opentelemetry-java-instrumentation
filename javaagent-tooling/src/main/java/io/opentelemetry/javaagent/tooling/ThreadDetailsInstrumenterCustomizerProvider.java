/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizerProvider;
import io.opentelemetry.instrumentation.api.internal.ConfigPropertiesUtil;
import io.opentelemetry.instrumentation.thread.internal.ThreadDetailsAttributesExtractor;

/**
 * {@link InstrumenterCustomizerProvider} that adds thread details (thread id and name) to all
 * instrumented spans when enabled via configuration.
 *
 * <p>Configuration: {@code otel.javaagent.add-thread-details} (default: {@code true})
 */
@AutoService(InstrumenterCustomizerProvider.class)
public class ThreadDetailsInstrumenterCustomizerProvider implements InstrumenterCustomizerProvider {

  private static final String ADD_THREAD_DETAILS = "otel.javaagent.add-thread-details";

  @Override
  public void customize(InstrumenterCustomizer customizer) {
    // Read from system properties/env vars via ConfigPropertiesUtil
    if (ConfigPropertiesUtil.getBoolean(ADD_THREAD_DETAILS, true)) {
      customizer.addAttributesExtractor(new ThreadDetailsAttributesExtractor<>());
    }
  }
}
