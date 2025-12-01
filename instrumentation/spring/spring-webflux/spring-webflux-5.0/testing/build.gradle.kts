plugins {
  id("otel.java-conventions")
  id("otel.javaagent-testing")
}

dependencies {
  implementation("io.opentelemetry.javaagent:opentelemetry-testing-common")

  implementation("org.springframework:spring-webflux:5.0.0.RELEASE")
//  implementation("org.springframework.boot:spring-boot-starter-reactor-netty:2.0.0.RELEASE")
  implementation("org.springframework.boot:spring-boot:2.0.0.RELEASE")
  implementation("org.springframework.boot:spring-boot-autoconfigure:2.0.0.RELEASE")
  implementation("org.springframework:spring-context:5.0.0.RELEASE")
  implementation("org.springframework:spring-beans:5.0.0.RELEASE")
}
