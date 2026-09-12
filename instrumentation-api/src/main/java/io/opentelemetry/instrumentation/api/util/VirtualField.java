/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.util;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.instrumentation.api.internal.RuntimeVirtualFieldSupplier;
import javax.annotation.Nullable;

/**
 * Represents a "virtual" field of type {@code F} that is added to type {@code T} in the runtime.
 *
 * <p>A virtual field has similar semantics to a weak-keys strong-values map: the value will be
 * garbage collected when their owner instance is collected. It is discouraged to use a virtual
 * field for keeping values that might reference their key, as it may cause memory leaks.
 *
 * @param <T> The type that will contain the new virtual field.
 * @param <F> The field type that'll be added to {@code T}.
 */
// we're using an abstract class here so that we can call static find() in pre-jdk8 advice classes
public abstract class VirtualField<T, F> {

  /**
   * Finds a {@link VirtualField} instance for given {@code type} and {@code fieldType}.
   *
   * <p>In runtime, when using the javaagent, the <em>calls</em> to this method are rewritten to
   * something more performant while injecting <em>inline</em> advice into a method. This rewriting
   * is not performed for <em>non-inline</em> advice. Users are advised not to rely on the agent
   * automatically rewriting the virtual field usages, and to instead look up the {@link
   * VirtualField} once and store it in a field to avoid repeatedly calling this method. Rewriting
   * calls to this method may be removed in a future agent release.
   *
   * @param type The type that will contain the new virtual field.
   * @param fieldType The field type that will be added to {@code type}.
   * @see VirtualField#find(String, Class, Class)
   */
  public static <U extends T, V extends F, T, F> VirtualField<U, V> find(
      Class<T> type, Class<F> fieldType) {
    return find(RuntimeVirtualFieldSupplier.DEFAULT_FIELD_NAME, type, fieldType);
  }

  /**
   * Finds a {@link VirtualField} instance for given {@code fieldName}, {@code type} and {@code
   * fieldType}.
   *
   * <p>Conceptually this can be thought of as adding a field with name {@code fieldName} to class
   * {@code type} with type {@code fieldType}. Alternatively this can be viewed as a map where an
   * object of type {@code type} is associated with another map where they key is composed of {@code
   * fieldName} and {@code fieldType} and the value is the value of the virtual field.
   *
   * <p>Naming a virtual field keeps two instrumentations from accidentally sharing one field when
   * they attach unrelated state of a common type - such as {@link String} or {@link Boolean} - to
   * the same {@code type}. When {@code type} is a JDK type that many instrumentations could use as
   * a carrier, prefer a name that is specific to the instrumentation over a generic one.
   *
   * <p>Calls to this method may be expensive, caller should keep the returned {@link VirtualField}
   * and reuse it instead of repeatedly calling this method.
   *
   * <p>Unlike calls to {@link VirtualField#find(Class, Class)} calls to this method are never
   * rewritten.
   *
   * @param fieldName The name of the virtual field. Must be a java identifier that does not contain
   *     {@code $}, because the name is used as a part of generated class, field and method names.
   *     In the javaagent an invalid name fails the muzzle code generation for the instrumentation.
   * @param type The type that will contain the new virtual field.
   * @param fieldType The field type that will be added to {@code type}.
   */
  public static <U extends T, V extends F, T, F> VirtualField<U, V> find(
      String fieldName, Class<T> type, Class<F> fieldType) {
    requireNonNull(fieldName);
    requireNonNull(type);
    requireNonNull(fieldType);
    return RuntimeVirtualFieldSupplier.get().find(fieldName, type, fieldType);
  }

  /** Gets the value of this virtual field. */
  @Nullable
  public abstract F get(T object);

  /** Sets the new value of this virtual field. */
  public abstract void set(T object, @Nullable F fieldValue);
}
