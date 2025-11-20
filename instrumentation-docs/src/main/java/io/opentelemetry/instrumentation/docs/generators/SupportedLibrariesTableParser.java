/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.generators;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the supported libraries table from docs/supported-libraries.md and transforms it to the
 * opentelemetry.io format.
 */
public class SupportedLibrariesTableParser {

  private static final Pattern LINK_PATTERN =
      Pattern.compile("\\[([^\\]]+)\\]\\(\\.\\./(instrumentation/[^)]+)\\)");
  private static final String MAVEN_CENTRAL_BASE =
      "https://central.sonatype.com/artifact/io.opentelemetry.instrumentation/";

  /**
   * Parses the supported libraries table from the given file.
   *
   * @param sourceFile path to docs/supported-libraries.md
   * @return the transformed table content ready for opentelemetry.io
   */
  public String parseAndTransform(Path sourceFile) throws IOException {
    List<String> lines = Files.readAllLines(sourceFile);
    StringBuilder result = new StringBuilder();

    boolean inTable = false;
    boolean headerProcessed = false;
    boolean tableEnded = false;
    List<String> linkDefinitions = new ArrayList<>();

    for (String line : lines) {
      // Collect link definitions (e.g., "[HTTP Server Spans]: https://...")
      // These can appear anywhere in the file, but typically after the table
      if (line.matches("^\\[[^\\]]+\\]:\\s+https?://.*")) {
        String transformedLink = transformLinkDefinition(line);
        linkDefinitions.add(transformedLink);
        continue;
      }

      // Detect start of table
      if (!tableEnded
          && line.startsWith("| Library/Framework")
          && line.contains("Semantic Conventions")) {
        inTable = true;
        // Transform header: "Semantic Conventions" -> "Functionality / Semantic Conventions"
        String transformedHeader =
            line.replace("Semantic Conventions", "Functionality / Semantic Conventions");
        result.append(transformedHeader).append("\n");
        continue;
      }

      // Process separator line
      if (inTable && !headerProcessed && line.startsWith("|---")) {
        result.append(line).append("\n");
        headerProcessed = true;
        continue;
      }

      // Process table rows
      if (inTable && headerProcessed && line.startsWith("|")) {
        // Check if we've reached the end of the table
        if (line.trim().equals("|")) {
          tableEnded = true;
          inTable = false;
          break;
        }
        String transformedLine = transformLinks(line);
        result.append(transformedLine).append("\n");
      }

      // Stop table processing if we hit a blank line after table starts
      if (inTable && headerProcessed && line.trim().isEmpty()) {
        tableEnded = true;
        inTable = false;
      }
    }

    // Add footer and link definitions
    result.append(
        "\n[1]: Standalone library instrumentations are published as separate artifacts and can be used without the Java agent.\n");

    // Add semantic convention link definitions
    if (!linkDefinitions.isEmpty()) {
      result.append("\n");
      for (String linkDef : linkDefinitions) {
        result.append(linkDef).append("\n");
      }
    }

    return result.toString();
  }

  /**
   * Transforms relative links to Maven Central URLs.
   *
   * @param line a table row with potential relative links
   * @return the line with transformed links
   */
  private static String transformLinks(String line) {
    Matcher matcher = LINK_PATTERN.matcher(line);
    StringBuffer sb = new StringBuffer();

    while (matcher.find()) {
      String linkText = matcher.group(1);
      // matcher.group(2) is the relative path, not needed for transformation

      // Extract artifact name from link text (e.g., "opentelemetry-alibaba-druid-1.0")
      String artifactId = linkText;

      // Build Maven Central URL
      String mavenCentralUrl = MAVEN_CENTRAL_BASE + artifactId;

      // Replace with Maven Central link
      matcher.appendReplacement(sb, "[" + linkText + "](" + mavenCentralUrl + ")");
    }
    matcher.appendTail(sb);

    return sb.toString();
  }

  /**
   * Transforms semantic convention link definitions from GitHub URLs to opentelemetry.io URLs.
   *
   * @param linkDefinition a line like "[HTTP Server Spans]: https://github.com/..."
   * @return the transformed link definition for opentelemetry.io
   */
  private static String transformLinkDefinition(String linkDefinition) {
    // Pattern: [Link Text]:
    // https://github.com/open-telemetry/semantic-conventions/blob/main/docs/PATH
    // Transform to: [Link Text]: /docs/specs/semconv/PATH

    String pattern = "https://github\\.com/open-telemetry/semantic-conventions/blob/main/docs/";
    String replacement = "/docs/specs/semconv/";

    // Also handle .md extensions (remove them)
    String transformed = linkDefinition.replaceFirst(pattern, replacement);
    transformed = transformed.replace(".md#", "/#");
    transformed = transformed.replace(".md", "/");

    return transformed;
  }

  /**
   * Extracts just the table rows (without header/separator) for testing or comparison.
   *
   * @param sourceFile path to docs/supported-libraries.md
   * @return list of table row lines
   */
  public List<String> extractTableRows(Path sourceFile) throws IOException {
    List<String> lines = Files.readAllLines(sourceFile);
    List<String> rows = new ArrayList<>();

    boolean inTable = false;
    boolean headerSkipped = false;

    for (String line : lines) {
      if (line.startsWith("| Library/Framework")) {
        inTable = true;
        continue;
      }

      if (inTable && !headerSkipped && line.startsWith("|---")) {
        headerSkipped = true;
        continue;
      }

      if (inTable && headerSkipped && line.startsWith("|")) {
        if (line.trim().equals("|")) {
          break;
        }
        rows.add(line);
      }

      if (inTable && headerSkipped && line.trim().isEmpty()) {
        break;
      }
    }

    return rows;
  }
}
