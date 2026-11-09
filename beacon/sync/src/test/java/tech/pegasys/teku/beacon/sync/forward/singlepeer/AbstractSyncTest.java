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

package tech.pegasys.teku.beacon.sync.forward.singlepeer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.PeerStatus;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.block.BlockImporter;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.storage.client.RecentChainData;

public abstract class AbstractSyncTest {

  protected final Spec spec = getSpec();
  protected final Eth2Peer peer = mock(Eth2Peer.class);
  protected final BlockImporter blockImporter = mock(BlockImporter.class);
  protected final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  protected final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPool.class);
  protected final ExecutionPayloadManager executionPayloadManager =
      mock(ExecutionPayloadManager.class);
  protected final RecentChainData recentChainData = mock(RecentChainData.class);

  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  protected final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @BeforeEach
  public void setup() {
    when(recentChainData.getSpec()).thenReturn(spec);
  }

  protected Spec getSpec() {
    return TestSpecFactory.createDefault();
  }

  @SuppressWarnings("unchecked")
  protected final ArgumentCaptor<RpcResponseListener<SignedBeaconBlock>>
      blockResponseListenerArgumentCaptor = ArgumentCaptor.forClass(RpcResponseListener.class);

  @SuppressWarnings("unchecked")
  protected final ArgumentCaptor<RpcResponseListener<BlobSidecar>>
      blobSidecarResponseListenerArgumentCaptor =
          ArgumentCaptor.forClass(RpcResponseListener.class);

  @SuppressWarnings("unchecked")
  protected final ArgumentCaptor<RpcResponseListener<SignedExecutionPayloadEnvelope>>
      executionPayloadResponseListenerArgumentCaptor =
          ArgumentCaptor.forClass(RpcResponseListener.class);

  protected void completeRequestWithBlockAtSlot(final SafeFuture<Void> request, final UInt64 slot) {
    completeRequestWithBlocksAtSlots(request, slot, UInt64.ONE);
  }

  protected void completeRequestWithBlocksAtSlots(
      final SafeFuture<Void> request, final UInt64 startSlot, final UInt64 count) {
    // Capture latest response listener
    verify(peer, atLeastOnce())
        .requestBlocksByRange(any(), any(), blockResponseListenerArgumentCaptor.capture());
    final RpcResponseListener<SignedBeaconBlock> responseListener =
        blockResponseListenerArgumentCaptor.getValue();
    final List<SignedBeaconBlock> blocks =
        respondWithBlocksAtSlots(request, responseListener, startSlot, count);
    for (final SignedBeaconBlock block : blocks) {
      verify(blockImporter).importBlock(block);
    }
    request.complete(null);
    asyncRunner.executeQueuedActions();
  }

  protected Map<UInt64, List<BlobSidecar>> completeRequestWithBlobSidecarsAtSlots(
      final SafeFuture<Void> request, final UInt64 startSlot, final UInt64 count) {
    // Capture latest response listener
    verify(peer, atLeastOnce())
        .requestBlobSidecarsByRange(
            any(), any(), blobSidecarResponseListenerArgumentCaptor.capture());
    final RpcResponseListener<BlobSidecar> responseListener =
        blobSidecarResponseListenerArgumentCaptor.getValue();
    final Map<UInt64, List<BlobSidecar>> blobSidecarsBySlot =
        respondWithBlobSidecarsAtSlots(request, responseListener, startSlot, count);
    request.complete(null);
    asyncRunner.executeQueuedActions();
    return blobSidecarsBySlot;
  }

  protected Map<UInt64, SignedExecutionPayloadEnvelope> completeRequestWithExecutionPayloadsAtSlots(
      final SafeFuture<Void> request, final UInt64 startSlot, final UInt64 count) {
    // Capture latest response listener
    verify(peer, atLeastOnce())
        .requestExecutionPayloadEnvelopesByRange(
            any(), any(), executionPayloadResponseListenerArgumentCaptor.capture());
    final RpcResponseListener<SignedExecutionPayloadEnvelope> responseListener =
        executionPayloadResponseListenerArgumentCaptor.getValue();
    final Map<UInt64, SignedExecutionPayloadEnvelope> executionPayloadsBySlot =
        respondWithExecutionPayloadsAtSlots(request, responseListener, startSlot, count);
    request.complete(null);
    asyncRunner.executeQueuedActions();
    return executionPayloadsBySlot;
  }

  protected List<SignedBeaconBlock> respondWithBlocksAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<SignedBeaconBlock> responseListener,
      final UInt64 startSlot,
      final UInt64 count) {
    return respondWithBlocksAtSlots(request, responseListener, getSlotsRange(startSlot, count));
  }

  protected List<SignedBeaconBlock> respondWithBlocksAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<SignedBeaconBlock> responseListener,
      final UInt64... slots) {
    final List<SignedBeaconBlock> blocks = new ArrayList<>();
    for (final UInt64 slot : slots) {
      final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
      blocks.add(block);
      responseListener.onResponse(block).propagateExceptionTo(request);
    }
    return blocks;
  }

  protected Map<UInt64, List<BlobSidecar>> respondWithBlobSidecarsAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<BlobSidecar> responseListener,
      final UInt64 startSlot,
      final UInt64 count) {
    return respondWithBlobSidecarsAtSlots(
        request, responseListener, getSlotsRange(startSlot, count));
  }

  protected Map<UInt64, List<BlobSidecar>> respondWithBlobSidecarsAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<BlobSidecar> responseListener,
      final UInt64... slots) {
    final Map<UInt64, List<BlobSidecar>> blobSidecarsBySlot = new HashMap<>();
    for (final UInt64 slot : slots) {
      final BlobSidecar blobSidecar =
          dataStructureUtil
              .createRandomBlobSidecarBuilder()
              .signedBeaconBlockHeader(dataStructureUtil.randomSignedBeaconBlockHeader(slot))
              .build();
      blobSidecarsBySlot.computeIfAbsent(slot, __ -> new ArrayList<>()).add(blobSidecar);
      responseListener.onResponse(blobSidecar).propagateExceptionTo(request);
    }
    return blobSidecarsBySlot;
  }

  protected Map<UInt64, SignedExecutionPayloadEnvelope> respondWithExecutionPayloadsAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<SignedExecutionPayloadEnvelope> responseListener,
      final UInt64 startSlot,
      final UInt64 count) {
    return respondWithExecutionPayloadsAtSlots(
        request, responseListener, getSlotsRange(startSlot, count));
  }

  protected Map<UInt64, SignedExecutionPayloadEnvelope> respondWithExecutionPayloadsAtSlots(
      final SafeFuture<Void> request,
      final RpcResponseListener<SignedExecutionPayloadEnvelope> responseListener,
      final UInt64... slots) {
    final Map<UInt64, SignedExecutionPayloadEnvelope> executionPayloadsBySlot = new HashMap<>();
    for (final UInt64 slot : slots) {
      final SignedExecutionPayloadEnvelope executionPayload =
          dataStructureUtil.randomSignedExecutionPayloadEnvelope(slot.longValue());
      executionPayloadsBySlot.put(slot, executionPayload);
      responseListener.onResponse(executionPayload).propagateExceptionTo(request);
    }
    return executionPayloadsBySlot;
  }

  protected PeerStatus withPeerHeadSlot(
      final UInt64 peerHeadSlot, final UInt64 peerFinalizedEpoch, final Bytes32 peerHeadBlockRoot) {
    final StatusMessage statusMessage =
        spec.atSlot(peerHeadSlot)
            .getSchemaDefinitions()
            .getStatusMessageSchema()
            .create(
                Bytes4.leftPad(Bytes.EMPTY),
                Bytes32.ZERO,
                peerFinalizedEpoch,
                peerHeadBlockRoot,
                peerHeadSlot,
                Optional.of(UInt64.ZERO));

    final PeerStatus peerStatus = PeerStatus.fromStatusMessage(statusMessage);

    when(peer.getStatus()).thenReturn(peerStatus);
    return peerStatus;
  }

  protected UInt64[] getSlotsRange(final UInt64 startSlot, final UInt64 count) {
    return UInt64.range(startSlot, startSlot.plus(count)).toArray(UInt64[]::new);
  }
}
