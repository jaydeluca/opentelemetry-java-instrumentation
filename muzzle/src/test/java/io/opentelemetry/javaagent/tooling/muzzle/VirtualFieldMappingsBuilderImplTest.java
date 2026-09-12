/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

import io.opentelemetry.javaagent.tooling.muzzle.VirtualFieldMappings.Mapping;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VirtualFieldMappingsBuilderImplTest {

  @ParameterizedTest
  @ValueSource(strings = {"name", "subscriptionName", "_name", "name2", "ünïcödeName"})
  void validFieldNames(String fieldName) {
    VirtualFieldMappingsBuilderImpl builder = new VirtualFieldMappingsBuilderImpl();

    assertThatNoException()
        .isThrownBy(() -> builder.register(fieldName, "foo.Bar", "java.lang.String"));
    assertThat(builder.build().getMappings())
        .containsExactly(new Mapping(fieldName, "foo.Bar", "java.lang.String"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        // '$' is the separator used in generated names, allowing it would let ("a$b", "X.Y") and
        // ("a", "b.X.Y") generate the same names
        "a$b",
        "$name",
        "name$",
        // not valid in a jvm unqualified name
        "a.b",
        "a/b",
        "a;b",
        "a[b",
        // not a java identifier
        "a b",
        "a-b",
        "2name"
      })
  void invalidFieldNames(String fieldName) {
    VirtualFieldMappingsBuilderImpl builder = new VirtualFieldMappingsBuilderImpl();

    assertThatExceptionOfType(IllegalArgumentException.class)
        .isThrownBy(() -> builder.register(fieldName, "foo.Bar", "java.lang.String"))
        .withMessageContaining(fieldName);
  }

  @Test
  void unnamedFieldIsRegisteredWithTheDefaultName() {
    VirtualFieldMappingsBuilderImpl builder = new VirtualFieldMappingsBuilderImpl();
    builder.register("foo.Bar", "java.lang.String");

    assertThat(builder.build().getMappings())
        .containsExactly(new Mapping("", "foo.Bar", "java.lang.String"));
  }

  @Test
  void namedAndUnnamedFieldsForTheSameTypePairAreDistinct() {
    VirtualFieldMappingsBuilderImpl builder = new VirtualFieldMappingsBuilderImpl();
    builder.register("foo.Bar", "java.lang.String");
    builder.register("name", "foo.Bar", "java.lang.String");
    builder.register("other", "foo.Bar", "java.lang.String");

    VirtualFieldMappings mappings = builder.build();

    assertThat(mappings.getMappings()).hasSize(3);
    assertThat(mappings.hasUnnamedMapping("foo.Bar", "java.lang.String")).isTrue();
    assertThat(mappings.hasUnnamedMapping("foo.Bar", "java.lang.Integer")).isFalse();
  }
}
