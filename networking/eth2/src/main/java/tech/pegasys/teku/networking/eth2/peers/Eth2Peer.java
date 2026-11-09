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

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.DataColumnsByRootIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.RpcRequest;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.RpcRequestBodySelector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.bodyselector.SingleRpcRequestBodySelector;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.metadata.MetadataMessage;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;

public interface Eth2Peer extends Peer, SyncSource {
  static Eth2Peer create(
      final Spec spec,
      final Peer peer,
      final Optional<UInt256> discoveryNodeId,
      final BeaconChainMethods rpcMethods,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final PeerChainValidator peerChainValidator,
      final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator,
      final RateTracker blocksRequestTracker,
      final RateTracker blobSidecarsRequestTracker,
      final RateTracker dataColumnSidecarsRequestTracker,
      final RateTracker executionPayloadEnvelopesRequestTracker,
      final RateTracker lightClientUpdatesRequestTracker,
      final RateTracker requestTracker,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider,
      final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider) {
    return new DefaultEth2Peer(
        spec,
        peer,
        discoveryNodeId,
        rpcMethods,
        statusMessageFactory,
        metadataMessagesFactory,
        peerChainValidator,
        dataColumnSidecarSignatureValidator,
        blocksRequestTracker,
        blobSidecarsRequestTracker,
        dataColumnSidecarsRequestTracker,
        executionPayloadEnvelopesRequestTracker,
        lightClientUpdatesRequestTracker,
        requestTracker,
        metricsSystem,
        timeProvider,
        blobKzgCommitmentsProvider);
  }

  void updateStatus(PeerStatus status);

  void updateMetadataSeqNumber(UInt64 seqNumber);

  void subscribeInitialStatus(PeerStatusSubscriber subscriber);

  void subscribeStatusUpdates(PeerStatusSubscriber subscriber);

  void subscribeMetadataUpdates(PeerMetadataUpdateSubscriber subscriber);

  PeerStatus getStatus();

  Optional<SszBitvector> getRemoteAttestationSubnets();

  UInt64 finalizedEpoch();

  Checkpoint finalizedCheckpoint();

  int getOutstandingRequests();

  boolean hasStatus();

  SafeFuture<PeerStatus> sendStatus();

  SafeFuture<Void> sendGoodbye(UInt64 reason);

  SafeFuture<Void> requestBlocksByRoot(
      List<Bytes32> blockRoots, RpcResponseListener<SignedBeaconBlock> listener)
      throws RpcException;

  SafeFuture<Void> requestBlobSidecarsByRoot(
      List<BlobIdentifier> blobIdentifiers, RpcResponseListener<BlobSidecar> listener);

  SafeFuture<Void> requestDataColumnSidecarsByRoot(
      List<DataColumnsByRootIdentifier> dataColumnIdentifiers,
      RpcResponseListener<DataColumnSidecar> listener);

  SafeFuture<Void> requestExecutionPayloadEnvelopesByRoot(
      List<Bytes32> beaconBlockRoots, RpcResponseListener<SignedExecutionPayloadEnvelope> listener);

  SafeFuture<Optional<SignedBeaconBlock>> requestBlockBySlot(UInt64 slot);

  SafeFuture<Optional<SignedBeaconBlock>> requestBlockByRoot(Bytes32 blockRoot);

  SafeFuture<Optional<BlobSidecar>> requestBlobSidecarByRoot(BlobIdentifier blobIdentifier);

  SafeFuture<MetadataMessage> requestMetadata();

  default <I extends RpcRequest, O extends SszData> SafeFuture<O> requestSingleItem(
      final Eth2RpcMethod<I, O> method, final I request) {
    return requestSingleItem(method, new SingleRpcRequestBodySelector<>(request));
  }

  <I extends RpcRequest, O extends SszData> SafeFuture<O> requestSingleItem(
      final Eth2RpcMethod<I, O> method, final RpcRequestBodySelector<I> requestBodySelector);

  <T> Optional<RequestKey> approveObjectsRequest(
      RequestObject requestObject, ResponseCallback<T> callback, long objectsCount);

  void adjustObjectsRequest(
      RequestObject requestObject, RequestKey requestKey, long returnedObjectsCount);

  default Optional<RequestKey> approveBlocksRequest(
      final ResponseCallback<SignedBeaconBlock> callback, final long blocksCount) {
    return approveObjectsRequest(RequestObject.BLOCK, callback, blocksCount);
  }

  default void adjustBlocksRequest(final RequestKey requestKey, final long returnedBlocksCount) {
    adjustObjectsRequest(RequestObject.BLOCK, requestKey, returnedBlocksCount);
  }

  default Optional<RequestKey> approveBlobSidecarsRequest(
      final ResponseCallback<BlobSidecar> callback, final long blobSidecarsCount) {
    return approveObjectsRequest(RequestObject.BLOB_SIDECAR, callback, blobSidecarsCount);
  }

  default void adjustBlobSidecarsRequest(
      final RequestKey requestKey, final long returnedBlobSidecarsCount) {
    adjustObjectsRequest(RequestObject.BLOB_SIDECAR, requestKey, returnedBlobSidecarsCount);
  }

  default Optional<RequestKey> approveDataColumnSidecarsRequest(
      final ResponseCallback<DataColumnSidecar> callback, final long dataColumnSidecarsCount) {
    return approveObjectsRequest(
        RequestObject.DATA_COLUMN_SIDECAR, callback, dataColumnSidecarsCount);
  }

  default void adjustDataColumnSidecarsRequest(
      final RequestKey requestKey, final long returnedDataColumnSidecarsCount) {
    adjustObjectsRequest(
        RequestObject.DATA_COLUMN_SIDECAR, requestKey, returnedDataColumnSidecarsCount);
  }

  default Optional<RequestKey> approveExecutionPayloadEnvelopesRequest(
      final ResponseCallback<SignedExecutionPayloadEnvelope> callback,
      final long executionPayloadEnvelopesCount) {
    return approveObjectsRequest(
        RequestObject.EXECUTION_PAYLOAD_ENVELOPE, callback, executionPayloadEnvelopesCount);
  }

  default void adjustExecutionPayloadEnvelopesRequest(
      final RequestKey requestKey, final long returnedExecutionPayloadEnvelopesCount) {
    adjustObjectsRequest(
        RequestObject.EXECUTION_PAYLOAD_ENVELOPE,
        requestKey,
        returnedExecutionPayloadEnvelopesCount);
  }

  default Optional<RequestKey> approveLightClientUpdatesRequest(
      final ResponseCallback<LightClientUpdate> callback, final long lightClientUpdatesCount) {
    return approveObjectsRequest(
        RequestObject.LIGHT_CLIENT_UPDATE, callback, lightClientUpdatesCount);
  }

  default void adjustLightClientUpdatesRequest(
      final RequestKey requestKey, final long returnedLightClientUpdatesCount) {
    adjustObjectsRequest(
        RequestObject.LIGHT_CLIENT_UPDATE, requestKey, returnedLightClientUpdatesCount);
  }

  long getAvailableDataColumnSidecarsRequestCount();

  boolean approveRequest();

  SafeFuture<UInt64> sendPing();

  int getUnansweredPingCount();

  Optional<UInt256> getDiscoveryNodeId();

  interface PeerStatusSubscriber {
    void onPeerStatus(final PeerStatus initialStatus);
  }

  @FunctionalInterface
  interface PeerMetadataUpdateSubscriber {

    /**
     * Sends the current peer metadata upon subscription if metadata has been received already. Then
     * calls the method any time the peer metadata is updated
     */
    void onPeerMetadataUpdate(Eth2Peer peer, MetadataMessage metadata);
  }
}
