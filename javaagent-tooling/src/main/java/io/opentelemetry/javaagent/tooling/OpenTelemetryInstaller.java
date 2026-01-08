/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.incubator.ExtendedOpenTelemetry;
import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.instrumentation.config.bridge.ConfigPropertiesBackedConfigProvider;
import io.opentelemetry.instrumentation.config.bridge.DeclarativeConfigPropertiesBridgeBuilder;
import io.opentelemetry.javaagent.bootstrap.OpenTelemetrySdkAccess;
import io.opentelemetry.javaagent.tooling.config.EarlyInitAgentConfig;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.SdkAutoconfigureAccess;
import io.opentelemetry.sdk.autoconfigure.internal.AutoConfigureUtil;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.Arrays;

public final class OpenTelemetryInstaller {

  /**
   * Install the {@link OpenTelemetrySdk} using autoconfigure, and return the {@link
   * AutoConfiguredOpenTelemetrySdk}.
   *
   * @return the {@link AutoConfiguredOpenTelemetrySdk}
   */
  public static AutoConfiguredOpenTelemetrySdk installOpenTelemetrySdk(
      ClassLoader extensionClassLoader, EarlyInitAgentConfig earlyConfig) {

    AutoConfiguredOpenTelemetrySdk autoConfiguredSdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            // Don't use setResultAsGlobal() - we need to wrap the SDK before setting as global
            .setServiceClassLoader(extensionClassLoader)
            .build();
    OpenTelemetrySdk sdk = autoConfiguredSdk.getOpenTelemetrySdk();
    ConfigProperties configProperties = AutoConfigureUtil.getConfig(autoConfiguredSdk);
    ConfigProvider configProvider;
    if (configProperties != null) {
      // Provide a fake declarative configuration based on config properties
      // so that declarative configuration API can be used everywhere
      configProvider = ConfigPropertiesBackedConfigProvider.create(configProperties);

      // TEST HOOK: Wrap with recording provider if metadata collection is enabled
      configProvider = wrapWithRecordingProviderIfEnabled(configProvider, extensionClassLoader);

      sdk = new ExtendedOpenTelemetrySdkWrapper(sdk, configProvider);
    } else {
      // Provide a fake ConfigProperties until we have migrated all runtime configuration
      // access to use declarative configuration API
      configProvider = ((ExtendedOpenTelemetry) sdk).getConfigProvider();

      // TEST HOOK: Wrap with recording provider if metadata collection is enabled
      configProvider = wrapWithRecordingProviderIfEnabled(configProvider, extensionClassLoader);

      configProperties = getDeclarativeConfigBridgedProperties(earlyConfig, configProvider);
    }

    setForceFlush(sdk);
    GlobalOpenTelemetry.set(sdk);

    return SdkAutoconfigureAccess.create(
        sdk,
        SdkAutoconfigureAccess.getResource(autoConfiguredSdk),
        configProperties,
        configProvider);
  }

  /**
   * Wraps the ConfigProvider with a recording provider if metadata collection is enabled. Uses
   * reflection to load the recording provider from the extension classloader.
   */
  private static ConfigProvider wrapWithRecordingProviderIfEnabled(
      ConfigProvider configProvider, ClassLoader extensionClassLoader) {
    boolean collectMetadata = Boolean.getBoolean("collectMetadata");
    if (!collectMetadata) {
      return configProvider;
    }

    try {
      // Load RecordingConfigProvider class from extension classloader
      Class<?> recordingProviderClass =
          Class.forName(
              "io.opentelemetry.javaagent.testing.exporter.RecordingConfigProvider",
              true,
              extensionClassLoader);

      // Create instance: new RecordingConfigProvider(configProvider)
      Object recordingProvider =
          recordingProviderClass.getConstructor(ConfigProvider.class).newInstance(configProvider);

      // Store the RecordingConfigProvider in AgentTestingExporterFactory for access from test side
      try {
        Class<?> factoryClass =
            Class.forName(
                "io.opentelemetry.javaagent.testing.exporter.AgentTestingExporterFactory",
                true,
                extensionClassLoader);
        java.lang.reflect.Field providerField =
            factoryClass.getDeclaredField("recordingConfigProvider");
        providerField.setAccessible(true);
        providerField.set(null, recordingProvider);
      } catch (Exception e2) {
        // Ignore - factory may not be available
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

      return (ConfigProvider) recordingProvider;
    } catch (Exception e) {
      // Silently fail if test classes are not available (e.g., in production builds)
      return configProvider;
    }
  }

  // Visible for testing
  static ConfigProperties getDeclarativeConfigBridgedProperties(
      EarlyInitAgentConfig earlyConfig, ConfigProvider configProvider) {
    return new DeclarativeConfigPropertiesBridgeBuilder()
        .addMapping("otel.javaagent", "agent")
        .addOverride("otel.instrumentation.common.default-enabled", defaultEnabled(configProvider))
        // these properties are used to initialize the SDK before the configuration file
        // is loaded for consistency, we pass them to the bridge, so that they can be read
        // later with the same value from the {@link DeclarativeConfigPropertiesBridge}
        .addOverride("otel.javaagent.debug", earlyConfig.getBoolean("otel.javaagent.debug", false))
        .addOverride("otel.javaagent.logging", earlyConfig.getString("otel.javaagent.logging"))
        .buildFromInstrumentationConfig(configProvider.getInstrumentationConfig());
  }

  private static boolean defaultEnabled(ConfigProvider configProvider) {
    DeclarativeConfigProperties instrumentationConfig = configProvider.getInstrumentationConfig();
    if (instrumentationConfig == null) {
      return true;
    }

    String mode =
        instrumentationConfig
            .getStructured("java", empty())
            .getStructured("agent", empty())
            .getString("instrumentation_mode", "default");

    switch (mode) {
      case "none":
        return false;
      case "default":
        return true;
      default:
        throw new ConfigurationException("Unknown instrumentation mode: " + mode);
    }
  }

  private static void setForceFlush(OpenTelemetrySdk sdk) {
    OpenTelemetrySdkAccess.internalSetForceFlush(
        (timeout, unit) -> {
          CompletableResultCode traceResult = sdk.getSdkTracerProvider().forceFlush();
          CompletableResultCode metricsResult = sdk.getSdkMeterProvider().forceFlush();
          CompletableResultCode logsResult = sdk.getSdkLoggerProvider().forceFlush();
          CompletableResultCode.ofAll(Arrays.asList(traceResult, metricsResult, logsResult))
              .join(timeout, unit);
        });
  }

  private OpenTelemetryInstaller() {}
}
