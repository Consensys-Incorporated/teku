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

package tech.pegasys.teku.networking.eth2.peers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;

public class RateTrackerTest {

  StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
  private final AtomicInteger requestId = new AtomicInteger(0);

  @BeforeEach
  public void setUp() {
    requestId.set(0);
  }

  @Test
  public void shouldAllowAddingItemsWithinLimit() {
    final RateTracker tracker = createTracker(1, 1);
    final long objectsCount = 1;
    assertRequestsAllowed(tracker.generateRequestKey(objectsCount), timeProvider);
  }

  @Test
  public void shouldNotUnderflowWhenTimeWindowGreaterThanCurrentTime() {
    final RateTracker tracker = createTracker(1, 15000);
    final long objectsCount = 1;
    assertRequestsAllowed(tracker.generateRequestKey(objectsCount), timeProvider);
  }

  @Test
  public void shouldAllowAddingItemsAfterTimeoutPasses() {
    final RateTracker tracker = createTracker(1, 1);
    long objectsCount = 1;
    assertRequestsAllowed(tracker.generateRequestKey(objectsCount), timeProvider);
    timeProvider.advanceTimeBySeconds(2L);
    assertRequestsAllowed(tracker.generateRequestKey(objectsCount), timeProvider);
  }

  @Test
  public void shouldReturnFalseIfCacheFull() {
    final RateTracker tracker = createTracker(1, 1);
    final long objectsCount = 1;
    assertRequestsAllowed(tracker.generateRequestKey(objectsCount), timeProvider);
    assertThat(tracker.generateRequestKey(objectsCount)).isEmpty();
  }

  @Test
  public void shouldAddMultipleValuesToCache() {
    final RateTracker tracker = createTracker(10, 1);
    long objectsCount = 10;
    assertRequestsAllowed(tracker.generateRequestKey(objectsCount), timeProvider);
    assertThat(tracker.generateRequestKey(objectsCount)).isEmpty();

    timeProvider.advanceTimeBySeconds(2L);

    assertRequestsAllowed(tracker.generateRequestKey(1), timeProvider);
  }

  @Test
  void canAdjustRemainingRequests() {
    final RateTracker tracker = createTracker(10, 1);
    Optional<RequestKey> maybeRequestKey = tracker.generateRequestKey(10L);
    assertThat(maybeRequestKey).isPresent();
    tracker.adjustRequestObjectCount(maybeRequestKey.get(), 5L);
    assertThat(tracker.getAvailableObjectCount()).isEqualTo(5L);
  }

  private void assertRequestsAllowed(
      final Optional<RequestKey> maybeRequestKey, final StubTimeProvider timeProvider) {
    assertThat(maybeRequestKey).isPresent();
    assertThat(maybeRequestKey.get())
        .isEqualTo(new RequestKey(timeProvider.getTimeInSeconds(), requestId.getAndIncrement()));
  }

  private RateTracker createTracker(final int peerRateLimit, final int timeoutSeconds) {
    return RateTracker.create(peerRateLimit, timeoutSeconds, timeProvider, "");
  }
}
