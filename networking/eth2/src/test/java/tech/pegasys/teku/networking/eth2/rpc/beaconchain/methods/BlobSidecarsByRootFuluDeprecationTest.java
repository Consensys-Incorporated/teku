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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRootRequestMessageSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class BlobSidecarsByRootFuluDeprecationTest {
  private final UInt64 genesisTime = UInt64.valueOf(1982239L);
  private final UInt64 fuluForkEpoch = UInt64.valueOf(2);
  private BlobSidecarsByRootRequestMessageSchema messageSchema;
  private final ArgumentCaptor<BlobSidecar> blobSidecarCaptor =
      ArgumentCaptor.forClass(BlobSidecar.class);
  private final Optional<RequestKey> allowedObjectsRequest = Optional.of(new RequestKey(ZERO, 0));

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobSidecar> callback = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private String protocolId;
  private UInt64 fuluForkFirstSlot;
  private DataStructureUtil dataStructureUtil;
  private Spec spec;

  @BeforeEach
  public void setup() {
    spec =
        TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
            UInt64.ZERO, UInt64.ZERO, UInt64.ONE, fuluForkEpoch);
    dataStructureUtil = new DataStructureUtil(spec);
    messageSchema =
        spec.atEpoch(fuluForkEpoch)
            .getSchemaDefinitions()
            .toVersionFulu()
            .orElseThrow()
            .getBlobSidecarsByRootRequestMessageSchema();
    fuluForkFirstSlot = spec.computeStartSlotAtEpoch(fuluForkEpoch);
    final RpcEncoding rpcEncoding =
        RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());
    protocolId = BeaconChainMethodIds.getBlobSidecarsByRootMethodId(1, rpcEncoding);

    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveBlobSidecarsRequest(eq(callback), anyLong()))
        .thenReturn(allowedObjectsRequest);
    reset(combinedChainDataClient);
    when(combinedChainDataClient.getSlotByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(fuluForkFirstSlot)));
    // epoch 1 = electra is finalized, epochs 2+ not
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(spec.computeStartSlotAtEpoch(UInt64.ZERO)));
    when(combinedChainDataClient.getStore()).thenReturn(store);
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);

    // mock store
    when(store.getGenesisTime()).thenReturn(genesisTime);
    // current epoch is deneb fork epoch + 1
    when(store.getTimeSeconds())
        .thenReturn(
            spec.computeTimeAtSlot(
                fuluForkEpoch.increment().times(spec.getSlotsPerEpoch(ZERO)), genesisTime));
  }

  @Test
  public void byRootShouldSendResponseForCorrectRootsIfOneRootOutOfRange() {
    // Fulu is activated, but last finalized epoch is still pre-fulu. ByRoot requests for
    // non-finalized blobs must respond with the results for blocks between finalized and
    // fulu activation.
    final int maxBlobsPerBlock =
        SpecConfigDeneb.required(spec.forMilestone(SpecMilestone.DENEB).getConfig())
            .getMaxBlobsPerBlock();
    final Bytes32 firstBlockRoot = dataStructureUtil.randomBeaconState().hashTreeRoot();
    final Bytes32 secondBlockRoot = dataStructureUtil.randomBeaconState().hashTreeRoot();
    final Bytes32 thirdBlockRoot = dataStructureUtil.randomBeaconState().hashTreeRoot();
    final List<BlobIdentifier> blobIdentifiers =
        List.of(
            new BlobIdentifier(firstBlockRoot, dataStructureUtil.randomUInt64(maxBlobsPerBlock)),
            new BlobIdentifier(secondBlockRoot, dataStructureUtil.randomUInt64(maxBlobsPerBlock)),
            new BlobIdentifier(thirdBlockRoot, dataStructureUtil.randomUInt64(maxBlobsPerBlock)));

    final BlobSidecar firstBlobSidecar = mock(BlobSidecar.class);
    when(firstBlobSidecar.getSlot()).thenReturn(spec.computeStartSlotAtEpoch(UInt64.ONE));
    when(firstBlobSidecar.getBlockRoot()).thenReturn(firstBlockRoot);
    final BlobSidecar secondBlobSidecar = mock(BlobSidecar.class);
    when(secondBlobSidecar.getSlot())
        .thenReturn(spec.computeStartSlotAtEpoch(UInt64.ONE).increment());
    when(secondBlobSidecar.getBlockRoot()).thenReturn(secondBlockRoot);
    // let last blobSidecar slot has index after than fulu activation epoch
    final BlobSidecar thirdBlobSidecar = mock(BlobSidecar.class);
    when(thirdBlobSidecar.getSlot()).thenReturn(fuluForkFirstSlot);
    when(thirdBlobSidecar.getBlockRoot()).thenReturn(thirdBlockRoot);

    when(combinedChainDataClient.getBlobSidecarByBlockRootAndIndex(
            firstBlockRoot, blobIdentifiers.get(0).getIndex()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(firstBlobSidecar)));
    when(combinedChainDataClient.getBlobSidecarByBlockRootAndIndex(
            secondBlockRoot, blobIdentifiers.get(1).getIndex()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(secondBlobSidecar)));
    when(combinedChainDataClient.getBlobSidecarByBlockRootAndIndex(
            thirdBlockRoot, blobIdentifiers.get(2).getIndex()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(thirdBlobSidecar)));

    final BlobSidecarsByRootMessageHandler handler =
        new BlobSidecarsByRootMessageHandler(spec, metricsSystem, combinedChainDataClient);

    handler.onIncomingMessage(
        protocolId,
        peer,
        new BlobSidecarsByRootRequestMessage(messageSchema, blobIdentifiers),
        callback);

    // Requesting 3 blob sidecars
    verify(peer, times(1)).approveBlobSidecarsRequest(any(), eq(Long.valueOf(3)));
    // Sending 2 or 3 blob sidecars
    verify(peer, times(1))
        .adjustBlobSidecarsRequest(eq(allowedObjectsRequest.orElseThrow()), eq(Long.valueOf(2)));

    verify(combinedChainDataClient, times(1)).getSlotByBlockRoot(firstBlockRoot);
    verify(combinedChainDataClient, times(1)).getSlotByBlockRoot(secondBlockRoot);
    verify(combinedChainDataClient, times(1)).getSlotByBlockRoot(thirdBlockRoot);
    verify(callback, times(2)).respond(blobSidecarCaptor.capture());
    verify(callback).completeSuccessfully();

    final List<Bytes32> respondedBlobSidecarBlockRoots =
        blobSidecarCaptor.getAllValues().stream().map(BlobSidecar::getBlockRoot).toList();
    final List<Bytes32> blobIdentifiersBlockRoots =
        List.of(blobIdentifiers.get(0).getBlockRoot(), blobIdentifiers.get(1).getBlockRoot());
    assertThat(respondedBlobSidecarBlockRoots)
        .containsExactlyInAnyOrderElementsOf(blobIdentifiersBlockRoots);
  }
}
