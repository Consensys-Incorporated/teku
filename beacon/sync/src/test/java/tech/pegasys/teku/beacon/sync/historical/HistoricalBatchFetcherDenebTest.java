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

package tech.pegasys.teku.beacon.sync.historical;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.RespondingEth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.blobs.BlobSidecarManager;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(milestone = {DENEB})
public class HistoricalBatchFetcherDenebTest {

  private final AsyncBLSSignatureVerifier signatureVerifier = mock(AsyncBLSSignatureVerifier.class);
  private final BlobSidecarManager blobSidecarManager = mock(BlobSidecarManager.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Collection<SignedBeaconBlock>> blockCaptor =
      ArgumentCaptor.forClass(Collection.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Map<SlotAndBlockRoot, List<BlobSidecar>>> blobSidecarCaptor =
      ArgumentCaptor.forClass(Map.class);

  @SuppressWarnings("unchecked")
  private final ArgumentCaptor<Optional<UInt64>> earliestBlobSidecarSlotCaptor =
      ArgumentCaptor.forClass(Optional.class);

  private final int maxRequests = 5;

  private Spec spec;
  private ChainBuilder chainBuilder;
  private StorageSystem storageSystem;
  private CombinedChainDataClient chainDataClient;
  private List<SignedBeaconBlock> blockBatch;
  private Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBatch;
  private SignedBeaconBlock firstBlockInBatch;
  private SignedBeaconBlock lastBlockInBatch;
  private HistoricalBatchFetcher fetcher;
  private RespondingEth2Peer peer;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    chainBuilder = ChainBuilder.create(spec);
    storageSystem = InMemoryStorageSystemBuilder.create().specProvider(spec).build();
    storageSystem.chainUpdater().initializeGenesis();

    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(false);
    when(storageUpdateChannel.onFinalizedBlocks(any(), any(), any(), any()))
        .thenReturn(SafeFuture.COMPLETE);

    chainBuilder.generateGenesis();
    chainBuilder.generateBlocksUpToSlot(17);
    // 18, 19 will be with BlobSidecars
    chainBuilder.generateBlockAtSlot(
        18, ChainBuilder.BlockOptions.create().setGenerateRandomBlobs(true));
    chainBuilder.generateBlockAtSlot(
        19, ChainBuilder.BlockOptions.create().setGenerateRandomBlobs(true));
    chainBuilder.generateBlockAtSlot(20);

    blockBatch =
        chainBuilder
            .streamBlocksAndStates(10, 20)
            .map(SignedBlockAndState::getBlock)
            .collect(Collectors.toList());
    lastBlockInBatch = chainBuilder.getLatestBlockAndState().getBlock();
    firstBlockInBatch = blockBatch.getFirst();
    blobSidecarsBatch =
        chainBuilder
            .streamBlobSidecars(10, 20)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    chainDataClient =
        new CombinedChainDataClient(
            storageSystem.recentChainData(), mock(StorageQueryChannel.class), spec);

    peer = RespondingEth2Peer.create(spec, chainBuilder);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            lastBlockInBatch.getSlot(),
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(blockBatch.size()),
            Optional.empty(),
            maxRequests);

    when(signatureVerifier.verify(any(), any(), anyList()))
        .thenReturn(SafeFuture.completedFuture(true));
    when(blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(any(), anyList()))
        .thenAnswer(i -> DataAndValidationResult.validResult(i.getArgument(1)));
  }

  @TestTemplate
  public void run_returnAllBlocksAndBlobSidecarsOnFirstRequest() {
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    assertThat(peer.getOutstandingRequests()).isEqualTo(2);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(firstBlockInBatch);

    verify(storageUpdateChannel)
        .onFinalizedBlocks(
            blockCaptor.capture(),
            blobSidecarCaptor.capture(),
            any(),
            earliestBlobSidecarSlotCaptor.capture());
    assertThat(blockCaptor.getValue()).containsExactlyElementsOf(blockBatch);
    assertThat(blobSidecarCaptor.getValue()).isEqualTo(blobSidecarsBatch);
    assertThat(earliestBlobSidecarSlotCaptor.getValue()).contains(blockBatch.getFirst().getSlot());
  }

  @TestTemplate
  public void run_failsOnBlobSidecarsValidationFailure() {
    when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);
    when(blobSidecarManager.createAvailabilityCheckerAndValidateImmediately(any(), anyList()))
        .thenAnswer(
            i ->
                DataAndValidationResult.invalidResult(
                    i.getArgument(1), new IllegalStateException("oopsy")));

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();
    peer.completePendingRequests();

    assertThat(future)
        .failsWithin(Duration.ZERO)
        .withThrowableThat()
        .withMessageMatching(
            "java.lang.IllegalArgumentException: Blob sidecars validation for block .* failed: INVALID \\(oopsy\\)");
  }

  @TestTemplate
  public void run_requestBatchForRangeOfEmptyBlocks_blobsRequired() {
    runEmptyBlocksBatchTest(true);
  }

  @TestTemplate
  public void run_requestBatchForRangeOfEmptyBlocks_blobsNotRequired() {
    runEmptyBlocksBatchTest(false);
  }

  private void runEmptyBlocksBatchTest(final boolean blobSidecarsRequired) {
    if (blobSidecarsRequired) {
      when(blobSidecarManager.isAvailabilityRequiredAtSlot(any())).thenReturn(true);
    }
    final int batchSize = 10;
    final UInt64 maxSlot = lastBlockInBatch.getSlot().plus(batchSize * 2);
    fetcher =
        new HistoricalBatchFetcher(
            storageUpdateChannel,
            signatureVerifier,
            chainDataClient,
            spec,
            blobSidecarManager,
            peer,
            maxSlot,
            lastBlockInBatch.getRoot(),
            UInt64.valueOf(batchSize),
            Optional.empty(),
            maxRequests);

    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    final SafeFuture<SignedBeaconBlock> future = fetcher.run();

    for (int i = 0; i < maxRequests; i++) {
      final int outstandingRequests = blobSidecarsRequired ? 2 : 1;
      assertThat(peer.getOutstandingRequests()).isEqualTo(outstandingRequests);
      peer.completePendingRequests();
    }
    assertThat(peer.getOutstandingRequests()).isEqualTo(1);
    peer.completePendingRequests();
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    assertThat(future).isCompletedWithValue(lastBlockInBatch);

    verify(storageUpdateChannel)
        .onFinalizedBlocks(
            blockCaptor.capture(),
            blobSidecarCaptor.capture(),
            any(),
            earliestBlobSidecarSlotCaptor.capture());
    assertThat(blockCaptor.getValue()).containsExactly(lastBlockInBatch);
    final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars = blobSidecarCaptor.getValue();
    if (blobSidecarsRequired) {
      assertThat(blobSidecars).isEmpty();
      assertThat(earliestBlobSidecarSlotCaptor.getValue()).contains(UInt64.valueOf(31));
    } else {
      assertThat(blobSidecars).isEmpty();
      assertThat(earliestBlobSidecarSlotCaptor.getValue()).isEmpty();
    }
  }
}
