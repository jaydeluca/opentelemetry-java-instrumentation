/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import org.junit.jupiter.api.Test;

class InstrumenterParserTest {

  @Test
  void testParseInstrumenterForScopeInfo() {
    String instrumenterCode =
        """
  static {
    FinatraCodeAttributesGetter codeAttributesGetter = new FinatraCodeAttributesGetter();
    INSTRUMENTER =
        Instrumenter.<Class<?>, Void>builder(
                GlobalOpenTelemetry.get(),
                "io.opentelemetry.finatra-2.9",
                CodeSpanNameExtractor.create(codeAttributesGetter))
            .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
            .buildInstrumenter();
  }
  """;

    InstrumentationScopeInfo result = InstrumenterParser.parseInstrumenter(instrumenterCode);

    assertThat(result.getName()).isEqualTo("io.opentelemetry.finatra-2.9");
  }
}
