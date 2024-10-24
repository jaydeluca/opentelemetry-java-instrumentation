/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.resources;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.semconv.SchemaUrls;
import io.opentelemetry.semconv.incubating.ProcessIncubatingAttributes;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.SetSystemProperty;

class ProcessResourceTest {

  @Test
  @SetSystemProperty(key = "os.name", value = "Linux 4.12")
  void notWindows() {
    Resource resource = ProcessResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    Attributes attributes = resource.getAttributes();

    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH))
        .matches(".*[/\\\\]java");
    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_COMMAND_LINE))
        .contains(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH));
    // With Java 9+ and a compiled jar, ResourceAttributes.PROCESS_COMMAND_ARGS
    // will be set instead of ResourceAttributes.PROCESS_COMMAND_LINE
  }

  @Test
  @SetSystemProperty(key = "os.name", value = "Windows 10")
  void windows() {
    Resource resource = ProcessResource.buildResource();
    assertThat(resource.getSchemaUrl()).isEqualTo(SchemaUrls.V1_24_0);
    Attributes attributes = resource.getAttributes();

    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_PID)).isGreaterThan(1);
    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH))
        .matches(".*[/\\\\]java\\.exe");
    assertThat(attributes.get(ProcessIncubatingAttributes.PROCESS_COMMAND_LINE))
        .contains(attributes.get(ProcessIncubatingAttributes.PROCESS_EXECUTABLE_PATH));
    // With Java 9+ and a compiled jar, ResourceAttributes.PROCESS_COMMAND_ARGS
    // will be set instead of ResourceAttributes.PROCESS_COMMAND_LINE
  }
}
