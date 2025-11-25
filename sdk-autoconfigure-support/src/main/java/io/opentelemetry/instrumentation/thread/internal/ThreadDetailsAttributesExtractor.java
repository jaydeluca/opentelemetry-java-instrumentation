/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.thread.internal;

import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

/**
 * {@link AttributesExtractor} that adds thread details (thread id and name) to spans.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ThreadDetailsAttributesExtractor<REQUEST, RESPONSE>
    implements AttributesExtractor<REQUEST, RESPONSE> {

  // attributes are not stable yet
  public static final AttributeKey<Long> THREAD_ID = longKey("thread.id");
  public static final AttributeKey<String> THREAD_NAME = stringKey("thread.name");

  @Override
  public void onStart(AttributesBuilder attributes, Context parentContext, REQUEST request) {
    Thread currentThread = Thread.currentThread();
    attributes.put(THREAD_ID, currentThread.getId());
    attributes.put(THREAD_NAME, currentThread.getName());
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      REQUEST request,
      @Nullable RESPONSE response,
      @Nullable Throwable error) {}
}
