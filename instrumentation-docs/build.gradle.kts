plugins {
  id("otel.java-conventions")
}

dependencies {
  implementation("com.google.guava:guava")

  testImplementation("org.assertj:assertj-core:3.27.3")

  testImplementation(enforcedPlatform("org.junit:junit-bom:5.11.4"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  val generateDocs by registering(JavaExec::class) {
    dependsOn(classes)

    mainClass.set("io.opentelemetry.instrumentation.docs.MetaDataGenerator")
    classpath(sourceSets["main"].runtimeClasspath)
  }
}
