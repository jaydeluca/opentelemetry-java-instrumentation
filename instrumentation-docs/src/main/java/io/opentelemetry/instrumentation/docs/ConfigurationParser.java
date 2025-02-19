/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class ConfigurationParser {

  private ConfigurationParser() {}

  private static final Pattern CONFIG_PATTERN =
      Pattern.compile(
          "private static final (\\w+) \\w+ =\\s*"
              + "AgentInstrumentationConfig\\.get\\(\\)\\s*\\.get(\\w+)\\(\"([^\"]+)\",\\s*([^)]+)\\);",
          Pattern.DOTALL);

  static List<ConfigurationProperty> parse(String input) {
    List<ConfigurationProperty> properties = new ArrayList<>();
    Matcher matcher = CONFIG_PATTERN.matcher(input);

    while (matcher.find()) {
      String type = matcher.group(1).toLowerCase(Locale.ROOT);
      String name = matcher.group(3);
      String defaultValue = matcher.group(4);

      properties.add(new ConfigurationProperty(name, type, defaultValue));
    }

    return properties;
  }
}
