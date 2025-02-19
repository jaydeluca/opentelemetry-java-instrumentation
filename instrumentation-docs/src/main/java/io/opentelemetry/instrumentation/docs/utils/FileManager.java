/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.opentelemetry.instrumentation.docs.InstrumentationType;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileManager {

  private final String rootDir;

  public FileManager(String rootDir) {
    this.rootDir = rootDir;
  }

  public List<String> getJavaCodePaths(String instrumentationDirectory) {
    Path rootPath = Paths.get(instrumentationDirectory);

    try (Stream<Path> walk = Files.walk(rootPath)) {
      return walk.filter(Files::isRegularFile)
          .map(Path::toString)
          .filter(
              path ->
                  !path.contains("/build/") && !path.contains("/test/") && path.endsWith(".java"))
          .collect(Collectors.toList());
    } catch (IOException e) {
      System.out.println("Error traversing directory: " + e.getMessage());
      return List.of();
    }
  }

  public Map<String, String> findStringInFiles(
      List<String> fileList, Map<String, String> searchStrings) {
    Map<String, String> matchingFiles = new HashMap<>();
    for (String filePath : fileList) {
      if (filePath.endsWith(".java")) {
        try (BufferedReader reader = Files.newBufferedReader(Paths.get(filePath), UTF_8)) {
          String line;
          while ((line = reader.readLine()) != null) {
            for (Map.Entry<String, String> entry : searchStrings.entrySet()) {
              if (line.contains(entry.getValue())) {
                matchingFiles.put(entry.getKey(), filePath);
                break;
              }
            }
          }
        } catch (IOException e) {
          // File may have been removed or is inaccessible; ignore or log as needed
        }
      }
    }
    return matchingFiles;
  }

  public List<InstrumentationPath> getInstrumentationPaths() {
    Path rootPath = Paths.get(rootDir);

    try (Stream<Path> walk = Files.walk(rootPath)) {
      return walk.filter(Files::isDirectory)
          .filter(dir -> !dir.toString().contains("/build"))
          .filter(dir -> isValidInstrumentationPath(dir.toString()))
          .map(dir -> parseInstrumentationPath(dir.toString()))
          .collect(Collectors.toList());
    } catch (IOException e) {
      System.out.println("Error traversing directory: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  private static InstrumentationPath parseInstrumentationPath(String filePath) {
    if (filePath == null || filePath.isEmpty()) {
      return null;
    }

    String instrumentationSegment = "/instrumentation/";
    int startIndex = filePath.indexOf(instrumentationSegment) + instrumentationSegment.length();
    String[] parts = filePath.substring(startIndex).split("/");

    if (parts.length < 2) {
      return null;
    }

    InstrumentationType instrumentationType =
        InstrumentationType.fromString(parts[parts.length - 1]);
    String name = parts[parts.length - 2];
    String namespace = name.contains("-") ? name.split("-")[0] : name;

    return new InstrumentationPath(name, filePath, namespace, namespace, instrumentationType);
  }

  public static boolean isValidInstrumentationPath(String filePath) {
    if (filePath == null || filePath.isEmpty()) {
      return false;
    }
    String instrumentationSegment = "/instrumentation/";

    if (!filePath.contains(instrumentationSegment)) {
      return false;
    }

    int javaagentCount = filePath.split("/javaagent", -1).length - 1;
    if (javaagentCount > 1) {
      return false;
    }

    if (filePath.contains("/test/")
        || filePath.contains("/testing")
        || filePath.contains("-common/")
        || filePath.contains("bootstrap/src")) {
      return false;
    }

    return filePath.endsWith("javaagent") || filePath.endsWith("library");
  }

  public List<String> findBuildGradleFiles(String instrumentationDirectory) {
    Path rootPath = Paths.get(instrumentationDirectory);

    try (Stream<Path> walk = Files.walk(rootPath)) {
      return walk.filter(Files::isRegularFile)
          .filter(
              path ->
                  path.getFileName().toString().equals("build.gradle.kts")
                      && !path.toString().contains("/testing/"))
          .map(Path::toString)
          .collect(Collectors.toList());
    } catch (IOException e) {
      System.out.println("Error traversing directory: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  public String readFileToString(String filePath) {
    try {
      return Files.readString(Paths.get(filePath));
    } catch (IOException e) {
      System.out.println("Error reading file: " + e.getMessage());
      return null;
    }
  }
}
