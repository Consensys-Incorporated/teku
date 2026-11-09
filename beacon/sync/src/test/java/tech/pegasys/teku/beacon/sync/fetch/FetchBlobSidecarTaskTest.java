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

package tech.pegasys.teku.beacon.sync.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class FetchBlobSidecarTaskTest extends AbstractFetchTaskTest {

  @Test
  public void run_successful() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    final FetchBlobSidecarTask task = new FetchBlobSidecarTask(eth2P2PNetwork, blobIdentifier);
    assertThat(task.getKey()).isEqualTo(blobIdentifier);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));

    final SafeFuture<FetchResult<BlobSidecar>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(blobSidecar);
  }

  @Test
  public void run_noPeers() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    final FetchBlobSidecarTask task = new FetchBlobSidecarTask(eth2P2PNetwork, blobIdentifier);

    final SafeFuture<FetchResult<BlobSidecar>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).isEmpty();
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.NO_AVAILABLE_PEERS);
  }

  @Test
  public void run_failAndRetryWithNoNewPeers() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    final FetchBlobSidecarTask task = new FetchBlobSidecarTask(eth2P2PNetwork, blobIdentifier);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final SafeFuture<FetchResult<BlobSidecar>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Retry
    final SafeFuture<FetchResult<BlobSidecar>> result2 = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult2 = result2.getNow(null);
    assertThat(fetchResult2.getPeer()).isEmpty();
    assertThat(fetchResult2.isSuccessful()).isFalse();
    assertThat(fetchResult2.getStatus()).isEqualTo(Status.NO_AVAILABLE_PEERS);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);
  }

  @Test
  public void run_failAndRetryWithNewPeer() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    final FetchBlobSidecarTask task = new FetchBlobSidecarTask(eth2P2PNetwork, blobIdentifier);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final SafeFuture<FetchResult<BlobSidecar>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Add another peer
    final Eth2Peer peer2 = registerNewPeer(2);
    when(peer2.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));

    // Retry
    final SafeFuture<FetchResult<BlobSidecar>> result2 = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult2 = result2.getNow(null);
    assertThat(fetchResult2.getPeer()).hasValue(peer2);
    assertThat(fetchResult2.isSuccessful()).isTrue();
    assertThat(fetchResult2.getResult()).hasValue(blobSidecar);
    assertThat(task.getNumberOfRetries()).isEqualTo(1);
  }

  @Test
  public void run_withMultiplesPeersAvailable() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    final FetchBlobSidecarTask task = new FetchBlobSidecarTask(eth2P2PNetwork, blobIdentifier);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));
    when(peer.getOutstandingRequests()).thenReturn(1);
    // Add another peer
    final Eth2Peer peer2 = registerNewPeer(2);
    when(peer2.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));
    when(peer2.getOutstandingRequests()).thenReturn(0);

    // We should choose the peer that is less busy, which successfully returns the blob sidecar
    final SafeFuture<FetchResult<BlobSidecar>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer2);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(blobSidecar);
  }

  @Test
  public void run_withPreferredPeer() {
    final Eth2Peer preferredPeer = createNewPeer(1);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    when(preferredPeer.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));

    final FetchBlobSidecarTask task =
        new FetchBlobSidecarTask(eth2P2PNetwork, Optional.of(preferredPeer), blobIdentifier);

    // Add a peer
    registerNewPeer(2);

    final SafeFuture<FetchResult<BlobSidecar>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(preferredPeer);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(blobSidecar);
  }

  @Test
  public void run_withRandomPeerWhenFetchingWithPreferredPeerFails() {
    final Eth2Peer preferredPeer = createNewPeer(1);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    when(preferredPeer.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final FetchBlobSidecarTask task =
        new FetchBlobSidecarTask(eth2P2PNetwork, Optional.of(preferredPeer), blobIdentifier);

    final SafeFuture<FetchResult<BlobSidecar>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(preferredPeer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Add a peer
    final Eth2Peer peer = registerNewPeer(2);
    when(peer.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));

    // Retry
    final SafeFuture<FetchResult<BlobSidecar>> result2 = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult2 = result2.getNow(null);
    assertThat(fetchResult2.getPeer()).hasValue(peer);
    assertThat(fetchResult2.isSuccessful()).isTrue();
    assertThat(fetchResult2.getResult()).hasValue(blobSidecar);
    assertThat(task.getNumberOfRetries()).isEqualTo(1);
  }

  @Test
  public void cancel() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());
    final FetchBlobSidecarTask task = new FetchBlobSidecarTask(eth2P2PNetwork, blobIdentifier);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlobSidecarByRoot(blobIdentifier))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));

    task.cancel();
    final SafeFuture<FetchResult<BlobSidecar>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<BlobSidecar> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).isEmpty();
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.CANCELLED);
  }
}
