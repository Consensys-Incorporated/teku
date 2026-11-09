/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.infrastructure.time;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import org.apache.commons.lang3.tuple.Pair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PerformanceTracker {

  private final TimeProvider timeProvider;
  protected final List<Pair<String, UInt64>> events =
      Collections.synchronizedList(new ArrayList<>());

  public PerformanceTracker(final TimeProvider timeProvider) {
    this.timeProvider = timeProvider;
  }

  public UInt64 addEvent(final String label, final UInt64 timestamp) {
    events.add(Pair.of(label, timestamp));
    return timestamp;
  }

  public UInt64 addEvent(final String label) {
    final UInt64 timestamp = timeProvider.getTimeInMillis();
    return addEvent(label, timestamp);
  }

  public void report(
      final UInt64 startTime,
      final boolean isLateEvent,
      final EventReporter eventReporter,
      final Consumer<UInt64> totalDurationReporter,
      final SlowEventReporter slowEventReporter) {

    final List<String> eventTimings = new ArrayList<>();

    UInt64 previousEventTimestamp = startTime;
    for (Pair<String, UInt64> event : events) {

      // minusMinZero because sometimes time does actually go backwards so be safe.
      final UInt64 stepDuration = event.getRight().minusMinZero(previousEventTimestamp);
      previousEventTimestamp = event.getRight();

      eventReporter.report(event, stepDuration);

      if (isLateEvent) {
        eventTimings.add(
            event.getLeft() + (eventTimings.isEmpty() ? " " : " +") + stepDuration + "ms");
      }
    }

    final UInt64 totalProcessingDuration =
        events.getLast().getRight().minusMinZero(events.getFirst().getRight());
    totalDurationReporter.accept(totalProcessingDuration);

    if (isLateEvent) {
      final String combinedTimings = String.join(", ", eventTimings);
      slowEventReporter.report(totalProcessingDuration, combinedTimings);
    }
  }

  public interface EventReporter {
    void report(Pair<String, UInt64> event, UInt64 stepDuration);
  }

  public interface SlowEventReporter {
    void report(UInt64 totalDuration, String eventTimings);
  }
}
