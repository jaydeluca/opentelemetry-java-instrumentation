/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import javax.annotation.Nullable;

/**
 * Holds a reference to the RecordingConfigProvider that is installed during agent initialization.
 * This allows test code to access the recorded configuration usages after tests complete.
 *
 * <p>This is a simple holder class since the RecordingConfigProvider is created in the agent
 * classloader but needs to be accessible from the test classloader.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ConfigRecordingAgentListener {

  @Nullable private static volatile Object recordingProvider;

  /**
   * Gets the currently installed RecordingConfigProvider, or null if not installed or not in
   * metadata collection mode.
   *
   * <p>Note: Returns Object to avoid classloader issues. Cast to RecordingConfigProvider in calling
   * code.
   */
  @Nullable
  public static Object getRecordingProvider() {
    return recordingProvider;
  }

  /**
   * Sets the RecordingConfigProvider. This is called during agent initialization when metadata
   * collection is enabled.
   */
  public static void setRecordingProvider(Object provider) {
    recordingProvider = provider;
  }

  /** Clears the recording provider reference. Can be called to reset state between test runs. */
  public static void reset() {
    recordingProvider = null;
  }

  private ConfigRecordingAgentListener() {}
}
