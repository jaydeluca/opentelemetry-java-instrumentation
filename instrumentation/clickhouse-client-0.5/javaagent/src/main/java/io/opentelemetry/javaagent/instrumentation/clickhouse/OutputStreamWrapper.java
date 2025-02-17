/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clickhouse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class OutputStreamWrapper extends OutputStream {

  private final OutputStream delegate;
  private final ByteArrayOutputStream buffer;

  public OutputStreamWrapper(OutputStream delegate) {
    this.delegate = delegate;
    this.buffer = new ByteArrayOutputStream();
  }

  @Override
  public void write(int b) throws IOException {
    buffer.write(b);
    delegate.write(b);
  }

  @Override
  public void write(byte[] b) throws IOException {
    buffer.write(b);
    delegate.write(b);
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    buffer.write(b, off, len);
    delegate.write(b, off, len);
  }

  @Override
  public void close() throws IOException {
    delegate.close();
  }

  @Override
  public void flush() throws IOException {
    delegate.flush();
  }

  public String getCapturedData() {
    return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
  }
}
