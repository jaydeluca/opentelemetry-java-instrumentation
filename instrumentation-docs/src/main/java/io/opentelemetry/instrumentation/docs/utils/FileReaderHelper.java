/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class FileReaderHelper {

  public BufferedReader getBufferedReader(String filePath) throws IOException {
    return Files.newBufferedReader(Paths.get(filePath), UTF_8);
  }

  public List<String> readAllLines(String filePath) throws IOException {
    return Files.readAllLines(Paths.get(filePath), UTF_8);
  }
}
