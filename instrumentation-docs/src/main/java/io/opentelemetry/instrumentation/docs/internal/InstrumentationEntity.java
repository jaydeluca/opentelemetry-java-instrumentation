/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Map;
import java.util.Set;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class InstrumentationEntity {
  private final String srcPath;
  private final String instrumentationName;
  private final String namespace;
  private final String group;
  private Map<InstrumentationType, Set<String>> targetVersions;

  public void setTargetVersions(Map<InstrumentationType, Set<String>> targetVersions) {
    this.targetVersions = targetVersions;
  }

  public void setMetadata(InstrumentationMetaData metadata) {
    this.metadata = metadata;
  }

  private InstrumentationMetaData metadata;

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public InstrumentationEntity(Builder builder) {
    this.srcPath = builder.srcPath;
    this.instrumentationName = builder.instrumentationName;
    this.namespace = builder.namespace;
    this.group = builder.group;
    this.metadata = builder.metadata;
    this.targetVersions = builder.targetVersions;
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

  public InstrumentationMetaData getMetadata() {
    return metadata;
  }

  public Map<InstrumentationType, Set<String>> getTargetVersions() {
    return targetVersions;
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static class Builder {
    private String srcPath;
    private String instrumentationName;
    private String namespace;
    private String group;
    private InstrumentationMetaData metadata;
    private Map<InstrumentationType, Set<String>> targetVersions;

    @CanIgnoreReturnValue
    public Builder srcPath(String srcPath) {
      this.srcPath = srcPath;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder instrumentationName(String instrumentationName) {
      this.instrumentationName = instrumentationName;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder group(String group) {
      this.group = group;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder metadata(InstrumentationMetaData metadata) {
      this.metadata = metadata;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder targetVersions(Map<InstrumentationType, Set<String>> targetVersions) {
      this.targetVersions = targetVersions;
      return this;
    }

    public InstrumentationEntity build() {
      return new InstrumentationEntity(this);
    }
  }
}
