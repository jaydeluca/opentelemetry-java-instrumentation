plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.clickhouse")
    module.set("client-v2")
    versions.set("[0.6.4,)")
    assertInverse.set(true)
    // endpoint selection tracking needs Endpoint#getHost()/#getPort(), added in 0.9.7, it is
    // verified by the directive below
    excludeInstrumentationName("clickhouse-client-v2-endpoint")
  }

  pass {
    // named so that the tasks of this directive don't clash with the ones of the directive above,
    // which covers the same artifact
    name.set("endpoint selection")
    group.set("com.clickhouse")
    module.set("client-v2")
    versions.set("[0.9.7,)")
    // no assertInverse, below 0.9.7 only the endpoint selection instrumentation fails muzzle while
    // a fail directive expects all instrumentations to fail
  }
}

dependencies {
  implementation(project(":instrumentation:clickhouse:clickhouse-client-common-0.5:javaagent"))
  library("com.clickhouse:client-v2:0.8.0")

  // Endpoint#getHost() and Endpoint#getPort() were added in 0.9.7.
  // Don't use library to make sure base test is run with the floor version.
  // Endpoint selection is tested separately in testEndpointSelection.
  compileOnly("com.clickhouse:client-v2:0.9.7")

  testInstrumentation(project(":instrumentation:clickhouse:clickhouse-client-v1-0.5:javaagent"))
}

testing {
  suites {
    register<JvmTestSuite>("testEndpointSelection") {
      dependencies {
        // first version that exposes Endpoint#getHost() and Endpoint#getPort()
        implementation("com.clickhouse:client-v2:${baseVersion("0.9.7").orLatest()}")
        implementation("org.testcontainers:testcontainers")
      }
    }
  }
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
    dependsOn(testStableSemconv, testing.suites)
  }
}
