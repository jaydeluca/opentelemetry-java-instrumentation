/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FileSearchTest {

  @Test
  void testIsValidInstrumentationPath_ValidJavaagentPath() {
    String filePath = "instrumentation/testInstrumentation/javaagent";
    assertThat(FileSearch.isValidInstrumentationPath(filePath)).isTrue();
  }

  @Test
  void testIsValidInstrumentationPath_ValidLibraryPath() {
    String filePath = "instrumentation/testInstrumentation/library";
    assertThat(FileSearch.isValidInstrumentationPath(filePath)).isTrue();
  }

  @Test
  void testIsValidInstrumentationPath_InvalidPath_NoInstrumentationSegment() {
    String filePath = "testInstrumentation/javaagent";
    assertThat(FileSearch.isValidInstrumentationPath(filePath)).isFalse();
  }

  @Test
  void testIsValidInstrumentationPath_InvalidPath_NoValidEnding() {
    String filePath = "instrumentation/testInstrumentation/other";
    assertThat(FileSearch.isValidInstrumentationPath(filePath)).isFalse();
  }

  @Test
  void testIsValidInstrumentationPath_InvalidPath_MultipleJavaAgent() {
    String filePath =
        "instrumentation/kubernetes-client-7.0/javaagent/src/version20Test/java/io/opentelemetry/javaagent";
    assertThat(FileSearch.isValidInstrumentationPath(filePath)).isFalse();
  }

  @Test
  void testIsValidInstrumentationPath_InvalidPath_TestPath() {
    String filePath =
        "instrumentation/spring/spring-web/spring-web-3.1/testing/src/test/java/io/opentelemetry/javaagent";
    assertThat(FileSearch.isValidInstrumentationPath(filePath)).isFalse();
  }

  @Test
  void testIsValidInstrumentationPath_EmptyPath() {
    String filePath = "";
    assertThat(FileSearch.isValidInstrumentationPath(filePath)).isFalse();
  }

  @Test
  void testIsValidInstrumentationPath_NullPath() {
    String filePath = null;
    assertThat(FileSearch.isValidInstrumentationPath(filePath)).isFalse();
  }

  @Test
  void testParseInstrumentationPath_ValidPath() {
    String filePath = "instrumentation/clickhouse/clickhouse-1.0/library";
    InstrumentationPath result = FileSearch.parseInstrumentationPath(filePath);
    assertThat(result.getInstrumentationName()).isEqualTo("clickhouse-1.0");
    assertThat(result.getNamespace()).isEqualTo("clickhouse");
    assertThat(result.getGroup()).isEqualTo("clickhouse");
    assertThat(result.getSrcPath()).isEqualTo("instrumentation/clickhouse/clickhouse-1.0/library");
  }

  @Test
  void testParseInstrumentationPath_ValidPath_WithParent() {
    String filePath = "instrumentation/clickhouse-1.0/javaagent";
    InstrumentationPath result = FileSearch.parseInstrumentationPath(filePath);
    assertThat(result.getInstrumentationName()).isEqualTo("clickhouse-1.0");
    assertThat(result.getNamespace()).isEqualTo("clickhouse");
    assertThat(result.getGroup()).isEqualTo("clickhouse");
  }

  @Test
  void testParseInstrumentationPath_EmptyPath() {
    String filePath = "";
    InstrumentationPath result = FileSearch.parseInstrumentationPath(filePath);
    assertThat(result).isEqualTo(null);
  }

  @Test
  void testParseInstrumentationPath_NullPath() {
    String filePath = null;
    InstrumentationPath result = FileSearch.parseInstrumentationPath(filePath);
    assertThat(result).isEqualTo(null);
  }

  //  @Test
  //  void testFindStringInFiles() throws Exception {
  //    Path tempDir = Files.createTempDirectory("testDir");
  //    Path tempFile = Files.createFile(tempDir.resolve("testFile.java"));
  //    Files.writeString(tempFile, ".addOperationMetrics(DbClientMetrics.get())");
  //
  //    List<String> result = FileSearch.findStringInFiles(List.of(tempFile.toString()),
  // List.of("DbClientMetrics.get()"));
  //
  //    assertThat(result).containsExactly(tempFile.toString());
  //    assertThat(result.size()).isEqualTo(1);
  //    assertThat(result.get(0)).endsWith("testFile.java");
  //
  //    Files.delete(tempFile);
  //    Files.delete(tempDir);
  //  }
}
