/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.generators;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.instrumentation.docs.generators.MarkerBasedUpdater.UpdateResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MarkerBasedUpdaterTest {

  @TempDir Path tempDir;

  @Test
  void shouldUpdateContentBetweenMarkers() {
    String originalContent =
        """
        # Documentation Page

        Some manual content here.

        <!-- BEGIN-GENERATED: COMPONENT:test-component SOURCE:test-source -->
        Old auto-generated content
        <!-- END-GENERATED: COMPONENT:test-component SOURCE:test-source -->

        More manual content.
        """;

    String newContent = "New auto-generated content\nWith multiple lines";

    MarkerBasedUpdater updater = new MarkerBasedUpdater();
    UpdateResult result =
        updater.updateContent(originalContent, "test-component", "test-source", newContent);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasChanges()).isTrue();
    assertThat(result.getUpdatedContent())
        .contains("New auto-generated content")
        .contains("With multiple lines")
        .contains("Some manual content here")
        .contains("More manual content")
        .doesNotContain("Old auto-generated content");
  }

  @Test
  void shouldPreserveManualContent() {
    String originalContent =
        """
        # Introduction

        Manual section 1

        <!-- BEGIN-GENERATED: COMPONENT:section1 SOURCE:repo -->
        Generated content
        <!-- END-GENERATED: COMPONENT:section1 SOURCE:repo -->

        Manual section 2
        """;

    MarkerBasedUpdater updater = new MarkerBasedUpdater();
    UpdateResult result = updater.updateContent(originalContent, "section1", "repo", "Updated");

    assertThat(result.getUpdatedContent())
        .contains("Manual section 1")
        .contains("Manual section 2")
        .contains("# Introduction");
  }

  @Test
  void shouldReportNoChangesWhenContentSame() {
    String content =
        """
        <!-- BEGIN-GENERATED: COMPONENT:test SOURCE:repo -->
        Same content
        <!-- END-GENERATED: COMPONENT:test SOURCE:repo -->
        """;

    MarkerBasedUpdater updater = new MarkerBasedUpdater();
    UpdateResult result = updater.updateContent(content, "test", "repo", "Same content");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasChanges()).isFalse();
  }

  @Test
  void shouldFailWhenMarkersNotFound() {
    String content = "Content without markers";

    MarkerBasedUpdater updater = new MarkerBasedUpdater();
    UpdateResult result = updater.updateContent(content, "missing", "repo", "New content");

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getErrorMessage()).contains("Markers not found");
  }

  @Test
  void shouldUpdateFile() throws IOException {
    Path testFile = tempDir.resolve("test.md");
    String originalContent =
        """
        # Test File

        <!-- BEGIN-GENERATED: COMPONENT:test SOURCE:repo -->
        Old content
        <!-- END-GENERATED: COMPONENT:test SOURCE:repo -->
        """;
    Files.writeString(testFile, originalContent);

    MarkerBasedUpdater updater = new MarkerBasedUpdater();
    UpdateResult result = updater.updateFile(testFile, "test", "repo", "New content");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.hasChanges()).isTrue();

    String updatedContent = Files.readString(testFile);
    assertThat(updatedContent).contains("New content").doesNotContain("Old content");
  }
}
