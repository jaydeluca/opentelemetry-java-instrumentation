/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.generators;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.internal.SemanticConvention;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Generates markdown documentation for the supported libraries table on opentelemetry.io.
 *
 * <p>This generator creates Markdown tables matching the format expected by the opentelemetry.io
 * documentation site, specifically for the Java agent supported libraries page.
 */
public record SupportedLibrariesDocGenerator(String version) {

  /**
   * Creates a new generator.
   *
   * @param version version string to include in generated content (e.g., "v2.11.0")
   */
  public SupportedLibrariesDocGenerator {}

  /**
   * Generates the complete supported libraries table markdown.
   *
   * @param modules list of instrumentation modules to document
   * @return Markdown table content (without surrounding markers)
   */
  public String generateSupportedLibrariesTable(List<InstrumentationModule> modules) {
    StringBuilder sb = new StringBuilder();

    // Group modules by library group (e.g., "akka", "kafka", etc.)
    Map<String, List<InstrumentationModule>> grouped =
        modules.stream()
            .filter(m -> m.getMetadata() != null)
            .sorted(Comparator.comparing(InstrumentationModule::getInstrumentationName))
            .collect(
                Collectors.groupingBy(
                    SupportedLibrariesDocGenerator::extractLibraryGroup, Collectors.toList()));

    // Generate table header matching existing format
    sb.append(
        "| Library/Framework | Auto-instrumented versions | Standalone Library Instrumentation [1] | Functionality / Semantic Conventions |\n");
    sb.append(
        "| ----------------- | -------------------------- | -------------------------------------- | ------------------------------------ |\n");

    // Generate rows grouped by library
    grouped.forEach(
        (group, groupModules) -> {
          for (InstrumentationModule module : groupModules) {
            generateTableRow(sb, module);
          }
        });

    sb.append("\n");
    sb.append("_Auto-generated for version ").append(version).append("_\n");

    return sb.toString();
  }

  /**
   * Generates the application servers table markdown.
   *
   * @param modules list of instrumentation modules
   * @return Markdown table content
   */
  public String generateAppServersTable(List<InstrumentationModule> modules) {
    // Filter to only app server modules
    List<InstrumentationModule> appServers =
        modules.stream()
            .filter(m -> m.getMetadata() != null)
            .filter(SupportedLibrariesDocGenerator::isAppServer)
            .sorted(Comparator.comparing(InstrumentationModule::getInstrumentationName))
            .toList();

    if (appServers.isEmpty()) {
      return "No application servers documented.\n";
    }

    StringBuilder sb = new StringBuilder();
    sb.append("| Application Server | Versions |\n");
    sb.append("|--------------------|----------|\n");

    for (InstrumentationModule module : appServers) {
      String displayName = getDisplayName(module);
      String versions = getVersionRange(module);
      sb.append("| ").append(displayName).append(" | ").append(versions).append(" |\n");
    }

    sb.append("\n");
    sb.append("_Auto-generated for version ").append(version).append("_\n");

    return sb.toString();
  }

  private static void generateTableRow(StringBuilder sb, InstrumentationModule module) {
    String displayName = getDisplayName(module);
    String libraryLink = getLibraryLink(module);
    String versions = getVersionRange(module);
    String libraryInstrumentation = getLibraryInstrumentation(module);
    String semanticConventions = getSemanticConventions(module);

    sb.append("| ");

    // Library name with link
    if (libraryLink != null) {
      sb.append("[").append(displayName).append("](").append(libraryLink).append(")");
    } else {
      sb.append(displayName);
    }

    sb.append(" | ")
        .append(versions)
        .append(" | ")
        .append(libraryInstrumentation)
        .append(" | ")
        .append(semanticConventions)
        .append(" |\n");
  }

  private static String extractLibraryGroup(InstrumentationModule module) {
    // Extract the library name from the instrumentation name
    // e.g., "akka-actor-2.3" -> "akka"
    String name = module.getInstrumentationName();
    int dashIndex = name.indexOf('-');
    if (dashIndex > 0) {
      return name.substring(0, dashIndex);
    }
    return name;
  }

  private static boolean isAppServer(InstrumentationModule module) {
    // Identify app servers based on name patterns or metadata
    String name = module.getInstrumentationName().toLowerCase(Locale.ROOT);
    return name.contains("tomcat")
        || name.contains("jetty")
        || name.contains("websphere")
        || name.contains("wildfly")
        || name.contains("undertow")
        || name.contains("glassfish");
  }

  private static String getDisplayName(InstrumentationModule module) {
    // Always prefer display_name from metadata if available
    if (module.getMetadata() != null && module.getMetadata().getDisplayName() != null) {
      String displayName = module.getMetadata().getDisplayName();
      if (!displayName.isEmpty()) {
        return displayName;
      }
    }
    // Fallback to instrumentation name
    return module.getInstrumentationName();
  }

  @Nullable
  private static String getLibraryLink(InstrumentationModule module) {
    if (module.getMetadata() != null) {
      return module.getMetadata().getLibraryLink();
    }
    return null;
  }

  private static String getVersionRange(InstrumentationModule module) {
    Map<InstrumentationType, Set<String>> targetVersions = module.getTargetVersions();
    if (targetVersions == null || targetVersions.isEmpty()) {
      return "N/A";
    }

    // Get the javaagent versions
    Set<String> javaagentVersions = targetVersions.get(InstrumentationType.JAVAAGENT);
    if (javaagentVersions == null || javaagentVersions.isEmpty()) {
      return "N/A";
    }

    // Convert set to sorted list for deterministic output
    List<String> versions = javaagentVersions.stream().sorted().toList();

    if (versions.size() == 1) {
      return formatVersionRange(versions.get(0));
    } else {
      // Multiple version ranges - join with <br>
      return versions.stream()
          .map(SupportedLibrariesDocGenerator::formatVersionRange)
          .collect(Collectors.joining("<br>"));
    }
  }

  private static String formatVersionRange(String versionSpec) {
    // Extract version range from Maven coordinate
    // e.g., "io.activej:activej-http:[6.0,)" -> "[6.0,)"
    String versionRange = versionSpec;
    if (versionSpec.contains(":")) {
      String[] parts = versionSpec.split(":");
      if (parts.length >= 3) {
        versionRange = parts[parts.length - 1];
      }
    }

    // Convert Maven version range to readable format
    // e.g., "[2.3,)" -> "2.3+"
    // e.g., "[2.0,3.0)" -> "2.0 - 2.x"
    if (versionRange.matches("\\[.*,\\)")) {
      String version = versionRange.replaceAll("[\\[\\),]", "").trim();
      return version + "+";
    } else if (versionRange.matches("\\[.*,.*\\)")) {
      // Handle ranges like "[2.0,3.0)"
      String[] rangeParts = versionRange.replaceAll("[\\[\\)\\(]", "").split(",");
      if (rangeParts.length == 2) {
        return rangeParts[0].trim() + " - " + rangeParts[1].trim();
      }
    }
    return versionRange;
  }

  private static String getLibraryInstrumentation(InstrumentationModule module) {
    // Check if library instrumentation exists
    String sourcePath = module.getSrcPath();
    if (sourcePath != null && sourcePath.contains("/library")) {
      // Extract the library module name
      String[] parts = sourcePath.split("/");
      for (int i = 0; i < parts.length; i++) {
        if ("instrumentation".equals(parts[i]) && i + 1 < parts.length) {
          String libName = parts[i + 1];
          return String.format(
              "[opentelemetry-%s](../%s/library)",
              libName, sourcePath.replace("instrumentation/", ""));
        }
      }
    }
    return "N/A";
  }

  private static String getSemanticConventions(InstrumentationModule module) {
    if (module.getMetadata() == null || module.getMetadata().getSemanticConventions() == null) {
      return "none";
    }

    List<SemanticConvention> conventions = module.getMetadata().getSemanticConventions();
    if (conventions.isEmpty()) {
      return "none";
    }

    // Format as links to semantic conventions
    return conventions.stream()
        .map(SupportedLibrariesDocGenerator::formatSemanticConvention)
        .collect(Collectors.joining(", "));
  }

  private static String formatSemanticConvention(SemanticConvention convention) {
    // Convert enum name to readable format
    // e.g., "HTTP_SERVER_SPANS" -> "[HTTP Server Spans]"
    String name = convention.name();

    // List of acronyms that should stay uppercase
    Set<String> acronyms = Set.of("HTTP", "RPC", "JVM", "GRPC", "DNS", "DB", "SQL", "URL");

    // Split by underscore, format each word, join with spaces
    String[] parts = name.split("_");
    StringBuilder readable = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        readable.append(" ");
      }
      String part = parts[i];

      // Keep acronyms uppercase, otherwise capitalize first letter only
      if (acronyms.contains(part)) {
        readable.append(part);
      } else {
        String lower = part.toLowerCase(Locale.ROOT);
        readable.append(Character.toUpperCase(lower.charAt(0)));
        if (lower.length() > 1) {
          readable.append(lower.substring(1));
        }
      }
    }

    // Return as markdown link reference
    return "[" + readable + "]";
  }
}
