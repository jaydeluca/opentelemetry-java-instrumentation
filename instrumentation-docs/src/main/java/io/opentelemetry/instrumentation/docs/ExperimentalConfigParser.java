/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExperimentalConfigParser {

  private ExperimentalConfigParser() {}

  static final Pattern multiLinePattern =
      Pattern.compile(
          "public\\s+\\w+\\s+(\\w+)\\s*\\(\\)\\s*\\{[^}]*?config\\.(\\w+)\\(\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*([^\\)]+)",
          Pattern.DOTALL);

  public static Map<String, ConfigurationProperty> extractConfigMap(String code) {
    Map<String, ConfigurationProperty> result = new HashMap<>();

    Matcher multiLineMatcher = multiLinePattern.matcher(code);
    while (multiLineMatcher.find()) {
      String methodName = multiLineMatcher.group(1);
      String type = multiLineMatcher.group(2).replace("get", "");
      String configKey = multiLineMatcher.group(3);
      String defaultValue = multiLineMatcher.group(4);
      result.put(methodName + "()", new ConfigurationProperty(configKey, type, defaultValue));
    }
    return result;
  }
}
