/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static io.opentelemetry.instrumentation.docs.GradleParser.parseMuzzleBlock;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.InstrumentationPath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class InstrumentationAnalyzer {

  private final FileManager fileSearch;

  InstrumentationAnalyzer(FileManager fileSearch) {
    this.fileSearch = fileSearch;
  }

  private static final Map<String, String> mappers =
      ImmutableMap.of(
          "db_client_metrics", "DbClientMetrics.get()",
          "db_client_spans", "DbClientSpanNameExtractor",
          "network_attributes", "NetworkAttributesGetter",
          "rpc_attributes", "RpcAttributesGetter",
          "server_attributes", "HttpServerAttributesGetter");

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
    }
    return entities;
  }

  void analyzeVersions(List<String> files, InstrumentationEntity entity) {
    for (String file : files) {
      String fileContents = fileSearch.readFileToString(file);
      List<String> versions = parseMuzzleBlock(fileContents);
      entity.setTargetVersions(versions);
    }
  }

  void analyzeConfigurations(List<String> files, InstrumentationEntity entity) {
    for (String file : files) {
      String fileContents = fileSearch.readFileToString(file);
      List<ConfigurationProperty> configs = ConfigurationParser.parse(fileContents);
      entity.setConfigurationProperties(configs);
    }
  }

  void analyzeSemanticConventions(List<String> files, InstrumentationEntity entity) {
    Map<String, String> semanticConventions = fileSearch.findStringInFiles(files, mappers);
    entity.setSemanticConventions(semanticConventions.keySet().stream().toList());
  }
}
