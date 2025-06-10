plugins {
  id("otel.javaagent-instrumentation")
}

muzzle {
  pass {
    group.set("com.github.oshi")
    module.set("oshi-core")
    versions.set("[5.3.1,)")
    // Could not parse POM https://repo.maven.apache.org/maven2/com/github/oshi/oshi-core/6.1.1/oshi-core-6.1.1.pom
    skip("6.1.1")
  }
}

dependencies {
  implementation(project(":instrumentation:oshi:library"))

  compileOnly("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")

  library("com.github.oshi:oshi-core:5.3.1")

  testImplementation(project(":instrumentation:oshi:testing"))
}

tasks {
  val testExperimental by registering(Test::class) {
    jvmArgs("-Dotel.instrumentation.oshi.experimental-metrics.enabled=true")
    systemProperty("testExperimental", "true")
  }

  check {
    dependsOn(testExperimental)
  }
}
