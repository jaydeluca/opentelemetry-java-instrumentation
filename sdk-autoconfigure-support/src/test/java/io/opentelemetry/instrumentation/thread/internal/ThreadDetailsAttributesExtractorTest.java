/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import org.junit.jupiter.api.Test;

class ThreadDetailsAttributesExtractorTest {

  private final ThreadDetailsAttributesExtractor<String, String> extractor =
      new ThreadDetailsAttributesExtractor<>();

  @Test
  void shouldExtractThreadDetails() {
    Thread currentThread = Thread.currentThread();
    AttributesBuilder attributes = Attributes.builder();

    extractor.onStart(attributes, Context.root(), "request");

    Attributes result = attributes.build();
    assertThat(result)
        .containsEntry(ThreadIncubatingAttributes.THREAD_ID, currentThread.getId())
        .containsEntry(ThreadIncubatingAttributes.THREAD_NAME, currentThread.getName());
  }

  @Test
  void shouldNotAddAttributesOnEnd() {
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(attributes, Context.root(), "request", "response", null);

    assertThat(attributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldNotAddAttributesOnEndWithError() {
    AttributesBuilder attributes = Attributes.builder();

    extractor.onEnd(
        attributes, Context.root(), "request", "response", new RuntimeException("test"));

    assertThat(attributes.build().isEmpty()).isTrue();
  }

  @Test
  void shouldUseCorrectAttributeKeys() {
    // Verify that the attribute keys match the constants
    assertThat(ThreadDetailsAttributesExtractor.THREAD_ID)
        .isEqualTo(AttributeKey.longKey("thread.id"));
    assertThat(ThreadDetailsAttributesExtractor.THREAD_NAME)
        .isEqualTo(AttributeKey.stringKey("thread.name"));
  }
}
