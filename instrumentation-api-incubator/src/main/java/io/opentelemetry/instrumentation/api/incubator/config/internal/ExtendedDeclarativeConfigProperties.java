/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.incubator.config.internal;

import static io.opentelemetry.api.incubator.config.DeclarativeConfigProperties.empty;

import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.common.ComponentLoader;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class ExtendedDeclarativeConfigProperties implements DeclarativeConfigProperties {

  private final DeclarativeConfigProperties delegate;

  ExtendedDeclarativeConfigProperties(DeclarativeConfigProperties delegate) {
    this.delegate = delegate;
  }

  public ExtendedDeclarativeConfigProperties get(String name) {
    return new ExtendedDeclarativeConfigProperties(delegate.getStructured(name, empty()));
  }

  @Nullable
  @Override
  public String getString(String name) {
    return delegate.getString(name);
  }

  @Nullable
  @Override
  public Boolean getBoolean(String name) {
    return delegate.getBoolean(name);
  }

  /**
   * Returns a boolean-valued configuration property or {@code defaultValue} if a property with name
   * {@code name} has not been configured.
   */
  @Override
  public boolean getBoolean(String name, boolean defaultValue) {
    Boolean value = delegate.getBoolean(name);
    if (value == null) {
      // If delegate is a RecordingDeclarativeConfigProperties, update the recorded usage with
      // the default value
      if (delegate
          .getClass()
          .getName()
          .equals(
              "io.opentelemetry.javaagent.testing.exporter.RecordingDeclarativeConfigProperties")) {
        try {
          java.lang.reflect.Method recordMethod =
              delegate
                  .getClass()
                  .getMethod(
                      "recordBooleanWithDefault", String.class, boolean.class, Boolean.class);
          recordMethod.invoke(delegate, name, defaultValue, null);
        } catch (Exception e) {
          // If reflection fails, just continue - the usage was already recorded by getBoolean()
        }
      }
      return defaultValue;
    }
    return value;
  }

  @Nullable
  @Override
  public Integer getInt(String name) {
    return delegate.getInt(name);
  }

  @Nullable
  @Override
  public Long getLong(String name) {
    return delegate.getLong(name);
  }

  @Nullable
  @Override
  public Double getDouble(String name) {
    return delegate.getDouble(name);
  }

  @Nullable
  @Override
  public <T> List<T> getScalarList(String name, Class<T> scalarType) {
    return delegate.getScalarList(name, scalarType);
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getStructured(String name) {
    return delegate.getStructured(name);
  }

  @Nullable
  @Override
  public List<DeclarativeConfigProperties> getStructuredList(String name) {
    return delegate.getStructuredList(name);
  }

  @Override
  public Set<String> getPropertyKeys() {
    return delegate.getPropertyKeys();
  }

  @Override
  public ComponentLoader getComponentLoader() {
    return delegate.getComponentLoader();
  }
}
