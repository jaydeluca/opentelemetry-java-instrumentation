/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static net.bytebuddy.jar.asm.Type.getType;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeneratedVirtualFieldNamesTest {

  @ParameterizedTest
  @CsvSource({
    "'', io.opentelemetry.javaagent.bootstrap.field.VirtualFieldImpl$$java$lang$Runnable$java$lang$String____",
    "test, io.opentelemetry.javaagent.bootstrap.field.VirtualFieldImpl$test$java$lang$Runnable$java$lang$String____"
  })
  void virtualFieldImplementation(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getVirtualFieldImplementationClassName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[][].class).getClassName()))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "'', io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessor$$java$lang$Runnable$java$lang$String__",
    "test, io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessor$test$java$lang$Runnable$java$lang$String__"
  })
  void accessorInterface(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getFieldAccessorInterfaceName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[].class).getClassName()))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "'', __opentelemetryVirtualField$$java$lang$Runnable$java$lang$String__",
    "test, __opentelemetryVirtualField$test$java$lang$Runnable$java$lang$String__"
  })
  void field(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getRealFieldName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[].class).getClassName()))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "'', __set__opentelemetryVirtualField$$java$lang$Runnable$java$lang$String__",
    "test, __set__opentelemetryVirtualField$test$java$lang$Runnable$java$lang$String__"
  })
  void setter(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getRealSetterName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[].class).getClassName()))
        .isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({
    "'', __get__opentelemetryVirtualField$$java$lang$Runnable$java$lang$String__",
    "test, __get__opentelemetryVirtualField$test$java$lang$Runnable$java$lang$String__"
  })
  void getter(String fieldName, String expected) {
    assertThat(
            GeneratedVirtualFieldNames.getRealGetterName(
                fieldName,
                getType(Runnable.class).getClassName(),
                getType(String[].class).getClassName()))
        .isEqualTo(expected);
  }

  // The field name, the owner type name and the field type name are joined with '$', and class name
  // separators are replaced with '$' too, so it would be easy for two distinct virtual fields to
  // generate the same names and end up sharing a single injected field. Field names that contain
  // '$' would still collide here; they are rejected when the mapping is registered, see
  // VirtualFieldMappingsBuilderImplTest#invalidFieldNames.
  @ParameterizedTest
  @CsvSource({
    // field "a" on b.X versus the unnamed field on a.b.X
    "a, b.X, '', a.b.X",
    // field "a" on X (default package) versus the unnamed field on a.X
    "a, X, '', a.X",
    // field "a" on b.X versus field "a.b" on X - the latter is rejected at registration, but the
    // shapes that differ only in where the separator falls must still not collide
    "a, b.X, ab, X",
  })
  void namesAreUnique(
      String fieldName, String typeName, String otherFieldName, String otherTypeName) {
    String fieldTypeName = getType(String.class).getClassName();

    assertThat(GeneratedVirtualFieldNames.getRealFieldName(fieldName, typeName, fieldTypeName))
        .isNotEqualTo(
            GeneratedVirtualFieldNames.getRealFieldName(
                otherFieldName, otherTypeName, fieldTypeName));
    assertThat(
            GeneratedVirtualFieldNames.getFieldAccessorInterfaceName(
                fieldName, typeName, fieldTypeName))
        .isNotEqualTo(
            GeneratedVirtualFieldNames.getFieldAccessorInterfaceName(
                otherFieldName, otherTypeName, fieldTypeName));
    assertThat(
            GeneratedVirtualFieldNames.getVirtualFieldImplementationClassName(
                fieldName, typeName, fieldTypeName))
        .isNotEqualTo(
            GeneratedVirtualFieldNames.getVirtualFieldImplementationClassName(
                otherFieldName, otherTypeName, fieldTypeName));
  }
}
