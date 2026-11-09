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

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

class BeaconBlocksByRangeMessageHandlerTest {
  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());

  private static final String V2_PROTOCOL_ID =
      BeaconChainMethodIds.getBlocksByRangeMethodId(2, RPC_ENCODING);

  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final List<StateAndBlockSummary> blocksWStates =
      IntStream.rangeClosed(0, 10)
          .mapToObj(dataStructureUtil::randomSignedBlockAndState)
          .collect(Collectors.toList());
  private final List<SignedBeaconBlock> blocks =
      blocksWStates.stream()
          .map(StateAndBlockSummary::getSignedBeaconBlock)
          .flatMap(Optional::stream)
          .toList();

  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<SignedBeaconBlock> listener = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  private final String protocolId = BeaconChainMethodIds.getBlocksByRangeMethodId(2, RPC_ENCODING);
  private final BeaconBlocksByRangeMessageHandler handler =
      new BeaconBlocksByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);
  private final Optional<RequestKey> maybeRequestKey = Optional.of(new RequestKey(ZERO, 0));

  @BeforeEach
  public void setup() {
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveBlocksRequest(any(), anyLong())).thenReturn(maybeRequestKey);
    when(combinedChainDataClient.getEarliestAvailableBlockSlot())
        .thenReturn(completedFuture(Optional.of(ZERO)));
    when(listener.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void validateRequest_phase0Spec_v2Request() {
    final Optional<RpcException> result =
        handler.validateRequest(
            V2_PROTOCOL_ID, new BeaconBlocksByRangeRequestMessage(ZERO, ONE, ONE));

    assertThat(result).isEmpty();
  }

  @Test
  public void validateRequest_altairSpec_v2RequestForPhase0Block() {
    final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.valueOf(4));
    final BeaconBlocksByRangeMessageHandler handler =
        new BeaconBlocksByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);

    final Optional<RpcException> result =
        handler.validateRequest(
            V2_PROTOCOL_ID, new BeaconBlocksByRangeRequestMessage(ZERO, ONE, ONE));

    assertThat(result).isEmpty();
  }

  @Test
  public void validateRequest_altairSpec_v2RequestForAltairBlock() {
    final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.valueOf(4));
    final BeaconBlocksByRangeMessageHandler handler =
        new BeaconBlocksByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);

    final Optional<RpcException> result =
        handler.validateRequest(
            V2_PROTOCOL_ID, new BeaconBlocksByRangeRequestMessage(UInt64.valueOf(32), ONE, ONE));

    assertThat(result).isEmpty();
  }

  @Test
  public void validateRequest_altairSpec_v2RequestForRangeOfBlocksAcrossForkBoundary() {
    final Spec spec = TestSpecFactory.createMinimalWithAltairForkEpoch(UInt64.valueOf(4));
    final BeaconBlocksByRangeMessageHandler handler =
        new BeaconBlocksByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);

    final Optional<RpcException> result =
        handler.validateRequest(
            V2_PROTOCOL_ID,
            new BeaconBlocksByRangeRequestMessage(UInt64.valueOf(30), UInt64.valueOf(10), ONE));

    assertThat(result).isEmpty();
  }

  @Test
  public void validateRequest_shouldRejectRequestWhenStepIsZero() {
    final int startBlock = 15;
    final int skip = 0;

    final Optional<RpcException> result =
        handler.validateRequest(
            protocolId,
            new BeaconBlocksByRangeRequestMessage(
                UInt64.valueOf(startBlock),
                UInt64.valueOf(spec.getNetworkingConfig().getMaxRequestBlocks()),
                UInt64.valueOf(skip)));

    assertThat(result)
        .hasValue(new RpcException(INVALID_REQUEST_CODE, "Step must be greater than zero"));
  }

  @Test
  public void validateRequest_shouldRejectRequestWhenCountIsTooBig() {
    final int startBlock = 15;
    final int skip = 1;

    final Optional<RpcException> result =
        handler.validateRequest(
            protocolId,
            new BeaconBlocksByRangeRequestMessage(
                UInt64.valueOf(startBlock),
                UInt64.valueOf(spec.getNetworkingConfig().getMaxRequestBlocks()).increment(),
                UInt64.valueOf(skip)));

    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE,
                "Only a maximum of 1024 blocks can be requested per request"));
  }

  @Test
  public void validateRequest_shouldRejectRequestWhenCountIsTooBigDueToIntOverflow() {
    final int startBlock = 15;
    final int skip = 1;

    final Optional<RpcException> result =
        handler.validateRequest(
            protocolId,
            new BeaconBlocksByRangeRequestMessage(
                UInt64.valueOf(startBlock),
                UInt64.valueOf(((long) Integer.MAX_VALUE) + 1),
                UInt64.valueOf(skip)));

    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE,
                "Only a maximum of 1024 blocks can be requested per request"));
  }

  @Test
  public void validateRequest_shouldRejectRequestWhenGetMaxSlotOverflows() {
    final int skip = 1;

    final Optional<RpcException> result =
        handler.validateRequest(
            protocolId,
            new BeaconBlocksByRangeRequestMessage(
                UInt64.MAX_VALUE, UInt64.valueOf(10), UInt64.valueOf(skip)));

    assertThat(result)
        .hasValue(
            new RpcException(INVALID_REQUEST_CODE, "Requested slot is too far in the future"));
  }

  @Test
  public void validateRequest_shouldRejectRequestWhenCountIsTooBigForDeneb() {
    final int startBlock = 15;
    final int skip = 1;

    final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(ONE);

    final BeaconBlocksByRangeMessageHandler handler =
        new BeaconBlocksByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);

    final SpecConfig config = spec.forMilestone(SpecMilestone.DENEB).getConfig();
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(config);
    final Optional<RpcException> result =
        handler.validateRequest(
            protocolId,
            new BeaconBlocksByRangeRequestMessage(
                UInt64.valueOf(startBlock),
                UInt64.valueOf(specConfigDeneb.getMaxRequestBlocksDeneb()).increment(),
                UInt64.valueOf(skip)));

    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE, "Only a maximum of 128 blocks can be requested per request"));
  }

  @Test
  public void shouldReturnNoBlocksWhenThereAreNoBlocksAtOrAfterStartSlot() {
    final int startBlock = 10;
    final int count = 1;
    final int skip = 1;
    // Series of empty blocks leading up to our best slot.
    withCanonicalHeadBlock(blocksWStates.get(1));
    withAncestorRoots(startBlock, count, skip, hotBlocks());

    when(combinedChainDataClient.getBlockAtSlotExact(any()))
        .thenReturn(completedFuture(Optional.empty()));

    requestBlocks(startBlock, count, skip);

    verifyNoBlocksReturned();
  }

  @Test
  public void shouldReturnErrorWhenFirstBlockIsMissing() {
    final int startBlock = 1;
    final int count = 5;
    final int skip = 1;
    withCanonicalHeadBlock(blocksWStates.get(8));
    withFinalizedBlocks(0, 1, 2, 3, 4, 5, 6, 7);

    when(combinedChainDataClient.getEarliestAvailableBlockSlot())
        .thenReturn(completedFuture(Optional.of(UInt64.valueOf(2))));

    requestBlocks(startBlock, count, skip);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Be protective: do not adjust due to error
    verify(peer, never()).adjustBlocksRequest(any(), anyLong());

    final RpcException expectedError =
        new RpcException.ResourceUnavailableException(
            "Requested historical blocks are currently unavailable");
    verify(listener).completeWithErrorResponse(expectedError);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void shouldReturnErrorWhenEarliestHistoricalBlockUnknown() {
    final int startBlock = 1;
    final int count = 5;
    final int skip = 1;
    withCanonicalHeadBlock(blocksWStates.get(8));
    withFinalizedBlocks(0, 1, 2, 3, 4, 5, 6, 7);

    when(combinedChainDataClient.getEarliestAvailableBlockSlot())
        .thenReturn(completedFuture(Optional.empty()));

    requestBlocks(startBlock, count, skip);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Be protective: do not adjust due to error
    verify(peer, never()).adjustBlocksRequest(any(), anyLong());

    final RpcException expectedError =
        new RpcException.ResourceUnavailableException(
            "Requested historical blocks are currently unavailable");
    verify(listener).completeWithErrorResponse(expectedError);
    verifyNoMoreInteractions(listener);
  }

  @Test
  public void shouldReturnRequestedNumberOfBlocksWhenFullySequential() {
    final int startBlock = 3;
    final int count = 5;
    final int skip = 1;
    withCanonicalHeadBlock(blocksWStates.get(10));
    withAncestorRoots(startBlock, count, skip, allBlocks());

    requestBlocks(startBlock, count, skip);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Sending 5 blocks as initially requested: No rate limiter adjustment
    verify(peer, never()).adjustBlocksRequest(any(), anyLong());

    verifyBlocksReturned(3, 4, 5, 6, 7);
  }

  @Test
  public void shouldOnlyReturnBlockAtStartSlotWhenCountIsOne() {
    final int startBlock = 3;
    final int count = 1;
    final int skip = 1;
    withCanonicalHeadBlock(blocksWStates.get(10));
    // getAncestorRoots for a count=1 request only returns the single root at startSlot
    // (endSlot = startSlot + step*count - 1 == startSlot), so hotRoots.lastKey() == startSlot.
    // This reproduces the real behaviour where startSlot == headSlot for the request state.
    withAncestorRoots(startBlock, count, skip, hotBlocks(startBlock));

    requestBlocks(startBlock, count, skip);

    // Requesting 1 block
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Sending 1 block as initially requested: No rate limiter adjustment
    verify(peer, never()).adjustBlocksRequest(any(), anyLong());

    verifyBlocksReturned(3);
  }

  @Test
  public void shouldReturnFirstBlockWhenStepIsGreaterThanOne() {
    // Asking for every second block from 2 onwards, up to 5 blocks.
    final int startBlock = 2;
    final int count = 5;
    final int skip = 2;
    withCanonicalHeadBlock(blocksWStates.get(10));

    withAncestorRoots(startBlock, count, skip, allBlocks());

    requestBlocks(startBlock, count, skip);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Sending 1 block
    verify(peer, times(1))
        .adjustBlocksRequest(eq(maybeRequestKey.orElseThrow()), eq(Long.valueOf(1)));

    verifyBlocksReturned(2);
  }

  @Test
  public void shouldReturnFewerBlocksWhenSomeSlotsAreEmpty() {
    // Asking for every block from 2 onwards, up to 5 blocks.
    final int startBlock = 2;
    final int count = 5;
    final int skip = 1;
    withCanonicalHeadBlock(blocksWStates.get(10));
    withAncestorRoots(startBlock, count, skip, hotBlocks(2, 3, 5, 6, 7));

    requestBlocks(startBlock, count, skip);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Sending 4 blocks only
    verify(peer, times(1))
        .adjustBlocksRequest(eq(maybeRequestKey.orElseThrow()), eq(Long.valueOf(4)));
    // Slot 4 is empty so we only return 4 blocks
    verifyBlocksReturned(2, 3, 5, 6);
  }

  @Test
  public void shouldReturnFirstBlockInRangeAccountingForSkip() {
    final int startBlock = 2;
    final int count = 4;
    final int skip = 2;
    withCanonicalHeadBlock(blocksWStates.get(10));
    // block 2 is missing, 3 is first in range, but 4 is the first if we're looking at skip, so
    // expect block 4 to be returned in this instance
    withAncestorRoots(startBlock, count, skip, hotBlocks(1, 3, 4, 5, 6, 8, 10));
    requestBlocks(startBlock, count, skip);

    // Requesting 4 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Sending 1 block
    verify(peer, times(1))
        .adjustBlocksRequest(eq(maybeRequestKey.orElseThrow()), eq(Long.valueOf(1)));

    verifyBlocksReturned(4);
  }

  @Test
  public void shouldStopAtBestSlot() {
    final int count = 50;
    final int startBlock = 15;
    final int skip = 5;

    withCanonicalHeadBlock(blocksWStates.get(5));
    withAncestorRoots(startBlock, count, skip, hotBlocks());

    handler.onIncomingMessage(
        protocolId,
        peer,
        new BeaconBlocksByRangeRequestMessage(
            UInt64.valueOf(startBlock), UInt64.valueOf(count), UInt64.valueOf(skip)),
        listener);

    // Requesting 50 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Sending 0 blocks
    verify(peer, times(1))
        .adjustBlocksRequest(eq(maybeRequestKey.orElseThrow()), eq(Long.valueOf(0)));

    verifyNoBlocksReturned();
    // The first block is after the best block available, so we shouldn't request anything
    verify(combinedChainDataClient, never()).getBlockAtSlotExact(any(), any());
  }

  @Test
  void shouldReturnBlocksFromFinalizedPeriod() {
    final int startBlock = 1;
    final int count = 5;
    final int skip = 1;
    withCanonicalHeadBlock(blocksWStates.get(8));
    withFinalizedBlocks(0, 1, 2, 3, 4, 5, 6, 7);

    requestBlocks(startBlock, count, skip);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Sending 5 blocks as initially requested: No rate limiter adjustment
    verify(peer, never()).adjustBlocksRequest(any(), anyLong());

    verifyBlocksReturned(1, 2, 3, 4, 5);
    verify(combinedChainDataClient, never()).getAncestorRoots(any(), any(), any());
  }

  @Test
  void shouldReturnMixOfFinalizedAndHotBlocks() {
    final int startBlock = 1;
    final int count = 5;
    final int skip = 1;
    withCanonicalHeadBlock(blocksWStates.get(8));
    withAncestorRoots(startBlock, count, skip, hotBlocks(4, 5, 6));
    withFinalizedBlocks(0, 1, 2, 3);

    requestBlocks(startBlock, count, skip);

    // Requesting 5 blocks
    verify(peer, times(1)).approveBlocksRequest(any(), eq(Long.valueOf(count)));
    // Sending 5 blocks as initially requested: No rate limiter adjustment
    verify(peer, never()).adjustBlocksRequest(any(), anyLong());

    verifyBlocksReturned(1, 2, 3, 4, 5);
  }

  private void requestBlocks(final int startBlock, final long count, final int skip) {

    handler.onIncomingMessage(
        protocolId,
        peer,
        new BeaconBlocksByRangeRequestMessage(
            UInt64.valueOf(startBlock), UInt64.valueOf(count), UInt64.valueOf(skip)),
        listener);
  }

  private void verifyNoBlocksReturned() {
    verifyBlocksReturned();
  }

  private void verifyBlocksReturned(final int... slots) {
    final InOrder inOrder = Mockito.inOrder(listener);
    for (int slot : slots) {
      inOrder.verify(listener).respond(blocks.get(slot));
    }
    inOrder.verify(listener).completeSuccessfully();
    verifyNoMoreInteractions(listener);
  }

  private void withAncestorRoots(
      final int startBlock,
      final int count,
      final int skip,
      final NavigableMap<UInt64, Bytes32> blockRoots) {
    when(combinedChainDataClient.getAncestorRoots(
            UInt64.valueOf(startBlock), UInt64.valueOf(skip), UInt64.valueOf(count)))
        .thenReturn(blockRoots);
  }

  private NavigableMap<UInt64, Bytes32> allBlocks() {
    return hotBlocks(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
  }

  private NavigableMap<UInt64, Bytes32> hotBlocks(final int... slots) {
    final NavigableMap<UInt64, Bytes32> blockRoots = new TreeMap<>();
    IntStream.of(slots)
        .forEach(
            slot -> {
              final SignedBeaconBlock block = blocks.get(slot);
              blockRoots.put(UInt64.valueOf(slot), block.getRoot());
              when(combinedChainDataClient.getBlockByBlockRoot(block.getRoot()))
                  .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
            });
    return blockRoots;
  }

  private void withFinalizedBlocks(final int... slots) {
    IntStream.of(slots)
        .forEach(
            slot -> {
              final SignedBeaconBlock block = blocks.get(slot);
              final SafeFuture<Optional<SignedBeaconBlock>> result =
                  completedFuture(Optional.of(block));
              when(combinedChainDataClient.getBlockByBlockRoot(block.getRoot())).thenReturn(result);
              when(combinedChainDataClient.getBlockAtSlotExact(block.getSlot())).thenReturn(result);
              when(combinedChainDataClient.isFinalized(block.getSlot())).thenReturn(true);
            });
  }

  private void withCanonicalHeadBlock(final StateAndBlockSummary chainHead) {
    when(combinedChainDataClient.getChainHead())
        .thenReturn(Optional.of(ChainHead.create(chainHead)));
  }
}
