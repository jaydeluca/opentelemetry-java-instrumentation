/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationParserTest {

  @Test
  void test() {

    String javaFileContent =
        "private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =\n"
            + "      AgentInstrumentationConfig.get()\n"
            + "          .getBoolean(\"otel.instrumentation.twilio.experimental-span-attributes\", false);";

    List<ConfigurationProperty> result = ConfigurationParser.parse(javaFileContent);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).name())
        .isEqualTo("otel.instrumentation.twilio.experimental-span-attributes");
    assertThat(result.get(0).type()).isEqualTo("boolean");
    assertThat(result.get(0).defaultValue()).isEqualTo("false");
  }
}
