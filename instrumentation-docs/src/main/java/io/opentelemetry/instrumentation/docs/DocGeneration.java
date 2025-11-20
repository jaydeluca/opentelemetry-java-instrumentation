/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import io.opentelemetry.instrumentation.docs.generators.DisableListDocGenerator;
import io.opentelemetry.instrumentation.docs.generators.MarkerBasedUpdater;
import io.opentelemetry.instrumentation.docs.generators.SupportedLibrariesTableParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Main entry point for generating and updating documentation on opentelemetry.io.
 *
 * <p>This class orchestrates the process of:
 *
 * <ol>
 *   <li>Analyzing instrumentation modules from this repository
 *   <li>Generating markdown content for supported libraries
 *   <li>Updating opentelemetry.io documentation files using marker-based replacement
 *   <li>Reporting on changes made
 * </ol>
 *
 * <p>System properties:
 *
 * <ul>
 *   <li>{@code basePath} - Path to the root of this repository (default: "./")
 *   <li>{@code docsRepoPath} - Path to opentelemetry.io repository clone (default:
 *       "../opentelemetry.io")
 *   <li>{@code generateVersion} - Version string to include in generated docs (default: read from
 *       version file)
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>
 * ./gradlew :instrumentation-docs:generateDocs \
 *   -PdocsRepoPath=../opentelemetry.io \
 *   -Pversion=v2.11.0
 * </pre>
 */
public class DocGeneration {
  private static final Logger logger = Logger.getLogger(DocGeneration.class.getName());

  private static final String SOURCE_ID = "opentelemetry-java-instrumentation";

  public static void main(String[] args) {
    try {
      String baseRepoPath = System.getProperty("basePath", "./");
      if (!baseRepoPath.endsWith("/")) {
        baseRepoPath += "/";
      }

      String docsRepoPath = System.getProperty("docsRepoPath", "../opentelemetry.io");
      String version = System.getProperty("generateVersion", detectVersion(baseRepoPath));

      logger.info("Starting documentation generation...");
      logger.info("Base repository: " + baseRepoPath);
      logger.info("Docs repository: " + docsRepoPath);
      logger.info("Version: " + version);

      // Parse supported libraries table from docs/supported-libraries.md
      SupportedLibrariesTableParser parser = new SupportedLibrariesTableParser();
      Path sourceLibrariesFile = Paths.get(baseRepoPath, "docs", "supported-libraries.md");
      String supportedLibrariesContent = parser.parseAndTransform(sourceLibrariesFile);
      logger.info("Parsed and transformed supported libraries table from " + sourceLibrariesFile);

      // Generate disable list from instrumentation-list.yaml (same source as auditor)
      Path instrumentationListFile = Paths.get(baseRepoPath, "docs", "instrumentation-list.yaml");
      String instrumentationListYaml = Files.readString(instrumentationListFile);

      DisableListDocGenerator disableListGenerator = new DisableListDocGenerator(version);
      String disableListContent =
          disableListGenerator.generateDisableListTableFromYaml(instrumentationListYaml);
      logger.info("Generated disable list from instrumentation-list.yaml");

      // Update documentation files
      Path docsPath = Paths.get(docsRepoPath);
      if (!Files.exists(docsPath)) {
        logger.severe("Documentation repository not found at: " + docsRepoPath);
        logger.severe("Please clone opentelemetry.io or specify correct path with -PdocsRepoPath");
        System.exit(1);
      }

      Path agentDocsDir = docsPath.resolve("content/en/docs/zero-code/java/agent");
      if (!Files.exists(agentDocsDir)) {
        logger.severe("Agent docs directory not found at: " + agentDocsDir);
        System.exit(1);
      }

      MarkerBasedUpdater updater = new MarkerBasedUpdater();
      boolean hasAnyChanges = false;

      // Update supported-libraries.md
      hasAnyChanges |=
          updateDocumentationPage(
              updater,
              agentDocsDir.resolve("supported-libraries.md"),
              "supported-libraries",
              supportedLibrariesContent);

      // Update disable.md
      hasAnyChanges |=
          updateDocumentationPage(
              updater, agentDocsDir.resolve("disable.md"), "disable-list", disableListContent);

      if (hasAnyChanges) {
        logger.info("\n✅ Documentation updated successfully!");
        logger.info("Review changes with: cd " + docsRepoPath + " && git diff");
      } else {
        logger.info("\n✅ All documentation is already up to date");
      }

    } catch (IOException e) {
      logger.severe("Error during documentation generation: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    } catch (RuntimeException e) {
      logger.severe("Unexpected error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  /**
   * Updates a single documentation page with marker-based content replacement.
   *
   * @param updater the marker-based updater
   * @param targetFile path to the file to update
   * @param componentId component identifier for markers
   * @param newContent new content to insert
   * @return true if changes were made, false otherwise
   */
  private static boolean updateDocumentationPage(
      MarkerBasedUpdater updater, Path targetFile, String componentId, String newContent) {

    if (!Files.exists(targetFile)) {
      logger.warning("Target file not found: " + targetFile);
      logger.warning("Skipping (markers may not have been added yet)");
      return false;
    }

    // Check if markers exist
    try {
      if (!updater.hasMarkers(targetFile, componentId, SOURCE_ID)) {
        logger.warning("Markers not found in " + targetFile);
        logger.warning(
            "Expected: <!-- BEGIN-GENERATED: COMPONENT:"
                + componentId
                + " SOURCE:"
                + SOURCE_ID
                + " -->");
        logger.warning("Skipping");
        return false;
      }

      // Update the file
      MarkerBasedUpdater.UpdateResult result =
          updater.updateFile(targetFile, componentId, SOURCE_ID, newContent);

      if (!result.isSuccess()) {
        logger.severe("Failed to update " + targetFile + ": " + result.getErrorMessage());
        return false;
      }

      if (result.hasChanges()) {
        logger.info("✅ Updated: " + targetFile.getFileName());
        return true;
      } else {
        logger.info("  No changes: " + targetFile.getFileName());
        return false;
      }

    } catch (IOException e) {
      logger.severe("Error updating " + targetFile + ": " + e.getMessage());
      return false;
    }
  }

  private static String detectVersion(String baseRepoPath) {
    // Try to read version from gradle.properties or version.gradle.kts
    try {
      Path versionFile = Paths.get(baseRepoPath, "version.gradle.kts");
      if (Files.exists(versionFile)) {
        String content = Files.readString(versionFile);
        // Look for version = "x.y.z"
        if (content.contains("version = \"")) {
          int start = content.indexOf("version = \"") + "version = \"".length();
          int end = content.indexOf("\"", start);
          if (end > start) {
            return "v" + content.substring(start, end);
          }
        }
      }
    } catch (IOException e) {
      logger.warning("Could not detect version: " + e.getMessage());
    }
    return "latest";
  }

  private DocGeneration() {}
}
