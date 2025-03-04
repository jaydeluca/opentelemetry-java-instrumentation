plugins {
  id("otel.java-conventions")
}

otelJava {
  minJavaVersionSupported.set(JavaVersion.VERSION_17)
}

tasks {
  val generateDocs by registering(JavaExec::class) {
    dependsOn(classes)

    mainClass.set("io.opentelemetry.instrumentation.docs.DocGeneratorApplication")
    classpath(sourceSets["main"].runtimeClasspath)
  }
}
