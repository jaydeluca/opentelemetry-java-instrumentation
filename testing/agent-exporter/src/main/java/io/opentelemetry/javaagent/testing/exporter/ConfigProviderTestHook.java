/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.api.incubator.config.ConfigProvider;

/**
 * Test hook for wrapping ConfigProvider. This class is loaded via reflection from
 * OpenTelemetryInstaller to avoid touching production code.
 */
public final class ConfigProviderTestHook {

  /**
   * Wraps the ConfigProvider with a recording provider if metadata collection is enabled.
   *
   * @param configProvider the original ConfigProvider
   * @param extensionClassLoader the extension classloader
   * @return the wrapped ConfigProvider, or the original if wrapping is not needed
   */
  public static ConfigProvider wrap(
      ConfigProvider configProvider, ClassLoader extensionClassLoader) {
    boolean collectMetadata = Boolean.getBoolean("collectMetadata");
    if (!collectMetadata) {
      return configProvider;
    }

    try {
      RecordingConfigProvider recordingProvider = new RecordingConfigProvider(configProvider);

      // Store the RecordingConfigProvider in AgentTestingExporterFactory for access from test side
      try {
        java.lang.reflect.Field providerField =
            AgentTestingExporterFactory.class.getDeclaredField("recordingConfigProvider");
        providerField.setAccessible(true);
        providerField.set(null, recordingProvider);
      } catch (Exception e2) {
        // Ignore - field may not exist
      }

      // Also try to set in ConfigRecordingAgentListener (may fail due to classloader, that's OK)
      try {
        // Try loading from the current thread's context classloader (test classloader)
        ClassLoader testClassLoader = Thread.currentThread().getContextClassLoader();
        if (testClassLoader != null) {
          Class<?> listenerClass =
              Class.forName(
                  "io.opentelemetry.instrumentation.testing.internal.ConfigRecordingAgentListener",
                  true,
                  testClassLoader);
          listenerClass
              .getMethod("setRecordingProvider", Object.class)
              .invoke(null, recordingProvider);
        }
      } catch (Exception e) {
        // Don't fail - the provider will be accessed from the factory via reflection
      }

      return recordingProvider;
    } catch (RuntimeException e) {
      // If wrapping fails, return original provider
      return configProvider;
    }
  }

  private ConfigProviderTestHook() {}
}
