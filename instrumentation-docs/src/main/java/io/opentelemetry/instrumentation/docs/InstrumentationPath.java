/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

class InstrumentationPath {
  private final String srcPath;
  private final String instrumentationName;
  private final String namespace;
  private final String group;
  private final InstrumentationType type;

  public InstrumentationPath(
      String instrumentationName,
      String srcPath,
      String namespace,
      String group,
      InstrumentationType type) {
    this.instrumentationName = instrumentationName;
    this.srcPath = srcPath;
    this.namespace = namespace;
    this.group = group;
    this.type = type;
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

  public InstrumentationType getType() {
    return type;
  }
}
