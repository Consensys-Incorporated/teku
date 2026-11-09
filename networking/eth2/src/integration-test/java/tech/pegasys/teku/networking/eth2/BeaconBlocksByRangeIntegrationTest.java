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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.LengthOutOfBoundsException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.UnrecognizedContextBytesException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;

public class BeaconBlocksByRangeIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @BeforeEach
  void setUp() {
    setUp(SpecMilestone.PHASE0, Optional.empty());
  }

  @Test
  public void shouldSendEmptyResponsePreGenesisEvent() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> response = requestBlocksByRange(peer);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> response = requestBlocksByRange(peer);
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldSendEmptyResponseWhenCountIsZero() throws Exception {
    final Eth2Peer peer = createPeer();

    final SignedBlockAndState block = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block);

    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer.requestBlocksByRange(UInt64.ONE, UInt64.ZERO, RpcResponseListener.from(blocks::add)));
    waitFor(() -> assertThat(peer.getOutstandingRequests()).isEqualTo(0));
    assertThat(blocks).isEmpty();
  }

  @Test
  public void shouldRespondWithBlocksFromCanonicalChain() throws Exception {
    final Eth2Peer peer = createPeer();

    final SignedBlockAndState block1 = peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    final List<SignedBeaconBlock> response = requestBlocksByRange(peer);
    assertThat(response).containsExactly(block1.getBlock(), block2.getBlock());
  }

  @Test
  public void shouldRespondWithSingleHotBlockWhenCountIsOne() throws Exception {
    // Regression test for #10985: a count=1 request for an available (hot) slot must return the
    // block at that slot rather than an empty response. This is the exact scenario from the issue:
    // start_slot = <available hot block slot>, count = 1, step = 1.
    final Eth2Peer peer = createPeer();

    peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState headBlock = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(headBlock);

    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer.requestBlocksByRange(
            headBlock.getSlot(), UInt64.ONE, RpcResponseListener.from(blocks::add)));
    waitFor(() -> assertThat(peer.getOutstandingRequests()).isEqualTo(0));

    assertThat(blocks).containsExactly(headBlock.getBlock());
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnectedImmediately() {
    final Eth2Peer peer = createPeer();

    // Setup chain
    peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    peer.disconnectImmediately(Optional.empty(), false);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlocksByRangeAfterPeerDisconnected() throws Exception {
    final Eth2Peer peer = createPeer();

    // Setup chain
    peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlockBySlotAfterPeerDisconnectedImmediately() {
    final Eth2Peer peer = createPeer();

    // Setup chain
    peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    peer.disconnectImmediately(Optional.empty(), false);
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockBySlot(UInt64.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void requestBlockBySlotAfterPeerDisconnected() throws Exception {
    final Eth2Peer peer = createPeer();

    // Setup chain
    peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockBySlot(UInt64.ONE);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  void requestBlockBySlot_shouldReturnEmptyWhenBlockIsNotKnown() throws Exception {
    final Eth2Peer peer = createPeer();

    final Optional<SignedBeaconBlock> result =
        waitFor(peer.requestBlockBySlot(UInt64.valueOf(49382982)));
    assertThat(result).isEmpty();
  }

  @ParameterizedTest(name = "{0} => {1}, nextSpecEnabledLocally={2}, nextSpecEnabledRemotely={3}")
  @MethodSource("generateSpecTransitionWithCombinationParams")
  public void requestBlockByRange_withDisparateVersionsEnabled_requestBaseMilestoneBlocks(
      final SpecMilestone baseMilestone,
      final SpecMilestone nextMilestone,
      final boolean nextSpecEnabledLocally,
      final boolean nextSpecEnabledRemotely)
      throws Exception {
    setUp(baseMilestone, Optional.of(nextMilestone));
    final Eth2Peer peer = createPeer(nextSpecEnabledLocally, nextSpecEnabledRemotely);

    // Setup chain
    final SignedBlockAndState block1 = peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();

    final Class<?> expectedBody = milestoneToBeaconBlockBodyClass(baseMilestone);

    peerStorage.chainUpdater().updateBestBlock(block2);
    assertThat(block1.getBlock().getMessage().getBody()).isInstanceOf(expectedBody);
    assertThat(block2.getBlock().getMessage().getBody()).isInstanceOf(expectedBody);

    final List<SignedBeaconBlock> response = requestBlocksByRange(peer);
    assertThat(response).containsExactly(block1.getBlock(), block2.getBlock());
  }

  @ParameterizedTest(name = "{0} => {1}, nextSpecEnabledLocally={2}, nextSpecEnabledRemotely={3}")
  @MethodSource("generateSpecTransitionWithCombinationParams")
  public void requestBlockByRange_withDisparateVersionsEnabled_requestNextSpecBlocks(
      final SpecMilestone baseMilestone,
      final SpecMilestone nextMilestone,
      final boolean nextSpecEnabledLocally,
      final boolean nextSpecEnabledRemotely) {
    setUp(baseMilestone, Optional.of(nextMilestone));
    setupPeerStorage(true);
    final Eth2Peer peer = createPeer(nextSpecEnabledLocally, nextSpecEnabledRemotely);

    // Setup chain
    peerStorage.chainUpdater().advanceChain(nextSpecSlot.minus(1));
    // Create next spec blocks
    final SignedBlockAndState block1 = peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    final Class<?> expectedBody = milestoneToBeaconBlockBodyClass(nextMilestone);

    assertThat(block1.getBlock().getMessage().getBody()).isInstanceOf(expectedBody);
    assertThat(block2.getBlock().getMessage().getBody()).isInstanceOf(expectedBody);

    peerStorage.chainUpdater().updateBestBlock(block2);

    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRange(
            block1.getSlot(), UInt64.valueOf(2), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    waitFor(() -> assertThat(peer.getOutstandingRequests()).isEqualTo(0));

    if (nextSpecEnabledLocally && nextSpecEnabledRemotely) {
      // We should receive a successful response
      assertThat(res).isCompleted();
      assertThat(blocks).containsExactly(block1.getBlock(), block2.getBlock());
    } else if (!nextSpecEnabledLocally && nextSpecEnabledRemotely) {
      assertThat(res).isCompletedExceptionally();
      assertThatThrownBy(res::get)
          .hasCauseInstanceOf(RpcException.class)
          .hasRootCauseInstanceOf(UnrecognizedContextBytesException.class)
          .hasMessageContaining("Must request blocks with compatible fork.");
    } else if (milestoneToBeaconBlockBodyClass(baseMilestone)
        .equals(milestoneToBeaconBlockBodyClass(nextMilestone))) {
      // no changes in BeaconBlockBody, so we should receive a successful response
      assertThat(res).isCompleted();
      assertThat(blocks).containsExactly(block1.getBlock(), block2.getBlock());
    } else {
      assertThat(res).isCompletedExceptionally();
      assertThatThrownBy(res::get)
          .hasCauseInstanceOf(RpcException.class)
          .satisfiesAnyOf(
              ex ->
                  assertThat(ex.getCause())
                      .isInstanceOf(DeserializationFailedException.class)
                      .hasMessageContaining("Failed to deserialize payload"),
              ex ->
                  assertThat(ex.getCause())
                      // happens when fields are removed and the ssz length bound of the next fork
                      // is less than the current one
                      .isInstanceOf(LengthOutOfBoundsException.class)
                      .hasMessageContaining("Chunk length is not within bounds for expected type"));
    }
  }

  private List<SignedBeaconBlock> requestBlocksByRange(final Eth2Peer peer)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(
        peer.requestBlocksByRange(
            UInt64.ONE, UInt64.valueOf(10), RpcResponseListener.from(blocks::add)));
    waitFor(() -> assertThat(peer.getOutstandingRequests()).isEqualTo(0));
    return blocks;
  }
}
