/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.gradle

import java.util.Properties

/**
 * Utility to access the plugin version at runtime.
 */
object PluginVersion {
  /**
   * Returns the version of the OpenTelemetry instrumentation Gradle plugins.
   * This version is used to auto-apply matching BOM versions.
   */
  fun get(): String {
    val props = Properties()
    val stream = PluginVersion::class.java.getResourceAsStream("/opentelemetry/gradle/plugin-version.properties")
    if (stream != null) {
      stream.use { props.load(it) }
      return props.getProperty("plugin.version") ?: error("plugin.version not found in properties file")
    }
    // Fallback for development/testing scenarios
    return System.getProperty("otel.instrumentation.plugin.version", "2.24.0-SNAPSHOT")
  }
}
