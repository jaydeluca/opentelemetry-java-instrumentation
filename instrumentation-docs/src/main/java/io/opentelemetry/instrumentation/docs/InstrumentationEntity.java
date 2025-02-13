/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import java.util.List;

class InstrumentationEntity {
  private final String srcPath;
  private final String instrumentationName;
  private final String namespace;
  private final String group;
  private final List<InstrumentationType> types;
  private List<String> targetVersions;
  private List<String> semanticConventions;

  public InstrumentationEntity(
      List<String> targetVersions,
      List<InstrumentationType> types,
      String group,
      String namespace,
      String instrumentationName,
      String srcPath) {
    this.targetVersions = targetVersions;
    this.types = types;
    this.group = group;
    this.namespace = namespace;
    this.instrumentationName = instrumentationName;
    this.srcPath = srcPath;
  }

  public InstrumentationEntity(
      String srcPath,
      String instrumentationName,
      String namespace,
      String group,
      List<InstrumentationType> types) {
    this.srcPath = srcPath;
    this.instrumentationName = instrumentationName;
    this.namespace = namespace;
    this.group = group;
    this.types = types;
  }

  public String getSrcPath() {
    return srcPath;
  }

  public String getInstrumentationName() {
    return instrumentationName;
  }

  public String getNamespace() {
    return namespace;
  }

  public String getGroup() {
    return group;
  }

  public List<InstrumentationType> getTypes() {
    return types;
  }

  public List<String> getTargetVersions() {
    return targetVersions;
  }

  public void setTargetVersions(List<String> targetVersions) {
    this.targetVersions = targetVersions;
  }

  public List<String> getSemanticConventions() {
    return semanticConventions;
  }

  public void setSemanticConventions(List<String> semanticConventions) {
    this.semanticConventions = semanticConventions;
  }
}
