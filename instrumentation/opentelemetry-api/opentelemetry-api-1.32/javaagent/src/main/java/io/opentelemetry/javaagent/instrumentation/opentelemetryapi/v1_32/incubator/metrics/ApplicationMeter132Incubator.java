/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_32.incubator.metrics;

import application.io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_10.metrics.ApplicationDoubleHistogramBuilder;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.v1_31.incubator.metrics.ApplicationMeter131;

class ApplicationMeter132Incubator extends ApplicationMeter131 {

  private final io.opentelemetry.api.metrics.Meter agentMeter;

  ApplicationMeter132Incubator(io.opentelemetry.api.metrics.Meter agentMeter) {
    super(agentMeter);
    this.agentMeter = agentMeter;
  }

  @Override
  public DoubleHistogramBuilder histogramBuilder(String name) {
    io.opentelemetry.api.metrics.DoubleHistogramBuilder builder = agentMeter.histogramBuilder(name);
    if (builder instanceof io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder) {
      return new ApplicationDoubleHistogramBuilder132Incubator(builder);
    }
    return new ApplicationDoubleHistogramBuilder(builder);
  }
}
