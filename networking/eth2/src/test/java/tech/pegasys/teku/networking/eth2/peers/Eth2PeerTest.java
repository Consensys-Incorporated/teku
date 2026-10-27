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

package tech.pegasys.teku.networking.eth2.peers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer.PeerStatusSubscriber;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.p2p.peer.DisconnectReason;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcRequestHandler;
import tech.pegasys.teku.networking.p2p.rpc.RpcStreamController;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.SingleRpcRequestBodySelector;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;

class Eth2PeerTest {

  private final UInt64 denebForkEpoch = UInt64.ONE;
  private final Spec spec = TestSpecFactory.createMinimalWithDenebForkEpoch(denebForkEpoch);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final Peer delegate = mock(Peer.class);
  private final BeaconChainMethods rpcMethods = mock(BeaconChainMethods.class);
  private final StatusMessageFactory statusMessageFactory = mock(StatusMessageFactory.class);
  private final MetadataMessagesFactory metadataMessagesFactory =
      mock(MetadataMessagesFactory.class);
  private final PeerChainValidator peerChainValidator = mock(PeerChainValidator.class);
  private final RateTracker blocksRateTracker = mock(RateTracker.class);
  private final RateTracker blobSidecarsRateTracker = mock(RateTracker.class);
  private final RateTracker dataColumnSidecarsRateTracker = mock(RateTracker.class);
  private final RateTracker executionPayloadEnvelopesRateTracker = mock(RateTracker.class);
  private final RateTracker lightClientUpdatesRateTracker = mock(RateTracker.class);
  private final RateTracker rateTracker = mock(RateTracker.class);
  private final MetricsSystem metricsSystem = mock(MetricsSystem.class);
  private final TimeProvider timeProvider = mock(TimeProvider.class);
  private final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator =
      mock(DataColumnSidecarSignatureValidator.class);
  private final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider =
      mock(BlobKzgCommitmentsProvider.class);

  private final PeerStatus randomPeerStatus = randomPeerStatus();

  private final Eth2Peer peer =
      Eth2Peer.create(
          spec,
          delegate,
          Optional.empty(),
          rpcMethods,
          statusMessageFactory,
          metadataMessagesFactory,
          peerChainValidator,
          dataColumnSidecarSignatureValidator,
          blocksRateTracker,
          blobSidecarsRateTracker,
          dataColumnSidecarsRateTracker,
          executionPayloadEnvelopesRateTracker,
          lightClientUpdatesRateTracker,
          rateTracker,
          metricsSystem,
          timeProvider,
          blobKzgCommitmentsProvider);

  @Test
  void updateStatus_shouldNotUpdateUntilValidationPasses() {
    final PeerStatusSubscriber peerStatusSubscriber = mock(PeerStatusSubscriber.class);
    peer.subscribeInitialStatus(peerStatusSubscriber);
    final SafeFuture<Boolean> validationResult = new SafeFuture<>();
    when(peerChainValidator.validate(peer, randomPeerStatus)).thenReturn(validationResult);

    peer.updateStatus(randomPeerStatus);

    verify(peerChainValidator).validate(peer, randomPeerStatus);
    assertThat(peer.hasStatus()).isFalse();
    verifyNoInteractions(peerStatusSubscriber);

    validationResult.complete(true);
    assertThat(peer.hasStatus()).isTrue();
    assertThat(peer.getStatus()).isEqualTo(randomPeerStatus);
    verify(peerStatusSubscriber).onPeerStatus(randomPeerStatus);
  }

  @Test
  void shouldCallCheckPeerIdentity() {
    final SafeFuture<Boolean> validationResult = new SafeFuture<>();
    when(peerChainValidator.validate(peer, randomPeerStatus)).thenReturn(validationResult);
    peer.updateStatus(randomPeerStatus);
    validationResult.complete(true);
    verify(delegate).checkPeerIdentity();
  }

  @Test
  void updateStatus_shouldNotUpdateStatusWhenValidationFails() {
    when(peerChainValidator.validate(peer, randomPeerStatus))
        .thenReturn(SafeFuture.completedFuture(false));

    peer.updateStatus(randomPeerStatus);

    assertThat(peer.hasStatus()).isFalse();
  }

  @Test
  void updateStatus_shouldNotUpdateSubsequentStatusWhenValidationFails() {
    final PeerStatus status2 = randomPeerStatus();
    when(peerChainValidator.validate(peer, randomPeerStatus))
        .thenReturn(SafeFuture.completedFuture(true));
    when(peerChainValidator.validate(peer, status2)).thenReturn(SafeFuture.completedFuture(false));

    peer.updateStatus(randomPeerStatus);

    assertThat(peer.hasStatus()).isTrue();

    peer.updateStatus(status2);

    // Status stays as the original peer status
    assertThat(peer.hasStatus()).isTrue();
    assertThat(peer.getStatus()).isEqualTo(randomPeerStatus);
  }

  @Test
  void updateStatus_shouldDisconnectPeerIfStatusValidationCompletesExceptionally() {
    when(peerChainValidator.validate(peer, randomPeerStatus))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Doh!")));

    peer.updateStatus(randomPeerStatus);

    assertThat(peer.hasStatus()).isFalse();
    verify(delegate).disconnectCleanly(DisconnectReason.UNABLE_TO_VERIFY_NETWORK);
  }

  @Test
  void updateStatus_shouldReportAllStatusesToSubscribers() {
    final PeerStatus status1 = randomPeerStatus();
    final PeerStatus status2 = randomPeerStatus();
    when(peerChainValidator.validate(any(), any())).thenReturn(SafeFuture.completedFuture(true));
    final PeerStatusSubscriber initialSubscriber = mock(PeerStatusSubscriber.class);
    final PeerStatusSubscriber subscriber = mock(PeerStatusSubscriber.class);

    peer.subscribeInitialStatus(initialSubscriber);
    peer.subscribeStatusUpdates(subscriber);

    peer.updateStatus(status1);

    verify(initialSubscriber).onPeerStatus(status1);
    verify(subscriber).onPeerStatus(status1);

    peer.updateStatus(status2);

    verify(initialSubscriber, never()).onPeerStatus(status2);
    verify(subscriber).onPeerStatus(status2);
  }

  @Test
  @SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
  public void shouldSendRequest_BlobSidecarsByRoot() {

    final Eth2RpcMethod<BlobSidecarsByRootRequestMessage, BlobSidecar> blobSidecarsByRootMethod =
        mock(Eth2RpcMethod.class);

    final RpcStreamController<RpcRequestHandler> rpcStreamController =
        mock(RpcStreamController.class);

    when(rpcMethods.blobSidecarsByRoot()).thenReturn(Optional.of(blobSidecarsByRootMethod));

    when(peer.sendRequest(any(), any(RpcRequest.class), any()))
        .thenReturn(SafeFuture.completedFuture(rpcStreamController));

    when(peer.sendRequest(any(), any(RpcRequestBodySelector.class), any()))
        .thenReturn(SafeFuture.completedFuture(rpcStreamController));

    final List<BlobIdentifier> blobIdentifiers =
        IntStream.range(0, 3).mapToObj(index -> dataStructureUtil.randomBlobIdentifier()).toList();

    peer.requestBlobSidecarsByRoot(blobIdentifiers, __ -> SafeFuture.COMPLETE);

    final ArgumentCaptor<RpcRequestBodySelector<BlobSidecarsByRootRequestMessage>> requestCaptor =
        ArgumentCaptor.forClass(RpcRequestBodySelector.class);

    verify(delegate, times(1)).sendRequest(any(), requestCaptor.capture(), any());

    final BlobSidecarsByRootRequestMessage request = resolveSingleRpcRequestBody(requestCaptor);

    assertThat(request.getMaximumResponseChunks()).isEqualTo(3);
  }

  @Test
  @SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
  public void shouldSendRequest_BlobSidecarByRoot() {

    final Eth2RpcMethod<BlobSidecarsByRootRequestMessage, BlobSidecar> blobSidecarsByRootMethod =
        mock(Eth2RpcMethod.class);

    final RpcStreamController<RpcRequestHandler> rpcStreamController =
        mock(RpcStreamController.class);

    when(rpcMethods.blobSidecarsByRoot()).thenReturn(Optional.of(blobSidecarsByRootMethod));

    when(peer.sendRequest(any(), any(RpcRequestBodySelector.class), any()))
        .thenReturn(SafeFuture.completedFuture(rpcStreamController));

    final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier();

    peer.requestBlobSidecarByRoot(blobIdentifier);

    final ArgumentCaptor<RpcRequestBodySelector<BlobSidecarsByRootRequestMessage>> requestCaptor =
        ArgumentCaptor.forClass(RpcRequestBodySelector.class);

    verify(delegate, times(1)).sendRequest(any(), requestCaptor.capture(), any());

    final BlobSidecarsByRootRequestMessage request = resolveSingleRpcRequestBody(requestCaptor);

    assertThat(request.getMaximumResponseChunks()).isEqualTo(1);
  }

  @Test
  @SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
  public void shouldNotUpdateRequestWhenWithinDeneb_BlobSidecarsByRange() {

    final Eth2RpcMethod<BlobSidecarsByRangeRequestMessage, BlobSidecar> blobSidecarsByRangeMethod =
        mock(Eth2RpcMethod.class);

    final RpcStreamController<RpcRequestHandler> rpcStreamController =
        mock(RpcStreamController.class);

    when(rpcMethods.blobSidecarsByRange()).thenReturn(Optional.of(blobSidecarsByRangeMethod));

    when(peer.sendRequest(any(), any(RpcRequestBodySelector.class), any()))
        .thenReturn(SafeFuture.completedFuture(rpcStreamController));

    final SpecConfigDeneb specConfigDeneb =
        SpecConfigDeneb.required(spec.forMilestone(SpecMilestone.DENEB).getConfig());
    final UInt64 denebForkEpoch = specConfigDeneb.getDenebForkEpoch();
    final UInt64 denebStartSlot = spec.computeStartSlotAtEpoch(denebForkEpoch);

    final UInt64 startSlot = denebStartSlot.plus(5);
    final UInt64 count = UInt64.valueOf(10);

    peer.requestBlobSidecarsByRange(startSlot, count, __ -> SafeFuture.COMPLETE);

    final ArgumentCaptor<RpcRequestBodySelector<BlobSidecarsByRangeRequestMessage>> requestCaptor =
        ArgumentCaptor.forClass(RpcRequestBodySelector.class);

    verify(delegate, times(1)).sendRequest(any(), requestCaptor.capture(), any());

    final BlobSidecarsByRangeRequestMessage request = resolveSingleRpcRequestBody(requestCaptor);

    assertThat(request.getStartSlot()).isEqualTo(startSlot);
    assertThat(request.getCount()).isEqualTo(count);
    assertThat(request.getMaximumResponseChunks()).isEqualTo(60); // 10 * MAX_BLOBS_PER_BLOCK (6)
  }

  @Test
  @SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
  public void shouldModifyRequestSpanningTheDenebForkTransition_BlobSidecarsByRange() {

    final Eth2RpcMethod<BlobSidecarsByRangeRequestMessage, BlobSidecar> blobSidecarsByRangeMethod =
        mock(Eth2RpcMethod.class);

    final RpcStreamController<RpcRequestHandler> rpcStreamController =
        mock(RpcStreamController.class);

    when(rpcMethods.blobSidecarsByRange()).thenReturn(Optional.of(blobSidecarsByRangeMethod));

    when(peer.sendRequest(any(), any(RpcRequestBodySelector.class), any()))
        .thenReturn(SafeFuture.completedFuture(rpcStreamController));

    peer.requestBlobSidecarsByRange(UInt64.ONE, UInt64.valueOf(13), __ -> SafeFuture.COMPLETE);

    final ArgumentCaptor<RpcRequestBodySelector<BlobSidecarsByRangeRequestMessage>> requestCaptor =
        ArgumentCaptor.forClass(RpcRequestBodySelector.class);

    verify(delegate, times(1)).sendRequest(any(), requestCaptor.capture(), any());

    final BlobSidecarsByRangeRequestMessage request = resolveSingleRpcRequestBody(requestCaptor);

    // Deneb starts from epoch 1, so request start slot should be 8 and the count should be 6
    assertThat(request.getStartSlot()).isEqualTo(UInt64.valueOf(8));
    assertThat(request.getCount()).isEqualTo(UInt64.valueOf(6));
    assertThat(request.getMaximumResponseChunks()).isEqualTo(36); // 6 * MAX_BLOBS_PER_BLOCK (6)
  }

  @Test
  @SuppressWarnings({"unchecked", "FutureReturnValueIgnored"})
  public void shouldSetCountToZeroWhenRequestIsPreDeneb_BlobSidecarsByRange() {

    final Eth2RpcMethod<BlobSidecarsByRangeRequestMessage, BlobSidecar> blobSidecarsByRangeMethod =
        mock(Eth2RpcMethod.class);

    final RpcStreamController<RpcRequestHandler> rpcStreamController =
        mock(RpcStreamController.class);

    when(rpcMethods.blobSidecarsByRange()).thenReturn(Optional.of(blobSidecarsByRangeMethod));

    when(peer.sendRequest(any(), any(RpcRequest.class), any()))
        .thenReturn(SafeFuture.completedFuture(rpcStreamController));

    peer.requestBlobSidecarsByRange(UInt64.ONE, UInt64.valueOf(7), __ -> SafeFuture.COMPLETE);

    verify(delegate, never()).sendRequest(any(), any(RpcRequest.class), any());
  }

  private <T extends RpcRequest> T resolveSingleRpcRequestBody(
      final ArgumentCaptor<RpcRequestBodySelector<T>> argumentCaptor) {
    final RpcRequestBodySelector<T> rpcRequestBodySelector = argumentCaptor.getValue();
    assertThat(rpcRequestBodySelector).isInstanceOf(SingleRpcRequestBodySelector.class);
    // For SingleRpcRequestBodySelector, the key applied to the function is irrelevant
    return rpcRequestBodySelector.getBody().apply("").orElseThrow();
  }

  private PeerStatus randomPeerStatus() {
    return new PeerStatus(
        dataStructureUtil.randomBytes4(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64(),
        Optional.of(dataStructureUtil.randomUInt64()));
  }
}
