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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;

class ExecutionPayloadEnvelopesRequestLimiterTest {

  private final NodeId peerA = new MockNodeId(1);
  private final NodeId peerB = new MockNodeId(2);
  private final NodeId peerC = new MockNodeId(3);
  private final NodeId peerD = new MockNodeId(4);

  // 2 in flight in total, 1 per peer, 1 waiting in total, 2 pending (in flight or waiting) per peer
  private final ExecutionPayloadEnvelopesRequestLimiter limiter =
      new ExecutionPayloadEnvelopesRequestLimiter(2, 1, 1, 2);

  @Test
  void shouldAdmitRequestsImmediatelyUpToTheLimit() {
    assertThat(limiter.acquire(peerA))
        .hasValueSatisfying(admission -> assertThat(admission).isCompleted());
    assertThat(limiter.acquire(peerB))
        .hasValueSatisfying(admission -> assertThat(admission).isCompleted());
  }

  @Test
  void shouldQueueRequestBeyondTheLimitAndAdmitItOnRelease() {
    acquireNow(peerA);
    acquireNow(peerB);

    final SafeFuture<Void> queued = limiter.acquire(peerC).orElseThrow();
    assertThat(queued).isNotDone();

    limiter.release(peerA);

    assertThat(queued).isCompleted();
  }

  @Test
  void shouldQueueRequestBeyondThePeerShareEvenWhenTheLimitHasRoom() {
    acquireNow(peerA);

    final SafeFuture<Void> queued = limiter.acquire(peerA).orElseThrow();
    assertThat(queued).isNotDone();
    // Another peer is still admitted straight away
    assertThat(limiter.acquire(peerB))
        .hasValueSatisfying(admission -> assertThat(admission).isCompleted());

    limiter.release(peerA);

    assertThat(queued).isCompleted();
  }

  @Test
  void shouldAdmitQueuedRequestOfAnotherPeerWhenTheFirstQueuedPeerIsAtItsShare() {
    final ExecutionPayloadEnvelopesRequestLimiter limiter =
        new ExecutionPayloadEnvelopesRequestLimiter(2, 1, 2, 2);
    acquireNow(limiter, peerA);
    acquireNow(limiter, peerB);
    final SafeFuture<Void> queuedForA = limiter.acquire(peerA).orElseThrow();
    final SafeFuture<Void> queuedForC = limiter.acquire(peerC).orElseThrow();

    // Peer A is still at its share, so the slot freed by B goes to C
    limiter.release(peerB);

    assertThat(queuedForA).isNotDone();
    assertThat(queuedForC).isCompleted();
  }

  @Test
  void shouldRejectRequestWhenTheQueueIsFull() {
    acquireNow(peerA);
    acquireNow(peerB);
    assertThat(limiter.acquire(peerC)).isPresent();

    assertThat(limiter.acquire(peerD)).isEmpty();
  }

  @Test
  void shouldRejectRequestWhenThePeerHasTooManyPendingRequests() {
    final ExecutionPayloadEnvelopesRequestLimiter limiter =
        new ExecutionPayloadEnvelopesRequestLimiter(2, 1, 10, 2);
    acquireNow(limiter, peerA);
    assertThat(limiter.acquire(peerA)).isPresent();

    assertThat(limiter.acquire(peerA)).isEmpty();
    assertThat(limiter.acquire(peerB)).isPresent();
  }

  @Test
  void shouldRemoveCancelledRequestFromTheQueue() {
    acquireNow(peerA);
    acquireNow(peerB);
    final SafeFuture<Void> queued = limiter.acquire(peerC).orElseThrow();

    assertThat(limiter.cancel(peerC, queued)).isTrue();
    // Its queue slot is free again
    assertThat(limiter.acquire(peerD)).isPresent();
  }

  @Test
  void shouldFreeThePeersPendingAllowanceWhenItsQueuedRequestIsCancelled() {
    final ExecutionPayloadEnvelopesRequestLimiter limiter =
        new ExecutionPayloadEnvelopesRequestLimiter(2, 1, 10, 2);
    acquireNow(limiter, peerA);
    final SafeFuture<Void> queued = limiter.acquire(peerA).orElseThrow();
    assertThat(limiter.acquire(peerA)).isEmpty();

    limiter.cancel(peerA, queued);

    assertThat(limiter.acquire(peerA)).isPresent();
  }

  @Test
  void shouldNotAdmitCancelledRequestOnRelease() {
    acquireNow(peerA);
    acquireNow(peerB);
    final SafeFuture<Void> queued = limiter.acquire(peerC).orElseThrow();
    limiter.cancel(peerC, queued);

    limiter.release(peerA);

    // The freed permit is still available
    acquireNow(peerD);
  }

  @Test
  void shouldNotCancelRequestAlreadyAdmitted() {
    acquireNow(peerA);
    acquireNow(peerB);
    final SafeFuture<Void> queued = limiter.acquire(peerC).orElseThrow();
    limiter.release(peerA);

    assertThat(limiter.cancel(peerC, queued)).isFalse();

    assertThat(queued).isCompleted();
    // Still counted as served until it is released
    assertThat(limiter.acquire(peerD).orElseThrow()).isNotDone();
  }

  @Test
  void shouldIgnoreReleaseWithoutMatchingAcquisition() {
    limiter.release(peerA);

    acquireNow(peerA);
    acquireNow(peerB);
    assertThat(limiter.acquire(peerC).orElseThrow()).isNotDone();
  }

  @Test
  void shouldNotLetAnotherPeersReleaseFreeAPeersShare() {
    acquireNow(peerA);
    final SafeFuture<Void> queued = limiter.acquire(peerA).orElseThrow();

    limiter.release(peerB);

    assertThat(queued).isNotDone();
  }

  private void acquireNow(final NodeId peerId) {
    acquireNow(limiter, peerId);
  }

  private static void acquireNow(
      final ExecutionPayloadEnvelopesRequestLimiter limiter, final NodeId peerId) {
    final Optional<SafeFuture<Void>> admission = limiter.acquire(peerId);
    assertThat(admission).isPresent();
    assertThat(admission.get()).isCompleted();
  }
}
