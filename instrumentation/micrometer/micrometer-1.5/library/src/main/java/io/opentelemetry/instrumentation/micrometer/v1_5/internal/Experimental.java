/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5.internal;

import io.micrometer.core.instrument.Meter;
import io.opentelemetry.instrumentation.micrometer.v1_5.OpenTelemetryMeterRegistryBuilder;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * This class is internal and experimental. Its APIs are unstable and can change at any time. Its
 * APIs (or a version of them) may be promoted to the public stable API in the future, but no
 * guarantees are made.
 */
public final class Experimental {

  @Nullable
  private static volatile BiConsumer<OpenTelemetryMeterRegistryBuilder, Boolean>
      setMicrometerHistogramGaugesEnabled;

  /**
   * Enables the generation of gauge-based Micrometer histograms. While the Micrometer bridge is
   * able to map Micrometer's {@code DistributionSummary} and {@code Timer} service level objectives
   * to OpenTelemetry histogram buckets, it might not cover all cases that are normally supported by
   * Micrometer (e.g. the bridge is not able to translate percentiles). With this setting enabled,
   * the Micrometer bridge will additionally emit Micrometer service level objectives and
   * percentiles as separate gauges.
   *
   * <p>Note that this setting does not concern the {@code LongTaskTimer}, as it is not bridged to
   * an OpenTelemetry histogram.
   *
   * <p>This is disabled by default, set this to {@code true} to enable gauge-based Micrometer
   * histograms.
   */
  public static void setMicrometerHistogramGaugesEnabled(
      OpenTelemetryMeterRegistryBuilder builder, boolean enabled) {
    if (setMicrometerHistogramGaugesEnabled != null) {
      setMicrometerHistogramGaugesEnabled.accept(builder, enabled);
    }
  }

  public static void internalSetMicrometerHistogramGaugesEnabled(
      BiConsumer<OpenTelemetryMeterRegistryBuilder, Boolean> setMicrometerHistogramGaugesEnabled) {
    Experimental.setMicrometerHistogramGaugesEnabled = setMicrometerHistogramGaugesEnabled;
  }

  @Nullable
  private static volatile BiConsumer<OpenTelemetryMeterRegistryBuilder, Predicate<Meter.Id>>
      setSuppressionPredicate;

  /**
   * Sets the predicate deciding which meters the bridge declines to bridge. A meter the predicate
   * accepts creates no OpenTelemetry instrument and records nothing.
   *
   * <p>The predicate is resolved by the caller -- the bridge itself has no view of which
   * instrumentations are enabled or which semantic-convention gates are satisfied.
   */
  public static void setSuppressionPredicate(
      OpenTelemetryMeterRegistryBuilder builder, Predicate<Meter.Id> predicate) {
    if (setSuppressionPredicate != null) {
      setSuppressionPredicate.accept(builder, predicate);
    }
  }

  public static void internalSetSuppressionPredicate(
      BiConsumer<OpenTelemetryMeterRegistryBuilder, Predicate<Meter.Id>> setSuppressionPredicate) {
    Experimental.setSuppressionPredicate = setSuppressionPredicate;
  }

  @Nullable
  private static volatile BiConsumer<OpenTelemetryMeterRegistryBuilder, Boolean>
      setMarkSuppressedInstruments;

  /**
   * Spike lever, not a proposed setting. When {@code false}, suppressed meters are plain Micrometer
   * noops rather than marked instruments -- reproducing what a {@code MeterFilter} DENY leaves in a
   * composite registry, so the two can be compared under one agent build.
   */
  public static void setMarkSuppressedInstruments(
      OpenTelemetryMeterRegistryBuilder builder, boolean marked) {
    if (setMarkSuppressedInstruments != null) {
      setMarkSuppressedInstruments.accept(builder, marked);
    }
  }

  public static void internalSetMarkSuppressedInstruments(
      BiConsumer<OpenTelemetryMeterRegistryBuilder, Boolean> setMarkSuppressedInstruments) {
    Experimental.setMarkSuppressedInstruments = setMarkSuppressedInstruments;
  }

  private Experimental() {}
}
