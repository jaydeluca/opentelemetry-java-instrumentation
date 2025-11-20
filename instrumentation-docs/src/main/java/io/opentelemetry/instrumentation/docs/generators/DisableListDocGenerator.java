/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.generators;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Generates markdown documentation for the instrumentation disable list on opentelemetry.io.
 *
 * <p>This generator creates a table listing all instrumentations that can be disabled, matching the
 * format expected by the disable.md page.
 *
 * <p>IMPORTANT: This uses instrumentation-list.yaml as the source of truth, matching the behavior
 * of SuppressionListAuditor. This ensures the generated list matches what the auditor expects.
 */
public record DisableListDocGenerator(String version) {

  // Instrumentations to exclude from the disable list
  // NOTE: These must match SuppressionListAuditor.INSTRUMENTATION_EXCLUSIONS
  private static final List<String> EXCLUSIONS = List.of("resources", "spring-boot-resources");

  // Overrides for instrumentation names (consolidate related instrumentations)
  // NOTE: These must match SuppressionListAuditor.INSTRUMENTATION_DISABLE_OVERRIDES
  private static final Map<String, String> NAME_OVERRIDES =
      Map.of("akka-actor-fork-join", "akka-actor");

  /**
   * Generates the complete disable list table markdown from instrumentation-list.yaml.
   *
   * <p>This method matches the logic in SuppressionListAuditor.parseInstrumentationList() and
   * identifyMissingItems() to ensure consistency between generation and auditing.
   *
   * @param instrumentationListYaml content of docs/instrumentation-list.yaml
   * @return Markdown table content (without surrounding markers)
   */
  public String generateDisableListTableFromYaml(String instrumentationListYaml) {
    // Parse YAML using the same logic as SuppressionListAuditor
    List<String> instrumentationNames = parseInstrumentationList(instrumentationListYaml);

    // Sanitize and deduplicate (same as auditor)
    Set<String> sanitizedNames =
        instrumentationNames.stream()
            // Remove version suffix (e.g., "akka-actor-2.3" -> "akka-actor")
            .map(name -> name.replaceFirst("-[0-9].*$", ""))
            // Filter exclusions
            .filter(name -> !EXCLUSIONS.contains(name))
            // Apply overrides
            .map(name -> NAME_OVERRIDES.getOrDefault(name, name))
            // Sort and deduplicate
            .collect(Collectors.toCollection(TreeSet::new));

    return generateTable(sanitizedNames);
  }

  /**
   * Parses instrumentation-list.yaml to extract instrumentation names. This matches the logic in
   * SuppressionListAuditor.parseInstrumentationList().
   */
  @SuppressWarnings("unchecked")
  private static List<String> parseInstrumentationList(String yamlContent) {
    List<String> instrumentationList = new java.util.ArrayList<>();
    org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml();
    Map<String, Object> data = yaml.load(yamlContent);

    if (data != null && data.get("libraries") instanceof Map) {
      Map<String, List<Map<String, Object>>> libraries =
          (Map<String, List<Map<String, Object>>>) data.get("libraries");
      for (List<Map<String, Object>> libraryGroup : libraries.values()) {
        for (Map<String, Object> instrumentation : libraryGroup) {
          if (instrumentation.get("name") instanceof String) {
            instrumentationList.add((String) instrumentation.get("name"));
          }
        }
      }
    }
    return instrumentationList;
  }

  /** Generates the markdown table from a set of instrumentation names. */
  private String generateTable(Set<String> instrumentationNames) {
    StringBuilder sb = new StringBuilder();

    // Generate table header matching existing format
    sb.append("| Library/Framework | Instrumentation name |\n");
    sb.append("| ----------------- | -------------------- |\n");

    // Generate rows matching existing format
    for (String name : instrumentationNames) {
      String displayName = formatDisplayName(name);

      sb.append("| ").append(displayName).append(" | `").append(name).append("` |\n");
    }

    sb.append("\n");
    sb.append("_Auto-generated for version ").append(version).append("_\n");

    return sb.toString();
  }

  /**
   * Formats the instrumentation name into a human-readable display name.
   *
   * @param instrumentationName the instrumentation name (e.g., "akka-actor")
   * @return formatted display name (e.g., "Akka Actor")
   */
  private static String formatDisplayName(String instrumentationName) {
    // Special case mappings for better display names
    Map<String, String> displayNames =
        Map.ofEntries(
            Map.entry("akka-actor", "Akka Actor"),
            Map.entry("akka-http", "Akka HTTP"),
            Map.entry("apache-dbcp", "Apache DBCP"),
            Map.entry("apache-httpasyncclient", "Apache HttpAsyncClient"),
            Map.entry("apache-httpclient", "Apache HttpClient"),
            Map.entry("apache-dubbo", "Apache Dubbo"),
            Map.entry("apache-shenyu", "Apache ShenYu"),
            Map.entry("async-http-client", "AsyncHttpClient (AHC)"),
            Map.entry("avaje-jex", "Avaje Jex"),
            Map.entry("aws-lambda", "AWS Lambda"),
            Map.entry("aws-sdk", "AWS SDK"),
            Map.entry("azure-core", "Azure SDK"),
            Map.entry("c3p0", "C3P0"),
            Map.entry("dropwizard-metrics", "Dropwizard Metrics"),
            Map.entry("dropwizard-views", "Dropwizard Views"),
            Map.entry("elasticsearch-api-client", "Elasticsearch API client"),
            Map.entry("elasticsearch-rest", "Elasticsearch REST client"),
            Map.entry("elasticsearch-transport", "Elasticsearch client"),
            Map.entry("external-annotations", "Additional tracing annotations"),
            Map.entry("google-http-client", "Google HTTP client"),
            Map.entry("grpc", "GRPC"),
            Map.entry("gwt", "Google Web Toolkit"),
            Map.entry("hikaricp", "HikariCP"),
            Map.entry("http-url-connection", "Java `HttpURLConnection`"),
            Map.entry("java-http-client", "Java HTTP Client"),
            Map.entry("java-http-server", "Java HTTP Server"),
            Map.entry("java-util-logging", "java.util.logging"),
            Map.entry("jaxrs", "JAX-RS (Server)"),
            Map.entry("jaxrs-client", "JAX-RS (Client)"),
            Map.entry("jaxws", "JAX-WS"),
            Map.entry("jboss-logmanager-appender", "JBoss Logging Appender"),
            Map.entry("jboss-logmanager-mdc", "JBoss Logging MDC"),
            Map.entry("jdbc", "Java JDBC"),
            Map.entry("jdbc-datasource", "Java JDBC `DataSource`"),
            Map.entry("jetty-httpclient", "Eclipse Jetty HTTP Client"),
            Map.entry("jsf-mojarra", "Eclipse Mojarra"),
            Map.entry("jsf-myfaces", "Apache MyFaces"),
            Map.entry("jms", "JMS"),
            Map.entry("jsp", "JSP"),
            Map.entry("kubernetes-client", "K8s Client"),
            Map.entry("kotlinx-coroutines", "kotlinx.coroutines"),
            Map.entry("ktor", "Ktor"),
            Map.entry("log4j-appender", "Log4j Appender"),
            Map.entry("log4j-context-data", "Log4j Context Data (2.x)"),
            Map.entry("log4j-mdc", "Log4j MDC (1.x)"),
            Map.entry("logback-appender", "Logback Appender"),
            Map.entry("logback-mdc", "Logback MDC"),
            Map.entry("methods", "Additional methods tracing"),
            Map.entry("mongo", "MongoDB"),
            Map.entry("mybatis", "MyBatis"),
            Map.entry("nats", "NATS Client"),
            Map.entry("okhttp", "OkHttp"),
            Map.entry("openai", "OpenAI"),
            Map.entry("opensearch-java", "OpenSearch Java"),
            Map.entry("opensearch-rest", "OpenSearch REST"),
            Map.entry("opentelemetry-api", "OpenTelemetry API"),
            Map.entry("opentelemetry-extension-annotations", "OpenTelemetry Extension Annotations"),
            Map.entry(
                "opentelemetry-instrumentation-annotations",
                "OpenTelemetry Instrumentation Annotations"),
            Map.entry("oracle-ucp", "Oracle UCP"),
            Map.entry("oshi", "OSHI (Operating System and Hardware Information)"),
            Map.entry("pekko-actor", "Apache Pekko Actor"),
            Map.entry("pekko-http", "Apache Pekko HTTP"),
            Map.entry("play-ws", "Play WS HTTP Client"),
            Map.entry("r2dbc", "R2DBC"),
            Map.entry("rabbitmq", "RabbitMQ Client"),
            Map.entry("reactor-kafka", "Reactor Kafka"),
            Map.entry("reactor-netty", "Reactor Netty"),
            Map.entry("rediscala", "Rediscala"),
            Map.entry("rmi", "Java RMI"),
            Map.entry("rocketmq-client", "Apache RocketMQ"),
            Map.entry("runtime-telemetry", "Java Runtime"),
            Map.entry("rxjava", "ReactiveX RxJava"),
            Map.entry("scala-fork-join", "Scala ForkJoinPool"),
            Map.entry("servlet", "Java Servlet"),
            Map.entry("executors", "java.util.concurrent"),
            Map.entry("spring-boot-actuator-autoconfigure", "Spring Boot Actuator Autoconfigure"),
            Map.entry("spring-cloud-aws", "Spring Cloud AWS"),
            Map.entry("spring-cloud-gateway", "Spring Cloud Gateway"),
            Map.entry("spring-core", "Spring Core"),
            Map.entry("spring-data", "Spring Data"),
            Map.entry("spring-integration", "Spring Integration"),
            Map.entry("spring-jms", "Spring JMS"),
            Map.entry("spring-kafka", "Spring Kafka"),
            Map.entry("spring-pulsar", "Spring Pulsar"),
            Map.entry("spring-rabbit", "Spring RabbitMQ"),
            Map.entry("spring-rmi", "Spring RMI"),
            Map.entry("spring-scheduling", "Spring Scheduling"),
            Map.entry("spring-security-config", "Spring Security Config"),
            Map.entry("spring-web", "Spring Web"),
            Map.entry("spring-webflux", "Spring WebFlux"),
            Map.entry("spring-webmvc", "Spring Web MVC"),
            Map.entry("spring-ws", "Spring Web Services"),
            Map.entry("tomcat-jdbc", "Tomcat JDBC"),
            Map.entry("twilio", "Twilio SDK"),
            Map.entry("vertx-http-client", "Eclipse Vert.x HttpClient"),
            Map.entry("vertx-kafka-client", "Eclipse Vert.x Kafka Client"),
            Map.entry("vertx-redis-client", "Eclipse Vert.x Redis Client"),
            Map.entry("vertx-rx-java", "Eclipse Vert.x RxJava"),
            Map.entry("vertx-sql-client", "Eclipse Vert.x SQL Client"),
            Map.entry("vertx-web", "Eclipse Vert.x Web"),
            Map.entry("vibur-dbcp", "Vibur DBCP"),
            Map.entry("xxl-job", "XXL-JOB"),
            Map.entry("zio", "ZIO"));

    if (displayNames.containsKey(instrumentationName)) {
      return displayNames.get(instrumentationName);
    }

    // Default: capitalize each word
    String[] parts = instrumentationName.split("-");
    StringBuilder result = new StringBuilder();

    // Common acronyms that should be fully uppercase
    Set<String> acronyms =
        Set.of(
            "http", "https", "grpc", "rpc", "jms", "jdbc", "jmx", "aws", "sql", "xml", "json",
            "api", "sdk", "jvm", "jsp", "rmi", "url", "uri", "tcp", "udp", "dns", "ssl", "tls",
            "oauth", "jwt", "uuid");

    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        result.append(" ");
      }
      String part = parts[i].toLowerCase(Locale.ROOT);

      // Check if this part is an acronym
      if (acronyms.contains(part)) {
        result.append(part.toUpperCase(Locale.ROOT));
      } else {
        // Capitalize first letter only
        result.append(Character.toUpperCase(part.charAt(0)));
        if (part.length() > 1) {
          result.append(part.substring(1));
        }
      }
    }
    return result.toString();
  }
}
