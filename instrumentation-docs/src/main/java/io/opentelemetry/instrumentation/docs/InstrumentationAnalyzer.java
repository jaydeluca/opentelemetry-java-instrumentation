/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static io.opentelemetry.instrumentation.docs.GradleParser.parseMuzzleBlock;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.FileReaderHelper;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class InstrumentationAnalyzer {

  private final FileManager fileSearch;
  private final FileReaderHelper fileReaderHelper;
  private final Map<String, ConfigurationProperty> experimentalConfigurations;

  InstrumentationAnalyzer(
      FileManager fileSearch,
      FileReaderHelper fileReaderHelper,
      Map<String, ConfigurationProperty> experimentalConfigurations) {
    this.fileSearch = fileSearch;
    this.fileReaderHelper = fileReaderHelper;
    this.experimentalConfigurations = experimentalConfigurations;
  }

  private static final Map<String, String> semConvMappers =
      ImmutableMap.of(
          "db_client_metrics", "DbClientMetrics.get()",
          "db_client_spans", "DbClientSpanNameExtractor",
          "network_attributes", "NetworkAttributesGetter",
          "rpc_attributes", "RpcAttributesGetter",
          "http_client_attributes", "HttpClientAttributesGetter",
          "http_server_attributes", "HttpServerAttributesGetter");

  private static final Map<String, String> spanTypeMappers =
      ImmutableMap.of(
          "CLIENT", "JavaagentHttpClientInstrumenters.create",
          "SERVER", "JavaagentHttpServerInstrumenters.create");

  /**
   * Converts a list of InstrumentationPath objects into a list of InstrumentationEntity objects.
   * Each InstrumentationEntity represents a unique combination of group, namespace, and
   * instrumentation name. The types of instrumentation (e.g., library, javaagent) are aggregated
   * into a list within each entity.
   *
   * @param paths the list of InstrumentationPath objects to be converted
   * @return a list of InstrumentationEntity objects with aggregated types
   */
  public static List<InstrumentationEntity> convertToEntities(List<InstrumentationPath> paths) {
    Map<String, InstrumentationEntity> entityMap = new HashMap<>();

    for (InstrumentationPath path : paths) {
      String key = path.group() + ":" + path.namespace() + ":" + path.instrumentationName();
      if (!entityMap.containsKey(key)) {
        entityMap.put(
            key,
            new InstrumentationEntity(
                path.srcPath().replace("/javaagent", "").replace("/library", ""),
                path.instrumentationName(),
                path.namespace(),
                path.group(),
                new ArrayList<>()));
      }
      entityMap.get(key).getTypes().add(path.type());
    }

    return new ArrayList<>(entityMap.values());
  }

  /**
   * Analyzes the given root directory to find all instrumentation paths and then analyze them. -
   * Extracts version information from each instrumentation's build.gradle file. - Identifies
   * different semantic conventions in use
   *
   * @return a list of InstrumentationEntity objects with target versions
   */
  List<InstrumentationEntity> analyze() {
    List<InstrumentationPath> paths = fileSearch.getInstrumentationPaths();
    List<InstrumentationEntity> entities = convertToEntities(paths);

    for (InstrumentationEntity entity : entities) {
      List<String> gradleFiles = fileSearch.findBuildGradleFiles(entity.getSrcPath());
      List<String> files = fileSearch.getJavaCodePaths(entity.getSrcPath());

      analyzeVersions(gradleFiles, entity);
      analyzeSemanticConventions(files, entity);
      analyzeConfigurations(files, entity);
      analyzeSpanTypes(files, entity);
    }
    return entities;
  }

  void analyzeVersions(List<String> files, InstrumentationEntity entity) {
    List<String> versions = new ArrayList<>();
    for (String file : files) {
      String fileContents = fileSearch.readFileToString(file);
      versions.addAll(parseMuzzleBlock(fileContents));
    }
    entity.setTargetVersions(versions);
  }

  void analyzeConfigurations(List<String> files, InstrumentationEntity entity) {
    List<ConfigurationProperty> configs = new ArrayList<>();
    for (String file : files) {
      String fileContents = fileSearch.readFileToString(file);
      configs.addAll(ConfigurationParser.parse(fileContents));

      for (String configKey : experimentalConfigurations.keySet()) {
        if (fileContents.contains("ExperimentalConfig.get()." + configKey)) {
          configs.add(experimentalConfigurations.get(configKey));
        }
      }
    }
    entity.setConfigurationProperties(configs);
  }

  void analyzeSemanticConventions(List<String> files, InstrumentationEntity entity) {
    Map<String, String> semanticConventions = findStringInFiles(files, semConvMappers);
    entity.setSemanticConventions(semanticConventions.keySet().stream().toList());
  }

  void analyzeSpanTypes(List<String> files, InstrumentationEntity entity) {
    Map<String, String> spanTypes = findStringInFiles(files, spanTypeMappers);
    entity.setSpanTypes(spanTypes.keySet().stream().toList());
  }

  public Map<String, String> findStringInFiles(
      List<String> fileList, Map<String, String> searchStrings) {
    Map<String, String> matchingFiles = new HashMap<>();
    for (String filePath : fileList) {
      if (filePath.endsWith(".java")) {
        try (BufferedReader reader = fileReaderHelper.getBufferedReader(filePath)) {
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
}
