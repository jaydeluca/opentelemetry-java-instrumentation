/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;

/**
 * A {@link ConfigProvider} that records all configuration accesses during test execution. This
 * wraps the real ConfigProvider and returns {@link RecordingDeclarativeConfigProperties} instances
 * that track what configurations are accessed.
 *
 * <p>The recorded configuration usages can be retrieved via {@link #getRecordedUsages()} and
 * written to metadata files for documentation generation.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RecordingConfigProvider implements ConfigProvider {

  private final ConfigProvider delegate;
  // Store raw data instead of ConfigUsage to avoid classloader issues
  // Map structure: path -> {path, key, type, defaultValue, actualValue}
  private final Map<String, Map<String, Object>> recordedUsages = new ConcurrentHashMap<>();
  @Nullable private volatile RecordingDeclarativeConfigProperties instrumentationConfig;

  public RecordingConfigProvider(ConfigProvider delegate) {
    this.delegate = delegate;
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    if (instrumentationConfig == null) {
      synchronized (this) {
        if (instrumentationConfig == null) {
          DeclarativeConfigProperties config = delegate.getInstrumentationConfig();
          if (config != null) {
            // Start with empty path - the underlying config starts empty, and DeclarativeConfigUtil
            // will call .get("java") which will add "java" to the path naturally
            instrumentationConfig =
                new RecordingDeclarativeConfigProperties(config, recordedUsages, "");
          }
        }
      }
    }
    return instrumentationConfig;
  }

  /**
   * Returns a map of all recorded configuration usages. The key is the full path (e.g.,
   * "instrumentation.java.graphql.capture_query") and the value is a map containing: - "path":
   * String (full path) - "key": String (property key) - "type": String (property type) -
   * "defaultValue": Object (default value, may be null) - "actualValue": Object (actual value, may
   * be null)
   */
  public Map<String, Map<String, Object>> getRecordedUsages() {
    return new ConcurrentHashMap<>(recordedUsages);
  }

  /** Clears all recorded configuration usages. This can be called between tests if needed. */
  public void reset() {
    recordedUsages.clear();
    instrumentationConfig = null;
  }
}
