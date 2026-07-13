/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

/**
 * A typed classification of a telemetry {@code when} condition string.
 *
 * <p>Historically the {@code when} value is a single free-form string copied verbatim from the
 * {@code metadataConfig} system property at collection time. That string overloads three unrelated
 * axes: the runtime environment (e.g. {@code Java17}), the semantic-convention stability mode (e.g.
 * {@code otel.semconv-stability.opt-in=database}), and arbitrary configuration-flag toggles (e.g.
 * {@code otel.instrumentation.foo.enabled=true}). This type parses that string into a structured
 * form so downstream tooling can reason about the axes explicitly rather than pattern-matching raw
 * strings in multiple places.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class TelemetryCondition {

  /**
   * The axis a {@link TelemetryCondition} belongs to.
   *
   * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
   * at any time.
   */
  public enum Kind {
    /** Emitted by default, with no additional configuration. */
    DEFAULT,
    /** Gated on the runtime environment (e.g. a minimum Java version). */
    RUNTIME,
    /** Gated on the semantic-convention stability opt-in. */
    SEMCONV,
    /** Gated on one or more configuration-flag toggles. */
    CONFIG
  }

  /** Matches an environment tag such as {@code Java17} or {@code Java21}. */
  private static final Pattern RUNTIME_PATTERN = Pattern.compile("Java(\\d+)");

  private static final String SEMCONV_OPT_IN = "otel.semconv-stability.opt-in";
  private static final String DEFAULT_RAW = "default";

  private final String raw;
  private final Kind kind;
  // Populated for RUNTIME conditions; null otherwise.
  @Nullable private final Integer javaMinVersion;
  // Populated for CONFIG and SEMCONV conditions; empty otherwise. Insertion order is preserved.
  private final Map<String, String> properties;

  private TelemetryCondition(
      String raw, Kind kind, @Nullable Integer javaMinVersion, Map<String, String> properties) {
    this.raw = raw;
    this.kind = kind;
    this.javaMinVersion = javaMinVersion;
    this.properties = Collections.unmodifiableMap(properties);
  }

  /**
   * Parses a normalized {@code when} string (as produced by {@code
   * TelemetryParser.normalizeWhenCondition}) into a typed condition.
   *
   * @param when the normalized when string; {@code null}, empty, or {@code "default"} all map to
   *     {@link Kind#DEFAULT}
   */
  public static TelemetryCondition parse(String when) {
    String raw = when == null ? "" : when.strip();
    if (raw.isEmpty() || raw.equals(DEFAULT_RAW)) {
      return new TelemetryCondition(DEFAULT_RAW, Kind.DEFAULT, null, new LinkedHashMap<>());
    }

    Matcher runtimeMatcher = RUNTIME_PATTERN.matcher(raw);
    if (runtimeMatcher.matches()) {
      return new TelemetryCondition(
          raw, Kind.RUNTIME, Integer.parseInt(runtimeMatcher.group(1)), new LinkedHashMap<>());
    }

    Map<String, String> properties = parseProperties(raw);
    // A semconv condition is one whose only property is the semconv stability opt-in. Everything
    // else that carries a key=value pair is treated as a generic config toggle.
    boolean semconvOnly =
        !properties.isEmpty() && properties.keySet().stream().allMatch(SEMCONV_OPT_IN::equals);
    return new TelemetryCondition(raw, semconvOnly ? Kind.SEMCONV : Kind.CONFIG, null, properties);
  }

  /**
   * Splits a raw condition into its {@code key=value} properties. Commas separate properties, but a
   * comma may also appear inside a value (notably the semconv opt-in accepts a comma-separated list
   * such as {@code otel.semconv-stability.opt-in=database,service.peer}). A segment without an
   * {@code =} is therefore treated as a continuation of the preceding property's value rather than
   * a new property.
   */
  private static Map<String, String> parseProperties(String raw) {
    Map<String, String> properties = new LinkedHashMap<>();
    String lastKey = null;
    for (String segment : raw.split(",", -1)) {
      int eq = segment.indexOf('=');
      if (eq >= 0) {
        String key = segment.substring(0, eq).strip();
        String value = segment.substring(eq + 1).strip();
        properties.put(key, value);
        lastKey = key;
      } else if (lastKey != null) {
        properties.put(lastKey, properties.get(lastKey) + "," + segment.strip());
      } else {
        // A bare token with no key=value form (unexpected in practice); record it keyed to itself
        // so information is not silently dropped.
        properties.put(segment.strip(), "");
        lastKey = segment.strip();
      }
    }
    return properties;
  }

  public String getRaw() {
    return raw;
  }

  public Kind getKind() {
    return kind;
  }

  /** The minimum Java version for a {@link Kind#RUNTIME} condition, or {@code null} otherwise. */
  @Nullable
  public Integer getJavaMinVersion() {
    return javaMinVersion;
  }

  /** The parsed {@code key=value} properties for {@link Kind#CONFIG}/{@link Kind#SEMCONV}. */
  public Map<String, String> getProperties() {
    return properties;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TelemetryCondition that)) {
      return false;
    }
    return raw.equals(that.raw) && kind == that.kind;
  }

  @Override
  public int hashCode() {
    return Objects.hash(raw, kind);
  }

  @Override
  public String toString() {
    return "TelemetryCondition{raw='" + raw + "', kind=" + kind + '}';
  }
}
