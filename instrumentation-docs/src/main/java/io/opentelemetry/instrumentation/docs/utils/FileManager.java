/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileManager {
  private static final Logger logger = Logger.getLogger(FileManager.class.getName());
  private final String rootDir;

  public FileManager(String rootDir) {
    this.rootDir = rootDir;
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
      logger.severe("Error traversing directory: " + e.getMessage());
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
    String instrumentationSegment = "instrumentation/";

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
      logger.severe("Error traversing directory: " + e.getMessage());
      return new ArrayList<>();
    }
  }

  public String getMetaDataFile(String instrumentationDirectory) {
    String metadataFile = instrumentationDirectory + "/metadata.yaml";
    if (Files.exists(Paths.get(metadataFile))) {
      return readFileToString(metadataFile);
    }
    return null;
  }

  public String getScope(String instrumentationDirectory) {
    String scopeYaml = instrumentationDirectory + "/.telemetry/scope.yaml";
    if (Files.exists(Paths.get(scopeYaml))) {
      return readFileToString(scopeYaml);
    }
    return null;
  }

  public String getMetrics(String instrumentationDirectory) {
    StringBuilder metricsContent = new StringBuilder();
    Path telemetryDir = Paths.get(instrumentationDirectory, ".telemetry");

    if (Files.exists(telemetryDir) && Files.isDirectory(telemetryDir)) {
      try (Stream<Path> files = Files.list(telemetryDir)) {
        files
            .filter(path -> path.getFileName().toString().startsWith("metrics-"))
            .forEach(
                path -> {
                  String content = readFileToString(path.toString());
                  if (content != null) {
                    metricsContent.append(content).append("\n");
                  }
                });
      } catch (IOException e) {
        logger.severe("Error reading metrics files: " + e.getMessage());
      }
    }

    return metricsContent.toString();
  }

  public String getSpans(String instrumentationDirectory) {
    StringBuilder spansContent = new StringBuilder();
    Path telemetryDir = Paths.get(instrumentationDirectory, ".telemetry");

    if (Files.exists(telemetryDir) && Files.isDirectory(telemetryDir)) {
      try (Stream<Path> files = Files.list(telemetryDir)) {
        files
            .filter(path -> path.getFileName().toString().startsWith("spans-"))
            .forEach(
                path -> {
                  String content = readFileToString(path.toString());
                  if (content != null) {
                    spansContent.append(content).append("\n");
                  }
                });
      } catch (IOException e) {
        logger.severe("Error reading spans files: " + e.getMessage());
      }
    }

    return spansContent.toString();
  }

  public String readFileToString(String filePath) {
    try {
      return Files.readString(Paths.get(filePath));
    } catch (IOException e) {
      logger.severe("Error reading file: " + e.getMessage());
      return null;
    }
  }
}
