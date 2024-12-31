/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import static java.util.Arrays.asList;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.util.List;

@AutoService(InstrumentationModule.class)
public class ClickHouseInstrumentationModule extends InstrumentationModule {

  public ClickHouseInstrumentationModule() {
    super("clickhouse-client", "clickhouse-client-0.5", "clickhouse");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return asList(
        new ClickHouseClientV1Instrumentation(),
        new ClickHouseClientV2Instrumentation(),
        new HttpApiClientHelperInstrumentation()
    );
  }
}
