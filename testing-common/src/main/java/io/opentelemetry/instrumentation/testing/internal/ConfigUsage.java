/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import javax.annotation.Nullable;

/**
 * Represents a single configuration property access during test execution. Records the key, type,
 * default value, actual value, and whether the default was used.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ConfigUsage {
  private final String path;
  private final String key;
  private final String type;
  @Nullable private final String defaultValue;
  @Nullable private final String actualValue;
  private final boolean wasDefaultUsed;

  public ConfigUsage(
      String path,
      String key,
      String type,
      @Nullable Object defaultValue,
      @Nullable Object actualValue) {
    this.path = path;
    this.key = key;
    this.type = type;
    this.defaultValue = defaultValue == null ? null : String.valueOf(defaultValue);
    this.actualValue = actualValue == null ? null : String.valueOf(actualValue);

    // Determine if default was used
    if (actualValue == null && defaultValue != null) {
      this.wasDefaultUsed = true;
    } else if (actualValue != null && defaultValue != null) {
      this.wasDefaultUsed = actualValue.equals(defaultValue);
    } else {
      this.wasDefaultUsed = false;
    }
  }

  public String getPath() {
    return path;
  }

  public String getKey() {
    return key;
  }

  public String getType() {
    return type;
  }

  @Nullable
  public String getDefaultValue() {
    return defaultValue;
  }

  @Nullable
  public String getActualValue() {
    return actualValue;
  }

  public boolean wasDefaultUsed() {
    return wasDefaultUsed;
  }

  @Override
  public String toString() {
    return "ConfigUsage{"
        + "path='"
        + path
        + '\''
        + ", key='"
        + key
        + '\''
        + ", type='"
        + type
        + '\''
        + ", defaultValue='"
        + defaultValue
        + '\''
        + ", actualValue='"
        + actualValue
        + '\''
        + ", wasDefaultUsed="
        + wasDefaultUsed
        + '}';
  }
}
