plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.clickhouse")
    module.set("client-v2")
    versions.set("[0.6.4,)")
    assertInverse.set(true)
    // failover instrumentation requires ClientNodeSelector, added in 0.10.0
    excludeInstrumentationName("clickhouse-client-v2-failover")
  }
  pass {
    group.set("com.clickhouse")
    module.set("client-v2")
    versions.set("[0.10.0-rc2,)")
  }
}

dependencies {
  implementation(project(":instrumentation:clickhouse:clickhouse-client-common-0.5:javaagent"))
  // Multiple-endpoint / failover support is not yet in a stable release. The endpoint-tracking
  // instrumentation targets ClientNodeSelector (present in the 0.10.x source and 0.11.0-rc1), but
  // the only published failover-capable artifact, 0.10.0-rc2, diverges from its own source: that
  // jar has no ClientNodeSelector (failover lives in private Client methods), so the tracking
  // advice cannot match at runtime and its test is @Disabled. This dependency stays on 0.10.0-rc2
  // only so the advice (which references com.clickhouse...transport.Endpoint) compiles.
  // TODO(#19306): move to a stable release that ships ClientNodeSelector, then re-enable the test.
  library("com.clickhouse:client-v2:0.10.0-rc2")
  testInstrumentation(project(":instrumentation:clickhouse:clickhouse-client-v1-0.5:javaagent"))
}

tasks {
  withType<Test>().configureEach {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
    systemProperty("collectMetadata", otelProps.collectMetadata)
  }

  val testStableSemconv = register<Test>("testStableSemconv") {
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath

    jvmArgs("-Dotel.semconv-stability.opt-in=database")
    systemProperty("metadataConfig", "otel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
