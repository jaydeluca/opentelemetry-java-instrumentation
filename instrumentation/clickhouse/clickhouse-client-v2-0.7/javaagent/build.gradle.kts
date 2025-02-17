plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.clickhouse.client")
    module.set("client-v2")
    versions.set("[0.7.1,)")
    assertInverse.set(true)
  }
}

dependencies {
  compileOnly("com.clickhouse:client-v2:0.7.1")
  compileOnly("com.google.auto.value:auto-value-annotations")
  annotationProcessor("com.google.auto.value:auto-value")

  testLibrary("com.clickhouse:client-v2:0.7.1")
  testLibrary("com.clickhouse:clickhouse-http-client:0.5.0")
  testLibrary("org.apache.httpcomponents.client5:httpclient5:5.2.3")
  testImplementation("org.apache.commons:commons-text:1.12.0")
}

tasks {
  test {
    usesService(gradle.sharedServices.registrations["testcontainersBuildService"].service)
  }

  val testStableSemconv by registering(Test::class) {
    jvmArgs("-Dotel.semconv-stability.opt-in=database")
  }

  check {
    dependsOn(testStableSemconv)
  }
}
