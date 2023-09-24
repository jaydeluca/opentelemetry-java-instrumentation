/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachedubbo.v2_7;

import org.apache.dubbo.config.bootstrap.DubboBootstrap;
import java.lang.reflect.InvocationTargetException;

class DubboTestUtil2 {
  static Object newFrameworkModel() {
    try {
      // only present in latest dep
      return Class.forName("org.apache.dubbo.rpc.model.FrameworkModel").getDeclaredConstructor().newInstance();
    } catch (ClassNotFoundException exception) {
      return null;
    } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
             InvocationTargetException e) {
      throw new RuntimeException(e);
    }
  }

  static DubboBootstrap newDubboBootstrap(Object frameworkModel) {
    if (frameworkModel == null) {
      return DubboBootstrap.getInstance();
    }
    return DubboBootstrap.getInstance();
  }
}
