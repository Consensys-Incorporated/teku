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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class BlobSidecarsByRangeFuluDeprecationTest {
  private final UInt64 genesisTime = UInt64.valueOf(1982239L);
  private final Optional<RequestKey> allowedObjectsRequest = Optional.of(new RequestKey(ZERO, 0));

  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());
  private final String protocolId =
      BeaconChainMethodIds.getBlobSidecarsByRangeMethodId(1, RPC_ENCODING);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobSidecar> callback = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private UInt64 fuluForkFirstSlot;
  private DataStructureUtil dataStructureUtil;
  private final Eth2Peer peer = mock(Eth2Peer.class);
  // ELECTRA live since epoch 0, FULU forks at epoch 2
  private final UInt64 fuluForkEpoch = UInt64.valueOf(2);
  private final Spec spec =
      TestSpecFactory.createMinimalFulu(
          builder ->
              builder
                  .fuluForkEpoch(fuluForkEpoch)
                  .denebBuilder(
                      // save blobs for 8 epochs instead of 4096 (test performance)
                      denebBuilder -> denebBuilder.minEpochsForBlobSidecarsRequests(8))
                  .fuluBuilder(
                      fuluBuilder ->
                          fuluBuilder

                              // save blobs for 8 epochs instead of 4096 (test performance)
                              .minEpochsForDataColumnSidecarsRequests(8)));
  private final UInt64 slotsPerEpoch = UInt64.valueOf(spec.getSlotsPerEpoch(ZERO));
  private final SpecConfigDeneb specConfigDeneb =
      SpecConfigDeneb.required(spec.forMilestone(SpecMilestone.ELECTRA).getConfig());
  private final int maxBlobsPerBlock = specConfigDeneb.getMaxBlobsPerBlock(); // 8

  @Test
  public void byRangeShouldSendToPeerPreFuluBlobSidecarsOnly() {

    // Current slot
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(UInt64.ONE).increment(); // slot 9
    final UInt64 count = slotsPerEpoch.times(2).plus(5); // count = 21
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(fuluForkEpoch.increment()); // slot 32

    // Requests for blobs from slots 9-30 where fulu forked in slot 16. Should return blobs from
    // slots 9-15 (inclusive)
    final UInt64 minSlotExpected = UInt64.valueOf(9);
    final UInt64 maxSlotExpected = UInt64.valueOf(15);
    runTestWith(startSlot, count, currentSlot, fuluForkEpoch, minSlotExpected, maxSlotExpected);
  }

  @Test
  public void byRangePreFuluStillInRange() {
    // ELECTRA live since epoch 0, FULU forks at epoch 2
    // Current slot 56, which is in epoch 7, 5 epochs after fulu < 8 epochs configured to preserve
    // blobs.
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(UInt64.ONE).increment(); // slot 9
    final UInt64 count = slotsPerEpoch.times(4); // count = 32
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(fuluForkEpoch.plus(5)); // slot 56

    // Requests for blobs from slots 9-30 where fulu forked in slot 16. Should return blobs from
    // slots 9-15 (inclusive)
    final UInt64 minSlotExpected = UInt64.valueOf(9);
    final UInt64 maxSlotExpected = UInt64.valueOf(15);
    runTestWith(startSlot, count, currentSlot, fuluForkEpoch, minSlotExpected, maxSlotExpected);
  }

  @Test
  public void byRangeShouldSendEmptyListToPeer() {
    // ELECTRA live since epoch 0, FULU forks at epoch 2
    // Current slot 96, which is 10 epochs after fulu > 8 epochs configured to preserve blobs ->
    // expect empty list
    final UInt64 startSlot = spec.computeStartSlotAtEpoch(UInt64.ONE).increment(); // slot 9
    final UInt64 count = slotsPerEpoch.times(4); // count = 32
    // current epoch is fulu fork epoch + 10 (> minEpochsForBlobSidecarsRequests=8)
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(fuluForkEpoch.plus(10)); // slot 96

    // Requests for blobs from slots 9-30 where fulu forked in slot 16. Should return no blobs.
    final UInt64 minSlotExpected = UInt64.MAX_VALUE;
    final UInt64 maxSlotExpected = UInt64.ZERO;
    runTestWith(startSlot, count, currentSlot, fuluForkEpoch, minSlotExpected, maxSlotExpected);
  }

  @Test
  public void byRangeStartSlotTooOldButRecentEnoughEndSlotShouldSendResponses() {
    // ELECTRA live since epoch 0, FULU forks at epoch 2
    // Current slot 72 epoch 9, which is 7 epochs after fulu < 8 epochs configured to preserve blobs
    // ->
    // But request start is slot 1, which is too old. Because count=16 the requested range is 1-17
    // Of these, slots 8-15 are in the range pre-fulu.
    final UInt64 startSlot = UInt64.ONE; // slot 9
    final UInt64 count = slotsPerEpoch.times(2); // count = 16
    // current epoch is fulu fork epoch + 10 (> minEpochsForBlobSidecarsRequests=8)
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(fuluForkEpoch.plus(7)); // slot 72

    // Requests for blobs from slots 1-16 where fulu forked in slot 16. Should return blobs from
    // slots 8-15.
    // because 1-7 are too old.
    final UInt64 minSlotExpected = UInt64.valueOf(8);
    final UInt64 maxSlotExpected = UInt64.valueOf(15);
    runTestWith(startSlot, count, currentSlot, fuluForkEpoch, minSlotExpected, maxSlotExpected);
  }

  @Test
  public void shouldIgnoreRequestsWhenStartSlotIsAfterFulu() {
    final UInt64 startSlot = spec.blobSidecarsDeprecationSlot().increment();
    final UInt64 count = slotsPerEpoch.times(2); // count = 16
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);
    final BlobSidecarsByRangeMessageHandler handler =
        new BlobSidecarsByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);

    when(peer.approveRequest()).thenReturn(true);

    handler.onIncomingMessage(protocolId, peer, request, callback);

    verify(peer, times(1)).approveRequest();
    verify(peer, never()).approveBlobSidecarsRequest(any(), anyLong());
    verify(callback, times(1)).completeSuccessfully();
    verify(callback, never()).respond(any());
    verifyNoInteractions(combinedChainDataClient);
    assertEquals(
        1,
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.NETWORK, "rpc_blob_sidecars_by_range_requests_total", "ignored"));
  }

  public void runTestWith(
      final UInt64 startSlot,
      final UInt64 count,
      final UInt64 currentSlot,
      final UInt64 fuluForkEpoch,
      final UInt64 minSlotExpected,
      final UInt64 maxSlotExpected) {

    dataStructureUtil = new DataStructureUtil(spec);
    fuluForkFirstSlot = spec.computeStartSlotAtEpoch(fuluForkEpoch);

    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveBlobSidecarsRequest(eq(callback), anyLong()))
        .thenReturn(allowedObjectsRequest);
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(dataStructureUtil.randomSignedBeaconBlock(fuluForkFirstSlot))));
    // epoch 1 = electra is finalized, epochs 2+ not
    when(combinedChainDataClient.getFinalizedBlock())
        .thenReturn(
            Optional.of(
                dataStructureUtil.randomSignedBeaconBlock(
                    spec.computeStartSlotAtEpoch(UInt64.ZERO))));
    when(combinedChainDataClient.getStore()).thenReturn(store);
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(callback.respond(any())).thenReturn(SafeFuture.COMPLETE);

    // mock store
    when(store.getGenesisTime()).thenReturn(genesisTime);
    when(store.getTimeSeconds()).thenReturn(spec.computeTimeAtSlot(currentSlot, genesisTime));

    final UInt64 latestFinalizedSlot =
        spec.computeStartSlotAtEpoch(spec.computeEpochAtSlot(currentSlot).minus(1));
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(latestFinalizedSlot));
    when(combinedChainDataClient.getEarliestAvailableBlobSidecarSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(ZERO)));

    when(store.getTimeSeconds()).thenReturn(spec.computeTimeAtSlot(currentSlot, genesisTime));

    final BlobSidecarsByRangeMessageHandler handler =
        new BlobSidecarsByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);
    // request for over 2 epochs from epoch 1 2nd slot to end of epoch 2. On epoch 2, the first slot
    // is where fulu is activated. Expect replies limited up to (not including) fulu activation slot
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);
    final List<BlobSidecar> allAvailableBlobs =
        setUpBlobSidecarsData(
            startSlot, currentSlot.minus(1)); // make blocks and blobs for slots 9-23
    final SlotAndBlockRoot canonicalSlotAndBlockRoot =
        allAvailableBlobs.getLast().getSlotAndBlockRoot();
    final UInt64 fuluDeprecationSlot =
        spec.computeStartSlotAtEpoch(
            spec.computeEpochAtSlot(currentSlot)
                .minusMinZero(specConfigDeneb.getMinEpochsForBlobSidecarsRequests()));

    final List<BlobSidecar> expectedSent =
        allAvailableBlobs.stream()
            .filter(
                blobSidecar ->
                    blobSidecar.getSlot().isLessThan(fuluForkFirstSlot)
                        && (blobSidecar
                                .getSlot()
                                .isLessThanOrEqualTo(latestFinalizedSlot) // include finalized
                            || blobSidecar
                                .getSlotAndBlockRoot()
                                .equals(
                                    canonicalSlotAndBlockRoot) // include canonical pre fulu only
                        )
                        && blobSidecar
                            .getSlot()
                            .isGreaterThanOrEqualTo(fuluDeprecationSlot) // recent enough
                )
            .toList();
    UInt64 expectedSlots =
        spec.blobSidecarsDeprecationSlot().min(startSlot.plus(count)).minus(startSlot);
    when(combinedChainDataClient.getAncestorRoots(eq(startSlot), eq(expectedSlots), any()))
        .thenReturn(
            ImmutableSortedMap.of(
                canonicalSlotAndBlockRoot.getSlot(), canonicalSlotAndBlockRoot.getBlockRoot()));

    handler.onIncomingMessage(protocolId, peer, request, callback);

    verify(peer, times(1))
        .approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    verify(peer, times(1))
        .adjustBlobSidecarsRequest(
            eq(allowedObjectsRequest.get()), eq(Long.valueOf(expectedSent.size())));

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(callback, times(expectedSent.size())).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(callback).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
    Set<UInt64> results = actualSent.stream().map(BlobSidecar::getSlot).collect(Collectors.toSet());
    UInt64 minSlotReceived = results.stream().min(UInt64::compareTo).orElse(UInt64.MAX_VALUE);
    UInt64 maxSlotReceived = results.stream().max(UInt64::compareTo).orElse(UInt64.ZERO);
    assertEquals(minSlotExpected, minSlotReceived);
    assertEquals(maxSlotExpected, maxSlotReceived);
  }

  private List<Pair<SignedBeaconBlockHeader, SlotAndBlockRootAndBlobIndex>> setupKeyAndHeaderList(
      final UInt64 startSlot, final UInt64 maxSlot) {
    final List<Pair<SignedBeaconBlockHeader, SlotAndBlockRootAndBlobIndex>> headerAndKeys =
        new ArrayList<>();
    UInt64.rangeClosed(startSlot, maxSlot)
        .forEach(
            slot -> {
              final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(slot);
              UInt64.rangeClosed(
                      ZERO,
                      dataStructureUtil
                          .randomUInt64(
                              spec.forMilestone(SpecMilestone.ELECTRA)
                                  .miscHelpers()
                                  .getBlobKzgCommitmentsCount(block))
                          .minusMinZero(1))
                  .forEach(
                      index ->
                          headerAndKeys.add(
                              Pair.of(
                                  block.asHeader(),
                                  new SlotAndBlockRootAndBlobIndex(slot, block.getRoot(), index))));
            });
    return headerAndKeys;
  }

  private List<BlobSidecar> setUpBlobSidecarsData(final UInt64 startSlot, final UInt64 maxSlot) {
    final List<Pair<SignedBeaconBlockHeader, SlotAndBlockRootAndBlobIndex>> headerAndKeys =
        setupKeyAndHeaderList(startSlot, maxSlot);
    when(combinedChainDataClient.getBlobSidecarKeys(any(), any(), anyLong()))
        .thenAnswer(
            args ->
                SafeFuture.completedFuture(
                    headerAndKeys
                        .subList(
                            0, Math.min(headerAndKeys.size(), Math.toIntExact(args.getArgument(2))))
                        .stream()
                        .map(Pair::getValue)
                        .filter(
                            key ->
                                key.getSlot().isLessThanOrEqualTo((UInt64) args.getArguments()[1])
                                    && key.getSlot()
                                        .isGreaterThanOrEqualTo((UInt64) args.getArguments()[0]))
                        .toList()));
    return headerAndKeys.stream()
        .map(this::setUpBlobSidecarDataForKey)
        .collect(Collectors.toList());
  }

  private BlobSidecar setUpBlobSidecarDataForKey(
      final Pair<SignedBeaconBlockHeader, SlotAndBlockRootAndBlobIndex> keyAndHeaders) {
    final BlobSidecar blobSidecar =
        dataStructureUtil
            .createRandomBlobSidecarBuilder()
            .signedBeaconBlockHeader(keyAndHeaders.getLeft())
            .index(keyAndHeaders.getValue().getBlobIndex())
            .build();
    when(combinedChainDataClient.getBlobSidecarByKey(keyAndHeaders.getValue()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blobSidecar)));
    return blobSidecar;
  }
}
