/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class GradleParserTest {

  @Test
  void testExtractMuzzleVersions_SinglePassBlock() {
    String gradleBuildFileContent =
        "muzzle {\n"
            + "  pass {\n"
            + "    group.set(\"org.elasticsearch.client\")\n"
            + "    module.set(\"rest\")\n"
            + "    versions.set(\"[5.0,6.4)\")\n"
            + "  }\n"
            + "}";
    List<String> versions = GradleParser.parseMuzzleBlock(gradleBuildFileContent);
    assertThat(versions.size()).isEqualTo(1);
    assertThat(versions.get(0)).isEqualTo("org.elasticsearch.client:rest: 5.0, 6.4");
  }
  //
  //  @Test
  //  void testExtractMuzzleVersions_MultiplePassBlocks() {
  //    String gradleBuildFileContent = "muzzle {\n" +
  //        "  pass {\n" +
  //        "    group.set(\"org.elasticsearch.client\")\n" +
  //        "    module.set(\"rest\")\n" +
  //        "    versions.set(\"[5.0,6.4)\")\n" +
  //        "  }\n" +
  //        "  pass {\n" +
  //        "    group.set(\"org.elasticsearch.client\")\n" +
  //        "    module.set(\"elasticsearch-rest-client\")\n" +
  //        "    versions.set(\"[5.0,6.4)\")\n" +
  //        "  }\n" +
  //        "}";
  //    List<String> versions = GradleParser.extractMuzzleVersions(gradleBuildFileContent);
  //    assertEquals(2, versions.size());
  //    assertEquals("org.elasticsearch.client:rest: 5.0, 6.4", versions.get(0));
  //    assertEquals("org.elasticsearch.client:elasticsearch-rest-client: 5.0, 6.4",
  // versions.get(1));
  //  }
  //
  //  @Test
  //  void testExtractMuzzleVersions_NoMuzzleBlock() {
  //    String gradleBuildFileContent = "dependencies {\n" +
  //        "  implementation 'org.springframework.boot:spring-boot-starter'\n" +
  //        "}";
  //    List<String> versions = GradleParser.extractMuzzleVersions(gradleBuildFileContent);
  //    assertTrue(versions.isEmpty());
  //  }
  //
  //  @Test
  //  void testExtractMuzzleVersions_EmptyContent() {
  //    String gradleBuildFileContent = "";
  //    List<String> versions = GradleParser.extractMuzzleVersions(gradleBuildFileContent);
  //    assertTrue(versions.isEmpty());
  //  }
}
