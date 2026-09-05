/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse.clientv2.v0_8;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static java.util.Collections.singletonList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Tracks which endpoint the client selects for a query, so that spans report the endpoint that was
 * actually used rather than an arbitrary one from the configured set. Kept separate from the base
 * module because it needs {@code Endpoint#getHost()} / {@code Endpoint#getPort()} (added in
 * client-v2 0.9.7), while the base query instrumentation still applies to older versions. Muzzle
 * disables this module on the versions that don't have them.
 */
@AutoService(InstrumentationModule.class)
public class ClickHouseClientV2EndpointInstrumentationModule extends InstrumentationModule {

  public ClickHouseClientV2EndpointInstrumentationModule() {
    super(
        "clickhouse-client-v2-endpoint",
        "clickhouse-client-v2",
        "clickhouse-client-v2-0.8",
        "clickhouse",
        "clickhouse-client");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    return hasClassesNamed("com.clickhouse.client.api.transport.Endpoint");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ClickHouseEndpointUsageInstrumentation());
  }
}
