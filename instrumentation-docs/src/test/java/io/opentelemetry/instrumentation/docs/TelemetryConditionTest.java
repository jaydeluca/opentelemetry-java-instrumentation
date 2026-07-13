/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.TelemetryCondition;
import io.opentelemetry.instrumentation.docs.internal.TelemetryCondition.Kind;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelemetryConditionTest {

  @Test
  void nullEmptyAndDefaultAllMapToDefault() {
    assertThat(TelemetryCondition.parse(null).getKind()).isEqualTo(Kind.DEFAULT);
    assertThat(TelemetryCondition.parse("").getKind()).isEqualTo(Kind.DEFAULT);
    assertThat(TelemetryCondition.parse("  ").getKind()).isEqualTo(Kind.DEFAULT);

    TelemetryCondition condition = TelemetryCondition.parse("default");
    assertThat(condition.getKind()).isEqualTo(Kind.DEFAULT);
    assertThat(condition.getRaw()).isEqualTo("default");
    assertThat(condition.getProperties()).isEmpty();
    assertThat(condition.getJavaMinVersion()).isNull();
  }

  @Test
  void runtimeConditionParsesJavaVersion() {
    TelemetryCondition java17 = TelemetryCondition.parse("Java17");
    assertThat(java17.getKind()).isEqualTo(Kind.RUNTIME);
    assertThat(java17.getJavaMinVersion()).isEqualTo(17);
    assertThat(java17.getProperties()).isEmpty();

    assertThat(TelemetryCondition.parse("Java21").getJavaMinVersion()).isEqualTo(21);
  }

  @Test
  void semconvOptInIsSemconvKind() {
    TelemetryCondition condition =
        TelemetryCondition.parse("otel.semconv-stability.opt-in=database");
    assertThat(condition.getKind()).isEqualTo(Kind.SEMCONV);
    assertThat(condition.getProperties())
        .containsExactly(Map.entry("otel.semconv-stability.opt-in", "database"));
  }

  @Test
  void semconvOptInWithCommaSeparatedValueStaysOneProperty() {
    // The comma here is part of the opt-in value list, not a property separator.
    TelemetryCondition condition =
        TelemetryCondition.parse("otel.semconv-stability.opt-in=database,service.peer");
    assertThat(condition.getKind()).isEqualTo(Kind.SEMCONV);
    assertThat(condition.getProperties())
        .containsExactly(Map.entry("otel.semconv-stability.opt-in", "database,service.peer"));
  }

  @Test
  void configFlagIsConfigKind() {
    TelemetryCondition condition =
        TelemetryCondition.parse("otel.instrumentation.foo.enabled=true");
    assertThat(condition.getKind()).isEqualTo(Kind.CONFIG);
    assertThat(condition.getProperties())
        .containsExactly(Map.entry("otel.instrumentation.foo.enabled", "true"));
    assertThat(condition.getJavaMinVersion()).isNull();
  }

  @Test
  void multipleDistinctFlagsSplitIntoSeparateProperties() {
    TelemetryCondition condition =
        TelemetryCondition.parse(
            "otel.instrumentation.common.experimental.view-telemetry.enabled=true,"
                + "otel.instrumentation.jsp.experimental-span-attributes=true");
    assertThat(condition.getKind()).isEqualTo(Kind.CONFIG);
    assertThat(condition.getProperties())
        .containsExactly(
            Map.entry("otel.instrumentation.common.experimental.view-telemetry.enabled", "true"),
            Map.entry("otel.instrumentation.jsp.experimental-span-attributes", "true"));
  }

  @Test
  void mixedSemconvAndFlagIsConfigKind() {
    // A condition that pairs a semconv opt-in with a non-semconv flag is a generic config toggle,
    // not a pure semconv condition.
    TelemetryCondition condition =
        TelemetryCondition.parse(
            "otel.semconv-stability.opt-in=database,"
                + "otel.instrumentation.couchbase.experimental-span-attributes=true");
    assertThat(condition.getKind()).isEqualTo(Kind.CONFIG);
    assertThat(condition.getProperties())
        .containsExactly(
            Map.entry("otel.semconv-stability.opt-in", "database"),
            Map.entry("otel.instrumentation.couchbase.experimental-span-attributes", "true"));
  }

  @Test
  void equalityIsBasedOnRawAndKind() {
    assertThat(TelemetryCondition.parse("Java17")).isEqualTo(TelemetryCondition.parse("Java17"));
    assertThat(TelemetryCondition.parse("Java17")).isNotEqualTo(TelemetryCondition.parse("Java21"));
    assertThat(TelemetryCondition.parse("default"))
        .isEqualTo(TelemetryCondition.parse(""))
        .isEqualTo(TelemetryCondition.parse(null));
  }
}
