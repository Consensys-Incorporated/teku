/*
 * Copyright Consensys Software Inc., 2026
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
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class FetchBlockTaskTest extends AbstractFetchTaskTest {

  @Test
  public void run_successful() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    final FetchBlockTask task = new FetchBlockTask(eth2P2PNetwork, blockRoot);
    assertThat(task.getKey()).isEqualTo(blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(block);
  }

  @Test
  public void run_emptyResponseFails() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final FetchBlockTask task = new FetchBlockTask(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(fetchResult.getResult()).isEmpty();
  }

  @Test
  public void run_wrongBlockRootFailsAndCanRetry() {
    final SignedBeaconBlock expectedBlock = dataStructureUtil.randomSignedBeaconBlock(10);
    final SignedBeaconBlock wrongBlock = dataStructureUtil.randomSignedBeaconBlock(11);
    final Bytes32 blockRoot = expectedBlock.getRoot();
    final FetchBlockTask task = new FetchBlockTask(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(wrongBlock)));

    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(fetchResult.getResult()).isEmpty();
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    final Eth2Peer peer2 = registerNewPeer(2);
    when(peer2.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(expectedBlock)));

    final SafeFuture<FetchResult<SignedBeaconBlock>> retryResult = task.run();
    assertThat(retryResult).isDone();
    final FetchResult<SignedBeaconBlock> retryFetchResult = retryResult.getNow(null);
    assertThat(retryFetchResult.getPeer()).hasValue(peer2);
    assertThat(retryFetchResult.isSuccessful()).isTrue();
    assertThat(retryFetchResult.getResult()).hasValue(expectedBlock);
    assertThat(task.getNumberOfRetries()).isEqualTo(1);
  }

  @Test
  public void run_noPeers() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    final FetchBlockTask task = new FetchBlockTask(eth2P2PNetwork, blockRoot);

    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).isEmpty();
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.NO_AVAILABLE_PEERS);
  }

  @Test
  public void run_failAndRetryWithNoNewPeers() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    final FetchBlockTask task = new FetchBlockTask(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Retry
    final SafeFuture<FetchResult<SignedBeaconBlock>> result2 = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult2 = result2.getNow(null);
    assertThat(fetchResult2.getPeer()).isEmpty();
    assertThat(fetchResult2.isSuccessful()).isFalse();
    assertThat(fetchResult2.getStatus()).isEqualTo(Status.NO_AVAILABLE_PEERS);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);
  }

  @Test
  public void run_failAndRetryWithNewPeer() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    final FetchBlockTask task = new FetchBlockTask(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Add another peer
    final Eth2Peer peer2 = registerNewPeer(2);
    when(peer2.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    // Retry
    final SafeFuture<FetchResult<SignedBeaconBlock>> result2 = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult2 = result2.getNow(null);
    assertThat(fetchResult2.getPeer()).hasValue(peer2);
    assertThat(fetchResult2.isSuccessful()).isTrue();
    assertThat(fetchResult2.getResult()).hasValue(block);
    assertThat(task.getNumberOfRetries()).isEqualTo(1);
  }

  @Test
  public void run_withMultiplesPeersAvailable() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    final FetchBlockTask task = new FetchBlockTask(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));
    when(peer.getOutstandingRequests()).thenReturn(1);
    // Add another peer
    final Eth2Peer peer2 = registerNewPeer(2);
    when(peer2.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(peer2.getOutstandingRequests()).thenReturn(0);

    // We should choose the peer that is less busy, which successfully returns the block
    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(peer2);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(block);
  }

  @Test
  public void run_withPreferredPeer() {
    final Eth2Peer preferredPeer = createNewPeer(1);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    when(preferredPeer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    final FetchBlockTask task =
        new FetchBlockTask(eth2P2PNetwork, Optional.of(preferredPeer), blockRoot);

    // Add a peer
    registerNewPeer(2);

    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(preferredPeer);
    assertThat(fetchResult.isSuccessful()).isTrue();
    assertThat(fetchResult.getResult()).hasValue(block);
  }

  @Test
  public void run_withRandomPeerWhenFetchingWithPreferredPeerFails() {
    final Eth2Peer preferredPeer = createNewPeer(1);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    when(preferredPeer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("whoops")));

    final FetchBlockTask task =
        new FetchBlockTask(eth2P2PNetwork, Optional.of(preferredPeer), blockRoot);

    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).hasValue(preferredPeer);
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.FETCH_FAILED);
    assertThat(task.getNumberOfRetries()).isEqualTo(0);

    // Add a peer
    final Eth2Peer peer = registerNewPeer(2);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    // Retry
    final SafeFuture<FetchResult<SignedBeaconBlock>> result2 = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult2 = result2.getNow(null);
    assertThat(fetchResult2.getPeer()).hasValue(peer);
    assertThat(fetchResult2.isSuccessful()).isTrue();
    assertThat(fetchResult2.getResult()).hasValue(block);
    assertThat(task.getNumberOfRetries()).isEqualTo(1);
  }

  @Test
  public void cancel() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);
    final Bytes32 blockRoot = block.getMessage().hashTreeRoot();
    final FetchBlockTask task = new FetchBlockTask(eth2P2PNetwork, blockRoot);

    final Eth2Peer peer = registerNewPeer(1);
    when(peer.requestBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    task.cancel();
    final SafeFuture<FetchResult<SignedBeaconBlock>> result = task.run();
    assertThat(result).isDone();
    final FetchResult<SignedBeaconBlock> fetchResult = result.getNow(null);
    assertThat(fetchResult.getPeer()).isEmpty();
    assertThat(fetchResult.isSuccessful()).isFalse();
    assertThat(fetchResult.getStatus()).isEqualTo(Status.CANCELLED);
  }
}
