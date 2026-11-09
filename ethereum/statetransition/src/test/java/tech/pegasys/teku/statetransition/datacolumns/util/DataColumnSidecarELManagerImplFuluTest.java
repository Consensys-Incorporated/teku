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

package tech.pegasys.teku.statetransition.datacolumns.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.datacolumns.util.DataColumnSidecarELManagerImpl.LOCAL_OR_RECOVERED_ORIGINS;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.logic.versions.deneb.types.VersionedHash;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELManager;
import tech.pegasys.teku.statetransition.util.FutureItems;
import tech.pegasys.teku.statetransition.util.PoolFactory;

public class DataColumnSidecarELManagerImplFuluTest
    extends AbstractDataColumnSidecarELManagerImplTest {

  private SignedBeaconBlock block;
  private List<BlobAndCellProofs> blobAndCellProofs;

  @Override
  @BeforeEach
  public void setup() {
    super.setup();
    block = dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    blobAndCellProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndCellProofs(
                        blobSidecar.getBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
  }

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalFulu();
  }

  @Test
  public void onNewBlock_startsFromFuluBlocks() {
    final Spec minimalWithFuluForkEpoch =
        TestSpecFactory.createMinimalWithFuluForkEpoch(UInt64.ONE);
    final DataColumnSidecarELManager dataColumnSidecarELRecoveryManagerCustom =
        new PoolFactory(metricsSystem)
            .createDataColumnSidecarELManager(
                minimalWithFuluForkEpoch,
                asyncRunner,
                recentChainData,
                executionLayer,
                dataColumnSidecarPublisher,
                dataColumnSidecarGossipValidator,
                custodyGroupCountManager,
                metricsSystem,
                timeProvider);
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    dataColumnSidecarELRecoveryManagerCustom.onSlot(UInt64.ONE);
    dataColumnSidecarELRecoveryManagerCustom.onNewBlock(block, Optional.empty());
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoInteractions(executionLayer);

    final int fuluSlot = minimalWithFuluForkEpoch.slotsPerEpoch(UInt64.ZERO) + 1;
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(fuluSlot);
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    dataColumnSidecarELRecoveryManagerCustom.onSlot(UInt64.valueOf(fuluSlot));
    dataColumnSidecarELRecoveryManagerCustom.onNewBlock(block2, Optional.empty());
    verify(executionLayer).engineGetBlobAndCellProofsList(any(), any());
  }

  @Test
  public void onNewDataColumnSidecar_ignoresLocalOrRecovered() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecar(block, UInt64.ZERO);
    dataColumnSidecarELManager.onSlot(currentSlot);

    LOCAL_OR_RECOVERED_ORIGINS.forEach(
        origin -> {
          dataColumnSidecarELManager.onNewDataColumnSidecar(dataColumnSidecar, origin);
          assertThat(asyncRunner.hasDelayedActions()).isFalse();
          verifyNoInteractions(executionLayer);
        });
  }

  @Test
  public void onNewDataColumnSidecar_startsRecovery() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecar(block, UInt64.ZERO);
    dataColumnSidecarELManager.onSlot(currentSlot);
    dataColumnSidecarELManager.onNewDataColumnSidecar(dataColumnSidecar, RemoteOrigin.GOSSIP);
    verify(executionLayer).engineGetBlobAndCellProofsList(any(), any());
  }

  @Test
  public void shouldNotPublish_whenNotInSync() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELManager.onSyncingStatusChanged(false);
    dataColumnSidecarELManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecarsForBlock(block);
    final List<BlobAndCellProofs> blobAndCellProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndCellProofs(
                        blobSidecar.getBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));
    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());
    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    verifyRecovery(false);
  }

  @Test
  public void shouldNotPublish_whenNotAllBlobsRetrieved() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlock(currentSlot.longValue());
    dataColumnSidecarELManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars =
        dataStructureUtil.randomBlobSidecarsForBlock(block).subList(0, 1);
    final List<BlobAndCellProofs> blobAndCellProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndCellProofs(
                        blobSidecar.getBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));
    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());
    verify(executionLayer).engineGetBlobAndCellProofsList(any(), any());
    verifyNoRecovery();
  }

  @Test
  public void shouldPublish_whenAllBlobsRetrieved() {
    dataColumnSidecarELManager.onSlot(currentSlot);
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));
    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());

    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    verifyRecovery(true);
  }

  @Test
  public void shouldMarkForEquivocation_AllColumnsRebuiltFromRetrievedFromEL() {
    dataColumnSidecarELManager.onSlot(currentSlot);
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));
    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());

    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    verifyRecovery(true);
    verify(dataColumnSidecarGossipValidator, times(1)).markForEquivocation(any(), any(), any());
  }

  @Test
  public void shouldRetry_whenElBlobsFetchingFails() {
    final DataColumnSidecarELManager dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES,
            dataColumnSidecarGossipValidator);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());
    verify(executionLayer).engineGetBlobAndCellProofsList(any(), any());
    for (int i = 1; i <= EL_BLOBS_FETCHING_MAX_RETRIES; i++) {
      assertThat(asyncRunner.hasDelayedActions()).isTrue();
      asyncRunner.executeQueuedActions();
      verify(executionLayer, times(i + 1)).engineGetBlobAndCellProofsList(any(), any());
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoRecovery();
  }

  @Test
  public void shouldRetry_whenMissingBlobsFromEl() {
    final DataColumnSidecarELManagerImpl dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES,
            dataColumnSidecarGossipValidator);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    final List<BlobSidecar> blobSidecars =
        dataStructureUtil.randomBlobSidecarsForBlock(block).subList(0, 1);
    final List<BlobAndCellProofs> blobAndCellProofs =
        blobSidecars.stream()
            .map(
                blobSidecar ->
                    new BlobAndCellProofs(
                        blobSidecar.getBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());
    final List<VersionedHash> versionedHashes =
        getVersionedHashes(dataColumnSidecarELRecoveryManager, block.getSlotAndBlockRoot());
    verify(executionLayer).engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    for (int i = 1; i <= EL_BLOBS_FETCHING_MAX_RETRIES; i++) {
      assertThat(asyncRunner.hasDelayedActions()).isTrue();
      asyncRunner.executeQueuedActions();
      verify(executionLayer, times(i + 1))
          .engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoRecovery();
  }

  @Test
  public void shouldRetry_whenEmptyBlobsListFromEl() {
    final DataColumnSidecarELManagerImpl dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES,
            dataColumnSidecarGossipValidator);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());
    final List<VersionedHash> versionedHashes =
        getVersionedHashes(dataColumnSidecarELRecoveryManager, block.getSlotAndBlockRoot());
    verify(executionLayer).engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    for (int i = 1; i <= EL_BLOBS_FETCHING_MAX_RETRIES; i++) {
      assertThat(asyncRunner.hasDelayedActions()).isTrue();
      asyncRunner.executeQueuedActions();
      verify(executionLayer, times(i + 1))
          .engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoRecovery();
  }

  @Test
  public void shouldStopRetry_whenDataColumnsAlreadyReceived() {
    final DataColumnSidecarELManagerImpl dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES,
            dataColumnSidecarGossipValidator);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    // 2 first call fails and then the 3rd succeeds
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));

    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());

    final List<VersionedHash> versionedHashes =
        getVersionedHashes(dataColumnSidecarELRecoveryManager, block.getSlotAndBlockRoot());
    verify(executionLayer).engineGetBlobAndCellProofsList(versionedHashes, currentSlot);

    // data columns received before the EL fetching is retried
    custodyGroupCountManager
        .getSamplingColumnIndices()
        .forEach(
            index ->
                dataColumnSidecarELRecoveryManager.onNewDataColumnSidecar(
                    dataStructureUtil.randomDataColumnSidecar(block.asHeader(), index),
                    RemoteOrigin.GOSSIP));
    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verifyNoMoreInteractions(executionLayer);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoRecovery();
  }

  @Test
  public void shouldStop_afterMaxRetries() {
    final DataColumnSidecarELManagerImpl dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES,
            dataColumnSidecarGossipValidator);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    // all calls fail so we keep retrying
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")));

    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());

    final List<VersionedHash> versionedHashes =
        getVersionedHashes(dataColumnSidecarELRecoveryManager, block.getSlotAndBlockRoot());

    verify(executionLayer).engineGetBlobAndCellProofsList(versionedHashes, currentSlot);
    reset(executionLayer);
    for (int i = 1; i <= EL_BLOBS_FETCHING_MAX_RETRIES; i++) {
      assertThat(asyncRunner.hasDelayedActions()).isTrue();
      asyncRunner.executeQueuedActions();
      verify(executionLayer).engineGetBlobAndCellProofsList(any(), any());
      reset(executionLayer);
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoRecovery();
  }

  @Test
  public void shouldStopRetry_whenElBlobsAreFetched() {
    final DataColumnSidecarELManagerImpl dataColumnSidecarELRecoveryManager =
        new DataColumnSidecarELManagerImpl(
            spec,
            asyncRunner,
            recentChainData,
            executionLayer,
            UInt64.valueOf(320),
            FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
            10,
            dataColumnSidecarPublisher,
            custodyGroupCountManager,
            metricsSystem,
            timeProvider,
            EL_BLOBS_FETCHING_DELAY,
            EL_BLOBS_FETCHING_MAX_RETRIES,
            dataColumnSidecarGossipValidator);
    dataColumnSidecarELRecoveryManager.onSlot(currentSlot);
    dataColumnSidecarELRecoveryManager.onSyncingStatusChanged(true);
    dataColumnSidecarELRecoveryManager.subscribeToRecoveredColumnSidecar(
        validDataColumnSidecarsListener);
    // 2 first call fails and then the 3rd succeeds
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new IllegalArgumentException("error")))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));

    dataColumnSidecarELRecoveryManager.onNewBlock(block, Optional.empty());

    final List<VersionedHash> versionedHashes =
        getVersionedHashes(dataColumnSidecarELRecoveryManager, block.getSlotAndBlockRoot());

    verify(executionLayer).engineGetBlobAndCellProofsList(versionedHashes, currentSlot);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    // already called once with error
    verify(executionLayer, times(2)).engineGetBlobAndCellProofsList(any(), any());

    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    verifyRecovery(true);
  }

  private void verifyNoRecovery() {
    verifyNoInteractions(dataColumnSidecarPublisher);
    verifyNoInteractions(validDataColumnSidecarsListener);
  }

  private void verifyRecovery(final boolean inSync) {
    if (inSync) {
      verify(dataColumnSidecarPublisher)
          .accept(
              argThat(dataColumnSidecars -> dataColumnSidecars.size() == sampleGroupCount),
              argThat(origin -> origin == RemoteOrigin.LOCAL_EL));
    } else {
      verifyNoInteractions(dataColumnSidecarPublisher);
    }

    verify(validDataColumnSidecarsListener, times(sampleGroupCount))
        .onNewValidSidecar(any(), eq(RemoteOrigin.LOCAL_EL));
  }

  private List<VersionedHash> getVersionedHashes(
      final DataColumnSidecarELManagerImpl dataColumnSidecarELRecoveryManager,
      final SlotAndBlockRoot slotAndBlockRoot) {
    final List<BlobIdentifier> missingBlobsIdentifiers =
        IntStream.range(
                0,
                dataColumnSidecarELRecoveryManager
                    .getRecoveryTask(slotAndBlockRoot)
                    .sszKzgCommitmentsFuture()
                    .getImmediately()
                    .size())
            .mapToObj(
                index -> new BlobIdentifier(slotAndBlockRoot.getBlockRoot(), UInt64.valueOf(index)))
            .toList();

    final SpecVersion specVersion = spec.atSlot(slotAndBlockRoot.getSlot());
    final MiscHelpersDeneb miscHelpersDeneb =
        specVersion.miscHelpers().toVersionDeneb().orElseThrow();

    final SszList<SszKZGCommitment> sszKZGCommitments =
        dataColumnSidecarELRecoveryManager
            .getRecoveryTask(slotAndBlockRoot)
            .sszKzgCommitmentsFuture()
            .getImmediately();
    return missingBlobsIdentifiers.stream()
        .map(
            blobIdentifier ->
                miscHelpersDeneb.kzgCommitmentToVersionedHash(
                    sszKZGCommitments.get(blobIdentifier.getIndex().intValue()).getKZGCommitment()))
        .toList();
  }
}
