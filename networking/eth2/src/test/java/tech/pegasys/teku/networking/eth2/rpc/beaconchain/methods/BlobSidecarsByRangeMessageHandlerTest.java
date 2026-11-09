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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import com.google.common.collect.ImmutableSortedMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;
import org.assertj.core.api.AssertionsForInterfaceTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
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
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobSidecarsByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.store.UpdatableStore;

@TestSpecContext(milestone = {SpecMilestone.DENEB, SpecMilestone.ELECTRA})
public class BlobSidecarsByRangeMessageHandlerTest {

  private static final RequestKey ZERO_OBJECTS_REQUEST_APPROVAL = new RequestKey(ZERO, 0);
  private static final RpcEncoding RPC_ENCODING =
      RpcEncoding.createSszSnappyEncoding(
          TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize());
  private final UInt64 genesisTime = UInt64.valueOf(1982239L);
  private final UInt64 currentForkEpoch = UInt64.valueOf(1);
  private final UInt64 count = UInt64.valueOf(5);
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final Eth2Peer peer = mock(Eth2Peer.class);

  @SuppressWarnings("unchecked")
  private final ResponseCallback<BlobSidecar> listener = mock(ResponseCallback.class);

  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final String protocolId =
      BeaconChainMethodIds.getBlobSidecarsByRangeMethodId(1, RPC_ENCODING);
  private final Optional<RequestKey> allowedObjectsRequest = Optional.of(new RequestKey(ZERO, 1));

  private SpecMilestone specMilestone;
  private Spec spec;
  private BlobSidecarsByRangeMessageHandler handler;
  private DataStructureUtil dataStructureUtil;
  private int maxBlobsPerBlock;
  private int slotsPerEpoch;
  private UInt64 startSlot;

  @BeforeEach
  public void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    specMilestone = specContext.getSpecMilestone();
    spec =
        switch (specContext.getSpecMilestone()) {
          case PHASE0 -> throw new IllegalArgumentException("Phase0 is an unsupported milestone");
          case ALTAIR -> throw new IllegalArgumentException("Altair is an unsupported milestone");
          case BELLATRIX ->
              throw new IllegalArgumentException("Bellatrix is an unsupported milestone");
          case CAPELLA -> throw new IllegalArgumentException("Capella is an unsupported milestone");
          case DENEB -> TestSpecFactory.createMinimalWithDenebForkEpoch(currentForkEpoch);
          case ELECTRA -> TestSpecFactory.createMinimalWithElectraForkEpoch(currentForkEpoch);
          case FULU -> TestSpecFactory.createMinimalWithFuluForkEpoch(currentForkEpoch);
          case GLOAS -> TestSpecFactory.createMinimalWithGloasForkEpoch(currentForkEpoch);
          case HEZE -> TestSpecFactory.createMinimalWithHezeForkEpoch(currentForkEpoch);
        };

    dataStructureUtil = new DataStructureUtil(spec);
    maxBlobsPerBlock =
        spec.getMaxBlobsPerBlockAtSlot(spec.computeStartSlotAtEpoch(currentForkEpoch))
            .orElseThrow();
    slotsPerEpoch = spec.getSlotsPerEpoch(ZERO);
    startSlot = currentForkEpoch.increment().times(slotsPerEpoch);
    handler = new BlobSidecarsByRangeMessageHandler(spec, metricsSystem, combinedChainDataClient);

    when(peer.approveRequest()).thenReturn(true);
    when(peer.approveBlobSidecarsRequest(eq(listener), anyLong()))
        .thenReturn(allowedObjectsRequest);
    when(combinedChainDataClient.getEarliestAvailableBlobSidecarSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(ZERO)));
    when(combinedChainDataClient.getStore()).thenReturn(store);
    when(combinedChainDataClient.getBestBlockRoot())
        .thenReturn(Optional.of(dataStructureUtil.randomBytes32()));
    // everything is finalized by default
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(startSlot.plus(count)));
    when(listener.respond(any())).thenReturn(SafeFuture.COMPLETE);

    // mock store
    when(store.getGenesisTime()).thenReturn(genesisTime);
    setCurrentEpoch(currentForkEpoch.increment());
  }

  @TestTemplate
  public void validateRequest_validRequest() {
    final Optional<RpcException> result =
        handler.validateRequest(
            protocolId, new BlobSidecarsByRangeRequestMessage(startSlot, ONE, maxBlobsPerBlock));
    assertThat(result).isEmpty();
  }

  @TestTemplate
  public void validateRequest_shouldRejectRequestWhenGetMaxSlotOverflows() {
    final Optional<RpcException> result =
        handler.validateRequest(
            protocolId,
            new BlobSidecarsByRangeRequestMessage(
                UInt64.MAX_VALUE, UInt64.valueOf(10), maxBlobsPerBlock));

    assertThat(result)
        .hasValue(
            new RpcException(INVALID_REQUEST_CODE, "Requested slot is too far in the future"));
  }

  @TestTemplate
  public void validateRequest_shouldRejectRequestWhenCountIsTooBig() {
    final UInt64 maxRequestBlobSidecars =
        UInt64.valueOf(
            SpecConfigDeneb.required(spec.forMilestone(specMilestone).getConfig())
                .getMaxRequestBlobSidecars());
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(
            startSlot, maxRequestBlobSidecars.increment(), maxBlobsPerBlock);

    final Optional<RpcException> result = handler.validateRequest(protocolId, request);

    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE,
                String.format(
                    "Only a maximum of %s blob sidecars can be requested per request",
                    maxRequestBlobSidecars)));

    final long countTooBigCount =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_range_requests_total",
            "count_too_big");

    assertThat(countTooBigCount).isOne();
  }

  @TestTemplate
  public void validateRequest_shouldRejectRequestWhenCountOverflowsIntoNegativeNumber() {
    final UInt64 maxRequestBlobSidecars =
        UInt64.valueOf(
            SpecConfigDeneb.required(spec.forMilestone(specMilestone).getConfig())
                .getMaxRequestBlobSidecars());
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(
            // bypass ArithmeticException when calculating maxSlot
            startSlot, UInt64.MAX_VALUE.minus(startSlot), maxBlobsPerBlock);

    final Optional<RpcException> result = handler.validateRequest(protocolId, request);

    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE,
                String.format(
                    "Only a maximum of %s blob sidecars can be requested per request",
                    maxRequestBlobSidecars)));

    final long countTooBigCount =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_range_requests_total",
            "count_too_big");

    assertThat(countTooBigCount).isOne();
  }

  @TestTemplate
  public void validateRequest_shouldRejectRequestWhenCountOverflowsIntoPositiveNumber() {
    final UInt64 maxRequestBlobSidecars =
        UInt64.valueOf(
            SpecConfigDeneb.required(spec.forMilestone(specMilestone).getConfig())
                .getMaxRequestBlobSidecars());
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(
            // this count will overflow into a positive number
            // ((Long.MAX_VALUE / 3) + 100) * 6 = 596
            startSlot, UInt64.valueOf((Long.MAX_VALUE / 3) + 100), maxBlobsPerBlock);

    final Optional<RpcException> result = handler.validateRequest(protocolId, request);

    assertThat(result)
        .hasValue(
            new RpcException(
                INVALID_REQUEST_CODE,
                String.format(
                    "Only a maximum of %s blob sidecars can be requested per request",
                    maxRequestBlobSidecars)));

    final long countTooBigCount =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_range_requests_total",
            "count_too_big");

    assertThat(countTooBigCount).isOne();
  }

  @TestTemplate
  public void shouldNotSendBlobSidecarsIfPeerIsRateLimited() {

    when(peer.approveBlobSidecarsRequest(listener, count.times(maxBlobsPerBlock).longValue()))
        .thenReturn(Optional.empty());

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer).approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // No adjustment
    verify(peer, never()).adjustBlobSidecarsRequest(any(), anyLong());

    final long rateLimitedCount =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_range_requests_total",
            "rate_limited");

    assertThat(rateLimitedCount).isOne();

    verifyNoInteractions(listener);
  }

  @TestTemplate
  public void shouldNotServeBlobSidecarsIfPeerRequestIsRateLimited() {
    when(peer.approveRequest()).thenReturn(false);

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    verify(peer, times(1)).approveRequest();
    verify(peer, never()).approveBlobSidecarsRequest(any(), anyLong());
    verifyNoInteractions(listener, combinedChainDataClient);

    final long rateLimitedCount =
        metricsSystem.getLabelledCounterValue(
            TekuMetricCategory.NETWORK,
            "rpc_blob_sidecars_by_range_requests_total",
            "rate_limited");

    assertThat(rateLimitedCount).isOne();
  }

  @TestTemplate
  public void shouldSendResourceUnavailableIfBlobSidecarsAreNotAvailable() {

    // current epoch is 5020
    setCurrentEpoch(UInt64.valueOf(5020));

    // earliest available sidecar epoch - 5010
    when(combinedChainDataClient.getEarliestAvailableBlobSidecarSlot())
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(currentForkEpoch.plus(5009).times(slotsPerEpoch))));

    // start slot in epoch 5000 within MIN_EPOCHS_FOR_BLOB_SIDECARS_REQUESTS range
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(
            UInt64.valueOf(5000).times(slotsPerEpoch), count, maxBlobsPerBlock);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer).approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // Be protective: do not adjust due to error
    verify(peer, never()).adjustBlobSidecarsRequest(any(), anyLong());

    // blob sidecars should be available from epoch 5000, but they are
    // available from epoch 5010
    verify(listener)
        .completeWithErrorResponse(
            new RpcException.ResourceUnavailableException(
                "Requested blob sidecars are not available."));
  }

  @TestTemplate
  public void shouldCompleteSuccessfullyIfNoBlobSidecarsInRange() {
    when(combinedChainDataClient.getBlobSidecarKeys(any(), any(), anyLong()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(
            currentForkEpoch.plus(1).times(slotsPerEpoch), count, maxBlobsPerBlock);

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer).approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // Sending 0 blob sidecars
    verify(peer)
        .adjustBlobSidecarsRequest(eq(allowedObjectsRequest.orElseThrow()), eq(Long.valueOf(0)));

    verify(combinedChainDataClient, never()).getBlobSidecarByKey(any());

    verify(listener, never()).respond(any());

    verify(listener).completeSuccessfully();
  }

  @TestTemplate
  public void shouldSendToPeerRequestedNumberOfFinalizedBlobSidecars() {

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);

    final List<BlobSidecar> expectedSent = setUpBlobSidecarsData(startSlot, request.getMaxSlot());

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer).approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // Sending expectedSent blob sidecars
    verify(peer)
        .adjustBlobSidecarsRequest(
            eq(allowedObjectsRequest.orElseThrow()), eq(Long.valueOf(expectedSent.size())));

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, times(expectedSent.size())).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  @TestTemplate
  public void shouldSendToPeerRequestedNumberOfCanonicalBlobSidecars() {

    final UInt64 latestFinalizedSlot = startSlot.plus(count).minus(3);
    when(combinedChainDataClient.getFinalizedBlockSlot())
        .thenReturn(Optional.of(latestFinalizedSlot));

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, count, maxBlobsPerBlock);

    final List<BlobSidecar> allAvailableBlobs =
        setUpBlobSidecarsData(startSlot, request.getMaxSlot());

    // we simulate that the canonical non-finalized chain only contains blobs from last
    // slotAndBlockRoot
    final SlotAndBlockRoot canonicalSlotAndBlockRoot =
        allAvailableBlobs.getLast().getSlotAndBlockRoot();

    final List<BlobSidecar> expectedSent =
        allAvailableBlobs.stream()
            .filter(
                blobSidecar ->
                    blobSidecar
                            .getSlot()
                            .isLessThanOrEqualTo(latestFinalizedSlot) // include finalized
                        || blobSidecar
                            .getSlotAndBlockRoot()
                            .equals(canonicalSlotAndBlockRoot) // include non canonical
                )
            .toList();

    // let return only canonical slot and block root as canonical
    when(combinedChainDataClient.getAncestorRoots(eq(startSlot), eq(ONE), any()))
        .thenReturn(
            ImmutableSortedMap.of(
                canonicalSlotAndBlockRoot.getSlot(), canonicalSlotAndBlockRoot.getBlockRoot()));

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 5 * maxBlobsPerBlock blob sidecars
    verify(peer).approveBlobSidecarsRequest(any(), eq(count.times(maxBlobsPerBlock).longValue()));
    // Sending expectedSent blob sidecars
    verify(peer)
        .adjustBlobSidecarsRequest(
            eq(allowedObjectsRequest.orElseThrow()), eq(Long.valueOf(expectedSent.size())));

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, times(expectedSent.size())).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  @TestTemplate
  public void shouldSendToPeerRequestedSingleSlotOfBlobSidecars() {

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, ONE, maxBlobsPerBlock);

    final List<BlobSidecar> expectedSent = setUpBlobSidecarsData(startSlot, request.getMaxSlot());

    handler.onIncomingMessage(protocolId, peer, request, listener);

    // Requesting 1 * maxBlobsPerBlock blob sidecars
    verify(peer).approveBlobSidecarsRequest(any(), eq(Long.valueOf(maxBlobsPerBlock)));
    // Sending expectedSent blob sidecars
    verify(peer)
        .adjustBlobSidecarsRequest(
            eq(allowedObjectsRequest.orElseThrow()), eq(Long.valueOf(expectedSent.size())));

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, times(expectedSent.size())).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).containsExactlyElementsOf(expectedSent);
  }

  @TestTemplate
  public void shouldIgnoreRequestWhenCountIsZero() {

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(startSlot, ZERO, maxBlobsPerBlock);

    when(peer.approveBlobSidecarsRequest(listener, 0))
        .thenReturn(Optional.of(ZERO_OBJECTS_REQUEST_APPROVAL));

    handler.onIncomingMessage(protocolId, peer, request, listener);

    verify(peer).approveBlobSidecarsRequest(any(), eq(Long.valueOf(0)));
    // no adjustment
    verify(peer, never()).adjustBlobSidecarsRequest(any(), anyLong());

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, never()).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).isEmpty();
  }

  @TestTemplate
  public void shouldIgnoreRequestWhenCountIsZeroAndHotSlotRequested() {
    // not finalized
    final UInt64 hotStartSlot = startSlot.plus(7);

    final BlobSidecarsByRangeRequestMessage request =
        new BlobSidecarsByRangeRequestMessage(hotStartSlot, ZERO, maxBlobsPerBlock);

    when(peer.approveBlobSidecarsRequest(listener, 0))
        .thenReturn(Optional.of(ZERO_OBJECTS_REQUEST_APPROVAL));

    handler.onIncomingMessage(protocolId, peer, request, listener);

    verify(peer).approveBlobSidecarsRequest(any(), eq(Long.valueOf(0)));
    // no adjustment
    verify(peer, never()).adjustBlobSidecarsRequest(any(), anyLong());

    final ArgumentCaptor<BlobSidecar> argumentCaptor = ArgumentCaptor.forClass(BlobSidecar.class);

    verify(listener, never()).respond(argumentCaptor.capture());

    final List<BlobSidecar> actualSent = argumentCaptor.getAllValues();

    verify(listener).completeSuccessfully();

    AssertionsForInterfaceTypes.assertThat(actualSent).isEmpty();
  }

  private void setCurrentEpoch(final UInt64 currentEpoch) {
    when(store.getTimeSeconds())
        .thenReturn(
            spec.computeTimeAtSlot(currentEpoch.times(spec.getSlotsPerEpoch(ZERO)), genesisTime));
  }

  private List<BlobSidecar> setUpBlobSidecarsData(final UInt64 startSlot, final UInt64 maxSlot) {
    final List<Pair<SignedBeaconBlockHeader, SlotAndBlockRootAndBlobIndex>> headerAndKeys =
        setupKeyAndHeaderList(startSlot, maxSlot);
    when(combinedChainDataClient.getBlobSidecarKeys(eq(startSlot), eq(maxSlot), anyLong()))
        .thenAnswer(
            args ->
                SafeFuture.completedFuture(
                    headerAndKeys
                        .subList(
                            0, Math.min(headerAndKeys.size(), Math.toIntExact(args.getArgument(2))))
                        .stream()
                        .map(Pair::getValue)
                        .toList()));
    return headerAndKeys.stream()
        .map(this::setUpBlobSidecarDataForKey)
        .collect(Collectors.toList());
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
                              spec.forMilestone(specMilestone)
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
