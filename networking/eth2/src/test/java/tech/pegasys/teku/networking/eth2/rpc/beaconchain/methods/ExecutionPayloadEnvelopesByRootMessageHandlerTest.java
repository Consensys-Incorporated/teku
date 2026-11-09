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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
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
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.ExecutionPayloadEnvelopesByRootRequestMessage;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

class ExecutionPayloadEnvelopesByRootMessageHandlerTest {

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());

  private static final String V2_PROTOCOL_ID =
      BeaconChainMethodIds.getExecutionPayloadEnvelopesByRootMethodId(1, RPC_ENCODING);

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  final ExecutionPayloadEnvelopesByRootMessageHandler handler =
      new ExecutionPayloadEnvelopesByRootMessageHandler(
          spec, combinedChainDataClient, metricsSystem);

  final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  final ResponseCallback<SignedExecutionPayloadEnvelope> callback = mock(ResponseCallback.class);

  @BeforeEach
  public void setup() {
    chainBuilder.generateGenesis();
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveExecutionPayloadEnvelopesRequest(any(), anyLong()))
        .thenReturn(Optional.of(new RequestKey(ZERO, 42)));
    // Slot is unknown by default, so the pre-flight range check lets every root through
    when(combinedChainDataClient.getSlotByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    // Forward execution payload envelope requests from the mock to the ChainBuilder
    when(combinedChainDataClient.getExecutionPayloadByBlockRoot(any()))
        .thenAnswer(
            i -> SafeFuture.completedFuture(chainBuilder.getExecutionPayload(i.getArgument(0))));
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);
  }

  @Test
  public void checkPeerIsRateLimited_requestNotApproved() {
    when(peer.approveRequest()).thenReturn(false);

    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(2);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(executionPayloadEnvelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // Requesting 2 execution payload envelopes
    verify(peer).approveExecutionPayloadEnvelopesRequest(any(), eq(2L));

    verify(callback, never()).completeSuccessfully();
    verify(callback, never()).respond(any());

    // verify counters
    assertThat(getRequestCounterValueForLabel("rate_limited")).isOne();
    assertThat(getRequestCounterValueForLabel("ok")).isZero();
  }

  @Test
  public void checkPeerIsRateLimited_noRequestKey() {
    when(peer.approveExecutionPayloadEnvelopesRequest(eq(callback), eq(2L)))
        .thenReturn(Optional.empty());

    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(2);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(executionPayloadEnvelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback, never()).completeSuccessfully();
    verify(callback, never()).respond(any());

    // verify counters
    assertThat(getRequestCounterValueForLabel("rate_limited")).isOne();
    assertThat(getRequestCounterValueForLabel("ok")).isZero();
  }

  @Test
  public void onIncomingMessage_respondsWithAllExecutionPayloadEnvelopes() {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(5);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(executionPayloadEnvelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // Requesting 5 execution payload envelopes
    verify(peer)
        .approveExecutionPayloadEnvelopesRequest(
            any(), eq(Long.valueOf(executionPayloadEnvelopes.size())));
    // Sending 5 execution payload envelopes: No rate limiter adjustment required
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());

    verify(callback, times(5)).respond(any());

    for (final SignedExecutionPayloadEnvelope executionPayloadEnvelope :
        executionPayloadEnvelopes) {
      verify(callback).respond(executionPayloadEnvelope);
    }

    verify(callback).completeSuccessfully();

    // verify counters
    assertThat(getRequestCounterValueForLabel("ok")).isOne();
    assertThat(getExecutionPayloadEnvelopesRequestedCounterValue()).isEqualTo(5);
  }

  @Test
  public void onIncomingMessage_respondsWithFinalizedEnvelopesViaUnblindingPath() {
    // Simulates the finalized lookup path
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(3);
    final Map<Bytes32, SignedExecutionPayloadEnvelope> envelopesByRoot =
        executionPayloadEnvelopes.stream()
            .collect(
                Collectors.toMap(
                    SignedExecutionPayloadEnvelope::getBeaconBlockRoot, Function.identity()));
    // Replace the default stub with one that returns a future that only completes after the
    // handler has invoked the async unblinding path to mimic the DB + EL latency
    final List<SafeFuture<Optional<SignedExecutionPayloadEnvelope>>> pendingLookups =
        new ArrayList<>();
    when(combinedChainDataClient.getExecutionPayloadByBlockRoot(any()))
        .thenAnswer(
            invocationOnMock -> {
              final SafeFuture<Optional<SignedExecutionPayloadEnvelope>> future =
                  new SafeFuture<>();
              pendingLookups.add(future);
              future.complete(
                  Optional.ofNullable(
                      envelopesByRoot.get((Bytes32) invocationOnMock.getArgument(0))));
              return future;
            });

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(executionPayloadEnvelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    assertThat(pendingLookups).hasSize(3);
    verify(callback, times(3)).respond(any());
    for (final SignedExecutionPayloadEnvelope envelope : executionPayloadEnvelopes) {
      verify(callback).respond(envelope);
    }
    verify(callback).completeSuccessfully();
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());
    assertThat(getRequestCounterValueForLabel("ok")).isOne();
  }

  @Test
  public void onIncomingMessage_respondsWithSomeOfTheExecutionPayloadEnvelopes() {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = buildChain(4);

    final List<Bytes32> beaconBlockRoots =
        new ArrayList<>(
            executionPayloadEnvelopes.stream()
                .map(SignedExecutionPayloadEnvelope::getMessage)
                .map(ExecutionPayloadEnvelope::getBeaconBlockRoot)
                .toList());

    beaconBlockRoots.add(Bytes32.random());

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromBeaconBlockRoots(beaconBlockRoots);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

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
    assertThat(getRequestCounterValueForLabel("ok")).isOne();
    assertThat(getExecutionPayloadEnvelopesRequestedCounterValue()).isEqualTo(5);
  }

  @Test
  public void onIncomingMessage_shouldSkipEnvelopesOutsideServableRange() {
    final UInt64 minEpochsForBlockRequests =
        UInt64.valueOf(spec.getNetworkingConfig().getMinEpochsForBlockRequests());

    final UInt64 currentEpoch = minEpochsForBlockRequests.plus(10);

    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(2);
    final SignedExecutionPayloadEnvelope oldEnvelope = envelopes.get(0);
    final SignedExecutionPayloadEnvelope newEnvelope = envelopes.get(1);

    final SignedExecutionPayloadEnvelope mockedOldEnvelope =
        mock(SignedExecutionPayloadEnvelope.class);
    final ExecutionPayloadEnvelope mockedOldMessage = mock(ExecutionPayloadEnvelope.class);
    final UInt64 oldSlot =
        spec.computeStartSlotAtEpoch(currentEpoch.minus(minEpochsForBlockRequests).minus(1));
    when(mockedOldEnvelope.getMessage()).thenReturn(mockedOldMessage);
    when(mockedOldMessage.getSlot()).thenReturn(oldSlot);
    when(mockedOldMessage.getBeaconBlockRoot())
        .thenReturn(oldEnvelope.getMessage().getBeaconBlockRoot());

    final SignedExecutionPayloadEnvelope mockedNewEnvelope =
        mock(SignedExecutionPayloadEnvelope.class);
    final ExecutionPayloadEnvelope mockedNewMessage = mock(ExecutionPayloadEnvelope.class);
    final UInt64 newSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    when(mockedNewEnvelope.getMessage()).thenReturn(mockedNewMessage);
    when(mockedNewMessage.getSlot()).thenReturn(newSlot);
    when(mockedNewMessage.getBeaconBlockRoot())
        .thenReturn(newEnvelope.getMessage().getBeaconBlockRoot());

    when(combinedChainDataClient.getExecutionPayloadByBlockRoot(
            oldEnvelope.getMessage().getBeaconBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(mockedOldEnvelope)));
    when(combinedChainDataClient.getExecutionPayloadByBlockRoot(
            newEnvelope.getMessage().getBeaconBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(mockedNewEnvelope)));

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromBeaconBlockRoots(
            List.of(
                oldEnvelope.getMessage().getBeaconBlockRoot(),
                newEnvelope.getMessage().getBeaconBlockRoot()));

    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback).respond(mockedNewEnvelope);
    verify(callback, never()).respond(mockedOldEnvelope);
    verify(callback).completeSuccessfully();
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(1L));
  }

  @Test
  public void onIncomingMessage_shouldServeEnvelopeAtExactMinServableEpoch() {
    final UInt64 minEpochsForBlockRequests =
        UInt64.valueOf(spec.getNetworkingConfig().getMinEpochsForBlockRequests());
    final UInt64 currentEpoch = minEpochsForBlockRequests.plus(10);

    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(1);
    final SignedExecutionPayloadEnvelope envelope = envelopes.get(0);

    // gloasForkEpoch = 0 in createMinimalGloas(), so minServableEpoch = max(10, 0) = 10
    final UInt64 exactBoundarySlot =
        spec.computeStartSlotAtEpoch(currentEpoch.minus(minEpochsForBlockRequests));

    final SignedExecutionPayloadEnvelope mockedEnvelope =
        mock(SignedExecutionPayloadEnvelope.class);
    final ExecutionPayloadEnvelope mockedMessage = mock(ExecutionPayloadEnvelope.class);
    when(mockedEnvelope.getMessage()).thenReturn(mockedMessage);
    when(mockedMessage.getSlot()).thenReturn(exactBoundarySlot);
    when(mockedMessage.getBeaconBlockRoot()).thenReturn(envelope.getMessage().getBeaconBlockRoot());

    when(combinedChainDataClient.getExecutionPayloadByBlockRoot(
            envelope.getMessage().getBeaconBlockRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(mockedEnvelope)));

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromBeaconBlockRoots(List.of(envelope.getMessage().getBeaconBlockRoot()));
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback).respond(mockedEnvelope);
    verify(callback).completeSuccessfully();
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());
  }

  @Test
  public void onIncomingMessage_shouldNotRetrieveEnvelopesForRootsOutsideServableRange() {
    final UInt64 minEpochsForBlockRequests =
        UInt64.valueOf(spec.getNetworkingConfig().getMinEpochsForBlockRequests());
    final UInt64 currentEpoch = minEpochsForBlockRequests.plus(10);

    when(recentChainData.getCurrentEpoch()).thenReturn(Optional.of(currentEpoch));

    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(2);
    final Bytes32 oldRoot = envelopes.get(0).getMessage().getBeaconBlockRoot();
    final Bytes32 newRoot = envelopes.get(1).getMessage().getBeaconBlockRoot();

    final UInt64 oldSlot =
        spec.computeStartSlotAtEpoch(currentEpoch.minus(minEpochsForBlockRequests).minus(1));
    final UInt64 newSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    when(combinedChainDataClient.getSlotByBlockRoot(oldRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(oldSlot)));
    when(combinedChainDataClient.getSlotByBlockRoot(newRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(newSlot)));

    final SignedExecutionPayloadEnvelope mockedNewEnvelope =
        mock(SignedExecutionPayloadEnvelope.class);
    final ExecutionPayloadEnvelope mockedNewMessage = mock(ExecutionPayloadEnvelope.class);
    when(mockedNewEnvelope.getMessage()).thenReturn(mockedNewMessage);
    when(mockedNewMessage.getSlot()).thenReturn(newSlot);
    when(combinedChainDataClient.getExecutionPayloadByBlockRoot(newRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(mockedNewEnvelope)));

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromBeaconBlockRoots(List.of(oldRoot, newRoot));

    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // the out of range root is dropped without touching the database or the execution layer
    verify(combinedChainDataClient, never()).getExecutionPayloadByBlockRoot(oldRoot);
    verify(combinedChainDataClient).getExecutionPayloadByBlockRoot(newRoot);
    verify(callback).respond(mockedNewEnvelope);
    verify(callback).completeSuccessfully();
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(1L));
  }

  private ExecutionPayloadEnvelopesByRootRequestMessage createRequestFromExecutionPayloadEnvelopes(
      final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes) {
    final List<Bytes32> beaconBlockRoots =
        executionPayloadEnvelopes.stream()
            .map(SignedExecutionPayloadEnvelope::getMessage)
            .map(ExecutionPayloadEnvelope::getBeaconBlockRoot)
            .toList();
    return createRequestFromBeaconBlockRoots(beaconBlockRoots);
  }

  private ExecutionPayloadEnvelopesByRootRequestMessage createRequestFromBeaconBlockRoots(
      final List<Bytes32> beaconBlockRoots) {
    return new ExecutionPayloadEnvelopesByRootRequestMessage(
        SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
            .getExecutionPayloadEnvelopesByRootRequestMessageSchema(),
        beaconBlockRoots);
  }

  private List<SignedExecutionPayloadEnvelope> buildChain(final int chainSize) {
    // Create some blocks to request
    final UInt64 latestSlot = chainBuilder.getLatestSlot();
    chainBuilder.generateBlocksUpToSlot(latestSlot.plus(chainSize));

    return chainBuilder.streamExecutionPayloads(latestSlot.plus(1)).toList();
  }

  private long getRequestCounterValueForLabel(final String label) {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.NETWORK,
        "rpc_execution_payload_envelopes_by_root_requests_total",
        label);
  }

  private long getExecutionPayloadEnvelopesRequestedCounterValue() {
    return metricsSystem.getCounterValue(
        TekuMetricCategory.NETWORK,
        "rpc_execution_payload_envelopes_by_root_requested_envelopes_total");
  }
}
