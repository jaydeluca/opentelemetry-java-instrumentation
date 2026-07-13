/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.TelemetryCondition;
import org.junit.jupiter.api.Test;

class TelemetryParserTest {

  @Test
  void classifyWhenReturnsTypedCondition() {
    assertThat(TelemetryParser.classifyWhen("default").getKind())
        .isEqualTo(TelemetryCondition.Kind.DEFAULT);
    assertThat(TelemetryParser.classifyWhen("Java21").getKind())
        .isEqualTo(TelemetryCondition.Kind.RUNTIME);
    assertThat(TelemetryParser.classifyWhen("otel.semconv-stability.opt-in=database").getKind())
        .isEqualTo(TelemetryCondition.Kind.SEMCONV);
    assertThat(TelemetryParser.classifyWhen("otel.instrumentation.foo.enabled=true").getKind())
        .isEqualTo(TelemetryCondition.Kind.CONFIG);
  }

  @Test
  void normalizeWhenConditionStripsQuotes() {
    String content =
        """
        when: "otel.instrumentation.common.experimental.view-telemetry.enabled=true,otel.instrumentation.jsp.experimental-span-attributes=true"
        metrics_by_scope:
          - scope: io.opentelemetry.jsp-2.3
        """;

    String result = TelemetryParser.normalizeWhenCondition(content);

    assertThat(result)
        .isEqualTo(
            "otel.instrumentation.common.experimental.view-telemetry.enabled=true,otel.instrumentation.jsp.experimental-span-attributes=true");
  }

  @Test
  void normalizeWhenConditionHandlesUnquotedValue() {
    String content =
        """
        when: default
        metrics_by_scope:
          - scope: io.opentelemetry.test
        """;

    String result = TelemetryParser.normalizeWhenCondition(content);

    assertThat(result).isEqualTo("default");
  }

  @Test
  void normalizeWhenConditionReturnsEmptyForNull() {
    String result = TelemetryParser.normalizeWhenCondition(null);

    assertThat(result).isEmpty();
  }

  @Test
  void normalizeWhenConditionHandlesComplexConditions() {
    String content =
        """
        when: "config1=value1,config2=value2,config3=value3"
        spans_by_scope:
          - scope: io.opentelemetry.test
        """;

    String result = TelemetryParser.normalizeWhenCondition(content);

    assertThat(result).isEqualTo("config1=value1,config2=value2,config3=value3");
  }
}
