/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.instrumentation.api.internal.RuntimeVirtualFieldSupplier;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

public final class VirtualFieldMappings {
  private final Set<Mapping> mappings;

  // Mapping has a package private constructor, so only this package can build the argument
  VirtualFieldMappings(Set<Mapping> mappings) {
    this.mappings = mappings;
  }

  public int size() {
    return mappings.size();
  }

  public boolean isEmpty() {
    return mappings.isEmpty();
  }

  /**
   * Returns {@code true} if the <em>unnamed</em> virtual field for this type pair is registered.
   */
  public boolean hasUnnamedMapping(String typeName, String fieldTypeName) {
    return mappings.contains(
        new Mapping(RuntimeVirtualFieldSupplier.DEFAULT_FIELD_NAME, typeName, fieldTypeName));
  }

  public Set<Mapping> getMappings() {
    return mappings;
  }

  public void forEach(Consumer<Mapping> action) {
    for (Mapping mapping : mappings) {
      action.accept(mapping);
    }
  }

  public static final class Mapping {
    private final String fieldName;
    private final String typeName;
    private final String fieldTypeName;

    Mapping(String fieldName, String typeName, String fieldTypeName) {
      this.fieldName = fieldName;
      this.typeName = typeName;
      this.fieldTypeName = fieldTypeName;
    }

    public String getFieldName() {
      return fieldName;
    }

    public String getTypeName() {
      return typeName;
    }

    public String getFieldTypeName() {
      return fieldTypeName;
    }

    @Override
    public String toString() {
      return "Mapping{"
          + "fieldName='"
          + fieldName
          + '\''
          + ", typeName='"
          + typeName
          + '\''
          + ", fieldTypeName='"
          + fieldTypeName
          + '\''
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof Mapping)) {
        return false;
      }
      Mapping mapping = (Mapping) o;
      return Objects.equals(fieldName, mapping.fieldName)
          && Objects.equals(typeName, mapping.typeName)
          && Objects.equals(fieldTypeName, mapping.fieldTypeName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(fieldName, typeName, fieldTypeName);
    }
  }
}
