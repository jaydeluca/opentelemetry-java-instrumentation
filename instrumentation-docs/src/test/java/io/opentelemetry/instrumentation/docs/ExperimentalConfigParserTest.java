/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static io.opentelemetry.instrumentation.docs.ExperimentalConfigParser.extractConfigMap;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ExperimentalConfigParserTest {

  @Test
  void testFullFile() {
    String code =
        """
      public final class ExperimentalConfig {

        private static final ExperimentalConfig instance =
            new ExperimentalConfig(AgentInstrumentationConfig.get());

        private final InstrumentationConfig config;
        private final List<String> messagingHeaders;

        public static ExperimentalConfig get() {
          return instance;
        }

        public ExperimentalConfig(InstrumentationConfig config) {
          this.config = config;
          messagingHeaders =
              config.getList("otel.instrumentation.messaging.experimental.capture-headers", emptyList());
        }

        public boolean controllerTelemetryEnabled() {
          return config.getBoolean(
              "otel.instrumentation.common.experimental.controller-telemetry.enabled", false);
        }

        public boolean viewTelemetryEnabled() {
          return config.getBoolean(
              "otel.instrumentation.common.experimental.view-telemetry.enabled", false);
        }

        public boolean messagingReceiveInstrumentationEnabled() {
          return config.getBoolean(
              "otel.instrumentation.messaging.experimental.receive-telemetry.enabled", false);
        }

        public boolean indyEnabled() {
          return config.getBoolean("otel.javaagent.experimental.indy", false);
        }

        public List<String> getMessagingHeaders() {
          return messagingHeaders;
        }
      }
      """;

    Map<String, ConfigurationProperty> configMap = extractConfigMap(code);

    // Not sure how to get the messaging headers
    assertThat(configMap).hasSize(4);

    assertThat(configMap)
        .hasEntrySatisfying(
            "controllerTelemetryEnabled()",
            value -> {
              assertThat(value.name())
                  .isEqualTo(
                      "otel.instrumentation.common.experimental.controller-telemetry.enabled");
              assertThat(value.type()).isEqualTo("Boolean");
              assertThat(value.defaultValue()).isEqualTo("false");
            });

    assertThat(configMap)
        .hasEntrySatisfying(
            "viewTelemetryEnabled()",
            value -> {
              assertThat(value.name())
                  .isEqualTo("otel.instrumentation.common.experimental.view-telemetry.enabled");
              assertThat(value.type()).isEqualTo("Boolean");
              assertThat(value.defaultValue()).isEqualTo("false");
            });

    assertThat(configMap)
        .hasEntrySatisfying(
            "messagingReceiveInstrumentationEnabled()",
            value -> {
              assertThat(value.name())
                  .isEqualTo(
                      "otel.instrumentation.messaging.experimental.receive-telemetry.enabled");
              assertThat(value.type()).isEqualTo("Boolean");
              assertThat(value.defaultValue()).isEqualTo("false");
            });

    assertThat(configMap)
        .hasEntrySatisfying(
            "indyEnabled()",
            value -> {
              assertThat(value.name()).isEqualTo("otel.javaagent.experimental.indy");
              assertThat(value.type()).isEqualTo("Boolean");
              assertThat(value.defaultValue()).isEqualTo("false");
            });
  }

  @Test
  void testParsesMultiLine() {
    String code =
        "  public ExperimentalConfig(InstrumentationConfig config) {\n"
            + "    this.config = config;\n"
            + "    messagingHeaders =\n"
            + "        config.getList(\"otel.instrumentation.messaging.experimental.capture-headers\", emptyList());\n"
            + "  }\n"
            + "\n"
            + "  public boolean controllerTelemetryEnabled() {\n"
            + "    return config.getBoolean(\n"
            + "        \"otel.instrumentation.common.experimental.controller-telemetry.enabled\", false);\n"
            + "  }";

    Map<String, ConfigurationProperty> configMap = extractConfigMap(code);
    assertThat(configMap).hasSize(1);
    assertThat(configMap)
        .hasEntrySatisfying(
            "controllerTelemetryEnabled()",
            value -> {
              assertThat(value.name())
                  .isEqualTo(
                      "otel.instrumentation.common.experimental.controller-telemetry.enabled");
              assertThat(value.type()).isEqualTo("Boolean");
              assertThat(value.defaultValue()).isEqualTo("false");
            });
  }
}
