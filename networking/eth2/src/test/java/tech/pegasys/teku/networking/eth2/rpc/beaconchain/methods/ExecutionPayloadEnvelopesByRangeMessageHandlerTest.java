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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.NETWORK;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRangeRequestMessage;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionPayloadEnvelopesByRangeMessageHandlerTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());

  private static final String PROTOCOL_ID =
      BeaconChainMethodIds.getExecutionPayloadEnvelopesByRangeMethodId(1, RPC_ENCODING);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ExecutionPayloadEnvelopesByRangeMessageHandler handler =
      new ExecutionPayloadEnvelopesByRangeMessageHandler(
          spec,
          SpecConfigGloas.required(spec.getGenesisSpecConfig()),
          metricsSystem,
          combinedChainDataClient);
  final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  final ResponseCallback<SignedExecutionPayloadEnvelope> callback = mock(ResponseCallback.class);

  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @BeforeEach
  public void setup() {
    chainBuilder.generateGenesis();
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    // Current epoch 0 keeps the whole chain within the servable range by default
    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(ZERO));
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveExecutionPayloadEnvelopesRequest(any(), anyLong()))
        .thenReturn(Optional.of(new RequestKey(ZERO, 42)));
    // Genesis is finalized by default so every requested slot is resolved on the head chain
    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(ZERO));
    // Forward head chain node requests from the mock to the ChainBuilder: every block on the
    // chain has its payload built on by the next block, so all nodes are FULL
    stubHeadChainNodes(__ -> PAYLOAD_STATUS_FULL);
    // Forward execution payload envelope block root requests from the mock to the ChainBuilder
    when(combinedChainDataClient.getExecutionPayloadByBlockRoot(any()))
        .thenAnswer(
            i -> SafeFuture.completedFuture(chainBuilder.getExecutionPayload(i.getArgument(0))));

    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  void validateRequest_countTooBig() {
    final Optional<RpcException> response =
        handler.validateRequest(
            PROTOCOL_ID,
            new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(256)));
    assertThat(response).isNotEmpty();
    assertThat(response.get().getMessage())
        .containsIgnoringCase("maximum of 128 execution payload envelopes");
    assertThat(getLabelledCounterValue("count_too_big")).isEqualTo(1);
    verifyNoInteractions(combinedChainDataClient);
  }

  @Test
  void validateRequest_maxCount() {
    final Optional<RpcException> response =
        handler.validateRequest(
            PROTOCOL_ID,
            new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(128)));
    assertThat(response).isEmpty();
    assertThat(getLabelledCounterValue("count_too_big")).isEqualTo(0);
  }

  @Test
  void validateRequest_shouldRejectRequestWhenGetMaxSlotOverflows() {
    final Optional<RpcException> response =
        handler.validateRequest(
            PROTOCOL_ID,
            new ExecutionPayloadEnvelopesByRangeRequestMessage(
                UInt64.MAX_VALUE, UInt64.valueOf(10)));

    assertThat(response)
        .hasValue(
            new RpcException(INVALID_REQUEST_CODE, "Requested slot is too far in the future"));
    verifyNoInteractions(combinedChainDataClient);
  }

  @Test
  void onIncomingMessage_requestNotApproved() {
    when(peer.approveRequest()).thenReturn(false);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(2));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    // Requesting 2 execution payload envelopes
    verify(peer).approveExecutionPayloadEnvelopesRequest(any(), eq(2L));
    verify(callback, never()).completeSuccessfully();
    verify(callback, never()).respond(any());

    // verify counters
    assertThat(getLabelledCounterValue("rate_limited")).isOne();
    assertThat(getLabelledCounterValue("ok")).isZero();
  }

  @Test
  void onIncomingMessage_respondsAllPayloads() {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(5);
    final UInt64 payloadCount = UInt64.valueOf(5);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, payloadCount);
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    // Requesting 5 execution payload envelopes
    verify(peer).approveExecutionPayloadEnvelopesRequest(any(), eq(payloadCount.longValue()));
    verify(callback, times(5)).respond(any());
    for (final SignedExecutionPayloadEnvelope executionPayloadEnvelope :
        executionPayloadEnvelopes) {
      verify(callback).respond(executionPayloadEnvelope);
    }

    verify(callback).completeSuccessfully();

    // verify counters
    assertThat(getLabelledCounterValue("ok")).isOne();
    assertThat(getExecutionPayloadEnvelopesRequestedCounterValue()).isEqualTo(5);
  }

  @Test
  public void onIncomingMessage_respondsWithSomeOfTheExecutionPayloadEnvelopes() {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(4);
    final UInt64 payloadCount = UInt64.valueOf(5);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, payloadCount);
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    // Requesting 5 execution payload envelopes
    verify(peer).approveExecutionPayloadEnvelopesRequest(any(), eq(5L));
    // Sending 4 execution payload envelopes: Rate limiter adjustment required
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(4L));
    verify(callback, times(4)).respond(any());

    for (final SignedExecutionPayloadEnvelope executionPayloadEnvelope :
        executionPayloadEnvelopes) {
      verify(callback).respond(executionPayloadEnvelope);
    }
    verify(callback).completeSuccessfully();

    // verify counters
    assertThat(getLabelledCounterValue("ok")).isOne();
    assertThat(getExecutionPayloadEnvelopesRequestedCounterValue()).isEqualTo(5);
  }

  @Test
  public void onIncomingMessage_respondsFromFinalizedRange() {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(6);
    // All requested slots are before the finalized block
    stubFinalizedChainHeadAndBlockLookup(UInt64.valueOf(6));

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(5));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    verify(callback, times(5)).respond(any());
    verify(combinedChainDataClient, never()).getAncestorNodes(any(), any());
    for (final SignedExecutionPayloadEnvelope executionPayloadEnvelope :
        executionPayloadEnvelopes.subList(0, 5)) {
      verify(callback).respond(executionPayloadEnvelope);
      verify(combinedChainDataClient).getBlockAtSlotExact(executionPayloadEnvelope.getSlot());
    }
    verify(callback).completeSuccessfully();
    assertThat(getLabelledCounterValue("ok")).isOne();
  }

  @Test
  public void onIncomingMessage_skipsFinalizedSlotWithoutBlock() {
    // Build 6 slots of blocks and pretend slot 3 was a skipped slot in the finalized chain
    final List<SignedExecutionPayloadEnvelope> allEnvelopes = buildChain(6).subList(0, 5);
    final UInt64 skippedSlot = UInt64.valueOf(3);

    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(UInt64.valueOf(6)));
    stubChainHead(UInt64.valueOf(6));
    when(combinedChainDataClient.getBlockAtSlotExact(any()))
        .thenAnswer(
            i -> {
              final UInt64 slot = i.getArgument(0);
              if (slot.equals(skippedSlot)) {
                return SafeFuture.completedFuture(Optional.empty());
              }
              return SafeFuture.completedFuture(
                  chainBuilder.getBlock(chainBuilder.getBlockAtSlot(slot).getRoot()));
            });

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(5));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    // 4 envelopes served, skipped slot produces no response
    verify(callback, times(4)).respond(any());
    for (final SignedExecutionPayloadEnvelope envelope : allEnvelopes) {
      if (envelope.getMessage().getSlot().equals(skippedSlot)) {
        verify(callback, never()).respond(envelope);
      } else {
        verify(callback).respond(envelope);
      }
    }
    verify(callback).completeSuccessfully();
  }

  @Test
  public void onIncomingMessage_shouldNotServeRangeEntirelyOutsideServableRange() {
    buildChain(12);
    // minServableEpoch = 1, so slots below the start of epoch 1 must not be served
    stubCurrentEpoch(1);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(5));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    // no envelope is retrieved from the database or the execution layer
    verify(combinedChainDataClient, never()).getExecutionPayloadByBlockRoot(any());
    verify(combinedChainDataClient, never()).getBlockAtSlotExact(any());
    verify(callback, never()).respond(any());
    verify(callback).completeSuccessfully();
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(0L));
  }

  @Test
  public void onIncomingMessage_shouldTrimRangeStartingOutsideServableRange() {
    buildChain(12);
    // minServableEpoch = 1, so the servable range starts at slot 8 with 8 slots per epoch
    stubCurrentEpoch(1);
    final UInt64 minServableSlot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    assertThat(minServableSlot).isEqualTo(UInt64.valueOf(8));

    // slots 5 to 10 requested, only 8, 9 and 10 are servable
    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.valueOf(5), UInt64.valueOf(6));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    verify(callback, times(3)).respond(any());
    // slots below the servable range are dropped and the count is reduced rather than shifted, so
    // slot 11 is never served even though it is within the servable range
    for (final SignedExecutionPayloadEnvelope envelope :
        chainBuilder.streamExecutionPayloads(UInt64.valueOf(5), UInt64.valueOf(11)).toList()) {
      if (envelope.getSlot().isLessThan(minServableSlot)
          || envelope.getSlot().isGreaterThan(UInt64.valueOf(10))) {
        verify(callback, never()).respond(envelope);
      } else {
        verify(callback).respond(envelope);
      }
    }
    verify(callback).completeSuccessfully();
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(3L));
  }

  @Test
  public void onIncomingMessage_shouldServeSingleSlotRemainingAfterTrimming() {
    buildChain(8);
    // minServableEpoch = 1, so of the requested slots only slot 8 survives trimming, and it is also
    // the last hot slot, so the request must not be treated as already complete
    stubCurrentEpoch(1);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.valueOf(6), UInt64.valueOf(3));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    final SignedExecutionPayloadEnvelope expected =
        chainBuilder.getExecutionPayloadAtSlot(UInt64.valueOf(8)).orElseThrow();
    verify(callback).respond(expected);
    verify(callback, times(1)).respond(any());
    verify(callback).completeSuccessfully();
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(1L));
  }

  @Test
  public void onIncomingMessage_shouldServeEnvelopeWhenStartSlotIsHeadSlot() {
    buildChain(5);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.valueOf(5), UInt64.ONE);
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    final SignedExecutionPayloadEnvelope expected =
        chainBuilder.getExecutionPayloadAtSlot(UInt64.valueOf(5)).orElseThrow();
    verify(callback).respond(expected);
    verify(callback).completeSuccessfully();
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());
  }

  @Test
  public void onIncomingMessage_shouldNotServeEnvelopeOfPayloadTheHeadChainDidNotBuildOn() {
    final List<SignedExecutionPayloadEnvelope> allEnvelopes = buildChain(5);
    // The block at slot 4 built on the EMPTY variant of the block at slot 3, so the payload of
    // slot 3 is not part of the chain even though its envelope is available
    final UInt64 emptySlot = UInt64.valueOf(3);
    stubHeadChainNodes(slot -> slot.equals(emptySlot) ? PAYLOAD_STATUS_EMPTY : PAYLOAD_STATUS_FULL);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(5));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    verify(callback, times(4)).respond(any());
    for (final SignedExecutionPayloadEnvelope envelope : allEnvelopes) {
      if (envelope.getSlot().equals(emptySlot)) {
        verify(callback, never()).respond(envelope);
      } else {
        verify(callback).respond(envelope);
      }
    }
    // The envelope of the EMPTY node is never even looked up
    verify(combinedChainDataClient, never())
        .getExecutionPayloadByBlockRoot(chainBuilder.getBlockAtSlot(emptySlot).getRoot());
    verify(callback).completeSuccessfully();
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(4L));
  }

  @Test
  public void onIncomingMessage_shouldNotServeHeadEnvelopeWhenHeadPayloadStatusIsEmpty() {
    final List<SignedExecutionPayloadEnvelope> allEnvelopes = buildChain(5);
    // Fork choice selected the EMPTY variant of the head even though its payload is available
    final UInt64 headSlot = UInt64.valueOf(5);
    stubHeadChainNodes(slot -> slot.equals(headSlot) ? PAYLOAD_STATUS_EMPTY : PAYLOAD_STATUS_FULL);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(5));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    verify(callback, times(4)).respond(any());
    for (final SignedExecutionPayloadEnvelope envelope : allEnvelopes) {
      if (envelope.getSlot().equals(headSlot)) {
        verify(callback, never()).respond(envelope);
      } else {
        verify(callback).respond(envelope);
      }
    }
    verify(callback).completeSuccessfully();
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(4L));
  }

  @Test
  public void onIncomingMessage_shouldNotServeHeadEnvelopeWhenHeadPayloadStatusIsPending() {
    final List<SignedExecutionPayloadEnvelope> allEnvelopes = buildChain(5);
    // The head payload has been received but fork choice has not selected the FULL variant
    final UInt64 headSlot = UInt64.valueOf(5);
    stubHeadChainNodes(
        slot -> slot.equals(headSlot) ? PAYLOAD_STATUS_PENDING : PAYLOAD_STATUS_FULL);

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(5));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    verify(callback, times(4)).respond(any());
    for (final SignedExecutionPayloadEnvelope envelope : allEnvelopes) {
      if (envelope.getSlot().equals(headSlot)) {
        verify(callback, never()).respond(envelope);
      } else {
        verify(callback).respond(envelope);
      }
    }
    verify(callback).completeSuccessfully();
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(4L));
  }

  @Test
  public void onIncomingMessage_shouldResolveFinalizedBlockPayloadStatusFromHeadChain() {
    final List<SignedExecutionPayloadEnvelope> allEnvelopes = buildChain(5);
    // The finalized block is the last requested slot. Whether its payload is part of the chain is
    // only known from the head chain, so it must not be served from the finalized database.
    final UInt64 finalizedSlot = UInt64.valueOf(5);
    stubFinalizedChainHeadAndBlockLookup(finalizedSlot);
    // Only the finalized block is still in fork choice, and the head chain goes through its EMPTY
    // variant
    doReturn(
            new TreeMap<>(
                Map.of(
                    finalizedSlot,
                    ForkChoiceNode.createEmpty(
                        chainBuilder.getBlockAtSlot(finalizedSlot).getRoot()))))
        .when(combinedChainDataClient)
        .getAncestorNodes(any(), any());

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(5));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    // Slots 1 to 4 are served from the finalized database, the finalized block's envelope is not
    verify(callback, times(4)).respond(any());
    for (final SignedExecutionPayloadEnvelope envelope : allEnvelopes) {
      if (envelope.getSlot().equals(finalizedSlot)) {
        verify(callback, never()).respond(envelope);
        verify(combinedChainDataClient, never()).getBlockAtSlotExact(finalizedSlot);
      } else {
        verify(callback).respond(envelope);
        verify(combinedChainDataClient).getBlockAtSlotExact(envelope.getSlot());
      }
    }
    verify(callback).completeSuccessfully();
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(4L));
  }

  @Test
  public void onIncomingMessage_shouldServeRangeStraddlingFinalizedBlockFromBothSources() {
    final List<SignedExecutionPayloadEnvelope> allEnvelopes = buildChain(5);
    // The finalized block is at slot 3 and the chain built on its payload. Slots before it come
    // from the finalized database, the finalized block and its descendants from the head chain.
    final UInt64 finalizedSlot = UInt64.valueOf(3);
    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(finalizedSlot));
    stubHeadChainNodes(finalizedSlot, __ -> PAYLOAD_STATUS_FULL);
    stubFinalizedBlockLookup();

    final ExecutionPayloadEnvelopesByRangeRequestMessage message =
        new ExecutionPayloadEnvelopesByRangeRequestMessage(UInt64.ONE, UInt64.valueOf(5));
    handler.onIncomingMessage(PROTOCOL_ID, peer, message, callback);

    verify(callback, times(5)).respond(any());
    for (final SignedExecutionPayloadEnvelope envelope : allEnvelopes) {
      verify(callback).respond(envelope);
      if (envelope.getSlot().isLessThan(finalizedSlot)) {
        verify(combinedChainDataClient).getBlockAtSlotExact(envelope.getSlot());
      } else {
        verify(combinedChainDataClient, never()).getBlockAtSlotExact(envelope.getSlot());
      }
    }
    verify(callback).completeSuccessfully();
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());
  }

  private void stubCurrentEpoch(final int epochsAfterMinEpochsForBlockRequests) {
    when(recentChainData.getCurrentEpoch())
        .thenReturn(
            Optional.of(
                UInt64.valueOf(spec.getNetworkingConfig().getMinEpochsForBlockRequests())
                    .plus(epochsAfterMinEpochsForBlockRequests)));
  }

  /**
   * Stubs the head chain nodes returned for the requested range. Every block of the chain builder
   * within the range is present, with the payload status given by {@code payloadStatusBySlot}.
   */
  private void stubHeadChainNodes(
      final Function<UInt64, ForkChoicePayloadStatus> payloadStatusBySlot) {
    stubHeadChainNodes(ZERO, payloadStatusBySlot);
  }

  /**
   * Stubs the head chain nodes returned for the requested range. Blocks before {@code
   * firstSlotInForkChoice} have been pruned from fork choice and are not returned.
   */
  private void stubHeadChainNodes(
      final UInt64 firstSlotInForkChoice,
      final Function<UInt64, ForkChoicePayloadStatus> payloadStatusBySlot) {
    doAnswer(
            i -> {
              final UInt64 startSlot = i.getArgument(0);
              final UInt64 count = i.getArgument(1);
              return chainBuilder
                  .streamBlocksAndStates(
                      startSlot.max(firstSlotInForkChoice), startSlot.plus(count).minusMinZero(1))
                  .collect(
                      Collectors.toMap(
                          blockAndState -> blockAndState.getBlock().getSlot(),
                          blockAndState ->
                              new ForkChoiceNode(
                                  blockAndState.getBlock().getRoot(),
                                  payloadStatusBySlot.apply(blockAndState.getBlock().getSlot())),
                          (e1, e2) -> e1,
                          TreeMap::new));
            })
        .when(combinedChainDataClient)
        .getAncestorNodes(any(), any());
  }

  private void stubFinalizedChainHeadAndBlockLookup(final UInt64 headSlot) {
    // The head is the finalized block
    when(combinedChainDataClient.getFinalizedBlockSlot()).thenReturn(Optional.of(headSlot));
    stubChainHead(headSlot);
    stubFinalizedBlockLookup();
  }

  private void stubFinalizedBlockLookup() {
    when(combinedChainDataClient.getBlockAtSlotExact(any()))
        .thenAnswer(
            invocationOnMock -> {
              final UInt64 slot = invocationOnMock.getArgument(0);
              return SafeFuture.completedFuture(
                  chainBuilder.getBlock(chainBuilder.getBlockAtSlot(slot).getRoot()));
            });
  }

  private void stubChainHead(final UInt64 headSlot) {
    final ChainHead chainHead = mock(ChainHead.class);
    when(chainHead.getSlot()).thenReturn(headSlot);
    when(combinedChainDataClient.getChainHead()).thenReturn(Optional.of(chainHead));
  }

  private List<SignedExecutionPayloadEnvelope> buildChain(final int chainSize) {
    // Create some blocks to request
    final UInt64 latestSlot = chainBuilder.getLatestSlot();
    chainBuilder.generateBlocksUpToSlot(latestSlot.plus(chainSize));

    return chainBuilder.streamExecutionPayloads(latestSlot.plus(1)).toList();
  }

  private long getLabelledCounterValue(final String label) {
    return metricsSystem.getLabelledCounterValue(
        NETWORK, "rpc_execution_payload_envelopes_by_range_requests_total", label);
  }

  private long getExecutionPayloadEnvelopesRequestedCounterValue() {
    return metricsSystem.getCounterValue(
        TekuMetricCategory.NETWORK,
        "rpc_execution_payload_envelopes_by_range_requested_envelopes_total");
  }
}
