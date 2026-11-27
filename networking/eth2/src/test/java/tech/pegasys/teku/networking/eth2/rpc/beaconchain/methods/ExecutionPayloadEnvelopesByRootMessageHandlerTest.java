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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.SERVER_ERROR_CODE;

import com.google.common.base.Throwables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.p2p.mock.MockNodeId;
import tech.pegasys.teku.networking.p2p.peer.NodeId;
import tech.pegasys.teku.networking.p2p.rpc.StreamClosedException;
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

  // Small batches so that batching is exercised with short requests
  private static final int UNBLINDING_BATCH_SIZE = 2;

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private static final NodeId PEER_ID = new MockNodeId(1);
  // 2 requests served at a time, 1 per peer, 1 waiting, 2 pending per peer
  private final ExecutionPayloadEnvelopesRequestLimiter requestLimiter =
      new ExecutionPayloadEnvelopesRequestLimiter(2, 1, 1, 2);

  final ExecutionPayloadEnvelopesByRootMessageHandler handler =
      new ExecutionPayloadEnvelopesByRootMessageHandler(
          spec, combinedChainDataClient, metricsSystem, requestLimiter, UNBLINDING_BATCH_SIZE);

  final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  final ResponseCallback<SignedExecutionPayloadEnvelope> callback = mock(ResponseCallback.class);

  private final SafeFuture<Void> streamClosed = new SafeFuture<>();

  @BeforeEach
  public void setup() {
    chainBuilder.generateGenesis();
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(peer.getId()).thenReturn(PEER_ID);
    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveExecutionPayloadEnvelopesRequest(any(), anyLong()))
        .thenReturn(Optional.of(new RequestKey(ZERO, 42)));
    // Slot is unknown by default, so the pre-flight range check lets every root through
    when(combinedChainDataClient.getSlotByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    // Forward batched execution payload envelope requests from the mock to the ChainBuilder
    when(combinedChainDataClient.getExecutionPayloadsByBlockRoots(any()))
        .thenAnswer(
            i -> SafeFuture.completedFuture(getExecutionPayloadsByBlockRoots(i.getArgument(0))));
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);
    when(callback.getStreamClosedFuture()).thenReturn(streamClosed);
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
    // Envelopes are never looked up one root at a time
    verify(combinedChainDataClient, never()).getExecutionPayloadByBlockRoot(any());

    verify(callback).completeSuccessfully();

    // verify counters
    assertThat(getRequestCounterValueForLabel("ok")).isOne();
    assertThat(getExecutionPayloadEnvelopesRequestedCounterValue()).isEqualTo(5);
  }

  @Test
  public void onIncomingMessage_shouldRetrieveEnvelopesInBatchesInRequestOrder() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(5);
    final List<Bytes32> roots =
        envelopes.stream().map(SignedExecutionPayloadEnvelope::getBeaconBlockRoot).toList();

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromBeaconBlockRoots(roots);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // One lookup per batch, each batch sent before the next is loaded
    final InOrder inOrder = inOrder(combinedChainDataClient, callback);
    inOrder
        .verify(combinedChainDataClient)
        .getExecutionPayloadsByBlockRoots(Set.of(roots.get(0), roots.get(1)));
    inOrder.verify(callback).respond(envelopes.get(0));
    inOrder.verify(callback).respond(envelopes.get(1));
    inOrder
        .verify(combinedChainDataClient)
        .getExecutionPayloadsByBlockRoots(Set.of(roots.get(2), roots.get(3)));
    inOrder.verify(callback).respond(envelopes.get(2));
    inOrder.verify(callback).respond(envelopes.get(3));
    inOrder.verify(combinedChainDataClient).getExecutionPayloadsByBlockRoots(Set.of(roots.get(4)));
    inOrder.verify(callback).respond(envelopes.get(4));
    inOrder.verify(callback).completeSuccessfully();
    verify(combinedChainDataClient, times(3)).getExecutionPayloadsByBlockRoots(any());
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());
  }

  @Test
  public void onIncomingMessage_shouldChargeRetrievedRootsEvenWhenNoEnvelopeIsFound() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(3);
    // The execution layer has no payload body for any of the requested roots
    doReturn(SafeFuture.completedFuture(Map.of()))
        .when(combinedChainDataClient)
        .getExecutionPayloadsByBlockRoots(any());

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(envelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback, never()).respond(any());
    verify(callback).completeSuccessfully();
    // Lookups were done for every root, so nothing is refunded
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());
  }

  @Test
  public void onIncomingMessage_shouldStopRetrievingEnvelopesWhenStreamIsClosed() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(4);
    // The peer resets the stream after the first batch has been sent: open at admission and
    // before the first batch, closed before the second
    when(callback.isClosed()).thenReturn(false, false, true);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(envelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback, times(2)).respond(any());
    verify(callback).respond(envelopes.get(0));
    verify(callback).respond(envelopes.get(1));
    // No further root is resolved or retrieved once the stream is closed
    verify(combinedChainDataClient, times(1)).getExecutionPayloadsByBlockRoots(any());
    verify(combinedChainDataClient, never())
        .getSlotByBlockRoot(envelopes.get(2).getBeaconBlockRoot());
    verify(callback, never()).completeSuccessfully();
    verify(callback).completeWithUnexpectedError(argThat(this::isStreamClosedError));
    // Charged only for the batch looked up before the stream closed
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(2L));
  }

  @Test
  public void onIncomingMessage_shouldRejectRequestWhenTooManyAreBeingServed() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(2);
    // Other peers' requests take every permit and every queue slot
    acquireNow(new MockNodeId(2));
    acquireNow(new MockNodeId(3));
    assertThat(requestLimiter.acquire(new MockNodeId(4))).isPresent();

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(envelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback)
        .completeWithErrorResponse(argThat(error -> error.getResponseCode() == SERVER_ERROR_CODE));
    verify(callback, never()).respond(any());
    verify(callback, never()).completeSuccessfully();
    // No work is done for the rejected request, so it is fully refunded
    verify(combinedChainDataClient, never()).getSlotByBlockRoot(any());
    verify(combinedChainDataClient, never()).getExecutionPayloadsByBlockRoots(any());
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(0L));
    assertThat(getRequestCounterValueForLabel("concurrency_limited")).isOne();
    assertThat(getRequestCounterValueForLabel("ok")).isZero();
  }

  @Test
  public void onIncomingMessage_shouldAdmitNextRequestOnceServed() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(2);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(envelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback).completeSuccessfully();
    assertThat(isAdmittedNow(PEER_ID)).isTrue();
  }

  @Test
  public void onIncomingMessage_shouldAdmitNextRequestOnceServingFailed() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(2);
    doReturn(SafeFuture.failedFuture(new RuntimeException("Execution layer unavailable")))
        .when(combinedChainDataClient)
        .getExecutionPayloadsByBlockRoots(any());

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(envelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback).completeWithUnexpectedError(any());
    assertThat(isAdmittedNow(PEER_ID)).isTrue();
  }

  @Test
  public void onIncomingMessage_shouldServeRootsHeldInMemoryWithoutAPermit() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(2);
    envelopes.forEach(
        envelope ->
            when(recentChainData.getExecutionPayloadIfAvailable(envelope.getBeaconBlockRoot()))
                .thenReturn(Optional.of(envelope)));
    // Other peers' requests take every permit and every queue slot
    acquireNow(new MockNodeId(2));
    acquireNow(new MockNodeId(3));
    assertThat(requestLimiter.acquire(new MockNodeId(4))).isPresent();

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(envelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    envelopes.forEach(envelope -> verify(callback).respond(envelope));
    verify(callback).completeSuccessfully();
    assertThat(getRequestCounterValueForLabel("concurrency_limited")).isZero();
  }

  @Test
  public void onIncomingMessage_shouldQueueRequestWhenPeerAlreadyHasItsShareOfRequests() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(2);
    // The limit has room, but this peer already has a request being served
    acquireNow(PEER_ID);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(envelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // Nothing is looked up while waiting
    verify(combinedChainDataClient, never()).getSlotByBlockRoot(any());
    verify(callback, never()).respond(any());
    verify(callback, never()).completeWithErrorResponse(any());
    assertThat(getRequestCounterValueForLabel("queued")).isOne();

    requestLimiter.release(PEER_ID);

    envelopes.forEach(envelope -> verify(callback).respond(envelope));
    verify(callback).completeSuccessfully();
  }

  @Test
  public void onIncomingMessage_shouldCancelQueuedRequestAsSoonAsItsStreamCloses() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(2);
    // This peer already has its share of requests being served
    acquireNow(PEER_ID);

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(envelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    streamClosed.complete(null);

    verify(callback).completeWithUnexpectedError(argThat(this::isStreamClosedError));
    verify(peer).adjustExecutionPayloadEnvelopesRequest(any(), eq(0L));
    verify(combinedChainDataClient, never()).getSlotByBlockRoot(any());
    // The peer's pending allowance is back: it can queue another request
    assertThat(requestLimiter.acquire(PEER_ID)).isPresent();
  }

  @Test
  public void onIncomingMessage_shouldResolveTheSlotsOfABatchInParallel() {
    final List<SignedExecutionPayloadEnvelope> envelopes = buildChain(4);
    final List<SafeFuture<Optional<UInt64>>> pendingLookups = new ArrayList<>();
    doAnswer(
            i -> {
              final SafeFuture<Optional<UInt64>> lookup = new SafeFuture<>();
              pendingLookups.add(lookup);
              return lookup;
            })
        .when(combinedChainDataClient)
        .getSlotByBlockRoot(any());

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromExecutionPayloadEnvelopes(envelopes);
    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // Both lookups of the first batch are issued before either completes
    assertThat(pendingLookups).hasSize(2);
    verify(combinedChainDataClient, never()).getExecutionPayloadsByBlockRoots(any());
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
    // Charged for all 5 roots looked up, though only 4 envelopes were found
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());

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

    stubExecutionPayloadsByBlockRoots(
        Map.of(
            oldEnvelope.getMessage().getBeaconBlockRoot(), mockedOldEnvelope,
            newEnvelope.getMessage().getBeaconBlockRoot(), mockedNewEnvelope));

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromBeaconBlockRoots(
            List.of(
                oldEnvelope.getMessage().getBeaconBlockRoot(),
                newEnvelope.getMessage().getBeaconBlockRoot()));

    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    verify(callback).respond(mockedNewEnvelope);
    verify(callback, never()).respond(mockedOldEnvelope);
    verify(callback).completeSuccessfully();
    // Both roots had unknown slots, so both were looked up and charged
    verify(peer, never()).adjustExecutionPayloadEnvelopesRequest(any(), anyLong());
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

    stubExecutionPayloadsByBlockRoots(
        Map.of(envelope.getMessage().getBeaconBlockRoot(), mockedEnvelope));

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
    stubExecutionPayloadsByBlockRoots(Map.of(newRoot, mockedNewEnvelope));

    final ExecutionPayloadEnvelopesByRootRequestMessage message =
        createRequestFromBeaconBlockRoots(List.of(oldRoot, newRoot));

    handler.onIncomingMessage(V2_PROTOCOL_ID, peer, message, callback);

    // the out of range root is dropped without touching the database or the execution layer
    verify(combinedChainDataClient).getExecutionPayloadsByBlockRoots(Set.of(newRoot));
    verify(combinedChainDataClient, times(1)).getExecutionPayloadsByBlockRoots(any());
    verify(callback).respond(mockedNewEnvelope);
    verify(callback).completeSuccessfully();
    // Only the retrieved root is charged for
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

  private Map<Bytes32, SignedExecutionPayloadEnvelope> getExecutionPayloadsByBlockRoots(
      final Set<Bytes32> blockRoots) {
    final Map<Bytes32, SignedExecutionPayloadEnvelope> result = new HashMap<>();
    blockRoots.forEach(
        blockRoot ->
            chainBuilder
                .getExecutionPayload(blockRoot)
                .ifPresent(envelope -> result.put(blockRoot, envelope)));
    return result;
  }

  /** Stubs the batched lookup with the given envelopes. */
  private void stubExecutionPayloadsByBlockRoots(
      final Map<Bytes32, SignedExecutionPayloadEnvelope> envelopesByRoot) {
    doAnswer(
            i -> {
              final Set<Bytes32> blockRoots = i.getArgument(0);
              final Map<Bytes32, SignedExecutionPayloadEnvelope> result = new HashMap<>();
              blockRoots.forEach(
                  blockRoot -> {
                    if (envelopesByRoot.containsKey(blockRoot)) {
                      result.put(blockRoot, envelopesByRoot.get(blockRoot));
                    }
                  });
              return SafeFuture.completedFuture(result);
            })
        .when(combinedChainDataClient)
        .getExecutionPayloadsByBlockRoots(any());
  }

  private void acquireNow(final NodeId peerId) {
    assertThat(isAdmittedNow(peerId)).isTrue();
  }

  private boolean isAdmittedNow(final NodeId peerId) {
    return requestLimiter.acquire(peerId).map(SafeFuture::isDone).orElse(false);
  }

  private boolean isStreamClosedError(final Throwable error) {
    return Throwables.getRootCause(error) instanceof StreamClosedException;
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
