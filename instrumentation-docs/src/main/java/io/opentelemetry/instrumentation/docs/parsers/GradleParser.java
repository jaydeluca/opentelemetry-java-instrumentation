/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import io.opentelemetry.instrumentation.docs.internal.DependencyInfo;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/** Handles parsing of Gradle build files to extract muzzle and dependency information. */
public class GradleParser {

  private static final Pattern variablePattern =
      Pattern.compile("val\\s+(\\w+)\\s*=\\s*\"([^\"]+)\"");

  private static final Pattern muzzlePassBlockPattern =
      Pattern.compile("pass\\s*\\{(.*?)}", Pattern.DOTALL);

  private static final Pattern libraryPattern =
      Pattern.compile("library\\(\"([^\"]+:[^\"]+):([^\"]+)\"\\)");

  private static final Pattern testLibraryPattern =
      Pattern.compile("testLibrary\\(\"([^\"]+:[^\"]+):([^\"]+)\"\\)");

  private static final Pattern compileOnlyPattern =
      Pattern.compile(
          "compileOnly\\(\"([^\"]+:[^\"]+)(?::[^\"]+)?\"\\)\\s*\\{\\s*version\\s*\\{.*?strictly\\(\"([^\"]+)\"\\).*?}\\s*",
          Pattern.DOTALL);

  private static final Pattern simpleCompileOnlyPattern =
      Pattern.compile("compileOnly\\(\"([^\"]+:[^\"]+):([^\"]+)\"\\)");

  private static final Pattern latestDepTestLibraryPattern =
      Pattern.compile("latestDepTestLibrary\\(\"([^\"]+:[^\"]+):([^\"]+)\"\\)");

  private static final Pattern coreJdkPattern = Pattern.compile("coreJdk\\(\\)");

  private static final Pattern ifBlockPattern =
      Pattern.compile("if\\s*\\([^)]*\\)\\s*\\{.*?}", Pattern.DOTALL);

  private static final Pattern testingBlockPattern =
      Pattern.compile("testing\\s*\\{.*?^}", Pattern.DOTALL | Pattern.MULTILINE);

  private static final Pattern otelJavaBlockPattern =
      Pattern.compile("otelJava\\s*\\{.*?}", Pattern.DOTALL);

  private static final Pattern minJavaVersionPattern =
      Pattern.compile("minJavaVersionSupported\\.set\\(JavaVersion\\.VERSION_(\\d+)\\)");

  /**
   * Parses gradle files for muzzle and dependency information
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @return A set of strings summarizing the group, module, and version ranges
   */
  public static DependencyInfo parseGradleFile(
      String gradleFileContents, InstrumentationType type) {
    DependencyInfo results;
    Map<String, String> variables = extractVariables(gradleFileContents);

    if (type.equals(InstrumentationType.JAVAAGENT)) {
      results = parseMuzzle(gradleFileContents, variables);
    } else {
      results = parseLibraryDependencies(gradleFileContents, variables);
    }

    return results;
  }

  /**
   * Parses the "muzzle" block from the given Gradle file content and extracts information about
   * each "pass { ... }" entry, returning a set of version summary strings.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @param variables Map of variable names to their values
   * @return A set of strings summarizing the group, module, and version ranges
   */
  private static DependencyInfo parseMuzzle(
      String gradleFileContents, Map<String, String> variables) {
    Set<String> results = new HashSet<>();
    Matcher passBlockMatcher = muzzlePassBlockPattern.matcher(gradleFileContents);

    Integer minJavaVersion = parseMinJavaVersion(gradleFileContents);

    while (passBlockMatcher.find()) {
      String passBlock = passBlockMatcher.group(1);

      if (coreJdkPattern.matcher(passBlock).find()) {
        if (minJavaVersion != null) {
          results.add("Java " + minJavaVersion + "+");
        } else {
          results.add("Java 8+");
        }
      }

      String group = extractValue(passBlock, "group\\.set\\(\"([^\"]+)\"\\)");
      String module = extractValue(passBlock, "module\\.set\\(\"([^\"]+)\"\\)");
      String versionRange = extractValue(passBlock, "versions\\.set\\(\"([^\"]+)\"\\)");

      if (group != null && module != null && versionRange != null) {
        String summary = group + ":" + module + ":" + interpolate(versionRange, variables);
        results.add(summary);
      }
    }
    return new DependencyInfo(results, minJavaVersion);
  }

  /**
   * Parses the "dependencies" block from the given Gradle file content and extracts information
   * about what library versions are supported. Uses a priority-based approach:
   *
   * <p>Priority 1: library() declarations - use as-is Priority 2: compileOnly() declarations - use
   * as primary source Priority 3: testLibrary() with matching artifactId - use for min version
   * Priority 4: Filtered testLibrary() - fallback only
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @param variables Map of variable names to their values
   * @return A set of strings summarizing the group, module, and versions
   */
  private static DependencyInfo parseLibraryDependencies(
      String gradleFileContents, Map<String, String> variables) {
    Map<String, String> versions = new HashMap<>();
    boolean hasLibraryDeclarations = false;

    // Priority 1: Extract library() declarations
    Matcher libraryMatcher = libraryPattern.matcher(gradleFileContents);
    while (libraryMatcher.find()) {
      String groupAndArtifact = libraryMatcher.group(1);
      String version = libraryMatcher.group(2);
      versions.put(groupAndArtifact, version);
      hasLibraryDeclarations = true;
    }

    // Priority 2: Extract compileOnly() declarations (only if no library() found)
    if (!hasLibraryDeclarations) {
      // Build list of excluded ranges (testing blocks)
      List<int[]> excludedRanges = buildExcludedRanges(gradleFileContents);

      // Extract compileOnly() declarations with strictly() version
      Matcher compileOnlyMatcher = compileOnlyPattern.matcher(gradleFileContents);
      while (compileOnlyMatcher.find()) {
        if (isInExcludedRange(compileOnlyMatcher.start(), excludedRanges)) {
          continue; // Skip compileOnly inside testing blocks
        }
        String groupAndArtifact = compileOnlyMatcher.group(1);
        String version = compileOnlyMatcher.group(2);
        versions.put(groupAndArtifact, version);
      }

      // Extract simple compileOnly() declarations
      Matcher simpleCompileOnlyMatcher = simpleCompileOnlyPattern.matcher(gradleFileContents);
      while (simpleCompileOnlyMatcher.find()) {
        if (isInExcludedRange(simpleCompileOnlyMatcher.start(), excludedRanges)) {
          continue; // Skip compileOnly inside testing blocks
        }
        String groupAndArtifact = simpleCompileOnlyMatcher.group(1);
        String version = simpleCompileOnlyMatcher.group(2);
        // Only add if not already present from strictly() pattern
        if (!versions.containsKey(groupAndArtifact)) {
          versions.put(groupAndArtifact, version);
        }
      }

      // Priority 3: For compileOnly entries, look for matching testLibrary to determine min
      // version
      if (!versions.isEmpty()) {
        enrichWithTestLibraryMinVersions(gradleFileContents, versions);
      }
    }

    // Priority 4: Use testLibrary as fallback only when no library() or compileOnly() found
    if (versions.isEmpty()) {
      extractFilteredTestLibraries(gradleFileContents, versions);
    }

    // Extract latestDepTestLibrary for upper bounds
    Matcher latestDepTestLibraryMatcher = latestDepTestLibraryPattern.matcher(gradleFileContents);
    while (latestDepTestLibraryMatcher.find()) {
      String groupAndArtifact = latestDepTestLibraryMatcher.group(1);
      String version = latestDepTestLibraryMatcher.group(2);
      if (versions.containsKey(groupAndArtifact)) {
        versions.put(groupAndArtifact, versions.get(groupAndArtifact) + "," + version);
      }
    }

    Set<String> results = new HashSet<>();
    for (Map.Entry<String, String> entry : versions.entrySet()) {
      if (entry.getValue().contains(",")) {
        results.add(interpolate(entry.getKey() + ":[" + entry.getValue() + ")", variables));
      } else {
        results.add(interpolate(entry.getKey() + ":" + entry.getValue(), variables));
      }
    }

    Integer minJavaVersion = parseMinJavaVersion(gradleFileContents);

    return new DependencyInfo(results, minJavaVersion);
  }

  /**
   * For each compileOnly entry, look for matching testLibrary entries with the same
   * groupId:artifactId to determine the minimum supported version. This handles the pattern where
   * we compile against a newer version but support older versions.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @param versions Map of artifact to version (will be updated with min versions)
   */
  private static void enrichWithTestLibraryMinVersions(
      String gradleFileContents, Map<String, String> versions) {
    Matcher testLibraryMatcher = testLibraryPattern.matcher(gradleFileContents);

    while (testLibraryMatcher.find()) {
      String groupAndArtifact = testLibraryMatcher.group(1);
      String testVersion = testLibraryMatcher.group(2);

      // Check if we have a compileOnly entry for the same artifact
      if (versions.containsKey(groupAndArtifact)) {
        String currentVersion = versions.get(groupAndArtifact);
        // If the test version is different and looks like a minimum version, use it
        if (!currentVersion.equals(testVersion) && !testVersion.contains("+")) {
          versions.put(groupAndArtifact, testVersion + "," + currentVersion);
        }
      }
    }
  }

  /**
   * Extracts testLibrary entries with smart filtering to exclude test artifacts and prefer relevant
   * libraries. Only used as fallback when no library() or compileOnly() found.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @param versions Map of artifact to version (will be populated)
   */
  private static void extractFilteredTestLibraries(
      String gradleFileContents, Map<String, String> versions) {
    Matcher testLibraryMatcher = testLibraryPattern.matcher(gradleFileContents);

    while (testLibraryMatcher.find()) {
      String groupAndArtifact = testLibraryMatcher.group(1);
      String version = testLibraryMatcher.group(2);

      if (!isTestArtifact(groupAndArtifact) && !versions.containsKey(groupAndArtifact)) {
        versions.put(groupAndArtifact, version);
      }
    }
  }

  /**
   * Determines if an artifact is a test-related artifact that should be filtered out.
   *
   * @param groupAndArtifact The group:artifact string
   * @return true if this is a test artifact
   */
  private static boolean isTestArtifact(String groupAndArtifact) {
    String lowerCase = groupAndArtifact.toLowerCase(Locale.ROOT);
    return lowerCase.endsWith("-test")
        || lowerCase.endsWith("-testing")
        || lowerCase.contains("starter-test")
        || lowerCase.contains(":junit")
        || lowerCase.contains(":mockito")
        || lowerCase.contains("test-support");
  }

  /**
   * Builds a list of excluded ranges (if blocks, testing blocks) that should be ignored during
   * parsing.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @return List of [start, end] position ranges to exclude
   */
  private static List<int[]> buildExcludedRanges(String gradleFileContents) {
    List<int[]> excludedRanges = new ArrayList<>();

    // Exclude if blocks
    Matcher ifBlockMatcher = ifBlockPattern.matcher(gradleFileContents);
    while (ifBlockMatcher.find()) {
      excludedRanges.add(new int[] {ifBlockMatcher.start(), ifBlockMatcher.end()});
    }

    // Exclude testing blocks
    Matcher testingBlockMatcher = testingBlockPattern.matcher(gradleFileContents);
    while (testingBlockMatcher.find()) {
      excludedRanges.add(new int[] {testingBlockMatcher.start(), testingBlockMatcher.end()});
    }

    return excludedRanges;
  }

  @Nullable
  public static Integer parseMinJavaVersion(String gradleFileContents) {
    List<int[]> excludedRanges = buildExcludedRanges(gradleFileContents);

    Matcher otelJavaMatcher = otelJavaBlockPattern.matcher(gradleFileContents);
    while (otelJavaMatcher.find()) {
      int blockStart = otelJavaMatcher.start();

      if (isInExcludedRange(blockStart, excludedRanges)) {
        continue; // Skip blocks inside 'if' statements
      }

      String otelJavaBlock = otelJavaMatcher.group();
      Matcher versionMatcher = minJavaVersionPattern.matcher(otelJavaBlock);
      if (versionMatcher.find()) {
        return Integer.parseInt(versionMatcher.group(1));
      }
    }

    return null;
  }

  private static boolean isInExcludedRange(int position, List<int[]> ranges) {
    for (int[] range : ranges) {
      if (position >= range[0] && position <= range[1]) {
        return true;
      }
    }
    return false;
  }

  /**
   * Extracts variables from the given Gradle file content.
   *
   * @param gradleFileContents Contents of a Gradle build file as a String
   * @return A map of variable names to their values
   */
  private static Map<String, String> extractVariables(String gradleFileContents) {
    Map<String, String> variables = new HashMap<>();
    Matcher variableMatcher = variablePattern.matcher(gradleFileContents);

    while (variableMatcher.find()) {
      variables.put(variableMatcher.group(1), variableMatcher.group(2));
    }

    return variables;
  }

  /**
   * Interpolates variables in the given text using the provided variable map.
   *
   * @param text Text to interpolate
   * @param variables Map of variable names to their values
   * @return Interpolated text
   */
  private static String interpolate(String text, Map<String, String> variables) {
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      text = text.replace("$" + entry.getKey(), entry.getValue());
    }
    return text;
  }

  /**
   * Utility method to extract the first captured group from matching the given regex.
   *
   * @param text Text to search
   * @param regex Regex with a capturing group
   * @return The first captured group, or null if not found
   */
  @Nullable
  private static String extractValue(String text, String regex) {
    Pattern pattern = Pattern.compile(regex);
    Matcher matcher = pattern.matcher(text);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  public static Map<InstrumentationType, Set<String>> extractVersions(
      List<String> gradleFiles, InstrumentationModule module) {
    Map<InstrumentationType, Set<String>> versionsByType = new HashMap<>();
    gradleFiles.forEach(file -> processGradleFile(file, versionsByType, module));
    return versionsByType;
  }

  private static void processGradleFile(
      String filePath,
      Map<InstrumentationType, Set<String>> versionsByType,
      InstrumentationModule module) {
    String fileContents = FileManager.readFileToString(filePath);
    if (fileContents == null) {
      return;
    }

    Optional<InstrumentationType> type = determineInstrumentationType(filePath);
    if (type.isEmpty()) {
      return;
    }

    DependencyInfo dependencyInfo = parseGradleFile(fileContents, type.get());
    if (dependencyInfo == null) {
      return;
    }

    addVersions(versionsByType, type.get(), dependencyInfo.versions());
    setMinJavaVersionIfPresent(module, dependencyInfo);
  }

  private static Optional<InstrumentationType> determineInstrumentationType(String filePath) {
    if (filePath.contains("/javaagent/")) {
      return Optional.of(InstrumentationType.JAVAAGENT);
    } else if (filePath.contains("/library/")) {
      return Optional.of(InstrumentationType.LIBRARY);
    }
    return Optional.empty();
  }

  private static void addVersions(
      Map<InstrumentationType, Set<String>> versionsByType,
      InstrumentationType type,
      Set<String> versions) {
    versionsByType.computeIfAbsent(type, k -> new HashSet<>()).addAll(versions);
  }

  private static void setMinJavaVersionIfPresent(
      InstrumentationModule module, DependencyInfo dependencyInfo) {
    if (dependencyInfo.minJavaVersionSupported() != null) {
      module.setMinJavaVersion(dependencyInfo.minJavaVersionSupported());
    }
  }

  private GradleParser() {}
}
