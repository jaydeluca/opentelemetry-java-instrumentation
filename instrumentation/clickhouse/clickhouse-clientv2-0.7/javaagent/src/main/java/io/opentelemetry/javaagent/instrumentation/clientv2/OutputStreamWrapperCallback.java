/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.clientv2;

import java.io.IOException;
import java.io.OutputStream;
import org.apache.hc.core5.io.IOCallback;

public class OutputStreamWrapperCallback implements IOCallback<OutputStream> {
  private final IOCallback<OutputStream> delegate;
  private final OutputStreamWrapper wrappedStream;

  public OutputStreamWrapperCallback(
      IOCallback<OutputStream> delegate, OutputStreamWrapper wrappedStream) {
    this.delegate = delegate;
    this.wrappedStream = wrappedStream;
  }

  @Override
  public void execute(OutputStream outputStream) throws IOException {
    delegate.execute(wrappedStream);
  }
}
