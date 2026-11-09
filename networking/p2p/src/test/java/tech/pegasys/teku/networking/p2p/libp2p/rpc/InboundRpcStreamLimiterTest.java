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

package tech.pegasys.teku.networking.p2p.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.LIBP2P;
import static tech.pegasys.teku.networking.p2p.libp2p.rpc.InboundRpcStreamLimiter.AcquireResult.ACQUIRED;
import static tech.pegasys.teku.networking.p2p.libp2p.rpc.InboundRpcStreamLimiter.AcquireResult.GLOBAL_LIMIT_REACHED;
import static tech.pegasys.teku.networking.p2p.libp2p.rpc.InboundRpcStreamLimiter.AcquireResult.PEER_LIMIT_REACHED;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

class InboundRpcStreamLimiterTest {

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final NodeId peerA = new MockNodeId(1);
  private final NodeId peerB = new MockNodeId(2);
  private final NodeId peerC = new MockNodeId(3);

  @Test
  void shouldLimitActiveStreamsAndAllowNewStreamsAfterRelease() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(metricsSystem, 2, 2);

    assertThat(limiter.tryAcquire(peerA)).isEqualTo(ACQUIRED);
    assertThat(limiter.tryAcquire(peerB)).isEqualTo(ACQUIRED);
    assertThat(limiter.tryAcquire(peerC)).isEqualTo(GLOBAL_LIMIT_REACHED);
    assertThat(limiter.getActiveStreams()).isEqualTo(2);

    limiter.release(peerA);

    assertThat(limiter.tryAcquire(peerC)).isEqualTo(ACQUIRED);
    assertThat(limiter.getActiveStreams()).isEqualTo(2);
  }

  @Test
  void shouldRejectNonPositiveLimits() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InboundRpcStreamLimiter(metricsSystem, 0, 1));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new InboundRpcStreamLimiter(metricsSystem, 1, 0));
  }

  @Test
  void shouldRejectReleaseWithoutAcquisition() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(metricsSystem, 1, 1);

    assertThatIllegalStateException().isThrownBy(() -> limiter.release(peerA));
    assertThat(limiter.getActiveStreams()).isZero();
  }

  @Test
  void shouldCapStreamsPerPeerBelowTheGlobalLimit() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(metricsSystem, 10, 2);

    assertThat(limiter.tryAcquire(peerA)).isEqualTo(ACQUIRED);
    assertThat(limiter.tryAcquire(peerA)).isEqualTo(ACQUIRED);
    assertThat(limiter.tryAcquire(peerA)).isEqualTo(PEER_LIMIT_REACHED);
    assertThat(limiter.tryAcquire(peerB)).isEqualTo(ACQUIRED);

    assertThat(limiter.getActiveStreams()).isEqualTo(3);
    assertThat(limiter.getActiveStreams(peerA)).isEqualTo(2);
    assertCounterValue("rpc_inbound_streams_rejected_total", "peer_limit", 1);
  }

  @Test
  void shouldNotCountStreamRejectedByPeerLimitAgainstGlobalLimit() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(metricsSystem, 2, 1);

    assertThat(limiter.tryAcquire(peerA)).isEqualTo(ACQUIRED);
    assertThat(limiter.tryAcquire(peerA)).isEqualTo(PEER_LIMIT_REACHED);
    assertThat(limiter.getActiveStreams()).isOne();
    assertThat(limiter.tryAcquire(peerB)).isEqualTo(ACQUIRED);
  }

  @Test
  void shouldReportGlobalLimitBeforePeerLimit() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(metricsSystem, 1, 5);

    assertThat(limiter.tryAcquire(peerA)).isEqualTo(ACQUIRED);
    assertThat(limiter.tryAcquire(peerB)).isEqualTo(GLOBAL_LIMIT_REACHED);

    assertCounterValue("rpc_inbound_streams_rejected_total", "global_limit", 1);
    assertCounterValue("rpc_inbound_streams_rejected_total", "peer_limit", 0);
  }

  @Test
  void shouldCountExpiredStreamsByDeadline() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(metricsSystem, 10, 2);

    limiter.recordLifetimeExpired();
    limiter.recordFinGraceExpired();
    limiter.recordFinGraceExpired();

    assertCounterValue("rpc_inbound_streams_expired_total", "lifetime", 1);
    assertCounterValue("rpc_inbound_streams_expired_total", "fin_grace", 2);
  }

  @Test
  void shouldForgetPeerOnceAllItsStreamsAreReleased() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(metricsSystem, 10, 2);
    limiter.tryAcquire(peerA);
    limiter.tryAcquire(peerA);

    limiter.release(peerA);
    limiter.release(peerA);

    assertThat(limiter.getActiveStreams(peerA)).isZero();
    assertThat(limiter.getActiveStreams()).isZero();
    assertThatIllegalStateException().isThrownBy(() -> limiter.release(peerA));
    // a reconnecting peer starts from zero
    assertThat(limiter.tryAcquire(peerA)).isEqualTo(ACQUIRED);
    assertThat(limiter.tryAcquire(peerA)).isEqualTo(ACQUIRED);
  }

  @Test
  void shouldExposeActiveStreamsAsGauge() {
    final InboundRpcStreamLimiter limiter = new InboundRpcStreamLimiter(metricsSystem, 10, 2);

    limiter.tryAcquire(peerA);

    assertThat(metricsSystem.getGauge(LIBP2P, "rpc_inbound_streams_active").getValue())
        .isEqualTo(1.0);
  }

  private void assertCounterValue(
      final String metricName, final String reason, final long expectedValue) {
    assertThat(metricsSystem.getLabelledCounterValue(LIBP2P, metricName, reason))
        .isEqualTo(expectedValue);
  }
}
