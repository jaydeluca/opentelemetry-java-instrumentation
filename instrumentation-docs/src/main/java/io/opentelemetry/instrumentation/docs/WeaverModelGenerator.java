/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.instrumentation.docs.internal.ConfigurationOption;
import io.opentelemetry.instrumentation.docs.internal.EmittedMetrics;
import io.opentelemetry.instrumentation.docs.internal.EmittedSpans;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import java.io.BufferedWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Generates OpenTelemetry Weaver semantic-convention registry files for each instrumentation
 * module, under {@code <module>/model/}: a {@code manifest.yaml} plus a {@code
 * <instrumentation-name>.yaml} containing standard {@code groups:} entries. This mirrors the
 * hand-maintained schema and directory convention already used under {@code
 * instrumentation/jmx-metrics/model/}.
 */
public class WeaverModelGenerator {

  private static final Logger logger = Logger.getLogger(WeaverModelGenerator.class.getName());

  // Tracks the "otel" dependency's version. Keep in sync with dependencyManagement's
  // semConvVersion; there is no automated cross-module link to that Gradle-only value.
  private static final String SEMCONV_VERSION = "1.44.0";

  private static final Set<String> KNOWN_SEMCONV_ATTRIBUTES = loadKnownSemconvAttributes();

  private static final Path baseRepoPath = getPath();

  private static Path getPath() {
    String base = System.getProperty("basePath");
    if (base == null || base.isBlank()) {
      return Paths.get(".");
    }
    return Paths.get(base);
  }

  public static void generateWeaverModels(List<InstrumentationModule> modules) throws IOException {
    for (InstrumentationModule module : modules) {
      if (isEmpty(module)) {
        continue;
      }

      Path moduleSrc = baseRepoPath.resolve(module.getSrcPath());
      Path modelDir = moduleSrc.resolve("model");
      Files.createDirectories(modelDir);

      Path manifestPath = modelDir.resolve("manifest.yaml");
      try (BufferedWriter writer = Files.newBufferedWriter(manifestPath, UTF_8)) {
        generateManifest(module, writer);
      }

      Path signalsPath = modelDir.resolve(module.getInstrumentationName() + ".yaml");
      try (BufferedWriter writer = Files.newBufferedWriter(signalsPath, UTF_8)) {
        generateSignals(module, writer);
      }
    }
  }

  private static boolean isEmpty(InstrumentationModule module) {
    return countEntries(module.getMetrics()) == 0 && countEntries(module.getSpans()) == 0;
  }

  private static int countEntries(@Nullable Map<String, ? extends List<?>> byWhen) {
    if (byWhen == null) {
      return 0;
    }
    int count = 0;
    for (List<?> list : byWhen.values()) {
      if (list != null) {
        count += list.size();
      }
    }
    return count;
  }

  public static void generateManifest(InstrumentationModule module, BufferedWriter writer)
      throws IOException {
    writer.write("# This file is generated and should not be manually edited.\n");
    writer.write("name: " + module.getInstrumentationName() + "\n");
    writer.write(
        "description: This registry contains the semantic conventions for the "
            + module.getResolvedName()
            + " instrumentation\n");
    writer.write(
        "schema_url: https://github.com/open-telemetry/opentelemetry-java-instrumentation/"
            + module.getSrcPath()
            + "/\n");
    writer.write("dependencies:\n");
    writer.write("  - name: otel\n");
    writer.write(
        "    registry_path: https://github.com/open-telemetry/semantic-conventions@v"
            + SEMCONV_VERSION
            + "[model]\n");
    writer.write("    schema_url: https://opentelemetry.io/schemas/" + SEMCONV_VERSION + "\n");
  }

  public static void generateSignals(InstrumentationModule module, BufferedWriter writer)
      throws IOException {
    List<ConfigurationOption> configs = module.getMetadata().getConfigurations();
    List<ResolvedMetric> metrics = resolveMetrics(module, configs);
    List<ResolvedSpan> spans = resolveSpans(module, configs);

    Map<String, TelemetryAttribute> localAttributes = new TreeMap<>();
    for (ResolvedMetric metric : metrics) {
      collectLocalAttributes(metric.attributes, localAttributes);
    }
    for (ResolvedSpan span : spans) {
      collectLocalAttributes(span.attributes, localAttributes);
    }

    writer.write("# This file is generated and should not be manually edited.\n");
    writer.write("groups:\n");

    if (!localAttributes.isEmpty()) {
      writeLocalAttributeGroup(module, writer, localAttributes.values());
    }

    for (ResolvedMetric metric : metrics) {
      writeMetricGroup(metric, writer);
    }

    for (ResolvedSpan span : spans) {
      writeSpanGroup(module, span, writer);
    }
  }

  private static void collectLocalAttributes(
      List<TelemetryAttribute> attributes, Map<String, TelemetryAttribute> localAttributes) {
    for (TelemetryAttribute attribute : attributes) {
      if (!KNOWN_SEMCONV_ATTRIBUTES.contains(attribute.getName())) {
        localAttributes.putIfAbsent(attribute.getName(), attribute);
      }
    }
  }

  private static void writeLocalAttributeGroup(
      InstrumentationModule module, BufferedWriter writer, Iterable<TelemetryAttribute> attributes)
      throws IOException {
    writer.write("  - id: registry." + module.getInstrumentationName() + "\n");
    writer.write("    type: attribute_group\n");
    writer.write(
        "    brief: Attributes specific to the "
            + module.getResolvedName()
            + " instrumentation.\n");
    writer.write("    attributes:\n");
    for (TelemetryAttribute attribute : attributes) {
      writer.write("      - id: " + attribute.getName() + "\n");
      writer.write("        type: " + mapAttributeType(attribute.getType()) + "\n");
      writer.write("        stability: development\n");
      // TODO: this brief is a placeholder; local/custom attributes carry no description in the
      // collected telemetry data, so this needs human review before the model is considered final.
      writer.write(
          "        brief: \""
              + attribute.getName()
              + "\" attribute emitted by the "
              + module.getResolvedName()
              + " instrumentation.\n");
    }
  }

  private static void writeMetricGroup(ResolvedMetric resolved, BufferedWriter writer)
      throws IOException {
    EmittedMetrics.Metric metric = resolved.metric;
    writer.write("  - id: metric." + metric.getName() + "\n");
    writer.write("    type: metric\n");
    writer.write("    metric_name: " + metric.getName() + "\n");
    writer.write("    stability: development\n");
    writer.write("    brief: " + quote(metric.getDescription()) + "\n");
    writer.write("    instrument: " + metric.getInstrumentType() + "\n");
    writer.write("    unit: " + quote(metric.getUnit()) + "\n");
    if (resolved.note != null) {
      writer.write("    note: " + quote(resolved.note) + "\n");
    }
    if (!resolved.attributes.isEmpty()) {
      writer.write("    attributes:\n");
      for (TelemetryAttribute attribute : resolved.attributes) {
        writeAttributeRef(
            writer, attribute.getName(), resolved.attributeConditions.get(attribute.getName()));
      }
    }
  }

  private static void writeSpanGroup(
      InstrumentationModule module, ResolvedSpan resolved, BufferedWriter writer)
      throws IOException {
    EmittedSpans.Span span = resolved.span;
    String kind = span.getSpanKind().toLowerCase(Locale.ROOT);
    writer.write("  - id: span." + module.getInstrumentationName() + "." + kind + "\n");
    writer.write("    type: span\n");
    writer.write("    span_kind: " + kind + "\n");
    writer.write("    stability: development\n");
    // TODO: this brief is a placeholder; emitted span data carries no description, so this needs
    // human review before the model is considered final.
    writer.write("    brief: " + module.getResolvedName() + " " + kind + " span.\n");
    if (resolved.note != null) {
      writer.write("    note: " + quote(resolved.note) + "\n");
    }
    if (!resolved.attributes.isEmpty()) {
      writer.write("    attributes:\n");
      for (TelemetryAttribute attribute : resolved.attributes) {
        writeAttributeRef(
            writer, attribute.getName(), resolved.attributeConditions.get(attribute.getName()));
      }
    }
  }

  private static void writeAttributeRef(
      BufferedWriter writer, String attributeName, @Nullable String condition) throws IOException {
    writer.write("      - ref: " + attributeName + "\n");
    if (condition != null) {
      writer.write("        requirement_level:\n");
      writer.write("          conditionally_required: " + quote(condition) + "\n");
    }
  }

  /**
   * Merges metrics across all "when" buckets: metrics present in the default bucket are emitted as
   * unconditional groups (attributes that only appear in a non-default bucket are marked {@code
   * conditionally_required}); metrics that never appear in the default bucket are emitted as their
   * own group with a {@code note} describing the condition under which they exist.
   */
  // visible for testing
  static List<ResolvedMetric> resolveMetrics(
      InstrumentationModule module, List<ConfigurationOption> configs) {
    Map<String, List<EmittedMetrics.Metric>> byWhen = module.getMetrics();
    Map<String, ResolvedMetric> resolved = new TreeMap<>();

    List<EmittedMetrics.Metric> defaults = byWhen == null ? null : byWhen.get("default");
    if (defaults != null) {
      for (EmittedMetrics.Metric metric : defaults) {
        resolved.put(
            metric.getName(),
            new ResolvedMetric(
                metric, null, new ArrayList<>(metric.getAttributes()), new TreeMap<>()));
      }
    }

    if (byWhen != null) {
      for (Map.Entry<String, List<EmittedMetrics.Metric>> entry : byWhen.entrySet()) {
        if ("default".equals(entry.getKey()) || entry.getValue() == null) {
          continue;
        }
        String condition = resolveWhenCondition(entry.getKey(), configs);
        for (EmittedMetrics.Metric metric : entry.getValue()) {
          ResolvedMetric existing = resolved.get(metric.getName());
          if (existing == null) {
            resolved.put(
                metric.getName(),
                new ResolvedMetric(
                    metric, condition, new ArrayList<>(metric.getAttributes()), new TreeMap<>()));
          } else {
            for (TelemetryAttribute attribute : metric.getAttributes()) {
              boolean inDefault =
                  existing.attributes.stream()
                      .anyMatch(a -> a.getName().equals(attribute.getName()));
              if (!inDefault) {
                existing.attributes.add(attribute);
                existing.attributeConditions.putIfAbsent(attribute.getName(), condition);
              }
            }
          }
        }
      }
    }

    return new ArrayList<>(resolved.values());
  }

  /**
   * Merges spans across all "when" buckets, grouping by span kind (emitted span data carries no
   * span name). See {@link #resolveMetrics} for the merge semantics.
   */
  // visible for testing
  static List<ResolvedSpan> resolveSpans(
      InstrumentationModule module, List<ConfigurationOption> configs) {
    Map<String, List<EmittedSpans.Span>> byWhen = module.getSpans();
    Map<String, ResolvedSpan> resolved = new TreeMap<>();

    List<EmittedSpans.Span> defaults = byWhen == null ? null : byWhen.get("default");
    if (defaults != null) {
      for (EmittedSpans.Span span : defaults) {
        resolved.put(
            span.getSpanKind(),
            new ResolvedSpan(span, null, new ArrayList<>(span.getAttributes()), new TreeMap<>()));
      }
    }

    if (byWhen != null) {
      for (Map.Entry<String, List<EmittedSpans.Span>> entry : byWhen.entrySet()) {
        if ("default".equals(entry.getKey()) || entry.getValue() == null) {
          continue;
        }
        String condition = resolveWhenCondition(entry.getKey(), configs);
        for (EmittedSpans.Span span : entry.getValue()) {
          ResolvedSpan existing = resolved.get(span.getSpanKind());
          if (existing == null) {
            resolved.put(
                span.getSpanKind(),
                new ResolvedSpan(
                    span, condition, new ArrayList<>(span.getAttributes()), new TreeMap<>()));
          } else {
            for (TelemetryAttribute attribute : span.getAttributes()) {
              boolean inDefault =
                  existing.attributes.stream()
                      .anyMatch(a -> a.getName().equals(attribute.getName()));
              if (!inDefault) {
                existing.attributes.add(attribute);
                existing.attributeConditions.putIfAbsent(attribute.getName(), condition);
              }
            }
          }
        }
      }
    }

    return new ArrayList<>(resolved.values());
  }

  /**
   * Resolves a raw {@code when} string (e.g. {@code
   * "otel.instrumentation.x.enabled=true,otel.instrumentation.y.enabled=true"}) into human-readable
   * text, looking up each property against the module's declared configuration options for its
   * description.
   */
  // visible for testing
  static String resolveWhenCondition(String when, List<ConfigurationOption> configs) {
    List<String> parts = new ArrayList<>();
    for (String rawCondition : when.split(",")) {
      String condition = rawCondition.trim();
      String propertyName = condition;
      int eq = condition.indexOf('=');
      if (eq >= 0) {
        propertyName = condition.substring(0, eq);
      }
      String description = findConfigurationDescription(propertyName, configs);
      if (description != null) {
        parts.add("`" + condition + "` (" + description + ")");
      } else {
        parts.add("`" + condition + "`");
      }
    }
    return "Only emitted when " + String.join(" and ", parts) + ".";
  }

  @Nullable
  private static String findConfigurationDescription(
      String propertyName, List<ConfigurationOption> configs) {
    if (configs == null) {
      return null;
    }
    for (ConfigurationOption config : configs) {
      if (propertyName.equals(config.name())) {
        return config.description();
      }
    }
    return null;
  }

  private static String mapAttributeType(String otelAttributeType) {
    return switch (otelAttributeType) {
      case "STRING" -> "string";
      case "LONG" -> "int";
      case "DOUBLE" -> "double";
      case "BOOLEAN" -> "boolean";
      case "STRING_ARRAY" -> "string[]";
      case "LONG_ARRAY" -> "int[]";
      case "DOUBLE_ARRAY" -> "double[]";
      case "BOOLEAN_ARRAY" -> "boolean[]";
      default -> "string";
    };
  }

  private static String quote(@Nullable String s) {
    if (s == null) {
      return "\"\"";
    }
    return "\"" + s.replace("\"", "\\\"") + "\"";
  }

  /**
   * Scans the {@code io.opentelemetry.semconv} and {@code io.opentelemetry.semconv.incubating}
   * packages on the classpath for {@code AttributeKey} constants, so emitted attributes that match
   * a known upstream semantic-convention attribute can be written as {@code ref:} instead of being
   * redefined locally.
   */
  private static Set<String> loadKnownSemconvAttributes() {
    Set<String> keys = new TreeSet<>();
    for (String packagePath :
        new String[] {"io/opentelemetry/semconv/", "io/opentelemetry/semconv/incubating/"}) {
      try {
        collectAttributeKeysFromPackage(packagePath, keys);
      } catch (IOException e) {
        logger.log(WARNING, "Unable to scan semconv package " + packagePath, e);
      }
    }
    return keys;
  }

  private static void collectAttributeKeysFromPackage(String packagePath, Set<String> keys)
      throws IOException {
    Enumeration<URL> resources =
        WeaverModelGenerator.class.getClassLoader().getResources(packagePath);
    while (resources.hasMoreElements()) {
      URL url = resources.nextElement();
      if (!"jar".equals(url.getProtocol())) {
        continue;
      }
      JarURLConnection connection = (JarURLConnection) url.openConnection();
      try (JarFile jarFile = connection.getJarFile()) {
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
          String name = entries.nextElement().getName();
          if (!name.startsWith(packagePath)
              || !name.endsWith(".class")
              || name.contains("$")
              || name.substring(packagePath.length()).contains("/")) {
            continue;
          }
          String className = name.substring(0, name.length() - ".class".length()).replace('/', '.');
          collectAttributeKeysFromClass(className, keys);
        }
      }
    }
  }

  private static void collectAttributeKeysFromClass(String className, Set<String> keys) {
    try {
      Class<?> clazz = Class.forName(className);
      for (Field field : clazz.getFields()) {
        if (AttributeKey.class.isAssignableFrom(field.getType())) {
          Object value = field.get(null);
          if (value instanceof AttributeKey<?> attributeKey) {
            keys.add(attributeKey.getKey());
          }
        }
      }
    } catch (ReflectiveOperationException e) {
      logger.log(FINE, "Unable to inspect semconv class " + className, e);
    }
  }

  // visible for testing
  static final class ResolvedMetric {
    final EmittedMetrics.Metric metric;
    @Nullable final String note;
    // The union of attributes across all "when" buckets this metric appears in (unlike
    // metric.getAttributes(), which only reflects the bucket it was first seen in).
    final List<TelemetryAttribute> attributes;
    final Map<String, String> attributeConditions;

    ResolvedMetric(
        EmittedMetrics.Metric metric,
        @Nullable String note,
        List<TelemetryAttribute> attributes,
        Map<String, String> attributeConditions) {
      this.metric = metric;
      this.note = note;
      this.attributes = attributes;
      this.attributeConditions = attributeConditions;
    }
  }

  // visible for testing
  static final class ResolvedSpan {
    final EmittedSpans.Span span;
    @Nullable final String note;
    // The union of attributes across all "when" buckets this span kind appears in (unlike
    // span.getAttributes(), which only reflects the bucket it was first seen in).
    final List<TelemetryAttribute> attributes;
    final Map<String, String> attributeConditions;

    ResolvedSpan(
        EmittedSpans.Span span,
        @Nullable String note,
        List<TelemetryAttribute> attributes,
        Map<String, String> attributeConditions) {
      this.span = span;
      this.note = note;
      this.attributes = attributes;
      this.attributeConditions = attributeConditions;
    }
  }

  private WeaverModelGenerator() {}
}
