/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.FileReaderHelper;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InstrumentationAnalyzerTest {
  @Mock private FileManager fileSearch;
  @Mock private FileReaderHelper fileReaderHelper;
  private final Map<String, ConfigurationProperty> experimentalConfigurations =
      Map.of(
          "messagingReceiveInstrumentationEnabled()",
          new ConfigurationProperty(
              "otel.instrumentation.messaging.experimental.receive-telemetry.enabled",
              "Boolean",
              "false"));

  private InstrumentationAnalyzer analyzer;

  @BeforeEach
  void setUp() {
    analyzer =
        new InstrumentationAnalyzer(fileSearch, fileReaderHelper, experimentalConfigurations);
  }

  @Test
  void testAnalyzeSemanticConventionsServerAttributes() throws IOException {
    String fileContent =
        "static {\n"
            + "    INSTRUMENTER =\n"
            + "        JavaagentHttpServerInstrumenters.create(\n"
            + "            AkkaHttpUtil.instrumentationName(),\n"
            + "            new AkkaHttpServerAttributesGetter(),\n"
            + "            AkkaHttpServerHeaders.INSTANCE);\n"
            + "  }";

    InstrumentationEntity entity =
        new InstrumentationEntity(
            "instrumentation/akkahttp/server", "akka-http-server", "akka", "akka", List.of());

    BufferedReader reader = new BufferedReader(new StringReader(fileContent));
    when(fileReaderHelper.getBufferedReader("singleton.java")).thenReturn(reader);

    analyzer.analyzeSemanticConventions(List.of("singleton.java"), entity);

    assertThat(entity.getSemanticConventions()).containsExactly("server_attributes");
  }

  @Test
  void testAnalyzeSpanTypesServer() throws IOException {
    String fileContent =
        "static {\n"
            + "    INSTRUMENTER =\n"
            + "        JavaagentHttpServerInstrumenters.create(\n"
            + "            AkkaHttpUtil.instrumentationName(),\n"
            + "            new AkkaHttpServerAttributesGetter(),\n"
            + "            AkkaHttpServerHeaders.INSTANCE);\n"
            + "  }";

    InstrumentationEntity entity =
        new InstrumentationEntity(
            "instrumentation/akkahttp/server", "akka-http-server", "akka", "akka", List.of());

    BufferedReader reader = new BufferedReader(new StringReader(fileContent));
    when(fileReaderHelper.getBufferedReader("singleton.java")).thenReturn(reader);

    analyzer.analyzeSpanTypes(List.of("singleton.java"), entity);

    assertThat(entity.getSpanTypes()).containsExactly("SERVER");
  }

  @Test
  void testAnalyzeSpanTypesClient() throws IOException {
    String fileContent =
        "static {\n"
            + "    INSTRUMENTER =\n"
            + "        JavaagentHttpClientInstrumenters.create(\n"
            + "            AkkaHttpUtil.instrumentationName(),\n"
            + "            new AkkaHttpServerAttributesGetter(),\n"
            + "            AkkaHttpServerHeaders.INSTANCE);\n"
            + "  }";

    InstrumentationEntity entity =
        new InstrumentationEntity(
            "instrumentation/akkahttp/server", "akka-http-server", "akka", "akka", List.of());

    BufferedReader reader = new BufferedReader(new StringReader(fileContent));
    when(fileReaderHelper.getBufferedReader("singleton.java")).thenReturn(reader);

    analyzer.analyzeSemanticConventions(List.of("singleton.java"), entity);

    assertThat(entity.getSemanticConventions()).containsExactly("server_attributes");
  }

  @Test
  void testConvertToEntities() {
    List<InstrumentationPath> paths =
        Arrays.asList(
            new InstrumentationPath(
                "log4j-appender-2.17",
                "instrumentation/log4j/log4j-appender-2.17/library",
                "log4j",
                "log4j",
                InstrumentationType.LIBRARY),
            new InstrumentationPath(
                "log4j-appender-2.17",
                "instrumentation/log4j/log4j-appender-2.17/javaagent",
                "log4j",
                "log4j",
                InstrumentationType.JAVAAGENT),
            new InstrumentationPath(
                "spring-web",
                "instrumentation/spring/spring-web/library",
                "spring",
                "spring",
                InstrumentationType.LIBRARY));

    List<InstrumentationEntity> entities = InstrumentationAnalyzer.convertToEntities(paths);

    assertThat(entities.size()).isEqualTo(2);

    InstrumentationEntity log4jEntity =
        entities.stream()
            .filter(e -> e.getInstrumentationName().equals("log4j-appender-2.17"))
            .findFirst()
            .orElse(null);

    assertThat(log4jEntity.getNamespace()).isEqualTo("log4j");
    assertThat(log4jEntity.getGroup()).isEqualTo("log4j");
    assertThat(log4jEntity.getSrcPath()).isEqualTo("instrumentation/log4j/log4j-appender-2.17");
    assertThat(log4jEntity.getTypes()).hasSize(2);
    assertThat(log4jEntity.getTypes())
        .containsExactly(InstrumentationType.LIBRARY, InstrumentationType.JAVAAGENT);

    InstrumentationEntity springEntity =
        entities.stream()
            .filter(e -> e.getInstrumentationName().equals("spring-web"))
            .findFirst()
            .orElse(null);

    assertThat(springEntity).isNotNull();
    assertThat(springEntity.getNamespace()).isEqualTo("spring");
    assertThat(springEntity.getGroup()).isEqualTo("spring");
    assertThat(springEntity.getSrcPath()).isEqualTo("instrumentation/spring/spring-web");
    assertThat(springEntity.getTypes()).hasSize(1);
    assertThat(springEntity.getTypes()).containsExactly(InstrumentationType.LIBRARY);
  }

  @Test
  void testExperimentalConfigParsing() throws IOException {
    String fileContent =
        ".setMessagingReceiveInstrumentationEnabled(\n"
            + "    ExperimentalConfig.get().messagingReceiveInstrumentationEnabled());\n";

    InstrumentationEntity entity =
        new InstrumentationEntity(
            "instrumentation/akkahttp/server", "akka-http-server", "akka", "akka", List.of());

    BufferedReader reader = new BufferedReader(new StringReader(fileContent));
    when(fileReaderHelper.getBufferedReader("singleton.java")).thenReturn(reader);

    analyzer.analyzeConfigurations(List.of("singleton.java"), entity);

    ConfigurationProperty result = entity.getConfigurationProperties().get(0);

    assertThat(result.name())
        .isEqualTo("otel.instrumentation.messaging.experimental.receive-telemetry.enabled");
    assertThat(result.type()).isEqualTo("Boolean");
    assertThat(result.defaultValue()).isEqualTo("false");
  }
}
