/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import io.opentelemetry.javaagent.bootstrap.field.VirtualFieldAccessorMarker;

final class GeneratedVirtualFieldNames {

  /**
   * Note: the value here has to be inside on of the prefixes in {@link
   * io.opentelemetry.javaagent.tooling.Constants#BOOTSTRAP_PACKAGE_PREFIXES}. This ensures that
   * 'isolating' (or 'module') classloaders like jboss and osgi see injected classes. This works
   * because we instrument those classloaders to load everything inside bootstrap packages.
   */
  static final String DYNAMIC_CLASSES_PACKAGE =
      VirtualFieldAccessorMarker.class.getPackage().getName() + ".";

  private GeneratedVirtualFieldNames() {}

  public static boolean isVirtualFieldInterfaceName(String className) {
    return className.startsWith(DYNAMIC_CLASSES_PACKAGE + "VirtualFieldAccessor$");
  }

  static String getVirtualFieldImplementationClassName(
      String fieldName, String typeName, String fieldTypeName) {
    return DYNAMIC_CLASSES_PACKAGE
        + "VirtualFieldImpl$"
        + suffix(fieldName, typeName, fieldTypeName);
  }

  static String getFieldAccessorInterfaceName(
      String fieldName, String typeName, String fieldTypeName) {
    return DYNAMIC_CLASSES_PACKAGE
        + "VirtualFieldAccessor$"
        + suffix(fieldName, typeName, fieldTypeName);
  }

  static String getRealFieldName(String fieldName, String typeName, String fieldTypeName) {
    return "__opentelemetryVirtualField$" + suffix(fieldName, typeName, fieldTypeName);
  }

  static String getRealGetterName(String fieldName, String typeName, String fieldTypeName) {
    return "__get" + getRealFieldName(fieldName, typeName, fieldTypeName);
  }

  static String getRealSetterName(String fieldName, String typeName, String fieldTypeName) {
    return "__set" + getRealFieldName(fieldName, typeName, fieldTypeName);
  }

  /**
   * Builds the {@code <fieldName>$<typeName>$<fieldTypeName>} part that all generated names share.
   *
   * <p>The field name segment is always emitted, even when it is empty (the unnamed virtual field).
   * Skipping it for unnamed fields would make this encoding ambiguous, because {@link
   * #sanitizeClassName} also turns package separators into {@code $}: field {@code "a"} on type
   * {@code b.X} and the unnamed field on type {@code a.b.X} would both produce {@code a$b$X}, and
   * the two distinct virtual fields would end up sharing one injected field. Field names containing
   * {@code $} are rejected when the mapping is registered, so the first {@code $} here always
   * terminates the field name.
   */
  private static String suffix(String fieldName, String typeName, String fieldTypeName) {
    return fieldName + "$" + sanitizeClassName(typeName) + "$" + sanitizeClassName(fieldTypeName);
  }

  private static String sanitizeClassName(String className) {
    className = className.replace('.', '$');
    if (className.endsWith("[]")) {
      className = className.replace('[', '_').replace(']', '_');
    }
    return className;
  }
}
