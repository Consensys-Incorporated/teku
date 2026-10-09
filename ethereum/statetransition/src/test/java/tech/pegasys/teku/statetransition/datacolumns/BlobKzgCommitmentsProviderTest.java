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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

class BlobKzgCommitmentsProviderTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final CombinedChainDataClient combinedChainDataClient =
      mock(CombinedChainDataClient.class);

  private BlobKzgCommitmentsProvider provider;

  @BeforeEach
  void setUp() {
    provider = new BlobKzgCommitmentsProvider(spec, combinedChainDataClient, 2);
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
  }

  @Test
  void returnsCommitmentsFedFromBlockWithoutQueryingStore() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 3);
    final SszList<SszKZGCommitment> commitments =
        block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();

    provider.onNewBlock(block);

    assertThat(provider.getBlobKzgCommitments(block.getRoot()).join()).contains(commitments);
    verify(combinedChainDataClient, never()).getBlockByBlockRoot(block.getRoot());
  }

  @Test
  void returnsStateOnlyCheckpointCommitmentsWithoutRequiringBlock() {
    final BeaconStateGloas state =
        BeaconStateGloas.required(dataStructureUtil.randomBeaconState(UInt64.valueOf(8)));
    final AnchorPoint anchor = mock(AnchorPoint.class);
    when(anchor.getRoot()).thenReturn(dataStructureUtil.randomBytes32());
    when(anchor.getState()).thenReturn(state);
    when(combinedChainDataClient.getLatestFinalized()).thenReturn(Optional.of(anchor));

    assertThat(provider.getBlobKzgCommitments(anchor.getRoot()))
        .isCompletedWithValue(
            Optional.of(state.getLatestExecutionPayloadBid().getBlobKzgCommitments()));
    assertThat(provider.getBlobKzgCommitments(dataStructureUtil.randomBytes32()))
        .isCompletedWithValue(Optional.empty());
  }

  @Test
  void storesEmptyCommitmentsListAsKnownContext() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 0);

    provider.onNewBlock(block);

    final Optional<SszList<SszKZGCommitment>> result =
        provider.getBlobKzgCommitments(block.getRoot()).join();
    assertThat(result).isPresent();
    assertThat(result.orElseThrow()).isEmpty();
    verify(combinedChainDataClient, never()).getBlockByBlockRoot(block.getRoot());
  }

  @Test
  void returnsSidecarCommitmentsWithoutStoringThem() {
    final Spec fuluSpec = TestSpecFactory.createMinimalFulu();
    final DataStructureUtil fuluDataStructureUtil = new DataStructureUtil(fuluSpec);
    final SignedBeaconBlock block =
        fuluDataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 2);
    final DataColumnSidecar dataColumnSidecar =
        fuluDataStructureUtil.randomDataColumnSidecar(block, UInt64.ZERO);
    final SszList<SszKZGCommitment> commitments =
        dataColumnSidecar.getMaybeKzgCommitments().orElseThrow();

    assertThat(provider.getBlobKzgCommitments(dataColumnSidecar).join()).contains(commitments);
    assertThat(provider.getBlobKzgCommitments(dataColumnSidecar.getBeaconBlockRoot()).join())
        .isEmpty();
    verify(combinedChainDataClient).getBlockByBlockRoot(dataColumnSidecar.getBeaconBlockRoot());
  }

  @Test
  void ignoresBlocksWhoseBodyHasNoBlobCommitmentsField() {
    final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
    final SignedBeaconBlock blockWithoutBlobCommitments =
        new DataStructureUtil(phase0Spec).randomSignedBeaconBlock(UInt64.ONE);

    provider.onNewBlock(blockWithoutBlobCommitments);

    assertThat(provider.getBlobKzgCommitments(blockWithoutBlobCommitments.getRoot()).join())
        .isEmpty();
    verify(combinedChainDataClient).getBlockByBlockRoot(blockWithoutBlobCommitments.getRoot());
  }

  @Test
  void fallsBackToStoreOnMissAndCachesStoreResult() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 2);
    final SszList<SszKZGCommitment> commitments =
        block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();
    when(combinedChainDataClient.getBlockByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));

    assertThat(provider.getBlobKzgCommitments(block.getRoot()).join()).contains(commitments);
    assertThat(provider.getBlobKzgCommitments(block.getRoot()).join()).contains(commitments);

    verify(combinedChainDataClient, times(1)).getBlockByBlockRoot(block.getRoot());
  }

  @Test
  void storesImportedBlockAndEvictsParentRoot() {
    final SignedBeaconBlock parent =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);
    final SignedBeaconBlock importedChild =
        createBlockWithParentRoot(UInt64.valueOf(2), parent.getRoot(), 2);
    final SszList<SszKZGCommitment> childCommitments =
        importedChild.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();

    provider.onNewBlock(parent);
    provider.onBlockImported(importedChild, false);

    assertThat(provider.getBlobKzgCommitments(parent.getRoot()).join()).isEmpty();
    assertThat(provider.getBlobKzgCommitments(importedChild.getRoot()).join())
        .contains(childCommitments);
    verify(combinedChainDataClient).getBlockByBlockRoot(parent.getRoot());
    verify(combinedChainDataClient, never()).getBlockByBlockRoot(importedChild.getRoot());
  }

  @Test
  void storesGossipValidatedBlockBeforeImport() {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);
    final SszList<SszKZGCommitment> commitments =
        block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();

    provider.onBlockValidated(block);

    assertThat(provider.getBlobKzgCommitments(block.getRoot()).join()).contains(commitments);
    verify(combinedChainDataClient, never()).getBlockByBlockRoot(block.getRoot());
  }

  @Test
  void prunesEntriesAtOrBeforeFinalizedCheckpointStartSlot() {
    final UInt64 finalizedEpoch = UInt64.ONE;
    final UInt64 finalizedStartSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    final SignedBeaconBlock finalizedSlotBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(finalizedStartSlot, 1);
    final SignedBeaconBlock laterBlock =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(finalizedStartSlot.plus(1), 1);

    provider.onNewBlock(finalizedSlotBlock);
    provider.onNewBlock(laterBlock);
    provider.onNewFinalizedCheckpoint(new Checkpoint(finalizedEpoch, Bytes32.ZERO), false);

    assertThat(provider.getBlobKzgCommitments(finalizedSlotBlock.getRoot()).join()).isEmpty();
    assertThat(provider.getBlobKzgCommitments(laterBlock.getRoot()).join()).isPresent();
    verify(combinedChainDataClient).getBlockByBlockRoot(finalizedSlotBlock.getRoot());
    verify(combinedChainDataClient, never()).getBlockByBlockRoot(laterBlock.getRoot());
  }

  @Test
  void evictsLeastRecentlyUsedEntryWhenCapacityIsExceeded() {
    final SignedBeaconBlock first =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.ONE, 1);
    final SignedBeaconBlock second =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.valueOf(2), 1);
    final SignedBeaconBlock third =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(UInt64.valueOf(3), 1);

    provider.onNewBlock(first);
    provider.onNewBlock(second);
    provider.onNewBlock(third);

    assertThat(provider.getBlobKzgCommitments(first.getRoot()).join()).isEmpty();
    assertThat(provider.getBlobKzgCommitments(second.getRoot()).join()).isPresent();
    assertThat(provider.getBlobKzgCommitments(third.getRoot()).join()).isPresent();
    verify(combinedChainDataClient).getBlockByBlockRoot(first.getRoot());
  }

  private SignedBeaconBlock createBlockWithParentRoot(
      final UInt64 slot, final Bytes32 parentRoot, final int commitmentCount) {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(slot, commitmentCount);
    final BeaconBlock message = block.getMessage();
    final BeaconBlock messageWithParentRoot =
        new BeaconBlock(
            message.getSchema(),
            message.getSlot(),
            message.getProposerIndex(),
            parentRoot,
            message.getStateRoot(),
            message.getBody());
    return SignedBeaconBlock.create(spec, messageWithParentRoot, block.getSignature());
  }
}
