/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.exporter;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Wraps {@link DeclarativeConfigProperties} to record all configuration accesses during test
 * execution. This allows us to document what configurations each instrumentation uses, along with
 * their types and default values.
 *
 * <p>The wrapper delegates all calls to the underlying implementation while recording each access
 * in a shared map. The current path is tracked to build the full hierarchical key.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class RecordingDeclarativeConfigProperties implements DeclarativeConfigProperties {

  private final DeclarativeConfigProperties delegate;
  // Store raw data instead of ConfigUsage to avoid classloader issues
  // Map structure: path -> {path, key, type, defaultValue, actualValue}
  private final Map<String, Map<String, Object>> recordedUsages;
  private final String currentPath;

  public RecordingDeclarativeConfigProperties(
      DeclarativeConfigProperties delegate,
      Map<String, Map<String, Object>> recordedUsages,
      String currentPath) {
    this.delegate = delegate;
    this.recordedUsages = recordedUsages;
    this.currentPath = currentPath;
  }

  @Override
  public String getString(String name) {
    return getString(name, null);
  }

  @Override
  public String getString(String name, @Nullable String defaultValue) {
    String value = delegate.getString(name, defaultValue);
    recordUsage(name, "string", defaultValue, value);
    return value;
  }

  @Override
  public Boolean getBoolean(String name) {
    Boolean value = delegate.getBoolean(name);
    // Record with null as default - if a default value is used later, it will be updated
    recordUsage(name, "boolean", null, value);
    return value;
  }

  /**
   * Records a boolean configuration usage with a default value. This is called by
   * ExtendedDeclarativeConfigProperties when getBoolean(name, defaultValue) is used. Updates the
   * existing usage if it was already recorded by getBoolean(), otherwise creates a new one.
   */
  public void recordBooleanWithDefault(String name, boolean defaultValue, Boolean actualValue) {
    String relativePath = currentPath.isEmpty() ? name : currentPath + "." + name;
    // Prepend "instrumentation." to match the expected path format for extraction
    String fullPath =
        relativePath.isEmpty() ? "instrumentation" : "instrumentation." + relativePath;
    Object finalValue = actualValue != null ? actualValue : defaultValue;
    Map<String, Object> usageData = new HashMap<>();
    usageData.put("path", fullPath);
    usageData.put("key", name);
    usageData.put("type", "boolean");
    usageData.put("defaultValue", defaultValue);
    usageData.put("actualValue", finalValue);
    // Use put() instead of putIfAbsent() to update the existing usage with the default value
    recordedUsages.put(fullPath, usageData);
  }

  @Override
  public Integer getInt(String name) {
    Integer value = delegate.getInt(name);
    recordUsage(name, "integer", null, value);
    return value;
  }

  @Override
  public Long getLong(String name) {
    Long value = delegate.getLong(name);
    recordUsage(name, "long", null, value);
    return value;
  }

  @Override
  public Double getDouble(String name) {
    Double value = delegate.getDouble(name);
    recordUsage(name, "double", null, value);
    return value;
  }

  @Nullable
  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    return getScalarList(name, scalarType, null);
  }

  @Nullable
  @Override
  public <T> List<T> getScalarList(
      String name, Class<T> scalarType, @Nullable List<T> defaultValue) {
    List<T> value = delegate.getScalarList(name, scalarType, defaultValue);
    recordUsage(
        name,
        "list<" + scalarType.getSimpleName().toLowerCase(Locale.ROOT) + ">",
        defaultValue,
        value);
    return value;
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    return getStructured(name, null);
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getStructured(
      String name, @Nullable DeclarativeConfigProperties defaultValue) {
    DeclarativeConfigProperties value = delegate.getStructured(name, defaultValue);

    // For structured properties, we create a new recording wrapper with extended path
    // but don't record the getStructured call itself - only the leaf property accesses
    String newPath = currentPath.isEmpty() ? name : currentPath + "." + name;

    if (value == null) {
      // Even if the property doesn't exist, we still need to return a recording wrapper
      // so that subsequent property accesses are recorded. Use defaultValue if provided,
      // otherwise create a wrapper around empty().
      if (defaultValue != null) {
        return new RecordingDeclarativeConfigProperties(defaultValue, recordedUsages, newPath);
      }
      // If no defaultValue provided, return null (this shouldn't happen in practice)
      return null;
    }

    return new RecordingDeclarativeConfigProperties(value, recordedUsages, newPath);
  }

  @Override
  public List<DeclarativeConfigProperties> getStructuredList(String name) {
    List<DeclarativeConfigProperties> values = delegate.getStructuredList(name);

    // For structured lists, we record the access but don't wrap the elements for now
    // This is complex and may not be needed for most instrumentations
    recordUsage(name, "list<structured>", null, values);
    return values;
  }

  @Override
  public Set<String> getPropertyKeys() {
    return delegate.getPropertyKeys();
  }

  @Override
  public ComponentLoader getComponentLoader() {
    return delegate.getComponentLoader();
  }

  private void recordUsage(
      String name, String type, @Nullable Object defaultValue, @Nullable Object actualValue) {
    String relativePath = currentPath.isEmpty() ? name : currentPath + "." + name;
    // Prepend "instrumentation." to match the expected path format for extraction
    String fullPath =
        relativePath.isEmpty() ? "instrumentation" : "instrumentation." + relativePath;

    // Use putIfAbsent to avoid overwriting if the same property is accessed multiple times
    // We want to capture the first access which typically has the most meaningful context
    if (!recordedUsages.containsKey(fullPath)) {
      Map<String, Object> usageData = new HashMap<>();
      usageData.put("path", fullPath);
      usageData.put("key", name);
      usageData.put("type", type);
      usageData.put("defaultValue", defaultValue);
      usageData.put("actualValue", actualValue);
      recordedUsages.put(fullPath, usageData);
    }
  }

  /** Creates a new recording wrapper for the root instrumentation config. */
  public static RecordingDeclarativeConfigProperties createRoot(
      DeclarativeConfigProperties delegate) {
    return new RecordingDeclarativeConfigProperties(
        delegate, new java.util.concurrent.ConcurrentHashMap<>(), "");
  }
}
