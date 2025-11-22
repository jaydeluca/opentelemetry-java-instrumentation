/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.satisfies;

import io.opentelemetry.semconv.ServiceAttributes;
import io.opentelemetry.semconv.incubating.OsIncubatingAttributes;
import io.opentelemetry.semconv.incubating.TelemetryIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import org.junit.jupiter.api.condition.DisabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisabledIf("io.opentelemetry.smoketest.TestContainerManager#useWindowsContainers")
class SpringBoot4SmokeTest extends AbstractSmokeTest<Integer> {

  @Override
  protected void configure(SmokeTestOptions<Integer> options) {
    options
        .image(
            jdk ->
                String.format(
                    "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/smoke-test-spring-boot-4:jdk%s-%s",
                    jdk, getImageTag()))
        .waitStrategy(
            new TargetWaitStrategy.Log(
                java.time.Duration.ofMinutes(1), ".*Started SpringbootApplication in.*"))
        .setServiceName(false)
        .env("OTEL_METRICS_EXPORTER", "otlp")
        .env("OTEL_RESOURCE_ATTRIBUTES", "foo=bar");
  }

  private static String getImageTag() {
    // Use a tag for the image - in CI this would be set via the build
    return System.getProperty("io.opentelemetry.smoketest.springboot4.tag", "local");
  }

  @ParameterizedTest
  @ValueSource(ints = {17, 21, 25}) // Spring Boot 4 requires Java 17+
  void springBoot4SmokeTest(int jdk) {
    SmokeTestOutput output = start(jdk);

    var response = client().get("/greeting").aggregate().join();
    assertThat(response.contentUtf8()).isEqualTo("Hi!");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("GET /greeting")
                        .hasAttribute(
                            satisfies(ThreadIncubatingAttributes.THREAD_ID, a -> a.isNotNull()))
                        .hasAttribute(
                            satisfies(ThreadIncubatingAttributes.THREAD_NAME, a -> a.isNotBlank()))
                        .hasResourceSatisfying(
                            resource ->
                                resource
                                    .hasAttribute(
                                        TelemetryIncubatingAttributes.TELEMETRY_DISTRO_VERSION,
                                        getAgentVersion())
                                    .hasAttribute(
                                        satisfies(
                                            OsIncubatingAttributes.OS_TYPE, a -> a.isNotNull()))
                                    .hasAttribute(stringKey("foo"), "bar")
                                    .hasAttribute(
                                        ServiceAttributes.SERVICE_NAME, "otel-spring-test-app")
                                    .hasAttribute(ServiceAttributes.SERVICE_VERSION, "4.0.0")),
                span -> span.hasName("WebController.withSpan")));

    output.assertAgentVersionLogged();

    // Check trace IDs are logged via MDC instrumentation
    assertThat(output.getLoggedTraceIds()).isEqualTo(getSpanTraceIds());

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        metric -> metric.hasName("jvm.memory.used"),
        metric -> metric.hasName("jvm.memory.committed"),
        metric -> metric.hasName("jvm.memory.limit"),
        metric -> metric.hasName("jvm.memory.used_after_last_gc"));
  }
}
