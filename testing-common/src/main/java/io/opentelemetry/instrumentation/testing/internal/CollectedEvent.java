/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.internal;

import io.opentelemetry.api.internal.InternalAttributeKeyImpl;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Accumulates the documented shape of a single event (a log record carrying an event name) across
 * all the times it was emitted during a test class: its severity and the union of its attributes.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class CollectedEvent {

  @Nullable private String severity;
  private final Set<InternalAttributeKeyImpl<?>> attributeKeys = new LinkedHashSet<>();

  /**
   * Records the severity of an emission. The first non-null severity seen wins; severity is fixed
   * per emission site, so later emissions cannot disagree in practice.
   */
  public void setSeverityIfAbsent(String severity) {
    if (this.severity == null) {
      this.severity = severity;
    }
  }

  @Nullable
  public String getSeverity() {
    return severity;
  }

  public void addAttributeKey(InternalAttributeKeyImpl<?> key) {
    attributeKeys.add(key);
  }

  /** Returns the union of the attribute keys seen on this event, each carrying its own type. */
  public Set<InternalAttributeKeyImpl<?>> getAttributeKeys() {
    return attributeKeys;
  }
}
