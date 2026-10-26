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

import java.util.HashMap;
import java.util.Map;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

/**
 * Limits the number of active inbound RPC streams across every transport and connection, and the
 * share of those a single peer can hold. Also counts the inbound streams closed by a deadline, so
 * every {@code rpc_inbound_streams_*} metric lives here.
 */
public class InboundRpcStreamLimiter {

  public enum AcquireResult {
    ACQUIRED,
    GLOBAL_LIMIT_REACHED,
    PEER_LIMIT_REACHED
  }

  private final int maximumActiveStreams;
  private final int maximumActiveStreamsPerPeer;
  private final Map<NodeId, Integer> activeStreamsPerPeer = new HashMap<>();
  private final Counter globalLimitRejectedCounter;
  private final Counter peerLimitRejectedCounter;
  private final Counter lifetimeExpiredCounter;
  private final Counter finGraceExpiredCounter;
  private int activeStreams = 0;

  public InboundRpcStreamLimiter(
      final MetricsSystem metricsSystem,
      final int maximumActiveStreams,
      final int maximumActiveStreamsPerPeer) {
    if (maximumActiveStreams < 1 || maximumActiveStreamsPerPeer < 1) {
      throw new IllegalArgumentException("Maximum active inbound RPC streams must be positive");
    }
    this.maximumActiveStreams = maximumActiveStreams;
    this.maximumActiveStreamsPerPeer = maximumActiveStreamsPerPeer;

    metricsSystem.createGauge(
        TekuMetricCategory.LIBP2P,
        "rpc_inbound_streams_active",
        "Inbound RPC streams currently holding a permit",
        this::getActiveStreams);
    final LabelledMetric<Counter> rejectedCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P,
            "rpc_inbound_streams_rejected_total",
            "Inbound RPC streams rejected because an active stream limit was reached",
            "reason");
    globalLimitRejectedCounter = rejectedCounter.labels("global_limit");
    peerLimitRejectedCounter = rejectedCounter.labels("peer_limit");
    final LabelledMetric<Counter> expiredCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.LIBP2P,
            "rpc_inbound_streams_expired_total",
            "Inbound RPC streams closed because they outlived a deadline",
            "reason");
    lifetimeExpiredCounter = expiredCounter.labels("lifetime");
    finGraceExpiredCounter = expiredCounter.labels("fin_grace");
  }

  public synchronized AcquireResult tryAcquire(final NodeId peer) {
    if (activeStreams >= maximumActiveStreams) {
      globalLimitRejectedCounter.inc();
      return AcquireResult.GLOBAL_LIMIT_REACHED;
    }
    final int peerStreams = activeStreamsPerPeer.getOrDefault(peer, 0);
    if (peerStreams >= maximumActiveStreamsPerPeer) {
      peerLimitRejectedCounter.inc();
      return AcquireResult.PEER_LIMIT_REACHED;
    }
    activeStreamsPerPeer.put(peer, peerStreams + 1);
    activeStreams++;
    return AcquireResult.ACQUIRED;
  }

  public synchronized void release(final NodeId peer) {
    final Integer peerStreams = activeStreamsPerPeer.get(peer);
    if (peerStreams == null) {
      throw new IllegalStateException("Released an inbound RPC stream that was not acquired");
    }
    if (peerStreams == 1) {
      activeStreamsPerPeer.remove(peer);
    } else {
      activeStreamsPerPeer.put(peer, peerStreams - 1);
    }
    activeStreams--;
  }

  public void recordLifetimeExpired() {
    lifetimeExpiredCounter.inc();
  }

  public void recordFinGraceExpired() {
    finGraceExpiredCounter.inc();
  }

  synchronized int getActiveStreams() {
    return activeStreams;
  }

  synchronized int getActiveStreams(final NodeId peer) {
    return activeStreamsPerPeer.getOrDefault(peer, 0);
  }
}
