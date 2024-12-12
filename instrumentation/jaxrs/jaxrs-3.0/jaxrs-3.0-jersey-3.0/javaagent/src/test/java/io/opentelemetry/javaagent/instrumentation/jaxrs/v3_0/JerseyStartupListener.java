/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrs.v3_0;

import io.opentelemetry.instrumentation.jaxrs.v3_0.test.JaxRsApplicationPathTestApplication;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletException;
import java.util.Collections;
import org.glassfish.jersey.servlet.init.JerseyServletContainerInitializer;

// ServletContainerInitializer isn't automatically called due to the way this test is set up,
// so we call it ourselves
public class JerseyStartupListener implements ServletContextListener {
  @Override
  public void contextInitialized(ServletContextEvent servletContextEvent) {
    try {
      new JerseyServletContainerInitializer()
          .onStartup(
              Collections.singleton(JaxRsApplicationPathTestApplication.class),
              servletContextEvent.getServletContext());
    } catch (ServletException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @Override
  public void contextDestroyed(ServletContextEvent servletContextEvent) {}
}