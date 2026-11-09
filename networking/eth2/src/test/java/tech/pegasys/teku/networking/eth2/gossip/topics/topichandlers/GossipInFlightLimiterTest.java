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

package tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;

class GossipInFlightLimiterTest {
  private static final String TOPIC = "beacon_block";
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final GossipInFlightLimiter limiter =
      new GossipInFlightLimiter(metricsSystem, TOPIC, 100);

  @Test
  void shouldAdmitMessagesUpToLimit() {
    assertThat(limiter.tryAcquire(60)).isTrue();
    assertThat(limiter.tryAcquire(40)).isTrue();
    assertThat(limiter.getInFlightBytes()).isEqualTo(100);
    assertThat(getDiscardedCount()).isZero();
  }

  @Test
  void shouldDiscardMessageThatWouldExceedLimit() {
    assertThat(limiter.tryAcquire(60)).isTrue();

    assertThat(limiter.tryAcquire(41)).isFalse();

    assertThat(limiter.getInFlightBytes()).isEqualTo(60);
    assertThat(getDiscardedCount()).isEqualTo(1);
    // a smaller message still fits
    assertThat(limiter.tryAcquire(40)).isTrue();
  }

  @Test
  void shouldDiscardMessageLargerThanLimitEvenWhenNothingIsInFlight() {
    assertThat(limiter.tryAcquire(101)).isFalse();
    assertThat(limiter.getInFlightBytes()).isZero();
  }

  @Test
  void shouldAdmitMessagesAgainOnceReleased() {
    assertThat(limiter.tryAcquire(100)).isTrue();
    assertThat(limiter.tryAcquire(1)).isFalse();

    limiter.release(100);

    assertThat(limiter.getInFlightBytes()).isZero();
    assertThat(limiter.tryAcquire(100)).isTrue();
  }

  @Test
  void shouldCountDiscardsSinceLastReport() {
    assertThat(limiter.tryAcquire(100)).isTrue();
    assertThat(limiter.tryAcquire(1)).isFalse();
    assertThat(limiter.tryAcquire(1)).isFalse();

    assertThat(limiter.takeDiscardedSinceLastReport()).isEqualTo(2);
    assertThat(limiter.takeDiscardedSinceLastReport()).isZero();

    assertThat(limiter.tryAcquire(1)).isFalse();
    assertThat(limiter.takeDiscardedSinceLastReport()).isEqualTo(1);
    assertThat(getDiscardedCount()).isEqualTo(3);
  }

  @Test
  void unlimitedShouldAdmitEverythingWithoutTracking() {
    final GossipInFlightLimiter unlimited = GossipInFlightLimiter.UNLIMITED;

    assertThat(unlimited.tryAcquire(Long.MAX_VALUE)).isTrue();
    assertThat(unlimited.tryAcquire(Long.MAX_VALUE)).isTrue();
    assertThat(unlimited.getInFlightBytes()).isZero();

    unlimited.release(Long.MAX_VALUE);
    assertThat(unlimited.getInFlightBytes()).isZero();
    assertThat(unlimited.takeDiscardedSinceLastReport()).isZero();
  }

  @Test
  void shouldRejectNonPositiveLimit() {
    assertThatThrownBy(() -> new GossipInFlightLimiter(metricsSystem, TOPIC, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private long getDiscardedCount() {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.NETWORK, "gossip_messages_in_flight_limit_discarded_total", TOPIC);
  }
}
