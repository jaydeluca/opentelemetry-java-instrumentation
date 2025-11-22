import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
  id("otel.java-conventions")

  id("com.google.cloud.tools.jib")
  id("org.springframework.boot") version "4.0.0"
}

dependencies {
  implementation(platform("io.opentelemetry:opentelemetry-bom"))
  implementation(platform("org.springframework.boot:spring-boot-dependencies:4.0.0"))

  implementation("io.opentelemetry:opentelemetry-api")
  implementation(project(":instrumentation-annotations"))
  implementation("org.springframework.boot:spring-boot-starter-web")
}

// Note: Spring Boot 4 requires Java 17+
val targetJDK = project.findProperty("targetJDK") ?: "17"

val tag = findProperty("tag")
  ?: DateTimeFormatter.ofPattern("yyyyMMdd.HHmmSS").format(LocalDateTime.now())

java {
  // Jib detects the Java version from sourceCompatibility to determine the entrypoint format.
  // Spring Boot 4 requires Java 17+ as minimum
  if (targetJDK == "17") {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  } else if (targetJDK == "21") {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  } else if (targetJDK == "25") {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
  }
}

// Disable -Werror for Spring Framework 7.0 compatibility
tasks.withType<JavaCompile>().configureEach {
  options.compilerArgs.removeAll(listOf("-Werror"))
}

springBoot {
  buildInfo {
    properties {
      version = "4.0.0"
    }
  }
}

val repo = System.getenv("GITHUB_REPOSITORY") ?: "open-telemetry/opentelemetry-java-instrumentation"

jib {
  from.image = "eclipse-temurin:$targetJDK"
  to.image = "ghcr.io/$repo/smoke-test-spring-boot-4:jdk$targetJDK-$tag"
  container.ports = listOf("8080")
}

tasks {
  val springBootJar by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
  }

  artifacts {
    add("springBootJar", bootJar)
  }
}
