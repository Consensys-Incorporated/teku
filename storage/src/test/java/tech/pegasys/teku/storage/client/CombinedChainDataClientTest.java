/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.storage.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGProof;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CommitteeAssignment;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.StorageQueryChannel;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.store.UpdatableStore;

/** Note: Most tests should be added to the integration-test directory */
class CombinedChainDataClientTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ForkChoiceStrategy forkChoiceStrategy = mock(ForkChoiceStrategy.class);
  private final StorageQueryChannel historicalChainData = mock(StorageQueryChannel.class);
  private final UpdatableStore store = mock(UpdatableStore.class);
  private final CombinedChainDataClient client =
      new CombinedChainDataClient(recentChainData, historicalChainData, spec);
  private final ChainHead chainHead = mock(ChainHead.class);

  final List<SignedBeaconBlock> nonCanonicalBlocks = new ArrayList<>();
  final SignedBeaconBlock firstBlock = dataStructureUtil.randomSignedBeaconBlock(1);
  final SignedBeaconBlock secondBlock = dataStructureUtil.randomSignedBeaconBlock(1);
  final BlobSidecar sidecar = dataStructureUtil.randomBlobSidecar();
  final BlobSidecar sidecar2 = dataStructureUtil.randomBlobSidecar();

  @BeforeEach
  void setUp() {
    when(recentChainData.getForkChoiceStrategy()).thenReturn(Optional.of(forkChoiceStrategy));
    when(recentChainData.getFinalizedEpoch()).thenReturn(UInt64.ZERO);
    when(forkChoiceStrategy.isOptimistic(any())).thenReturn(Optional.of(true));
    when(chainHead.isOptimistic()).thenReturn(false);
    when(chainHead.getSlot()).thenReturn(UInt64.valueOf(8428924L));
  }

  @Test
  public void getCommitteesFromStateWithCache_shouldReturnCommitteeAssignments() {
    BeaconState state = dataStructureUtil.randomBeaconState();
    List<CommitteeAssignment> data =
        client.getCommitteesFromState(state, spec.getCurrentEpoch(state));
    assertThat(data.size()).isEqualTo(spec.getSlotsPerEpoch(state.getSlot()));
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldAddCanonicalBlockIfPresent() {
    nonCanonicalBlocks.add(firstBlock);
    assertThat(
            client
                .mergeNonCanonicalAndCanonicalBlocks(
                    nonCanonicalBlocks, chainHead, Optional.of(secondBlock))
                .stream()
                .map(BlockAndMetaData::getData))
        .containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldReturnNonCanonicalOnly() {
    nonCanonicalBlocks.add(firstBlock);
    nonCanonicalBlocks.add(secondBlock);

    assertThat(
            client
                .mergeNonCanonicalAndCanonicalBlocks(
                    nonCanonicalBlocks, chainHead, Optional.empty())
                .stream()
                .map(BlockAndMetaData::getData))
        .containsExactlyInAnyOrder(firstBlock, secondBlock);
  }

  @Test
  public void mergeNonCanonicalAndCanonicalBlocks_shouldReturnCanonicalOnly() {
    assertThat(
            client
                .mergeNonCanonicalAndCanonicalBlocks(
                    nonCanonicalBlocks, chainHead, Optional.of(secondBlock))
                .stream()
                .map(BlockAndMetaData::getData))
        .containsExactlyInAnyOrder(secondBlock);
  }

  @Test
  void isOptimistic_shouldReturnOptimisticStatusOfKnownBlocks() {
    when(forkChoiceStrategy.isOptimistic(firstBlock.getRoot())).thenReturn(Optional.of(true));
    when(forkChoiceStrategy.isOptimistic(secondBlock.getRoot())).thenReturn(Optional.of(false));

    assertThat(client.isOptimisticBlock(firstBlock.getRoot())).isTrue();
    assertThat(client.isOptimisticBlock(secondBlock.getRoot())).isFalse();
  }

  @Test
  void isOptimistic_shouldReturnOptimisticStatusOfFinalizedBlockForUnknownBlocks() {
    final Checkpoint finalized = dataStructureUtil.randomCheckpoint();
    when(recentChainData.getFinalizedCheckpoint()).thenReturn(Optional.of(finalized));
    when(forkChoiceStrategy.isOptimistic(firstBlock.getRoot())).thenReturn(Optional.empty());
    when(forkChoiceStrategy.isOptimistic(finalized.getRoot())).thenReturn(Optional.of(true));

    assertThat(client.isOptimisticBlock(firstBlock.getRoot())).isTrue();
  }

  @Test
  void getsEarliestAvailableBlobSidecarSlot() {
    when(historicalChainData.getEarliestAvailableBlobSidecarSlot())
        .thenReturn(SafeFuture.completedFuture(Optional.of(UInt64.ONE)));

    final Optional<UInt64> result =
        SafeFutureAssert.safeJoin(client.getEarliestAvailableBlobSidecarSlot());

    assertThat(result).hasValue(UInt64.ONE);
  }

  @Test
  void getStateForBlockProduction_retrievesStateUsingSelectedParentRoot() {
    final UInt64 proposalSlot = UInt64.valueOf(2);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final BeaconState state = dataStructureUtil.randomBeaconState(proposalSlot);
    final SlotAndBlockRoot slotAndBlockRoot = new SlotAndBlockRoot(proposalSlot, parentRoot);
    when(recentChainData.getStore()).thenReturn(store);
    when(chainHead.getRoot()).thenReturn(parentRoot);
    when(store.retrieveBlockState(slotAndBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final SafeFuture<BeaconState> future =
        client.getStateForBlockProduction(chainHead, proposalSlot);

    SafeFutureAssert.assertThatSafeFuture(future).isCompletedWithValue(state);
    verify(store).retrieveBlockState(slotAndBlockRoot);
    verify(chainHead, never()).getState();
  }

  @Test
  void getsBlobSidecarBySlotAndBlockRootAndBlobIndex() {
    final SlotAndBlockRootAndBlobIndex correctKey =
        new SlotAndBlockRootAndBlobIndex(
            sidecar.getSlot(), sidecar.getBlockRoot(), sidecar.getIndex());
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final SlotAndBlockRootAndBlobIndex incorrectKey =
        new SlotAndBlockRootAndBlobIndex(sidecar.getSlot(), blockRoot, sidecar.getIndex());
    setupGetBlobSidecar(correctKey, sidecar);

    final Optional<BlobSidecar> correctResult =
        SafeFutureAssert.safeJoin(client.getBlobSidecarByKey(correctKey));
    assertThat(correctResult).hasValue(sidecar);

    final Optional<BlobSidecar> incorrectResult =
        SafeFutureAssert.safeJoin(client.getBlobSidecarByKey(incorrectKey));
    assertThat(incorrectResult).isEmpty();
  }

  @Test
  void getsBlobSidecarBySlotAndBlobIndex() {
    final SlotAndBlockRootAndBlobIndex key =
        new SlotAndBlockRootAndBlobIndex(
            sidecar.getSlot(), sidecar.getBlockRoot(), sidecar.getIndex());
    setupGetBlobSidecar(key, sidecar);
    setupGetSlotForBlockRoot(sidecar.getBlockRoot(), sidecar.getSlot());
    setupGetRecentlyValidatedBlobSidecar(sidecar2.getBlockRoot(), sidecar2.getIndex(), sidecar2);

    final Optional<BlobSidecar> result =
        SafeFutureAssert.safeJoin(
            client.getBlobSidecarByBlockRootAndIndex(sidecar.getBlockRoot(), sidecar.getIndex()));
    assertThat(result).hasValue(sidecar);

    final Optional<BlobSidecar> result2 =
        SafeFutureAssert.safeJoin(
            client.getBlobSidecarByBlockRootAndIndex(sidecar2.getBlockRoot(), sidecar2.getIndex()));
    assertThat(result2).hasValue(sidecar2);

    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    setupGetSlotForBlockRoot(blockRoot, sidecar.getSlot().plus(1));
    final Optional<BlobSidecar> incorrectResult =
        SafeFutureAssert.safeJoin(
            client.getBlobSidecarByBlockRootAndIndex(blockRoot, sidecar.getIndex()));
    assertThat(incorrectResult).isEmpty();
  }

  @Test
  void getsDataColumnSidecarsBySlotAndBlockRoot() {
    final UInt64 slot = UInt64.valueOf(42);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final DataColumnSlotAndIdentifier firstIdentifier =
        new DataColumnSlotAndIdentifier(slot, blockRoot, UInt64.ZERO);
    final DataColumnSlotAndIdentifier secondIdentifier =
        new DataColumnSlotAndIdentifier(slot, blockRoot, UInt64.ONE);
    final DataColumnSlotAndIdentifier otherBlockIdentifier =
        new DataColumnSlotAndIdentifier(slot, dataStructureUtil.randomBytes32(), UInt64.ZERO);
    final DataColumnSidecar firstSidecar = mock(DataColumnSidecar.class);
    final DataColumnSidecar secondSidecar = mock(DataColumnSidecar.class);

    when(historicalChainData.getDataColumnIdentifiers(slot))
        .thenReturn(
            SafeFuture.completedFuture(
                List.of(firstIdentifier, otherBlockIdentifier, secondIdentifier)));
    when(historicalChainData.getSidecar(any()))
        .thenAnswer(
            args -> {
              final DataColumnSlotAndIdentifier identifier = args.getArgument(0);
              if (identifier.equals(firstIdentifier)) {
                return SafeFuture.completedFuture(Optional.of(firstSidecar));
              } else if (identifier.equals(secondIdentifier)) {
                return SafeFuture.completedFuture(Optional.of(secondSidecar));
              }
              return SafeFuture.completedFuture(Optional.empty());
            });

    final List<DataColumnSidecar> result =
        SafeFutureAssert.safeJoin(
            client.getDataColumnSidecars(new SlotAndBlockRoot(slot, blockRoot), List.of()));

    assertThat(result).containsExactly(firstSidecar, secondSidecar);
    verify(historicalChainData, never()).getSidecar(otherBlockIdentifier);
  }

  @Test
  void getDataColumnSidecarProofs_returnsStoredProofs() {
    final UInt64 slot = UInt64.valueOf(42);
    final List<List<KZGProof>> proofs =
        List.of(
            List.of(dataStructureUtil.randomKZGProof(), dataStructureUtil.randomKZGProof()),
            List.of(dataStructureUtil.randomKZGProof()));
    when(historicalChainData.getDataColumnSidecarsProofs(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(proofs)));

    assertThat(SafeFutureAssert.safeJoin(client.getDataColumnSidecarProofs(slot)))
        .isEqualTo(proofs);
  }

  @Test
  void getDataColumnSidecarProofs_returnsEmptyListWhenAbsent() {
    final UInt64 slot = UInt64.valueOf(42);
    when(historicalChainData.getDataColumnSidecarsProofs(slot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThat(SafeFutureAssert.safeJoin(client.getDataColumnSidecarProofs(slot))).isEmpty();
  }

  @Test
  void getBestFinalizedState_fetchesFinalizedState()
      throws ExecutionException, InterruptedException {
    final Checkpoint finalized = dataStructureUtil.randomCheckpoint(1024);
    when(recentChainData.getStore()).thenReturn(store);
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(1024));
    final BeaconState state = dataStructureUtil.randomBeaconState(slot);
    when(store.getFinalizedCheckpoint()).thenReturn(finalized);
    when(recentChainData.getBlockRootInEffectBySlot(any()))
        .thenReturn(Optional.of(state.getLatestBlockHeader().getBodyRoot()));
    when(store.retrieveBlockState(any(SlotAndBlockRoot.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));

    final SafeFuture<Optional<BeaconState>> maybeFinalizedState = client.getBestFinalizedState();
    // don't care about result, checking we called the right function with the right slot

    assertThat(maybeFinalizedState.get().orElseThrow().getSlot()).isEqualTo(slot);
    verify(recentChainData, times(1)).getBlockRootInEffectBySlot(slot);
  }

  @Test
  void getBestFinalizedState_gracefulIfNotReady() throws ExecutionException, InterruptedException {
    when(recentChainData.getStore()).thenReturn(null);
    final SafeFuture<Optional<BeaconState>> maybeFinalizedState = client.getBestFinalizedState();
    assertThat(maybeFinalizedState.get()).isEmpty();
  }

  @Test
  void getSlotByBlockRoot_shouldReturnSlotFromRecentChainData() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(5);
    when(recentChainData.getSlotForBlockRoot(block.getRoot()))
        .thenReturn(Optional.of(block.getSlot()));

    final Optional<UInt64> result =
        SafeFutureAssert.safeJoin(client.getSlotByBlockRoot(block.getRoot()));

    assertThat(result).hasValue(block.getSlot());
    verify(historicalChainData, never()).getFinalizedSlotByBlockRoot(any());
    verify(historicalChainData, never()).getNonCanonicalBlockByRoot(any());
  }

  @Test
  void getSlotByBlockRoot_shouldFallBackToFinalizedSlotLookup() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 slot = UInt64.valueOf(42);
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.empty());
    when(historicalChainData.getFinalizedSlotByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(slot)));

    final Optional<UInt64> result = SafeFutureAssert.safeJoin(client.getSlotByBlockRoot(blockRoot));

    assertThat(result).hasValue(slot);
    verify(historicalChainData, never()).getNonCanonicalBlockByRoot(any());
  }

  @Test
  void getSlotByBlockRoot_shouldFallBackToNonCanonicalBlockWhenEnabled() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(7);
    when(recentChainData.getSlotForBlockRoot(block.getRoot())).thenReturn(Optional.empty());
    when(historicalChainData.getFinalizedSlotByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(historicalChainData.getNonCanonicalBlockByRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    // by default should skip nonCanonical
    final Optional<UInt64> result =
        SafeFutureAssert.safeJoin(client.getSlotByBlockRoot(block.getRoot()));

    assertThat(result).isEmpty();
    verify(historicalChainData, never()).getNonCanonicalBlockByRoot(any());

    // when disabled, should skip nonCanonical
    final Optional<UInt64> result2 =
        SafeFutureAssert.safeJoin(client.getSlotByBlockRoot(block.getRoot(), false));

    assertThat(result2).isEmpty();
    verify(historicalChainData, never()).getNonCanonicalBlockByRoot(any());

    // when enabled, should return nonCanonical
    final Optional<UInt64> result3 =
        SafeFutureAssert.safeJoin(client.getSlotByBlockRoot(block.getRoot(), true));

    assertThat(result3).hasValue(block.getSlot());
    verify(historicalChainData).getNonCanonicalBlockByRoot(block.getRoot());
  }

  @Test
  void getSlotByBlockRoot_shouldReturnEmptyWhenNotFound() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.empty());
    when(historicalChainData.getFinalizedSlotByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(historicalChainData.getNonCanonicalBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final Optional<UInt64> result = SafeFutureAssert.safeJoin(client.getSlotByBlockRoot(blockRoot));

    assertThat(result).isEmpty();
  }

  @Test
  void getSlotByBlockRoot_fallsBackToRecentlyValidatedSlotWhenNotInForkChoiceOrDb() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 slot = UInt64.valueOf(99);
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.empty());
    when(historicalChainData.getFinalizedSlotByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(recentChainData.getRecentlyValidatedSlotByBlockRoot(blockRoot))
        .thenReturn(Optional.of(slot));

    assertThat(client.getSlotByBlockRoot(blockRoot)).isCompletedWithValue(Optional.of(slot));
  }

  @Test
  void getSlotByBlockRoot_prefersForkChoiceOverRecentlyValidated() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 hotSlot = UInt64.valueOf(7);
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.of(hotSlot));

    assertThat(client.getSlotByBlockRoot(blockRoot)).isCompletedWithValue(Optional.of(hotSlot));
    verify(recentChainData, never()).getRecentlyValidatedSlotByBlockRoot(any());
  }

  @Test
  void getSlotByBlockRoot_withNonCanonical_fallsBackToRecentlyValidatedWhenNonCanonicalEmpty() {
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final UInt64 slot = UInt64.valueOf(123);
    when(recentChainData.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.empty());
    when(historicalChainData.getFinalizedSlotByBlockRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(historicalChainData.getNonCanonicalBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(recentChainData.getRecentlyValidatedSlotByBlockRoot(blockRoot))
        .thenReturn(Optional.of(slot));

    // includeFinalizedNonCanonical=true must remain monotonic with the default overload: when the
    // non-canonical lookup is empty it still falls back to the recently-validated index.
    assertThat(client.getSlotByBlockRoot(blockRoot, true)).isCompletedWithValue(Optional.of(slot));
  }

  private void setupGetBlobSidecar(
      final SlotAndBlockRootAndBlobIndex key, final BlobSidecar result) {
    when(historicalChainData.getBlobSidecar(any()))
        .thenAnswer(
            args -> {
              final SlotAndBlockRootAndBlobIndex argKey = args.getArgument(0);
              if (argKey.equals(key)) {
                return SafeFuture.completedFuture(Optional.of(result));
              } else {
                return SafeFuture.completedFuture(Optional.empty());
              }
            });
  }

  private void setupGetSlotForBlockRoot(final Bytes32 blockRoot, final UInt64 slot) {
    when(recentChainData.getSlotForBlockRoot(any()))
        .thenAnswer(
            args -> {
              final Bytes32 argRoot = args.getArgument(0);
              if (blockRoot.equals(argRoot)) {
                return Optional.of(slot);
              } else {
                return Optional.empty();
              }
            });
  }

  private void setupGetRecentlyValidatedBlobSidecar(
      final Bytes32 blockRoot, final UInt64 index, final BlobSidecar result) {
    when(recentChainData.getRecentlyValidatedBlobSidecar(any(), any()))
        .thenAnswer(
            args -> {
              final Bytes32 blockRootArg = args.getArgument(0);
              final UInt64 indexArg = args.getArgument(1);
              if (blockRootArg.equals(blockRoot) && indexArg.equals(index)) {
                return Optional.of(result);
              } else {
                return Optional.empty();
              }
            });
  }
}
