/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package field;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import library.VirtualFieldTestClass;

public class VirtualFieldTestHelper {

  private VirtualFieldTestHelper() {}

  public static void test() {
    VirtualFieldTestClass instance = new VirtualFieldTestClass();
    {
      VirtualField<VirtualFieldTestClass, String> field =
          VirtualField.find(VirtualFieldTestClass.class, String.class);
      field.set(instance, "test");
      field.get(instance);
    }
    {
      // fields that differ only by name must not share storage with each other or with the unnamed
      // field for the same type pair
      VirtualField<VirtualFieldTestClass, String> unnamed =
          VirtualField.find(VirtualFieldTestClass.class, String.class);
      VirtualField<VirtualFieldTestClass, String> name =
          VirtualField.find("name", VirtualFieldTestClass.class, String.class);
      VirtualField<VirtualFieldTestClass, String> otherName =
          VirtualField.find("otherName", VirtualFieldTestClass.class, String.class);

      unnamed.set(instance, "unnamed value");
      name.set(instance, "name value");
      otherName.set(instance, "otherName value");

      assertValue(unnamed.get(instance), "unnamed value");
      assertValue(name.get(instance), "name value");
      assertValue(otherName.get(instance), "otherName value");

      // clearing one named field must leave the others alone
      name.set(instance, null);
      assertValue(name.get(instance), null);
      assertValue(unnamed.get(instance), "unnamed value");
      assertValue(otherName.get(instance), "otherName value");
    }
    {
      VirtualField<VirtualFieldTestClass, String[]> field =
          VirtualField.find(VirtualFieldTestClass.class, String[].class);
      field.set(instance, new String[] {"test"});
      field.get(instance);
    }
    {
      VirtualField<VirtualFieldTestClass, String[][]> field =
          VirtualField.find(VirtualFieldTestClass.class, String[][].class);
      field.set(instance, new String[][] {new String[] {"test"}});
      field.get(instance);
    }
  }

  // this helper runs inside advice, VirtualFieldTest asserts that it completes without throwing
  private static void assertValue(String actual, String expected) {
    if (expected == null ? actual != null : !expected.equals(actual)) {
      throw new IllegalStateException(
          "expected virtual field value " + expected + " but got " + actual);
    }
  }
}
