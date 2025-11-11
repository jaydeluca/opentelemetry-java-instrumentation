/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.springboot.controller;

import io.opentelemetry.api.trace.Span;
import java.net.URI;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * This controller demonstrates that context propagation works across http calls in Spring Boot 4.
 * Uses RestClient (introduced in Spring Framework 6.1) instead of RestTemplate.
 *
 * <p>Calling <code>/front</code> should return a string which contains two traceId separated by
 * ";". First traceId was reported by <code>/front</code> handler, the second one was returned by
 * <code>/back</code> handler which was called by <code>/front</code>. If context propagation works
 * correctly, then both values should be the same.
 */
@RestController
public class PropagatingController {
  private final RestClient restClient;
  private final Environment environment;

  public PropagatingController(Environment environment) {
    // Create RestClient directly since auto-configuration may not provide Builder bean in RC
    this.restClient = RestClient.create();
    this.environment = environment;
  }

  @RequestMapping("/front")
  public String front() {
    URI backend =
        ServletUriComponentsBuilder.fromCurrentContextPath()
            .port(environment.getProperty("local.server.port"))
            .path("/back")
            .build()
            .toUri();
    String backendTraceId = restClient.get().uri(backend).retrieve().body(String.class);
    String frontendTraceId = Span.current().getSpanContext().getTraceId();
    return String.format("%s;%s", frontendTraceId, backendTraceId);
  }

  @RequestMapping("/back")
  public String back() {
    return Span.current().getSpanContext().getTraceId();
  }
}
