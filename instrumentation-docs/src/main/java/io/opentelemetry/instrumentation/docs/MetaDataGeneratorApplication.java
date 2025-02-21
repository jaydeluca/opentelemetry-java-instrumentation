/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import io.opentelemetry.instrumentation.docs.utils.FileManager;
import io.opentelemetry.instrumentation.docs.utils.FileReaderHelper;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MetaDataGeneratorApplication {

  private MetaDataGeneratorApplication() {}

  public static void main(String[] args) {

    FileManager fileManager = new FileManager("instrumentation/");
    String experimentalConfigFile = fileManager.getExperimentalConfigFile();

    Map<String, ConfigurationProperty> configurationProperties =
        ExperimentalConfigParser.extractConfigMap(experimentalConfigFile);

    List<InstrumentationEntity> entities =
        new InstrumentationAnalyzer(fileManager, new FileReaderHelper(), configurationProperties)
            .analyze();

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

              if (entity.getSpanTypes() == null || entity.getSpanTypes().isEmpty()) {
                System.out.println("      span_types: []");
              } else {
                System.out.println("      span_types:");
                for (String spanType : entity.getSpanTypes()) {
                  System.out.println("        - " + spanType);
                }
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

              if (entity.getConfigurationProperties() == null
                  || entity.getConfigurationProperties().isEmpty()) {
                System.out.println("      configurations: []");
              } else {
                System.out.println("      configurations:");
                for (ConfigurationProperty property : entity.getConfigurationProperties()) {
                  System.out.println("        - name: " + property.name());
                  System.out.println("          type: " + property.type());
                  System.out.println("          default: " + property.defaultValue());
                }
              }
            });
  }
}
