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
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.metadata.BlobsAndMetaData;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.BlobReconstructionProvider;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

@TestSpecContext(allMilestones = true)
public class BlobSelectorFactoryTest {

  private final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
  private final Spec spec = TestSpecFactory.createMinimalDeneb();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private List<Bytes32> versionedHashes = Collections.emptyList();
  private final List<UInt64> indices = List.of(UInt64.ZERO);
  private final SignedBlockAndState blockAndState =
      dataStructureUtil.randomSignedBlockAndState(100);
  private final SignedBeaconBlock block = blockAndState.getBlock();
  private final List<BlobSidecar> blobSidecars = dataStructureUtil.randomBlobSidecars(1);
  private final List<Blob> blobs = blobSidecars.stream().map(BlobSidecar::getBlob).toList();
  private final BlobReconstructionProvider blobReconstructionProvider =
      mock(BlobReconstructionProvider.class);
  private final BlobSelectorFactory blobSelectorFactory =
      new BlobSelectorFactory(spec, client, blobReconstructionProvider);

  @BeforeEach
  public void setup() {
    final MiscHelpersDeneb miscHelpersDeneb =
        MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers());
    final BeaconBlockBodyDeneb beaconBlockBody =
        BeaconBlockBodyDeneb.required(block.getMessage().getBody());
    this.versionedHashes =
        List.of(
            miscHelpersDeneb
                .kzgCommitmentToVersionedHash(
                    beaconBlockBody.getBlobKzgCommitments().get(0).getKZGCommitment())
                .get());
    when(client.isCanonicalBlock(any(), any(), any())).thenReturn(true);
    when(client.isOptimisticBlock(any())).thenReturn(false);
    when(client.isFinalized(any())).thenReturn(true);
  }

  @Test
  public void headSelector_shouldGetHeadBlobs() throws ExecutionException, InterruptedException {
    when(client.getChainHead()).thenReturn(Optional.of(ChainHead.create(blockAndState)));
    when(client.getBlobSidecars(blockAndState.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobsAndMetaData> result =
        blobSelectorFactory.headSelector().getBlobs(versionedHashes).get();
    assertThat(result.get().getData()).isEqualTo(blobs);
  }

  @Test
  public void finalizedSelector_shouldGetFinalizedBlobs()
      throws ExecutionException, InterruptedException {
    // chainHead is required for optimistic/canonical checks
    when(client.getChainHead())
        .thenReturn(Optional.of(ChainHead.create(dataStructureUtil.randomBlockAndState(123))));
    when(client.getFinalizedBlock()).thenReturn(Optional.of(block));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobsAndMetaData> result =
        blobSelectorFactory.finalizedSelector().getBlobs(versionedHashes).get();
    assertThat(result.get().getData()).isEqualTo(blobs);
  }

  @Test
  public void genesisSelector_shouldGetGenesisBlobs()
      throws ExecutionException, InterruptedException {
    // chainHead is required for optimistic/canonical checks
    when(client.getChainHead())
        .thenReturn(Optional.of(ChainHead.create(dataStructureUtil.randomBlockAndState(123))));
    when(client.getBlockAtSlotExact(UInt64.ZERO))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobsAndMetaData> result =
        blobSelectorFactory.genesisSelector().getBlobs(versionedHashes).get();
    assertThat(result.get().getData()).isEqualTo(blobs);
  }

  @Test
  public void blockRootSelector_shouldGetBlobs() throws ExecutionException, InterruptedException {
    // chainHead is required for optimistic/canonical checks
    when(client.getChainHead())
        .thenReturn(Optional.of(ChainHead.create(dataStructureUtil.randomBlockAndState(123))));
    when(client.getBlockByBlockRoot(block.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobsAndMetaData> result =
        blobSelectorFactory.blockRootSelector(block.getRoot()).getBlobs(versionedHashes).get();
    assertThat(result.get().getData()).isEqualTo(blobs);
  }

  @Test
  public void slotSelector_shouldGetBlobs() throws ExecutionException, InterruptedException {
    // chainHead is required for optimistic/canonical checks
    final ChainHead chainHead = ChainHead.create(dataStructureUtil.randomBlockAndState(123));
    when(client.getChainHead()).thenReturn(Optional.of(chainHead));
    when(client.getBlockAtSlotExact(block.getSlot(), chainHead.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(block.getSlotAndBlockRoot(), indices))
        .thenReturn(SafeFuture.completedFuture(blobSidecars));

    final Optional<BlobsAndMetaData> result =
        blobSelectorFactory.slotSelector(block.getSlot()).getBlobs(versionedHashes).get();
    assertThat(result.get().getData()).isEqualTo(blobs);
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestException() {
    assertThrows(
        BadRequestException.class, () -> blobSelectorFactory.createSelectorForBlockId("a"));
  }

  @Test
  public void stateRootSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(
        UnsupportedOperationException.class,
        () -> blobSelectorFactory.stateRootSelector(dataStructureUtil.randomBytes32()));
  }

  @Test
  public void createSelectorForBlockId_shouldThrowBadRequestExceptionOnJustifiedKeyword() {
    assertThrows(
        BadRequestException.class, () -> blobSelectorFactory.createSelectorForBlockId("justified"));
  }

  @Test
  public void justifiedSelector_shouldThrowUnsupportedOperationException() {
    assertThrows(UnsupportedOperationException.class, blobSelectorFactory::justifiedSelector);
  }

  @Test
  public void shouldNotLookForBlobSidecarsWhenNoKzgCommitments()
      throws ExecutionException, InterruptedException {
    // chainHead is required for optimistic/canonical checks
    when(client.getChainHead())
        .thenReturn(Optional.of(ChainHead.create(dataStructureUtil.randomBlockAndState(123))));

    final SignedBeaconBlock blockWithEmptyCommitments =
        dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments();
    when(client.getBlockByBlockRoot(blockWithEmptyCommitments.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockWithEmptyCommitments)));

    final Optional<BlobsAndMetaData> result =
        blobSelectorFactory
            .blockRootSelector(blockWithEmptyCommitments.getRoot())
            .getBlobs(versionedHashes)
            .get();
    verify(client, never()).getBlobSidecars(any(SlotAndBlockRoot.class), anyList());
    assertThat(result).isPresent();
    assertThat(result.get().getData()).isEmpty();
  }

  @TestTemplate
  public void shouldLookForBlobSidecarsOnlyAfterDeneb(
      final TestSpecInvocationContextProvider.SpecContext ctx)
      throws ExecutionException, InterruptedException {
    final BlobSelectorFactory blobSelectorFactory =
        new BlobSelectorFactory(ctx.getSpec(), client, blobReconstructionProvider);
    // chainHead is required for optimistic/canonical checks
    final ChainHead chainHead = ChainHead.create(dataStructureUtil.randomBlockAndState(123));
    when(client.getChainHead()).thenReturn(Optional.of(chainHead));

    when(client.getBlockAtSlotExact(block.getSlot(), chainHead.getRoot()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(block)));
    when(client.getBlobSidecars(any(SlotAndBlockRoot.class), anyList()))
        .thenReturn(SafeFuture.completedFuture(List.of(dataStructureUtil.randomBlobSidecar())));
    when(blobReconstructionProvider.reconstructBlobs(any(), anyList(), anyBoolean()))
        .thenReturn(SafeFuture.completedFuture(List.of()));
    blobSelectorFactory.slotSelector(block.getSlot()).getBlobs(versionedHashes).get();
    if (ctx.getSpec().isMilestoneSupported(SpecMilestone.FULU)) {
      verify(blobReconstructionProvider)
          .reconstructBlobs(any(SlotAndBlockRoot.class), anyList(), anyBoolean());
      verify(client, never()).getBlobSidecars(any(SlotAndBlockRoot.class), anyList());
    } else if (ctx.getSpec().isMilestoneSupported(SpecMilestone.DENEB)) {
      verify(client).getBlobSidecars(any(SlotAndBlockRoot.class), anyList());
    } else {
      verify(client, never()).getBlobSidecars(any(SlotAndBlockRoot.class), anyList());
    }
  }

  @TestTemplate
  public void blockToVersionedHashBlobs_AllMilestones(
      final TestSpecInvocationContextProvider.SpecContext ctx) {

    final DataStructureUtil dataStructureUtil1 = new DataStructureUtil(ctx.getSpec());
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil1.randomSignedBeaconBlock();
    final BlobSelectorFactory blobSelectorFactory =
        new BlobSelectorFactory(ctx.getSpec(), client, blobReconstructionProvider);
    final Map<Bytes32, SlotAndBlockRootAndBlobIndex> blockToVersionedHashBlobs =
        blobSelectorFactory.blockToVersionedHashBlobs(Optional.of(signedBeaconBlock));
    if (ctx.getSpec().isMilestoneSupported(SpecMilestone.DENEB)) {
      assertThat(blockToVersionedHashBlobs.values())
          .containsExactlyInAnyOrderElementsOf(
              IntStream.range(
                      0,
                      signedBeaconBlock
                          .getMessage()
                          .getBody()
                          .getOptionalBlobKzgCommitments()
                          .map(SszList::size)
                          .orElse(0))
                  .mapToObj(
                      index ->
                          new SlotAndBlockRootAndBlobIndex(
                              signedBeaconBlock.getSlot(),
                              signedBeaconBlock.getRoot(),
                              UInt64.valueOf(index)))
                  .collect(Collectors.toSet()));
    } else {
      assertThat(blockToVersionedHashBlobs).isEmpty();
    }
  }

  @Test
  public void blockToVersionedHashBlobs_NoBlock() {
    final Map<Bytes32, SlotAndBlockRootAndBlobIndex> blockToVersionedHashBlobs =
        blobSelectorFactory.blockToVersionedHashBlobs(Optional.empty());
    assertThat(blockToVersionedHashBlobs).isEmpty();
  }

  @TestTemplate
  public void toBlobIdentifiers_EmptyVersionedHashesAllMilestones(
      final TestSpecInvocationContextProvider.SpecContext ctx) {

    final DataStructureUtil dataStructureUtil1 = new DataStructureUtil(ctx.getSpec());
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil1.randomSignedBeaconBlock();
    final BlobSelectorFactory blobSelectorFactory =
        new BlobSelectorFactory(ctx.getSpec(), client, blobReconstructionProvider);
    final Collection<SlotAndBlockRootAndBlobIndex> blobIdentifiers =
        blobSelectorFactory.toBlobIdentifiers(List.of(), Optional.of(signedBeaconBlock));
    if (ctx.getSpec().isMilestoneSupported(SpecMilestone.DENEB)) {
      assertThat(blobIdentifiers)
          .containsExactlyInAnyOrderElementsOf(
              IntStream.range(
                      0,
                      signedBeaconBlock
                          .getMessage()
                          .getBody()
                          .getOptionalBlobKzgCommitments()
                          .map(SszList::size)
                          .orElse(0))
                  .mapToObj(
                      index ->
                          new SlotAndBlockRootAndBlobIndex(
                              signedBeaconBlock.getSlot(),
                              signedBeaconBlock.getRoot(),
                              UInt64.valueOf(index)))
                  .collect(Collectors.toSet()));
    } else {
      assertThat(blobIdentifiers).isEmpty();
    }
  }

  @TestTemplate
  public void toBlobIdentifiers_VersionedHashFilterAllMilestones(
      final TestSpecInvocationContextProvider.SpecContext ctx) {
    assumeThat(ctx.getSpec().isMilestoneSupported(SpecMilestone.DENEB)).isTrue();

    final DataStructureUtil dataStructureUtil1 = new DataStructureUtil(ctx.getSpec());
    final SignedBeaconBlock signedBeaconBlock = dataStructureUtil1.randomSignedBeaconBlock();
    final BlobSelectorFactory blobSelectorFactory =
        new BlobSelectorFactory(ctx.getSpec(), client, blobReconstructionProvider);
    final Map<Bytes32, SlotAndBlockRootAndBlobIndex> blockToVersionedHashBlobs =
        blobSelectorFactory.blockToVersionedHashBlobs(Optional.of(signedBeaconBlock));
    final Map.Entry<Bytes32, SlotAndBlockRootAndBlobIndex> first =
        blockToVersionedHashBlobs.entrySet().stream().findFirst().get();
    final Collection<SlotAndBlockRootAndBlobIndex> blobIdentifiers =
        blobSelectorFactory.toBlobIdentifiers(
            List.of(first.getKey()), Optional.of(signedBeaconBlock));

    assertThat(blobIdentifiers).containsExactly(first.getValue());
  }
}
