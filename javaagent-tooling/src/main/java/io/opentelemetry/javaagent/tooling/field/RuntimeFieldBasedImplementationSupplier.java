/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.field;

import static io.opentelemetry.javaagent.tooling.field.GeneratedVirtualFieldNames.getVirtualFieldImplementationClassName;

import io.opentelemetry.instrumentation.api.internal.RuntimeVirtualFieldSupplier;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.PrivilegedAction;

final class RuntimeFieldBasedImplementationSupplier
    implements RuntimeVirtualFieldSupplier.VirtualFieldSupplier {

  @Override
  public <U extends T, V extends F, T, F> VirtualField<U, V> find(
      String fieldName, Class<T> type, Class<F> fieldType) {
    if (System.getSecurityManager() == null) {
      return findInternal(fieldName, type, fieldType);
    }
    return java.security.AccessController.doPrivileged(
        (PrivilegedAction<VirtualField<U, V>>) () -> findInternal(fieldName, type, fieldType));
  }

  private static <U extends T, V extends F, T, F> VirtualField<U, V> findInternal(
      String fieldName, Class<T> type, Class<F> fieldType) {
    try {
      String virtualFieldImplClassName =
          getVirtualFieldImplementationClassName(
              fieldName, type.getTypeName(), fieldType.getTypeName());
      Class<?> contextStoreClass = Class.forName(virtualFieldImplClassName, false, null);
      Method method = contextStoreClass.getMethod("getVirtualField", Class.class, Class.class);
      @SuppressWarnings("unchecked") // casting reflection result
      VirtualField<U, V> field = (VirtualField<U, V>) method.invoke(null, type, fieldType);
      return field;
    } catch (ClassNotFoundException e) {
      // unlike VirtualField#find(Class, Class), calls to the named overload are not rewritten, so
      // this is the only place a missing registration is reported - name the field to make that
      // actionable
      throw new IllegalStateException(
          String.format(
              "VirtualField not found. Cannot find implementation for %s. Was that field registered"
                  + " in the instrumentation module?",
              describe(fieldName, type, fieldType)),
          e);
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException("Failed to get " + describe(fieldName, type, fieldType), e);
    }
  }

  private static String describe(String fieldName, Class<?> type, Class<?> fieldType) {
    return String.format(
        "VirtualField<%s, %s>%s",
        type.getTypeName(),
        fieldType.getTypeName(),
        fieldName.isEmpty() ? "" : " named '" + fieldName + "'");
  }
}
