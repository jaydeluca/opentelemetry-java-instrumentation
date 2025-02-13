/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import java.util.Comparator;
import java.util.List;

public class MetaDataGeneratorApplication {

  private MetaDataGeneratorApplication() {}

  public static void main(String[] args) {
    String rootDirectory = "instrumentation/";
    List<InstrumentationEntity> entities = new InstrumentationAnalyzer().analyze(rootDirectory);

    printInstrumentationList(entities);
  }

  private static void printInstrumentationList(List<InstrumentationEntity> list) {
    list.stream()
        .sorted(Comparator.comparing(InstrumentationEntity::getGroup))
        .forEach(
            entity -> {
              System.out.println(entity.getGroup() + ":");
              System.out.println("  instrumentations:");
              System.out.println("    - name: " + entity.getInstrumentationName());
              System.out.println("      namespace: " + entity.getNamespace());
              System.out.println("      srcPath: " + entity.getSrcPath());
              System.out.println("      types:");
              for (InstrumentationType type : entity.getTypes()) {
                System.out.println("        - " + type);
              }
              if (entity.getSemanticConventions() == null
                  || entity.getSemanticConventions().isEmpty()) {
                System.out.println("      semantic_conventions: []");
              } else {
                System.out.println("      semantic_conventions:");
                for (String semconv : entity.getSemanticConventions()) {
                  System.out.println("        - " + semconv);
                }
              }

              if (entity.getTargetVersions() == null || entity.getTargetVersions().isEmpty()) {
                System.out.println("      target_versions: []");
              } else {
                System.out.println("      target_versions:");
                for (String version : entity.getTargetVersions()) {
                  System.out.println("        - " + version);
                }
              }
            });
  }
}
