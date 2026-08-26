/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.micrometer.v1_5;

import static java.util.Collections.singleton;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.FINEST;
import static java.util.logging.Level.WARNING;

import io.micrometer.core.instrument.Meter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/** Which metrics the bridge emits. */
enum MetricsMode {
  /** Today's behavior: bridge everything. */
  ALL,
  /** Do not bridge a metric the agent already emits natively. */
  PREFER_INSTRUMENTATION;

  private static final Logger logger = Logger.getLogger(MetricsMode.class.getName());

  /**
   * The concept-map classes the shipped policy acts on. {@code direct} only, per the measurement in
   * the proposal's section 9: cross-tabulated against a careful human's hand-written views config,
   * {@code direct} predicted 145 of 145 drops, while the ten non-{@code direct} rows split five and
   * five. {@code partial} and {@code semantic} rows are in the data and are deliberately not acted
   * on -- they need a curator's per-row opt-in, which is what the deferred {@code disabled:}
   * override is for.
   */
  private static final Set<String> SHIPPED_CLASSES = singleton("direct");

  static MetricsMode parseConfigValue(@Nullable String value) {
    if (value == null) {
      return ALL;
    }
    switch (value.toLowerCase(Locale.ROOT)) {
      case "all":
        return ALL;
      case "prefer-instrumentation":
        return PREFER_INSTRUMENTATION;
      default:
        logger.log(WARNING, "Unrecognized metrics mode \"{0}\", falling back to \"all\".", value);
        return ALL;
    }
  }

  Predicate<Meter.Id> suppressionPredicate() {
    if (this == ALL) {
      return id -> false;
    }

    AgentEmissionResolver resolver = AgentEmissionResolver.create();

    // Rule 1, name-keyed. Fold the mapping against the effective configuration once at startup:
    // gates and instrumentation enablement are both fixed by then, so the answer for a mapped
    // Micrometer name cannot change later.
    Set<String> resolved = new TreeSet<>();
    Set<String> withheld = new TreeSet<>();
    for (String row : BridgeMappingData.ROWS) {
      if (!row.startsWith("M\t")) {
        continue;
      }
      String micrometerName = row.split("\t", -1)[1];
      AgentEmissionResolver.Mapping mapping = resolver.mappingFor(micrometerName);
      if (mapping == null || !SHIPPED_CLASSES.contains(mapping.conceptClass)) {
        continue;
      }
      if (resolver.emitsNatively(mapping.otelName)) {
        resolved.add(micrometerName);
      } else {
        withheld.add(micrometerName);
      }
    }
    Set<String> dropSet = Collections.unmodifiableSet(new HashSet<>(resolved));

    logger.log(
        FINE,
        "micrometer bridge prefer-instrumentation: {0} of {1} mapped metrics resolve to a metric the"
            + " agent emits under this configuration ({2} withheld because the counterpart is gated"
            + " off or its instrumentation is disabled); {3} agent metric names are also suppressed"
            + " by name. enablement read from: {4}.",
        new Object[] {
          resolved.size(),
          resolver.mappingCount(),
          withheld.size(),
          resolver.agentNameCount(),
          resolver.enablementSource()
        });
    // The per-name detail is what answers "why is jvm.memory.used missing?" -- see the ergonomic
    // debt paragraph in the proposal's section 5.
    logger.log(FINEST, "micrometer bridge suppressing: {0}", resolved);
    logger.log(FINEST, "micrometer bridge withholding: {0}", withheld);

    return id -> {
      String name = id.getName();
      // Rule 1: a mapped Micrometer name whose agent counterpart is live.
      if (dropSet.contains(name)) {
        return true;
      }
      // Rule 2, name-symmetric. The bridged name IS one of the agent's own metric names -- which
      // happens when Micrometer's or Spring's OpenTelemetry naming conventions are opted in, an
      // opt-in that is a user-authored bean and so invisible to every agent setting. A name-keyed
      // drop-set structurally cannot express this: the rename moves the key out of the M rows.
      //
      // Weaker evidence than rule 1, and deliberately so. Rule 1's key carries proof the library is
      // present -- `hikaricp.connections.active` cannot exist without HikariCP. A semconv name
      // carries none, so this rule leans entirely on the counterpart being enabled.
      return resolver.emitsNatively(name);
    };
  }
}
