/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import io.opentelemetry.testing.internal.jackson.dataformat.yaml.YAMLFactory;
import io.opentelemetry.testing.internal.jackson.dataformat.yaml.YAMLGenerator;
import io.opentelemetry.testing.internal.jackson.dataformat.yaml.YAMLMapper;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Used in test runners to write configuration usage to metadata files within a .config directory in
 * each instrumentation module. This information is then parsed and used to generate the
 * instrumentation-list.yaml file.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ConfigMetaDataCollector {

  private static final String TMP_DIR = ".config";
  private static final Pattern MODULE_PATTERN =
      Pattern.compile("(.*?/instrumentation/.*?)(/javaagent|/library|/testing)");

  private static final YAMLMapper YAML =
      YAMLMapper.builder(
              YAMLFactory.builder()
                  .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                  .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                  .build())
          .build();

  public static void writeConfigToFiles(
      String path, Map<String, List<ConfigUsage>> configUsageByInstrumentation) throws IOException {

    if (configUsageByInstrumentation.isEmpty()) {
      return;
    }

    String moduleRoot = extractInstrumentationPath(path);
    writeConfigUsageData(moduleRoot, configUsageByInstrumentation);
  }

  private static String extractInstrumentationPath(String path) {
    Matcher matcher = MODULE_PATTERN.matcher(path);
    if (!matcher.find()) {
      throw new IllegalArgumentException("Invalid path: " + path);
    }

    String instrumentationPath = matcher.group(1);
    Path configDir = Paths.get(instrumentationPath, TMP_DIR);

    try {
      Files.createDirectories(configDir);
    } catch (FileAlreadyExistsException ignored) {
      // Directory already exists; nothing to do
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }

    return instrumentationPath;
  }

  private static void writeConfigUsageData(
      String instrumentationPath, Map<String, List<ConfigUsage>> configUsageByInstrumentation)
      throws IOException {

    Path configPath =
        Paths.get(instrumentationPath, TMP_DIR, "config-" + UUID.randomUUID() + ".yaml");

    // Build hierarchical structure preserving the full path from "instrumentation" onwards
    // Use TreeMap to sort keys alphabetically
    Map<String, Object> rootConfig = new TreeMap<>();

    for (Map.Entry<String, List<ConfigUsage>> entry : configUsageByInstrumentation.entrySet()) {
      List<ConfigUsage> usages = entry.getValue();

      for (ConfigUsage usage : usages) {
        // Use the full path starting from "instrumentation"
        // E.g., "instrumentation.java.graphql.capture_query"
        // This will create: instrumentation -> java -> graphql -> capture_query
        String fullPath = usage.getPath();
        // Keep the full path including "instrumentation" prefix
        addToHierarchy(rootConfig, fullPath, usage);
      }
    }

    // Sort recursively
    Map<String, Object> sortedConfig = sortMapRecursively(rootConfig);
    YAML.writeValue(configPath.toFile(), sortedConfig);
  }

  /**
   * Adds a configuration property to the hierarchical map structure. E.g., path: "capture_query",
   * usage: {type: boolean, default: true} result: {capture_query: {type: boolean, default: true}}
   *
   * <p>E.g., path: "query_sanitizer.enabled", usage: {type: boolean, default: true} result:
   * {query_sanitizer: {enabled: {type: boolean, default: true}}}
   *
   * <p>E.g., path: "instrumentation.java.graphql_java_12.0.enabled" will skip the version number
   * "0" and create: {instrumentation: {java: {graphql_java_12: {enabled: {...}}}}}
   */
  @SuppressWarnings("unchecked")
  private static void addToHierarchy(Map<String, Object> current, String path, ConfigUsage usage) {
    String[] parts = path.split("\\.");

    // Navigate/create nested maps for all parts except the last, skipping version numbers
    for (int i = 0; i < parts.length - 1; i++) {
      String part = parts[i];
      // Skip numeric version parts (e.g., "0", "12", "1.0") that may appear in paths like
      // "instrumentation.java.graphql_java_12.0.enabled"
      if (isNumeric(part)) {
        continue;
      }
      current = (Map<String, Object>) current.computeIfAbsent(part, k -> new LinkedHashMap<>());
    }

    // Add the final property with its metadata
    String finalKey = parts[parts.length - 1];
    // Skip if the final key is also numeric (shouldn't happen, but handle gracefully)
    if (isNumeric(finalKey)) {
      return;
    }
    Map<String, Object> propertyInfo = new LinkedHashMap<>();
    propertyInfo.put("type", usage.getType());
    propertyInfo.put("default", usage.getDefaultValue());

    current.put(finalKey, propertyInfo);
  }

  /** Checks if a string is numeric (represents a version number). */
  private static boolean isNumeric(String str) {
    if (str == null || str.isEmpty()) {
      return false;
    }
    for (int i = 0; i < str.length(); i++) {
      if (!Character.isDigit(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Recursively sorts a map and all nested maps alphabetically by key. Returns a new TreeMap with
   * sorted keys.
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> sortMapRecursively(Map<String, Object> map) {
    Map<String, Object> sorted = new TreeMap<>();
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Map) {
        // Recursively sort nested maps
        sorted.put(entry.getKey(), sortMapRecursively((Map<String, Object>) value));
      } else {
        sorted.put(entry.getKey(), value);
      }
    }
    return sorted;
  }

  private ConfigMetaDataCollector() {}
}
