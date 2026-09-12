/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.instrumentation.api.internal.RuntimeVirtualFieldSupplier;
import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings.Mapping;
import java.util.HashSet;
import java.util.Set;

public final class VirtualFieldMappingsBuilderImpl implements VirtualFieldMappingsBuilder {
  private final Set<Mapping> mappingSet = new HashSet<>();

  @Override
  @CanIgnoreReturnValue
  public VirtualFieldMappingsBuilder register(String typeName, String fieldTypeName) {
    return register(RuntimeVirtualFieldSupplier.DEFAULT_FIELD_NAME, typeName, fieldTypeName);
  }

  @Override
  @CanIgnoreReturnValue
  public VirtualFieldMappingsBuilder register(
      String fieldName, String typeName, String fieldTypeName) {
    // since we are going to use the field name as part of generated class and method names we are
    // not going to allow all kinds of names
    if (!isValidFieldName(fieldName)) {
      throw new IllegalArgumentException("Invalid field name: " + fieldName);
    }
    mappingSet.add(new Mapping(fieldName, typeName, fieldTypeName));
    return this;
  }

  /**
   * A field name must be a java identifier that does not contain {@code $}. Generated class, field
   * and method names join the field name, the owner type name and the field type name with {@code
   * $}, and class name separators are replaced with {@code $} as well. Allowing {@code $} inside a
   * field name would let two distinct virtual fields generate the same names - field {@code "a$b"}
   * on type {@code X.Y} and field {@code "a"} on type {@code b.X.Y} - which would make them share a
   * single injected field.
   */
  private static boolean isValidFieldName(String name) {
    if (name.isEmpty()) {
      // the unnamed virtual field
      return true;
    }

    int cp = name.codePointAt(0);
    if (cp == '$' || !Character.isJavaIdentifierStart(cp)) {
      return false;
    }
    for (int i = Character.charCount(cp); i < name.length(); i += Character.charCount(cp)) {
      cp = name.codePointAt(i);
      if (cp == '$' || !Character.isJavaIdentifierPart(cp)) {
        return false;
      }
    }
    return true;
  }

  void registerAll(VirtualFieldMappings mappings) {
    mappingSet.addAll(mappings.getMappings());
  }

  public VirtualFieldMappings build() {
    return new VirtualFieldMappings(mappingSet);
  }
}
