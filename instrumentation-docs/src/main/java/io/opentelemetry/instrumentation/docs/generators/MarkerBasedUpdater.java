/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.generators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Updates markdown files by replacing content between HTML comment markers while preserving manual
 * content.
 *
 * <p>Markers follow the format: {@code <!-- BEGIN-GENERATED: COMPONENT:name SOURCE:repo -->}
 * content {@code <!-- END-GENERATED: COMPONENT:name SOURCE:repo -->}
 *
 * <p>This approach allows manual documentation sections to coexist with auto-generated content,
 * making updates non-destructive and preserving custom edits outside marked regions.
 */
public class MarkerBasedUpdater {

  private static final String DEFAULT_COMPONENT_PREFIX = "COMPONENT:";
  private static final String DEFAULT_SOURCE_PREFIX = "SOURCE:";

  private final String componentPrefix;
  private final String sourcePrefix;

  /** Creates a new MarkerBasedUpdater with default marker prefixes. */
  public MarkerBasedUpdater() {
    this(DEFAULT_COMPONENT_PREFIX, DEFAULT_SOURCE_PREFIX);
  }

  /**
   * Creates a new MarkerBasedUpdater with custom marker prefixes.
   *
   * @param componentPrefix prefix for component identifier in markers
   * @param sourcePrefix prefix for source identifier in markers
   */
  public MarkerBasedUpdater(String componentPrefix, String sourcePrefix) {
    this.componentPrefix = componentPrefix;
    this.sourcePrefix = sourcePrefix;
  }

  /**
   * Updates a markdown file by replacing content between markers.
   *
   * @param filePath path to the markdown file to update
   * @param componentId component identifier (e.g., "supported-libraries")
   * @param sourceId source identifier (e.g., "opentelemetry-java-instrumentation")
   * @param newContent new content to insert between markers
   * @return UpdateResult indicating success and whether changes were made
   * @throws IOException if file operations fail
   */
  public UpdateResult updateFile(
      Path filePath, String componentId, String sourceId, String newContent) throws IOException {

    if (!Files.exists(filePath)) {
      return UpdateResult.failure("File not found: " + filePath);
    }

    String originalContent = Files.readString(filePath);
    UpdateResult result = updateContent(originalContent, componentId, sourceId, newContent);

    if (result.isSuccess() && result.hasChanges()) {
      Files.writeString(filePath, result.getUpdatedContent());
    }

    return result;
  }

  /**
   * Updates content string by replacing content between markers.
   *
   * @param originalContent original markdown content
   * @param componentId component identifier (e.g., "supported-libraries")
   * @param sourceId source identifier (e.g., "opentelemetry-java-instrumentation")
   * @param newContent new content to insert between markers
   * @return UpdateResult indicating success and whether changes were made
   */
  public UpdateResult updateContent(
      String originalContent, String componentId, String sourceId, String newContent) {

    MarkerPair markers = buildMarkers(componentId, sourceId);

    // Create pattern to match content between markers (non-greedy)
    String patternString = Pattern.quote(markers.begin()) + ".*?" + Pattern.quote(markers.end());
    Pattern pattern = Pattern.compile(patternString, Pattern.DOTALL);

    Matcher matcher = pattern.matcher(originalContent);

    if (!matcher.find()) {
      return UpdateResult.failure(
          String.format(
              "Markers not found for component '%s' and source '%s'", componentId, sourceId));
    }

    // Build replacement content with markers
    String replacement = markers.begin() + "\n" + newContent.trim() + "\n" + markers.end();

    // Replace the matched section
    String updatedContent = matcher.replaceFirst(Matcher.quoteReplacement(replacement));

    // Check if content actually changed
    boolean hasChanges = !originalContent.equals(updatedContent);

    return UpdateResult.success(updatedContent, hasChanges);
  }

  /**
   * Checks if a file contains the specified markers.
   *
   * @param filePath path to the markdown file
   * @param componentId component identifier
   * @param sourceId source identifier
   * @return true if both begin and end markers are present
   * @throws IOException if file operations fail
   */
  public boolean hasMarkers(Path filePath, String componentId, String sourceId) throws IOException {
    if (!Files.exists(filePath)) {
      return false;
    }

    String content = Files.readString(filePath);
    MarkerPair markers = buildMarkers(componentId, sourceId);

    return content.contains(markers.begin()) && content.contains(markers.end());
  }

  private MarkerPair buildMarkers(String componentId, String sourceId) {
    String begin =
        String.format(
            "<!-- BEGIN-GENERATED: %s%s %s%s -->",
            componentPrefix, componentId, sourcePrefix, sourceId);
    String end =
        String.format(
            "<!-- END-GENERATED: %s%s %s%s -->",
            componentPrefix, componentId, sourcePrefix, sourceId);
    return new MarkerPair(begin, end);
  }

  private record MarkerPair(String begin, String end) {}

  /** Result of an update operation. */
  public static class UpdateResult {
    private final boolean success;
    @javax.annotation.Nullable private final String updatedContent;
    private final boolean hasChanges;
    @javax.annotation.Nullable private final String errorMessage;

    private UpdateResult(
        boolean success,
        @javax.annotation.Nullable String updatedContent,
        boolean hasChanges,
        @javax.annotation.Nullable String errorMessage) {
      this.success = success;
      this.updatedContent = updatedContent;
      this.hasChanges = hasChanges;
      this.errorMessage = errorMessage;
    }

    static UpdateResult success(String updatedContent, boolean hasChanges) {
      return new UpdateResult(true, updatedContent, hasChanges, null);
    }

    static UpdateResult failure(String errorMessage) {
      return new UpdateResult(false, null, false, errorMessage);
    }

    public boolean isSuccess() {
      return success;
    }

    @javax.annotation.Nullable
    public String getUpdatedContent() {
      return updatedContent;
    }

    public boolean hasChanges() {
      return hasChanges;
    }

    @javax.annotation.Nullable
    public String getErrorMessage() {
      return errorMessage;
    }
  }
}
