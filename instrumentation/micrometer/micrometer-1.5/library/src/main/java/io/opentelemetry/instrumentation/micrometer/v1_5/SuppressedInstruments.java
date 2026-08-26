/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.noop.NoopCounter;
import io.micrometer.core.instrument.noop.NoopDistributionSummary;
import io.micrometer.core.instrument.noop.NoopFunctionCounter;
import io.micrometer.core.instrument.noop.NoopFunctionTimer;
import io.micrometer.core.instrument.noop.NoopGauge;
import io.micrometer.core.instrument.noop.NoopLongTaskTimer;
import io.micrometer.core.instrument.noop.NoopMeter;
import io.micrometer.core.instrument.noop.NoopTimer;
import io.opentelemetry.instrumentation.micrometer.v1_5.internal.OpenTelemetryInstrument;

/**
 * Placeholder meters returned when the bridge declines to bridge a meter.
 *
 * <p>These record nothing and create no OpenTelemetry instrument. Crucially they implement {@link
 * OpenTelemetryInstrument}, which is the marker the agent's {@code
 * AbstractCompositeMeter#firstChild()} rewrite already skips on: a composite that holds one of
 * these as a child keeps answering reads from a sibling registry that can actually report values. A
 * plain Micrometer noop -- which is what a {@code MeterFilter} DENY leaves behind -- carries no
 * marker and so is *not* skipped.
 */
final class SuppressedInstruments {

  // When false, plain Micrometer noops are returned instead -- carrying no marker, exactly the
  // artifact a MeterFilter DENY leaves behind. Only a spike lever for comparing the two, not a
  // proposed setting.
  static Gauge gauge(Meter.Id id, boolean marked) {
    return marked ? new SuppressedGauge(id) : new NoopGauge(id);
  }

  static Counter counter(Meter.Id id, boolean marked) {
    return marked ? new SuppressedCounter(id) : new NoopCounter(id);
  }

  static Timer timer(Meter.Id id, boolean marked) {
    return marked ? new SuppressedTimer(id) : new NoopTimer(id);
  }

  static DistributionSummary distributionSummary(Meter.Id id, boolean marked) {
    return marked ? new SuppressedDistributionSummary(id) : new NoopDistributionSummary(id);
  }

  static LongTaskTimer longTaskTimer(Meter.Id id, boolean marked) {
    return marked ? new SuppressedLongTaskTimer(id) : new NoopLongTaskTimer(id);
  }

  static FunctionTimer functionTimer(Meter.Id id, boolean marked) {
    return marked ? new SuppressedFunctionTimer(id) : new NoopFunctionTimer(id);
  }

  static FunctionCounter functionCounter(Meter.Id id, boolean marked) {
    return marked ? new SuppressedFunctionCounter(id) : new NoopFunctionCounter(id);
  }

  static Meter meter(Meter.Id id, boolean marked) {
    return marked ? new SuppressedMeter(id) : new NoopMeter(id);
  }

  private static final class SuppressedGauge extends NoopGauge implements OpenTelemetryInstrument {
    SuppressedGauge(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedCounter extends NoopCounter
      implements OpenTelemetryInstrument {
    SuppressedCounter(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedTimer extends NoopTimer implements OpenTelemetryInstrument {
    SuppressedTimer(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedDistributionSummary extends NoopDistributionSummary
      implements OpenTelemetryInstrument {
    SuppressedDistributionSummary(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedLongTaskTimer extends NoopLongTaskTimer
      implements OpenTelemetryInstrument {
    SuppressedLongTaskTimer(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedFunctionTimer extends NoopFunctionTimer
      implements OpenTelemetryInstrument {
    SuppressedFunctionTimer(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedFunctionCounter extends NoopFunctionCounter
      implements OpenTelemetryInstrument {
    SuppressedFunctionCounter(Meter.Id id) {
      super(id);
    }
  }

  private static final class SuppressedMeter extends NoopMeter implements OpenTelemetryInstrument {
    SuppressedMeter(Meter.Id id) {
      super(id);
    }
  }

  private SuppressedInstruments() {}
}
