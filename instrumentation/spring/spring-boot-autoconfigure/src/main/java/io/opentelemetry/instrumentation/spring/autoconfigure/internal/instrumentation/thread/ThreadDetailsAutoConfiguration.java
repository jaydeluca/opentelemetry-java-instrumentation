/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.thread;

import io.opentelemetry.instrumentation.spring.autoconfigure.internal.ConditionalOnEnabledInstrumentation;
import javax.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for thread details instrumentation.
 *
 * <p>This configuration is activated when the {@code
 * otel.instrumentation.common.thread_details.enabled} property is set to {@code true}.
 *
 * <p>When activated, this configuration sets a system property that {@link
 * ThreadDetailsInstrumenterCustomizerProvider} can read. This bridges Spring Boot's property system
 * with the ServiceLoader provider.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@ConditionalOnEnabledInstrumentation(module = "common.thread-details", enabledByDefault = false)
@Configuration
public class ThreadDetailsAutoConfiguration {

  private static final String THREAD_DETAILS_ENABLED =
      "otel.instrumentation.common.thread_details.enabled";

  /**
   * When Spring Boot activates this configuration (because the property is true in Spring's
   * environment), set a system property so that {@link ThreadDetailsInstrumenterCustomizerProvider}
   * can read it via {@code ConfigPropertiesUtil}.
   *
   * <p>This bridges Spring Boot's property system (which may use application.properties,
   * application.yml, or test annotations) to JVM system properties which the ServiceLoader provider
   * can access.
   */
  @PostConstruct
  public void enableThreadDetails() {
    System.setProperty(THREAD_DETAILS_ENABLED, "true");
  }
}
