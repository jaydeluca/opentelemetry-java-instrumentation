plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.clickhouse")
    module.set("client-v2")
    versions.set("[0.6.4,)")
    assertInverse.set(true)
    // The endpoint-tracking module needs Endpoint#getHost()/#getPort(), added in 0.9.7, so it is
    // expected to fail muzzle on the lower half of this range. A second pass over "[0.9.7,)" cannot
    // assert it there: muzzle derives its task names from group/module/version alone, so two passes
    // over the same artifact with overlapping ranges collide. The runtime muzzle check gates the
    // module on older versions instead, and the tests cover the supported range.
    excludeInstrumentationName("clickhouse-client-v2-endpoint")
  }
}

dependencies {
  implementation(project(":instrumentation:clickhouse:clickhouse-client-common-0.5:javaagent"))
  library("com.clickhouse:client-v2:0.10.0")
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
