plugins {
  id("otel.java-conventions")
  alias(springBoot40.plugins.versions)
  id("org.graalvm.buildtools.native")
}

description = "smoke-tests-otel-starter-spring-boot-4"

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

dependencies {
  implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))

  implementation("org.springframework.boot:spring-boot-starter-web")
  implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
  runtimeOnly("com.h2database:h2")
  implementation("org.apache.commons:commons-dbcp2")
  implementation("org.springframework.kafka:spring-kafka")
  implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  // TODO: Check if starter-aop is available in Spring Boot 4.0 or if it's been modularized
  // implementation("org.springframework.boot:spring-boot-starter-aop")

  implementation(project(":smoke-tests-otel-starter:spring-boot-common"))
  testImplementation("org.springframework.boot:spring-boot-starter-test")
  testImplementation("org.springframework.boot:spring-boot-resttestclient")
  testImplementation("org.springframework.boot:spring-boot-restclient")
  testImplementation(project(":instrumentation:spring:starters:spring-boot-starter"))
  testImplementation("org.springframework.boot:spring-boot-starter-kafka")
  testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb")
  testImplementation("org.testcontainers:testcontainers-junit-jupiter")
  testImplementation("org.testcontainers:testcontainers-kafka")
  testImplementation("org.testcontainers:testcontainers-mongodb")

  val testLatestDeps = gradle.startParameter.projectProperties["testLatestDeps"] == "true"
  if (testLatestDeps) {
    // with spring boot 4.x versions of org.mongodb:mongodb-driver-sync and org.mongodb:mongodb-driver-core
    // are not in sync
    testImplementation("org.mongodb:mongodb-driver-sync:latest.release")
  }
}

springBoot {
  mainClass = "io.opentelemetry.spring.smoketest.OtelSpringStarterSmokeTestApplication"
}

// Disable -Werror for Spring Framework 7.0 compatibility
tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.removeAll(listOf("-Werror"))
}

tasks {
  // Disable AOT processing for Spring Boot 4 due to modularization issues
  // with DataSourceAutoConfiguration package relocation
  named("processAot") {
    enabled = false
  }
  named("processTestAot") {
    enabled = false
  }

  compileAotJava {
    with(options) {
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none")
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    }
  }
  compileAotTestJava {
    with(options) {
      compilerArgs.add("-Xlint:-deprecation,-unchecked,none")
      // To disable warnings/failure coming from the Java compiler during the Spring AOT processing
      // -deprecation,-unchecked and none are required (none is not enough)
    }
  }
  checkstyleAot {
    isEnabled = false
  }
  checkstyleAotTest {
    isEnabled = false
  }
  bootJar {
    enabled = false
  }
}

graalvmNative {
  // See https://github.com/graalvm/native-build-tools/issues/572
  metadataRepository {
    enabled.set(false)
  }

  tasks.test {
    useJUnitPlatform()
    setForkEvery(1)
  }
}

// Disable collectReachabilityMetadata task to avoid configuration isolation issues
// See https://github.com/gradle/gradle/issues/17559
tasks.named("collectReachabilityMetadata").configure {
  enabled = false
}
