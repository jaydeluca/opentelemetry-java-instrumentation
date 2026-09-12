/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.parsers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.opentelemetry.instrumentation.docs.internal.EmittedEvents;
import io.opentelemetry.instrumentation.docs.internal.InstrumentationModule;
import io.opentelemetry.instrumentation.docs.internal.TelemetryAttribute;
import io.opentelemetry.instrumentation.docs.utils.FileManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * This class is responsible for parsing event files from the `.telemetry` directory of an
 * instrumentation module and filtering them by scope.
 */
public class EventParser {

  /**
   * Pull events from the `.telemetry` directory, filter them by scope, and return them keyed by the
   * `when` condition.
   *
   * @param module the instrumentation module to extract events for
   * @param fileManager the file manager to access the filesystem
   * @throws JsonProcessingException if there is an error processing the JSON from the event files
   */
  public static Map<String, List<EmittedEvents.Event>> getEvents(
      InstrumentationModule module, FileManager fileManager) throws JsonProcessingException {
    Map<String, EmittedEvents> events =
        EmittedEventParser.getEventsByScopeFromFiles(fileManager.rootDir(), module.getSrcPath());

    if (events.isEmpty()) {
      return new HashMap<>();
    }

    return filterEventsByScope(events, module.getScopeInfo().getName());
  }

  /**
   * Filters events by scope and aggregates attributes for each event name.
   *
   * @param eventsByScope the map of events by scope
   * @param scopeName the name of the scope to filter events for
   * @return a map of filtered events by `when`
   */
  private static Map<String, List<EmittedEvents.Event>> filterEventsByScope(
      Map<String, EmittedEvents> eventsByScope, String scopeName) {

    Map<String, List<EmittedEvents.Event>> result = new HashMap<>();

    for (Map.Entry<String, EmittedEvents> entry : eventsByScope.entrySet()) {
      EmittedEvents events = entry.getValue();
      if (events == null || events.getEventsByScope() == null) {
        continue;
      }

      Map<String, AggregatedEvent> eventsByName = new HashMap<>();
      for (EmittedEvents.EventsByScope scopeEvents : events.getEventsByScope()) {
        if (!TelemetryParser.scopeIsValid(scopeEvents.getScope(), scopeName)) {
          continue;
        }
        for (EmittedEvents.Event event : scopeEvents.getEvents()) {
          AggregatedEvent aggregated =
              eventsByName.computeIfAbsent(event.getName(), n -> new AggregatedEvent());
          aggregated.severityIfAbsent(event.getSeverity());
          addEventAttributes(event, aggregated.attributes);
        }
      }

      if (eventsByName.isEmpty()) {
        continue;
      }

      List<EmittedEvents.Event> filteredEvents = new ArrayList<>();
      for (Map.Entry<String, AggregatedEvent> eventEntry : eventsByName.entrySet()) {
        AggregatedEvent aggregated = eventEntry.getValue();
        filteredEvents.add(
            new EmittedEvents.Event(
                eventEntry.getKey(), aggregated.severity, new ArrayList<>(aggregated.attributes)));
      }
      result.put(events.getWhen(), filteredEvents);
    }

    return result;
  }

  private static void addEventAttributes(
      EmittedEvents.Event event, Set<TelemetryAttribute> attributes) {
    if (event.getAttributes() == null) {
      return;
    }

    for (TelemetryAttribute attr : event.getAttributes()) {
      if (!TelemetryParser.isExcludedAttribute(attr.getName())) {
        attributes.add(new TelemetryAttribute(attr.getName(), attr.getType()));
      }
    }
  }

  /** Accumulates the severity and the union of attributes seen for one event name. */
  private static class AggregatedEvent {
    @Nullable String severity;
    final Set<TelemetryAttribute> attributes = new HashSet<>();

    void severityIfAbsent(@Nullable String severity) {
      if (this.severity == null) {
        this.severity = severity;
      }
    }
  }

  private EventParser() {}
}
