/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static io.opentelemetry.instrumentation.docs.GradleParser.parseMuzzleBlock;

import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class InstrumentationAnalyzer {

  private static final Map<String, String> mappers =
      ImmutableMap.of("db_client_metrics", "DbClientMetrics.get()");

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
      String key =
          path.getGroup() + ":" + path.getNamespace() + ":" + path.getInstrumentationName();
      if (!entityMap.containsKey(key)) {
        entityMap.put(
            key,
            new InstrumentationEntity(
                path.getSrcPath().replace("/javaagent", "").replace("/library", ""),
                path.getInstrumentationName(),
                path.getNamespace(),
                path.getGroup(),
                new ArrayList<>()));
      }
      entityMap.get(key).getTypes().add(path.getType());
    }

    return new ArrayList<>(entityMap.values());
  }

  /**
   * Analyzes the given root directory to find all instrumentation paths and then analyze them. -
   * Extracts version information from each instrumentation's build.gradle file. - Identifies
   * different semantic conventions in use
   *
   * @param rootDirectory the root directory to search for instrumentation paths
   * @return a list of InstrumentationEntity objects with target versions
   */
  List<InstrumentationEntity> analyze(String rootDirectory) {
    List<InstrumentationPath> paths = FileSearch.getInstrumentationList(rootDirectory);
    List<InstrumentationEntity> entities = InstrumentationAnalyzer.convertToEntities(paths);

    for (InstrumentationEntity entity : entities) {

      List<String> files = FileSearch.findBuildGradleFiles(entity.getSrcPath());
      for (String file : files) {
        String fileContents = FileSearch.readFileToString(file);
        List<String> versions = parseMuzzleBlock(fileContents);
        entity.setTargetVersions(versions);

        Map<String, String> conventions = semanticConventions(entity.getSrcPath());
        entity.setSemanticConventions(conventions.keySet().stream().toList());
      }
    }
    return entities;
  }

  public static Map<String, String> semanticConventions(String path) {
    List<String> files = FileSearch.getJavaCodePaths(path);
    return FileSearch.findStringInFiles(files, mappers);
  }

  InstrumentationAnalyzer() {}
}
