/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.opentelemetry.instrumentation.api.incubator.instrumenter.InstrumenterCustomizer;
import io.opentelemetry.instrumentation.thread.internal.ThreadDetailsAttributesExtractor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThreadDetailsInstrumenterCustomizerProviderTest {

  private final ThreadDetailsInstrumenterCustomizerProvider provider =
      new ThreadDetailsInstrumenterCustomizerProvider();

  @AfterEach
  void cleanup() {
    // Clear the system property after each test
    System.clearProperty("otel.javaagent.add-thread-details");
  }

  @Test
  void shouldAddThreadDetailsWhenEnabled() {
    System.setProperty("otel.javaagent.add-thread-details", "true");
    InstrumenterCustomizer customizer = mock(InstrumenterCustomizer.class);

    provider.customize(customizer);

    verify(customizer).addAttributesExtractor(any(ThreadDetailsAttributesExtractor.class));
  }

  @Test
  void shouldAddThreadDetailsByDefault() {
    // Property not set - should default to true
    InstrumenterCustomizer customizer = mock(InstrumenterCustomizer.class);

    provider.customize(customizer);

    verify(customizer).addAttributesExtractor(any(ThreadDetailsAttributesExtractor.class));
  }

  @Test
  void shouldNotAddThreadDetailsWhenDisabled() {
    System.setProperty("otel.javaagent.add-thread-details", "false");
    InstrumenterCustomizer customizer = mock(InstrumenterCustomizer.class);

    provider.customize(customizer);

    verify(customizer, never()).addAttributesExtractor(any());
  }
}
