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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

/**
 * Caps concurrent execution payload envelope requests, in total and per peer, since serving them
 * may require EL calls. Requests over the caps wait in a short bounded queue; only overflow is
 * rejected.
 */
public class ExecutionPayloadEnvelopesRequestLimiter {

  private final int maxConcurrentRequests;
  private final int maxConcurrentRequestsPerPeer;
  private final int maxQueuedRequests;
  private final int maxPendingRequestsPerPeer;

  private final Map<NodeId, Integer> activeRequestsByPeer = new HashMap<>();
  private final Map<NodeId, Integer> pendingRequestsByPeer = new HashMap<>();
  private final Deque<Waiter> queue = new ArrayDeque<>();
  private int activeRequests = 0;

  /**
   * @param maxPendingRequestsPerPeer requests a peer may have served or queued at once
   */
  public ExecutionPayloadEnvelopesRequestLimiter(
      final int maxConcurrentRequests,
      final int maxConcurrentRequestsPerPeer,
      final int maxQueuedRequests,
      final int maxPendingRequestsPerPeer) {
    this.maxConcurrentRequests = maxConcurrentRequests;
    this.maxConcurrentRequestsPerPeer = maxConcurrentRequestsPerPeer;
    this.maxQueuedRequests = maxQueuedRequests;
    this.maxPendingRequestsPerPeer = maxPendingRequestsPerPeer;
  }

  /**
   * Returns a future completing once the request may be served, or empty if it is rejected. Each
   * returned future must be followed by exactly one {@link #release(NodeId)} once it completes.
   */
  public synchronized Optional<SafeFuture<Void>> acquire(final NodeId peerId) {
    final int pending = pendingRequestsByPeer.getOrDefault(peerId, 0);
    if (pending >= maxPendingRequestsPerPeer) {
      return Optional.empty();
    }
    if (canStart(peerId)) {
      start(peerId);
      pendingRequestsByPeer.put(peerId, pending + 1);
      return Optional.of(SafeFuture.COMPLETE);
    }
    if (queue.size() >= maxQueuedRequests) {
      return Optional.empty();
    }
    final Waiter waiter = new Waiter(peerId, new SafeFuture<>());
    queue.add(waiter);
    pendingRequestsByPeer.put(peerId, pending + 1);
    return Optional.of(waiter.admission());
  }

  /**
   * Removes a request that is still waiting, freeing its place. Returns false if it was already
   * admitted, in which case it must be released as usual.
   */
  public synchronized boolean cancel(final NodeId peerId, final SafeFuture<Void> admission) {
    final boolean removed = queue.removeIf(waiter -> waiter.admission().equals(admission));
    if (removed) {
      decrement(pendingRequestsByPeer, peerId);
    }
    return removed;
  }

  public void release(final NodeId peerId) {
    final List<Waiter> admitted = new ArrayList<>();
    synchronized (this) {
      final int active = activeRequestsByPeer.getOrDefault(peerId, 0);
      if (active == 0) {
        // Ignore unmatched releases
        return;
      }
      activeRequests--;
      decrement(activeRequestsByPeer, peerId);
      decrement(pendingRequestsByPeer, peerId);
      final Iterator<Waiter> waiters = queue.iterator();
      while (waiters.hasNext() && activeRequests < maxConcurrentRequests) {
        final Waiter waiter = waiters.next();
        if (canStart(waiter.peerId())) {
          waiters.remove();
          start(waiter.peerId());
          admitted.add(waiter);
        }
      }
    }
    // Complete outside the lock as admitted requests start being served synchronously
    admitted.forEach(waiter -> waiter.admission().complete(null));
  }

  private boolean canStart(final NodeId peerId) {
    return activeRequests < maxConcurrentRequests
        && activeRequestsByPeer.getOrDefault(peerId, 0) < maxConcurrentRequestsPerPeer;
  }

  private void start(final NodeId peerId) {
    activeRequests++;
    activeRequestsByPeer.merge(peerId, 1, Integer::sum);
  }

  private static void decrement(final Map<NodeId, Integer> counts, final NodeId peerId) {
    counts.computeIfPresent(peerId, (__, count) -> count == 1 ? null : count - 1);
  }

  private record Waiter(NodeId peerId, SafeFuture<Void> admission) {}
}
