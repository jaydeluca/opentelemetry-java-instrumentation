/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableCodeSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableRpcSemconv;
import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableServicePeerSemconv;
import static java.util.Arrays.asList;
import static java.util.logging.Level.FINEST;

import io.opentelemetry.instrumentation.api.internal.SemconvStability;
import io.opentelemetry.instrumentation.api.internal.SystemProperty;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Answers "does the agent already emit this metric, here, now?" for one of the agent's own metric
 * names.
 *
 * <p>This is the part of {@code prefer-instrumentation} that cannot be a shipped list. A row of
 * {@link BridgeMappingData} says the agent emits {@code db.client.connection.max} from any of eight
 * connection-pool instrumentations, when {@code otel.semconv-stability.opt-in=database} is set.
 * Whether that is true in a given JVM depends on the user's configuration, so it is folded here at
 * startup rather than baked into the data.
 *
 * <p>Two inputs, resolved independently and ANDed per row, ORed across rows:
 *
 * <ul>
 *   <li>the <b>gate</b> -- the registry's {@code when} condition, evaluated against {@link
 *       SemconvStability} and the agent's configuration properties.
 *   <li>the <b>instrumentation</b> -- whether any instrumentation declaring the metric is enabled.
 * </ul>
 *
 * <p>Both fail closed: an unrecognized gate and an unknown instrumentation both resolve to "the
 * agent does not emit this", which keeps the Micrometer copy. Losing a signal is worse than
 * duplicating one.
 */
final class AgentEmissionResolver {

  private static final Logger logger = Logger.getLogger(AgentEmissionResolver.class.getName());

  /**
   * Instrumentation names whose module overrides {@code defaultEnabled()}, among the 124 names the
   * mapping data references. Measured against the agent tree at {@code 57c630b802}: only these four
   * differ from the common default, and three of them flip under v3-preview.
   *
   * <p>{@code kafka-clients-0.11} is the load-bearing one. It carries the largest duplicate cluster
   * in the dataset, and its metrics module is off under v3-preview ({@code
   * KafkaMetricsInstrumentationModule.java:41-42}). Getting it wrong drops 110 Kafka metrics with
   * nothing replacing them -- measured, before the mapping data was corrected to exclude
   * library-only emitters.
   *
   * <p>This table is the concrete cost of not being able to read the agent's own decision. {@code
   * AgentDistributionConfig} computes enablement for every module with the real {@code
   * defaultEnabled} in hand, but it is not on the bootstrap class loader, so helper code cannot
   * always reach it -- see {@code Enablement}. Publishing the resolved set from {@code
   * InstrumentationModuleInstaller} would delete this table, which is why the proposal asks for it.
   */
  private static final Set<String> V3_PREVIEW_DISABLED =
      Collections.unmodifiableSet(
          new HashSet<>(asList("kafka-clients-0.11", "jedis-1.4", "lettuce-5.1")));

  private static final Set<String> DEFAULT_DISABLED =
      Collections.unmodifiableSet(
          new HashSet<>(asList("apache-commons-pool-2.0", "micrometer-1.5")));

  private final Map<String, List<Alternative>> emissions;
  private final Map<String, Mapping> mappings;
  private final Enablement enablement;

  private AgentEmissionResolver(
      Map<String, List<Alternative>> emissions,
      Map<String, Mapping> mappings,
      Enablement enablement) {
    this.emissions = emissions;
    this.mappings = mappings;
    this.enablement = enablement;
  }

  static AgentEmissionResolver create() {
    Map<String, List<Alternative>> emissions = new HashMap<>();
    Map<String, Mapping> mappings = new HashMap<>();
    for (String row : BridgeMappingData.ROWS) {
      String[] parts = row.split("\t", -1);
      if (parts.length != 4) {
        continue;
      }
      if ("M".equals(parts[0])) {
        mappings.put(parts[1], new Mapping(parts[2], parts[3]));
      } else if ("A".equals(parts[0])) {
        emissions
            .computeIfAbsent(parts[1], unused -> new ArrayList<>())
            .add(new Alternative(parts[2], asList(parts[3].split(","))));
      }
    }
    return new AgentEmissionResolver(emissions, mappings, new SystemPropertyEnablement());
  }

  /** The agent metric this Micrometer metric duplicates, or null if none is mapped. */
  Mapping mappingFor(String micrometerName) {
    return mappings.get(micrometerName);
  }

  /** Whether the agent emits one of its own metrics under the effective configuration. */
  boolean emitsNatively(String otelName) {
    List<Alternative> alternatives = emissions.get(otelName);
    if (alternatives == null) {
      return false;
    }
    for (Alternative alternative : alternatives) {
      if (gateSatisfied(alternative.when)
          && anyInstrumentationEnabled(alternative.instrumentations)) {
        return true;
      }
    }
    return false;
  }

  int mappingCount() {
    return mappings.size();
  }

  int agentNameCount() {
    return emissions.size();
  }

  // --- gates -------------------------------------------------------------------------------

  private static boolean gateSatisfied(String when) {
    if ("default".equals(when)) {
      return true;
    }
    if (when.startsWith("Java")) {
      try {
        return javaMajorVersion() >= Integer.parseInt(when.substring("Java".length()));
      } catch (NumberFormatException e) {
        return false;
      }
    }
    // A condition is a conjunction of `key=value[,value...]` terms. Commas separate both terms and
    // multi-valued right-hand sides (`otel.semconv-stability.opt-in=database,service.peer`), so a
    // comma-delimited token without an `=` continues the previous term's value list.
    String key = null;
    List<String> values = new ArrayList<>();
    for (String token : when.split(",")) {
      int eq = token.indexOf('=');
      if (eq < 0) {
        if (key == null) {
          return false;
        }
        values.add(token);
        continue;
      }
      if (key != null && !propertySatisfied(key, values)) {
        return false;
      }
      key = token.substring(0, eq);
      values = new ArrayList<>();
      values.add(token.substring(eq + 1));
    }
    return key != null && propertySatisfied(key, values);
  }

  private static boolean propertySatisfied(String key, List<String> values) {
    switch (key) {
      case "otel.semconv-stability.opt-in":
        for (String value : values) {
          if (!optInSatisfied(value)) {
            return false;
          }
        }
        return true;
      case "otel.semconv-stability.preview":
        for (String value : values) {
          // `messaging/dup` is the registry's marker for a duplicated declaration, same gate
          if (!value.startsWith("messaging") || !emitStableMessagingSemconv()) {
            return false;
          }
        }
        return true;
      case "otel.instrumentation.common.v3-preview":
        return SemconvStability.v3Preview();
      default:
        // an experimental flag; compare against the configured value
        String configured = SystemProperty.getString(key);
        return configured != null && configured.equals(String.join(",", values));
    }
  }

  private static boolean optInSatisfied(String value) {
    switch (value) {
      case "database":
        return emitStableDatabaseSemconv();
      case "rpc":
        return emitStableRpcSemconv();
      case "service.peer":
        return emitStableServicePeerSemconv();
      case "code":
        return emitStableCodeSemconv();
      default:
        return false;
    }
  }

  private static int javaMajorVersion() {
    String version = System.getProperty("java.specification.version", "8");
    int dot = version.indexOf('.');
    if (dot >= 0) {
      // "1.8" and friends
      version = version.substring(dot + 1);
    }
    try {
      return Integer.parseInt(version);
    } catch (NumberFormatException e) {
      return 8;
    }
  }

  // --- instrumentation enablement ------------------------------------------------------------

  private boolean anyInstrumentationEnabled(List<String> names) {
    for (String name : names) {
      if (enablement.isEnabled(name, defaultEnabledFor(name))) {
        return true;
      }
    }
    return false;
  }

  /**
   * The module's own {@code defaultEnabled()}, which is not centrally queryable -- it is a method
   * on each {@code InstrumentationModule}, and some compute it dynamically. See {@link
   * #V3_PREVIEW_DISABLED}.
   */
  private static boolean defaultEnabledFor(String name) {
    if (DEFAULT_DISABLED.contains(name)) {
      return false;
    }
    if (V3_PREVIEW_DISABLED.contains(name)) {
      return !SemconvStability.v3Preview();
    }
    return true;
  }

  String enablementSource() {
    return enablement.describe();
  }

  /**
   * Where the answer to "is this instrumentation enabled?" comes from.
   *
   * <p>The agent already computed it, correctly, in {@code AgentDistributionConfig}: the user's
   * enabled/disabled lists resolved against each module's own {@code defaultEnabled}, under both
   * the property and the declarative surface. The bridge cannot call it.
   *
   * <p>Not a matter of visibility at run time -- muzzle refuses first. A static reference to {@code
   * AgentDistributionConfig} from any of this module's helper classes makes the reference check
   * fail against the application class loader, and the agent then skips the <em>whole micrometer
   * module</em>:
   *
   * <pre>{@code
   * WARN MuzzleMatcher - -- AgentEmissionResolver$DistributionConfigEnablement:283
   *      Missing class io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig
   * WARN MuzzleMatcher - Instrumentation skipped, mismatched references were found: micrometer
   * }</pre>
   *
   * <p>Measured, not predicted -- an earlier revision of this class did exactly that and silently
   * turned the bridge off. So the only sources available here are the ones already on the bootstrap
   * class loader, and {@link SystemPropertyEnablement} is what that leaves. It is not equivalent,
   * which is the concrete reason the proposal asks upstream to publish the resolved set.
   */
  private interface Enablement {
    boolean isEnabled(String name, boolean defaultEnabled);

    String describe();
  }

  /**
   * Reads system properties and environment variables only. Under a declarative config file the
   * agent stops reading {@code otel.instrumentation.*.enabled} and takes enablement from {@code
   * distribution.javaagent.instrumentation.enabled} instead, so this source can disagree with the
   * agent -- in the dangerous direction as well as the safe one.
   */
  private static final class SystemPropertyEnablement implements Enablement {
    @Override
    public boolean isEnabled(String name, boolean defaultEnabled) {
      Boolean explicit = SystemProperty.getBoolean("otel.instrumentation." + name + ".enabled");
      if (explicit != null) {
        return explicit;
      }
      return defaultEnabled
          && SystemProperty.getBoolean("otel.instrumentation.common.default-enabled", true);
    }

    @Override
    public String describe() {
      return "system properties"
          + (agentDistributionConfigLoadable() ? "" : " (AgentDistributionConfig not loadable)");
    }
  }

  /**
   * Probe only, deliberately reflective so that muzzle does not see the reference. Records whether
   * the class is even loadable from here, which differs between the injecting and isolating helper
   * strategies -- and the agent switches between them under v3-preview.
   */
  private static boolean agentDistributionConfigLoadable() {
    try {
      Class.forName(
          "io.opentelemetry.javaagent.extension.instrumentation.internal.AgentDistributionConfig",
          false,
          AgentEmissionResolver.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException | LinkageError e) {
      logger.log(FINEST, "AgentDistributionConfig not loadable from the bridge", e);
      return false;
    }
  }

  static final class Mapping {
    final String otelName;
    final String conceptClass;

    Mapping(String otelName, String conceptClass) {
      this.otelName = otelName;
      this.conceptClass = conceptClass;
    }
  }

  private static final class Alternative {
    final String when;
    final List<String> instrumentations;

    Alternative(String when, List<String> instrumentations) {
      this.when = when;
      this.instrumentations = instrumentations;
    }
  }
}
