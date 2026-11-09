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

package tech.pegasys.teku.api.blobselector;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.metadata.BlobSidecarsAndMetaData;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.BlobSidecarReconstructionProvider;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

@TestSpecContext(allMilestones = true)
public class BlobSidecarSelectorFactoryTest {

  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil data = new DataStructureUtil(spec);
  private final List<UInt64> indices = List.of(UInt64.ZERO, UInt64.ONE);
  private final SignedBeaconBlock block = data.randomSignedBeaconBlock();
  private final List<BlobSidecar> blobSidecars = data.randomBlobSidecars(3);
  private final BlobSidecarSelectorFactory blobSidecarSelectorFactory =
      new BlobSidecarSelectorFactory(spec, client, mock(BlobSidecarReconstructionProvider.class));

  @Test
  public void headSelector_shouldGetHeadBlobSidecars()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);

    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    when(client.getBlobSidecars(blockAndState.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory.headSelector().getBlobSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(blobSidecars);
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedBlobSidecars()
      throws ExecutionException, InterruptedException {
    final AnchorPoint anchorPoint = data.randomAnchorPoint(UInt64.ONE);

    when(client.getLatestFinalized()).thenReturn(Optional.of(anchorPoint));
    when(client.getBlobSidecars(anchorPoint.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory.finalizedSelector().getBlobSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(blobSidecars);
  }

  @Test
  public void genesisSelector_shouldGetGenesisBlobSidecars()
      throws ExecutionException, InterruptedException {
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory.genesisSelector().getBlobSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(blobSidecars);
  }

  @Test
  public void blockRootSelector_shouldGetBlobSidecarsForFinalizedSlot()
      throws ExecutionException, InterruptedException {
    final UInt64 finalizedSlot = UInt64.valueOf(42);
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));

    when(client.getFinalizedSlotByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(finalizedSlot)));
    when(client.getBlobSidecars(new SlotAndBlockRoot(finalizedSlot, block.getRoot()), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory
            .blockRootSelector(block.getRoot())
            .getBlobSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(blobSidecars);
  }

  @Test
  public void blockRootSelector_shouldGetBlobSidecarsByRetrievingBlock()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));

    when(client.getFinalizedSlotByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    when(client.getBlockByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory
            .blockRootSelector(block.getRoot())
            .getBlobSidecars(indices)
            .get();
    assertThat(result.get().getData()).isEqualTo(blobSidecars);
  }

  @Test
  public void slotSelector_shouldGetBlobSidecarsFromFinalizedSlot()
      throws ExecutionException, InterruptedException {
    when(client.isFinalized(block.getSlot())).thenReturn(true);
    when(client.getBlobSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));
    when(client.isChainHeadOptimistic()).thenReturn(false);

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory.slotSelector(block.getSlot()).getBlobSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(blobSidecars);
  }

  @Test
  public void slotSelector_shouldGetBlobSidecarsByRetrievingBlockWhenSlotNotFinalized()
      throws ExecutionException, InterruptedException {

    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);

    when(client.isFinalized(block.getSlot())).thenReturn(false);
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory.slotSelector(block.getSlot()).getBlobSidecars(indices).get();
    assertThat(result.get().getData()).isEqualTo(blobSidecars);
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestException() {
    assertThrows(
        BadRequestException.class, () -> blobSidecarSelectorFactory.createSelectorForBlockId("a"));
  }

  @Test
  public void stateRootSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> blobSidecarSelectorFactory.stateRootSelector(data.randomBytes32()));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestExceptionOnJustifiedKeyword() {
    assertThrows(
        BadRequestException.class,
        () -> blobSidecarSelectorFactory.createSelectorForBlockId("justified"));
  }

  @Test
  public void justifiedSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class, blobSidecarSelectorFactory::justifiedSelector);
  }

  @Test
  public void shouldNotLookForBlobSidecarsWhenNoKzgCommitments()
      throws ExecutionException, InterruptedException {
    final SignedBlockAndState blockAndState = data.randomSignedBlockAndState(100);

    final SignedBeaconBlock blockWithEmptyCommitments =
        data.randomSignedBeaconBlockWithEmptyCommitments();
    when(client.isFinalized(blockWithEmptyCommitments.getSlot())).thenReturn(false);
    when(client.getBlockAtSlotExact(blockWithEmptyCommitments.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockWithEmptyCommitments)));
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    Optional<BlobSidecarsAndMetaData> maybeBlobsidecars =
        blobSidecarSelectorFactory
            .slotSelector(blockWithEmptyCommitments.getSlot())
            .getBlobSidecars(indices)
            .get();
    verify(client, never()).getBlobSidecars(any(SlotAndBlockRoot.class), anyList());
    assertThat(maybeBlobsidecars).isPresent();
    assertThat(maybeBlobsidecars.get().getData()).isEmpty();
  }

  @TestTemplate
  public void shouldLookForBlobSidecarsOnlyAfterDeneb(
      final TestSpecInvocationContextProvider.SpecContext ctx)
      throws ExecutionException, InterruptedException {
    final SignedBeaconBlock block = new DataStructureUtil(ctx.getSpec()).randomSignedBeaconBlock();
    when(client.isFinalized(block.getSlot())).thenReturn(false);
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of(data.randomBlobSidecar())));
    blobSidecarSelectorFactory.slotSelector(block.getSlot()).getBlobSidecars(indices).get();
    if (ctx.getSpec().isMilestoneSupported(SpecMilestone.DENEB)) {
      verify(client).getBlobSidecars(any(SlotAndBlockRoot.class), anyList());
    } else {
      verify(client, never()).getBlobSidecars(any(SlotAndBlockRoot.class), anyList());
    }
  }

  @Test
  public void genesisSelector_shouldAlwaysReturnOptimisticMetadataFieldFalse()
      throws ExecutionException, InterruptedException {
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory.genesisSelector().getBlobSidecars(indices).get();
    assertThat(result).isNotEmpty();
    final BlobSidecarsAndMetaData blobSidecarsAndMetaData = result.get();
    assertThat(blobSidecarsAndMetaData.isExecutionOptimistic()).isFalse();
  }

  @Test
  public void slotSelector_whenSelectingFinalizedBlockMetadataReturnsFinalizedTrue()
      throws ExecutionException, InterruptedException {
    when(client.isFinalized(block.getSlot())).thenReturn(true);
    when(client.getBlobSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));
    when(client.isChainHeadOptimistic()).thenReturn(false);

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory.slotSelector(block.getSlot()).getBlobSidecars(indices).get();

    assertThat(result).isNotEmpty();
    final BlobSidecarsAndMetaData blobSidecarsAndMetaData = result.get();
    assertThat(blobSidecarsAndMetaData.isFinalized()).isTrue();
  }

  @Test
  public void slotSelector_whenSelectingNonFinalizedBlockMetadataReturnsFinalizedFalse()
      throws ExecutionException, InterruptedException {
    when(client.isFinalized(block.getSlot())).thenReturn(false);
    when(client.getBlockAtSlotExact(block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));
    when(client.isChainHeadOptimistic()).thenReturn(false);
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobSidecarsAndMetaData> result =
        blobSidecarSelectorFactory.slotSelector(block.getSlot()).getBlobSidecars(indices).get();

    assertThat(result).isNotEmpty();
    final BlobSidecarsAndMetaData blobSidecarsAndMetaData = result.get();
    assertThat(blobSidecarsAndMetaData.isFinalized()).isFalse();
  }
}
