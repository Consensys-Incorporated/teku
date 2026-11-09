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

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.DeserializationFailedException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.UnrecognizedContextBytesException;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.PeerDisconnectedException;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconBlocksByRootIntegrationTest extends AbstractRpcMethodIntegrationTest {

  @BeforeEach
  void setUp() {
    setUp(SpecMilestone.PHASE0, Optional.empty());
  }

  @Test
  public void shouldSendEmptyResponseWhenNoBlocksAreAvailable() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> response = requestBlocksByRoot(peer, singletonList(Bytes32.ZERO));
    assertThat(response).isEmpty();
  }

  @Test
  public void shouldReturnSingleBlockWhenOnlyOneMatches() throws Exception {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();

    final List<SignedBeaconBlock> response =
        requestBlocksByRoot(peer, singletonList(block.getMessage().hashTreeRoot()));
    assertThat(response).containsExactly(block);
  }

  @Test
  public void requestBlocksByRootAfterPeerDisconnectedImmediately() throws RpcException {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hashTreeRoot();

    peer.disconnectImmediately(Optional.empty(), false);
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRoot(List.of(blockHash), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlocksByRootAfterPeerDisconnected()
      throws RpcException, InterruptedException, ExecutionException, TimeoutException {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hashTreeRoot();

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRoot(List.of(blockHash), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
    assertThat(blocks).isEmpty();
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnectedImmediately() {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hashTreeRoot();

    peer.disconnectImmediately(Optional.empty(), false);
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockByRoot(blockHash);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void requestBlockByRootAfterPeerDisconnected()
      throws InterruptedException, ExecutionException, TimeoutException {
    final Eth2Peer peer = createPeer();
    final SignedBeaconBlock block = addBlock();
    final Bytes32 blockHash = block.getMessage().hashTreeRoot();

    Waiter.waitFor(peer.disconnectCleanly(DisconnectReason.TOO_MANY_PEERS));
    final SafeFuture<Optional<SignedBeaconBlock>> res = peer.requestBlockByRoot(blockHash);

    waitFor(() -> assertThat(res).isDone());
    assertThat(res).isCompletedExceptionally();
    assertThatThrownBy(res::get).hasRootCauseInstanceOf(PeerDisconnectedException.class);
  }

  @Test
  public void shouldReturnMultipleBlocksWhenAllRequestsMatch() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> blocks = asList(addBlock(), addBlock(), addBlock());
    final List<Bytes32> blockRoots =
        blocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .map(BeaconBlock::hashTreeRoot)
            .collect(toList());
    final List<SignedBeaconBlock> response = requestBlocksByRoot(peer, blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  public void shouldReturnMultipleLargeBlocksWhenAllRequestsMatch() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> blocks = largeBlockSequence(3);
    final List<Bytes32> blockRoots =
        blocks.stream().map(SignedBeaconBlock::getRoot).collect(toList());
    final List<SignedBeaconBlock> response = requestBlocksByRoot(peer, blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  public void shouldReturnMatchingBlocksWhenSomeRequestsDoNotMatch() throws Exception {
    final Eth2Peer peer = createPeer();
    final List<SignedBeaconBlock> blocks = asList(addBlock(), addBlock(), addBlock());

    // Real block roots interspersed with ones that don't match any blocks
    final List<Bytes32> blockRoots =
        blocks.stream()
            .map(SignedBeaconBlock::getMessage)
            .map(BeaconBlock::hashTreeRoot)
            .flatMap(hash -> Stream.of(Bytes32.fromHexStringLenient("0x123456789"), hash))
            .collect(toList());

    final List<SignedBeaconBlock> response = requestBlocksByRoot(peer, blockRoots);
    assertThat(response).containsExactlyElementsOf(blocks);
  }

  @Test
  void requestBlockByRoot_shouldReturnEmptyWhenBlockIsNotKnown() throws Exception {
    final Eth2Peer peer = createPeer();
    final Optional<SignedBeaconBlock> result =
        waitFor(peer.requestBlockByRoot(Bytes32.fromHexStringLenient("0x123456789")));
    assertThat(result).isEmpty();
  }

  @ParameterizedTest(name = "{0} => {1}, nextSpecEnabledLocally={2}, nextSpecEnabledRemotely={3}")
  @MethodSource("generateSpecTransitionWithCombinationParams")
  public void requestBlockByRoot_withDisparateVersionsEnabled_requestPhase0Blocks(
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
    peerStorage.chainUpdater().updateBestBlock(block2);

    final Class<?> expectedBody = milestoneToBeaconBlockBodyClass(baseMilestone);

    assertThat(block1.getBlock().getMessage().getBody()).isInstanceOf(expectedBody);
    assertThat(block2.getBlock().getMessage().getBody()).isInstanceOf(expectedBody);

    final List<SignedBeaconBlock> response =
        requestBlocksByRoot(peer, List.of(block1.getRoot(), block2.getRoot()));
    assertThat(response).containsExactly(block1.getBlock(), block2.getBlock());
  }

  @ParameterizedTest(name = "{0} => {1}, nextSpecEnabledLocally={2}, nextSpecEnabledRemotely={3}")
  @MethodSource("generateSpecTransitionWithCombinationParams")
  public void requestBlockByRoot_withDisparateVersionsEnabled_requestNextSpecBlocks(
      final SpecMilestone baseMilestone,
      final SpecMilestone nextMilestone,
      final boolean nextSpecEnabledLocally,
      final boolean nextSpecEnabledRemotely)
      throws Exception {
    setUp(baseMilestone, Optional.of(nextMilestone));
    setupPeerStorage(true);
    final Eth2Peer peer = createPeer(nextSpecEnabledLocally, nextSpecEnabledRemotely);

    // Setup chain
    peerStorage.chainUpdater().advanceChain(nextSpecSlot.minus(1));
    // Create altair blocks
    final SignedBlockAndState block1 = peerStorage.chainUpdater().advanceChain();
    final SignedBlockAndState block2 = peerStorage.chainUpdater().advanceChain();
    peerStorage.chainUpdater().updateBestBlock(block2);

    final Class<?> expectedBody = milestoneToBeaconBlockBodyClass(nextMilestone);

    assertThat(block1.getBlock().getMessage().getBody()).isInstanceOf(expectedBody);
    assertThat(block2.getBlock().getMessage().getBody()).isInstanceOf(expectedBody);

    peerStorage.chainUpdater().updateBestBlock(block2);

    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    final SafeFuture<Void> res =
        peer.requestBlocksByRoot(
            List.of(block1.getRoot(), block2.getRoot()), RpcResponseListener.from(blocks::add));

    waitFor(() -> assertThat(res).isDone());

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
                      .isInstanceOf(RpcException.LengthOutOfBoundsException.class)
                      .hasMessageContaining("Chunk length is not within bounds for expected type"));
    }
  }

  private SignedBeaconBlock addBlock() {
    return peerStorage.chainUpdater().advanceChain().getBlock();
  }

  private List<SignedBeaconBlock> largeBlockSequence(final int count) {
    DataStructureUtil dataStructureUtil = new DataStructureUtil(TestSpecFactory.createDefault());
    final SignedBeaconBlock parent = peerStorage.chainBuilder().getLatestBlockAndState().getBlock();
    List<SignedBlockAndState> newBlocks =
        dataStructureUtil.randomSignedBlockAndStateSequence(parent, count, true);
    newBlocks.forEach(peerStorage.chainUpdater()::saveBlock);

    return newBlocks.stream().map(SignedBlockAndState::getBlock).collect(Collectors.toList());
  }

  private List<SignedBeaconBlock> requestBlocksByRoot(
      final Eth2Peer peer, final List<Bytes32> blockRoots)
      throws InterruptedException, ExecutionException, TimeoutException, RpcException {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    waitFor(peer.requestBlocksByRoot(blockRoots, RpcResponseListener.from(blocks::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return blocks;
  }
}
