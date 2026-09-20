/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationType;
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationMetadata;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeaverModelGeneratorTest {

  private static final EmittedMetrics.Metric HTTP_METRIC =
      new EmittedMetrics.Metric(
          "http.server.request.duration",
          "Duration of HTTP server requests.",
          "HISTOGRAM",
          "s",
          List.of(
              new TelemetryAttribute("http.request.method", "STRING"),
              new TelemetryAttribute("http.response.status_code", "LONG")));

  private static final InstrumentationMetadata METADATA =
      new InstrumentationMetadata.Builder().displayName("ActiveJ").build();

  private static InstrumentationModule.Builder baseModule() {
    return new InstrumentationModule.Builder()
        .namespace("activej")
        .group("activej")
        .srcPath("instrumentation/activej-http-6.0")
        .instrumentationName("activej-http-6.0")
        .metadata(METADATA);
  }

  private static String render(InstrumentationModule module) throws IOException {
    StringWriter stringWriter = new StringWriter();
    try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
      WeaverModelGenerator.generateSignals(module, writer);
    }
    return stringWriter.toString();
  }

  @Test
  void generateManifest() throws IOException {
    InstrumentationModule module =
        baseModule().metrics(Map.of("default", List.of(HTTP_METRIC))).build();

    StringWriter stringWriter = new StringWriter();
    try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
      WeaverModelGenerator.generateManifest(module, writer);
    }

    String expectedYaml =
        """
        # This file is generated and should not be manually edited.
        name: activej-http-6.0
        description: This registry contains the semantic conventions for the ActiveJ instrumentation
        schema_url: https://github.com/open-telemetry/opentelemetry-java-instrumentation/instrumentation/activej-http-6.0/
        dependencies:
          - name: otel
            registry_path: https://github.com/open-telemetry/semantic-conventions@v1.44.0[model]
            schema_url: https://opentelemetry.io/schemas/1.44.0
        """;

    assertThat(stringWriter.toString()).isEqualTo(expectedYaml);
  }

  @Test
  void knownSemconvAttributesAreEmittedAsRefsOnly() throws IOException {
    InstrumentationModule module =
        baseModule().metrics(Map.of("default", List.of(HTTP_METRIC))).build();

    String yaml = render(module);

    String expectedYaml =
        """
        # This file is generated and should not be manually edited.
        groups:
          - id: metric.http.server.request.duration
            type: metric
            metric_name: http.server.request.duration
            stability: development
            brief: "Duration of HTTP server requests."
            instrument: histogram
            unit: "s"
            attributes:
              - ref: http.request.method
              - ref: http.response.status_code
        """;

    assertThat(yaml).isEqualTo(expectedYaml);
  }

  @Test
  void localAttributeIsDefinedAndReferenced() throws IOException {
    EmittedMetrics.Metric metric =
        new EmittedMetrics.Metric(
            "custom.widget.count",
            "Number of widgets processed.",
            "LONG_SUM",
            true,
            "{widget}",
            List.of(new TelemetryAttribute("widget.custom.category", "STRING")));

    InstrumentationModule module = baseModule().metrics(Map.of("default", List.of(metric))).build();

    String yaml = render(module);

    String expectedYaml =
        """
        # This file is generated and should not be manually edited.
        groups:
          - id: registry.activej-http-6.0
            type: attribute_group
            brief: Attributes specific to the ActiveJ instrumentation.
            attributes:
              - id: widget.custom.category
                type: string
                stability: development
                brief: "widget.custom.category" attribute emitted by the ActiveJ instrumentation.
          - id: metric.custom.widget.count
            type: metric
            metric_name: custom.widget.count
            stability: development
            brief: "Number of widgets processed."
            instrument: counter
            unit: "{widget}"
            attributes:
              - ref: widget.custom.category
        """;

    assertThat(yaml).isEqualTo(expectedYaml);
  }

  @Test
  void attributeOnlyEmittedConditionallyIsMarkedConditionallyRequired() throws IOException {
    ConfigurationOption config =
        new ConfigurationOption(
            "otel.instrumentation.activej-http.experimental-attributes.enabled",
            "Enables collection of experimental HTTP attributes.",
            "false",
            ConfigurationType.BOOLEAN);
    InstrumentationMetadata metadata =
        new InstrumentationMetadata.Builder()
            .displayName("ActiveJ")
            .configurations(List.of(config))
            .build();

    EmittedMetrics.Metric defaultVariant =
        new EmittedMetrics.Metric(
            "http.server.request.duration",
            "Duration of HTTP server requests.",
            "HISTOGRAM",
            "s",
            List.of(new TelemetryAttribute("http.request.method", "STRING")));
    EmittedMetrics.Metric conditionalVariant =
        new EmittedMetrics.Metric(
            "http.server.request.duration",
            "Duration of HTTP server requests.",
            "HISTOGRAM",
            "s",
            List.of(
                new TelemetryAttribute("http.request.method", "STRING"),
                new TelemetryAttribute("http.request.body.size", "LONG")));

    InstrumentationModule module =
        baseModule()
            .metadata(metadata)
            .metrics(
                Map.of(
                    "default",
                    List.of(defaultVariant),
                    "otel.instrumentation.activej-http.experimental-attributes.enabled=true",
                    List.of(conditionalVariant)))
            .build();

    String yaml = render(module);

    assertThat(yaml)
        .contains("- ref: http.request.method")
        .doesNotContain("http.request.method\n        requirement_level")
        .contains("- ref: http.request.body.size")
        .contains("requirement_level:")
        .contains("conditionally_required:")
        .contains("otel.instrumentation.activej-http.experimental-attributes.enabled=true")
        .contains("Enables collection of experimental HTTP attributes.");
  }

  @Test
  void metricOnlyEmittedConditionallyGetsNote() throws IOException {
    EmittedMetrics.Metric conditionalOnlyMetric =
        new EmittedMetrics.Metric(
            "oshi.experimental.cpu.load",
            "Experimental CPU load metric.",
            "DOUBLE_GAUGE",
            "1",
            List.of());

    InstrumentationModule module =
        baseModule()
            .metrics(
                Map.of(
                    "otel.instrumentation.oshi.experimental-metrics.enabled=true",
                    List.of(conditionalOnlyMetric)))
            .build();

    String yaml = render(module);

    assertThat(yaml)
        .contains("id: metric.oshi.experimental.cpu.load")
        .contains("note:")
        .contains("otel.instrumentation.oshi.experimental-metrics.enabled=true");
  }

  @Test
  void spanGroupIsGeneratedFromSpanKind() throws IOException {
    EmittedSpans.Span span =
        new EmittedSpans.Span(
            "SERVER", List.of(new TelemetryAttribute("http.request.method", "STRING")));

    InstrumentationModule module = baseModule().spans(Map.of("default", List.of(span))).build();

    String yaml = render(module);

    String expectedYaml =
        """
        # This file is generated and should not be manually edited.
        groups:
          - id: span.activej-http-6.0.server
            type: span
            span_kind: server
            stability: development
            brief: ActiveJ server span.
            attributes:
              - ref: http.request.method
        """;

    assertThat(yaml).isEqualTo(expectedYaml);
  }

  @Test
  void resolveWhenConditionUsesConfigurationDescriptionWhenAvailable() {
    ConfigurationOption config =
        new ConfigurationOption(
            "otel.instrumentation.oshi.experimental-metrics.enabled",
            "Enables experimental OSHI metrics.",
            "false",
            ConfigurationType.BOOLEAN);

    String resolved =
        WeaverModelGenerator.resolveWhenCondition(
            "otel.instrumentation.oshi.experimental-metrics.enabled=true", List.of(config));

    assertThat(resolved)
        .isEqualTo(
            "Only emitted when `otel.instrumentation.oshi.experimental-metrics.enabled=true`"
                + " (Enables experimental OSHI metrics.).");
  }

  @Test
  void resolveWhenConditionFallsBackToRawStringWhenConfigUnknown() {
    String resolved =
        WeaverModelGenerator.resolveWhenCondition("some.unknown.property=true", List.of());

    assertThat(resolved).isEqualTo("Only emitted when `some.unknown.property=true`.");
  }
}
