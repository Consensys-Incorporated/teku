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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.datacolumns.util.DataColumnSidecarELManagerImpl.LOCAL_OR_RECOVERED_ORIGINS;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.execution.BlobAndCellProofs;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public class DataColumnSidecarELManagerImplGloasTest
    extends AbstractDataColumnSidecarELManagerImplTest {

  private SignedBeaconBlock block;
  private List<BlobAndCellProofs> blobAndCellProofs;
  final int commitmentCount = 2;

  @Override
  @BeforeEach
  public void setup() {
    super.setup();
    final BeaconBlock beaconBlock =
        dataStructureUtil.randomBeaconBlock(
            currentSlot, dataStructureUtil.randomBeaconBlockBodyWithCommitments(commitmentCount));
    block = dataStructureUtil.signedBlock(beaconBlock);
    blobAndCellProofs =
        IntStream.range(0, commitmentCount)
            .mapToObj(
                i ->
                    new BlobAndCellProofs(
                        dataStructureUtil.randomValidBlob(),
                        IntStream.range(0, 128)
                            .mapToObj(__ -> dataStructureUtil.randomKZGProof())
                            .toList()))
            .toList();
  }

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalGloas();
  }

  @Test
  public void onNewBlock_processesGloasBlocks() {
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));

    dataColumnSidecarELManager.onSlot(currentSlot);
    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());

    // block is processed and recovery is triggered
    verify(executionLayer).engineGetBlobAndCellProofsList(any(), eq(currentSlot));
  }

  @Test
  public void shouldSkipELFetch_whenAllCustodyColumnsSidecarsArrivedBeforeBlock() {
    // all data column sidecars arrive before their block
    dataColumnSidecarELManager.onSlot(currentSlot);
    for (final UInt64 index : custodyGroupCountManager.getSamplingColumnIndices()) {
      final DataColumnSidecar sidecar =
          dataStructureUtil.new RandomDataColumnSidecarBuilder()
              .slot(currentSlot)
              .beaconBlockRoot(block.getRoot())
              .index(index)
              .build();
      dataColumnSidecarELManager.onNewDataColumnSidecar(sidecar, RemoteOrigin.GOSSIP);
    }

    // block arrives now: recoveredColumnIndices should already contain all data column sidecars
    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());

    // the short circuit in fetchMissingBlobsFromLocalEL should fire: no EL call needed
    asyncRunner.executeQueuedActions();
    verifyNoInteractions(executionLayer);
  }

  @Test
  public void onNewDataColumnSidecar_ignoresCommitmentsCarriedBySidecar() {
    final DataColumnSidecar fuluSchemaSidecar =
        new DataStructureUtil(TestSpecFactory.createMinimalFulu())
            .randomDataColumnSidecar(
                block.asHeader(),
                custodyGroupCountManager.getSamplingColumnIndices().iterator().next());

    dataColumnSidecarELManager.onSlot(currentSlot);
    dataColumnSidecarELManager.onNewDataColumnSidecar(fuluSchemaSidecar, RemoteOrigin.GOSSIP);

    final DataColumnSidecarELManagerImpl.RecoveryTask recoveryTask =
        ((DataColumnSidecarELManagerImpl) dataColumnSidecarELManager)
            .getRecoveryTask(fuluSchemaSidecar.getSlotAndBlockRoot());
    assertThat(recoveryTask).isNotNull();
    assertThat(recoveryTask.sszKzgCommitmentsFuture()).isNotDone();
  }

  @Test
  public void onNewDataColumnSidecar_ignoresLocalOrRecovered() {
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.new RandomDataColumnSidecarBuilder()
            .slot(currentSlot)
            .index(custodyGroupCountManager.getSamplingColumnIndices().iterator().next())
            .build();

    dataColumnSidecarELManager.onSlot(currentSlot);

    LOCAL_OR_RECOVERED_ORIGINS.forEach(
        origin -> {
          dataColumnSidecarELManager.onNewDataColumnSidecar(dataColumnSidecar, origin);
          assertThat(
                  ((DataColumnSidecarELManagerImpl) dataColumnSidecarELManager)
                      .getRecoveryTask(dataColumnSidecar.getSlotAndBlockRoot()))
              .isNull();
          verifyNoInteractions(executionLayer);
          assertThat(asyncRunner.hasDelayedActions()).isFalse();
        });
  }

  @Test
  public void shouldPublish_whenAllBlobsRetrievedFromEl() {
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));

    dataColumnSidecarELManager.onSlot(currentSlot);
    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());

    asyncRunner.executeQueuedActions();
    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    // recovery succeeded and data was published
    verify(dataColumnSidecarPublisher)
        .accept(
            argThat(dataColumnSidecars -> dataColumnSidecars.size() == sampleGroupCount),
            argThat(origin -> origin == RemoteOrigin.LOCAL_EL));
    verify(validDataColumnSidecarsListener, times(sampleGroupCount))
        .onNewValidSidecar(any(), eq(RemoteOrigin.LOCAL_EL));
  }

  @Test
  public void shouldMarkForEquivocation_whenRecoverySucceeds() {
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));

    dataColumnSidecarELManager.onSlot(currentSlot);
    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());

    // Execute queued actions to complete the async recovery operation
    if (asyncRunner.hasDelayedActions()) {
      asyncRunner.executeQueuedActions();
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    // equivocation
    verify(dataColumnSidecarGossipValidator, times(1)).markForEquivocation(any(), any(), any());
  }

  @Test
  public void shouldNotPublish_whenNotInSync() {
    dataColumnSidecarELManager.onSyncingStatusChanged(false);
    dataColumnSidecarELManager.onSlot(currentSlot);
    when(executionLayer.engineGetBlobAndCellProofsList(any(), any()))
        .thenReturn(SafeFuture.completedFuture(blobAndCellProofs));

    dataColumnSidecarELManager.onNewBlock(block, Optional.empty());

    // Execute queued actions to complete the async recovery operation
    if (asyncRunner.hasDelayedActions()) {
      asyncRunner.executeQueuedActions();
    }
    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    // When not in sync, should not publish to gossip but should notify listeners
    verifyNoInteractions(dataColumnSidecarPublisher);
    verify(validDataColumnSidecarsListener, times(sampleGroupCount))
        .onNewValidSidecar(any(), eq(RemoteOrigin.LOCAL_EL));
  }
}
