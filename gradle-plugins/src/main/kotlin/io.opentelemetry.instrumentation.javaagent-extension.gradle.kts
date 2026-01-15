/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import io.opentelemetry.gradle.PluginVersion

/**
 * Gradle plugin for developing OpenTelemetry Java agent extensions.
 *
 * This plugin simplifies extension development by:
 * - Applying muzzle-check and muzzle-generation plugins for bytecode safety verification
 * - Automatically applying the java-library and com.gradleup.shadow plugins (via muzzle plugins)
 * - Auto-applying OpenTelemetry instrumentation BOMs matching the plugin version
 *
 * The plugin automatically applies the following BOMs to ensure version alignment:
 * - io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom
 * - io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha
 *
 * This eliminates the need to manually declare these BOMs and prevents version mismatches.
 *
 * Usage:
 * plugins {
 *   id("io.opentelemetry.instrumentation.javaagent-extension") version "VERSION"
 * }
 *
 * dependencies {
 *   // BOMs are auto-applied - you can now directly use instrumentation dependencies
 *   compileOnly("io.opentelemetry.javaagent:opentelemetry-javaagent-extension-api")
 *   compileOnly("io.opentelemetry.instrumentation:opentelemetry-instrumentation-api")
 * }
 *
 * Overriding the BOM version:
 * If you need to use a different version, simply declare your own BOM in dependencies:
 * dependencies {
 *   implementation(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom:2.22.0"))
 *   // Your explicit BOM version will take precedence over the plugin's auto-applied version
 * }
 *
 * Note: You can also use the muzzle plugins directly without this wrapper:
 * plugins {
 *   id("io.opentelemetry.instrumentation.muzzle-generation") version "VERSION"
 *   id("io.opentelemetry.instrumentation.muzzle-check") version "VERSION"
 * }
 *
 * @see <a href="https://github.com/open-telemetry/opentelemetry-java-instrumentation/blob/main/docs/contributing/muzzle.md">Muzzle Documentation</a>
 */

plugins {
  id("io.opentelemetry.instrumentation.muzzle-check")
  id("io.opentelemetry.instrumentation.muzzle-generation")
}

val pluginVersion = PluginVersion.get()

dependencies {
  // Add alpha BOM which transitively includes the stable BOM
  // Users can override by declaring their own BOM versions in higher-precedence configurations
  compileOnly(platform("io.opentelemetry.instrumentation:opentelemetry-instrumentation-bom-alpha:$pluginVersion"))
}
