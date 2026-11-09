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

package tech.pegasys.teku.statetransition.blobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;

public class BlockBlobSidecarsTrackerTest {
  private final Spec spec = TestSpecFactory.createMainnetDeneb();
  private final UInt64 maxBlobsPerBlock =
      UInt64.valueOf(SpecConfigDeneb.required(spec.getGenesisSpecConfig()).getMaxBlobsPerBlock());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final SignedBeaconBlock block =
      dataStructureUtil.randomSignedBeaconBlockWithCommitments(maxBlobsPerBlock.intValue());
  private final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();
  private final List<BlobSidecar> blobSidecarsForBlock =
      dataStructureUtil.randomBlobSidecarsForBlock(block);
  private final List<BlobIdentifier> blobIdentifiersForBlock =
      blobSidecarsForBlock.stream()
          .map(
              blobSidecar -> new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex()))
          .collect(Collectors.toList());

  @Test
  void isNotCompletedJustAfterCreation() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);

    SafeFutureAssert.assertThatSafeFuture(blockBlobSidecarsTracker.getCompletionFuture())
        .isNotCompleted();
    assertThat(blockBlobSidecarsTracker.getBlock()).isEmpty();
    assertThat(blockBlobSidecarsTracker.getBlobSidecars()).isEmpty();
    assertThat(blockBlobSidecarsTracker.getSlotAndBlockRoot()).isEqualTo(slotAndBlockRoot);
    assertThat(
            blockBlobSidecarsTracker.containsBlobSidecar(dataStructureUtil.randomBlobIdentifier()))
        .isFalse();
  }

  @Test
  void setBlock_shouldAcceptCorrectBlock() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);
    blockBlobSidecarsTracker.setBlock(block);

    SafeFutureAssert.assertThatSafeFuture(blockBlobSidecarsTracker.getCompletionFuture())
        .isNotCompleted();
    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars())
        .containsExactlyInAnyOrderElementsOf(blobIdentifiersForBlock);
    assertThat(blockBlobSidecarsTracker.getBlock()).isEqualTo(Optional.of(block));
    assertThat(blockBlobSidecarsTracker.getBlobSidecars()).isEmpty();
  }

  @Test
  void setBlock_shouldThrowWithWrongBlock() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(dataStructureUtil.randomSlotAndBlockRoot());
    assertThatThrownBy(() -> blockBlobSidecarsTracker.setBlock(block))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void setBlock_shouldAcceptBlockTwice() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);
    blockBlobSidecarsTracker.setBlock(block);
    blockBlobSidecarsTracker.setBlock(block);
    assertThat(blockBlobSidecarsTracker.getBlock()).isEqualTo(Optional.of(block));
  }

  @Test
  void setBlock_immediatelyCompletesWithBlockWithoutBlobs() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments();
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);
    final SafeFuture<Void> completionFuture = blockBlobSidecarsTracker.getCompletionFuture();

    SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();

    blockBlobSidecarsTracker.setBlock(block);

    SafeFutureAssert.assertThatSafeFuture(completionFuture).isCompleted();
    assertThat(blockBlobSidecarsTracker.isComplete()).isTrue();

    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars()).isEmpty();
    assertThat(blockBlobSidecarsTracker.getBlobSidecars()).isEmpty();
  }

  @Test
  void getCompletionFuture_returnsIndependentFutures() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlockWithEmptyCommitments();
    final SlotAndBlockRoot slotAndBlockRoot = block.getSlotAndBlockRoot();

    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);
    final SafeFuture<Void> completionFuture1 = blockBlobSidecarsTracker.getCompletionFuture();
    final SafeFuture<Void> completionFuture2 = blockBlobSidecarsTracker.getCompletionFuture();
    final SafeFuture<Void> completionFuture3 = blockBlobSidecarsTracker.getCompletionFuture();

    SafeFutureAssert.assertThatSafeFuture(completionFuture1).isNotCompleted();
    SafeFutureAssert.assertThatSafeFuture(completionFuture2).isNotCompleted();
    SafeFutureAssert.assertThatSafeFuture(completionFuture3).isNotCompleted();

    // future 2 timeouts
    final SafeFuture<Void> completionFuture2Timeout = completionFuture2.orTimeout(Duration.ZERO);
    assertThatThrownBy(() -> Waiter.waitFor(completionFuture2Timeout))
        .hasCauseInstanceOf(TimeoutException.class);

    // future2s are timed out
    SafeFutureAssert.assertThatSafeFuture(completionFuture2Timeout).isCompletedExceptionally();
    SafeFutureAssert.assertThatSafeFuture(completionFuture2).isCompletedExceptionally();

    // while the others are not yet completed
    SafeFutureAssert.assertThatSafeFuture(completionFuture1).isNotCompleted();
    SafeFutureAssert.assertThatSafeFuture(completionFuture3).isNotCompleted();

    // make tracker completes
    blockBlobSidecarsTracker.setBlock(block);

    // other futures are now completed
    SafeFutureAssert.assertThatSafeFuture(completionFuture1).isCompleted();
    SafeFutureAssert.assertThatSafeFuture(completionFuture3).isCompleted();
  }

  @Test
  void add_shouldWorkTillCompletionWhenAddingBlobsBeforeBlockIsSet() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);
    final BlobSidecar toAdd = blobSidecarsForBlock.getFirst();
    final Map<UInt64, BlobSidecar> added = new HashMap<>();
    final SafeFuture<Void> completionFuture = blockBlobSidecarsTracker.getCompletionFuture();

    added.put(toAdd.getIndex(), toAdd);
    blockBlobSidecarsTracker.add(toAdd);

    SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();
    assertThatThrownBy(blockBlobSidecarsTracker::getMissingBlobSidecars)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Block must be known to call this method");
    assertThat(blockBlobSidecarsTracker.getBlobSidecars())
        .containsExactlyInAnyOrderEntriesOf(added);

    blockBlobSidecarsTracker.setBlock(block);
    assertThat(blockBlobSidecarsTracker.getBlock()).isEqualTo(Optional.of(block));

    // now we know the block and we know about missing blobs
    final List<BlobIdentifier> stillMissing =
        blobIdentifiersForBlock.subList(1, blobIdentifiersForBlock.size());
    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars())
        .containsExactlyInAnyOrderElementsOf(stillMissing);
    SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();

    // let's complete with missing blobs
    for (int idx = 1; idx < blobIdentifiersForBlock.size(); idx++) {

      BlobSidecar blobSidecar = blobSidecarsForBlock.get(idx);

      blockBlobSidecarsTracker.add(blobSidecar);
      added.put(blobSidecar.getIndex(), blobSidecar);
      assertThat(blockBlobSidecarsTracker.getBlobSidecars())
          .containsExactlyInAnyOrderEntriesOf(added);

      if (idx == blobIdentifiersForBlock.size() - 1) {
        SafeFutureAssert.assertThatSafeFuture(completionFuture).isCompleted();
        assertThat(blockBlobSidecarsTracker.isComplete()).isTrue();
      } else {
        SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();
        assertThat(blockBlobSidecarsTracker.isComplete()).isFalse();
      }
    }

    SafeFutureAssert.assertThatSafeFuture(completionFuture).isCompleted();
    assertThat(blockBlobSidecarsTracker.isComplete()).isTrue();
  }

  @Test
  void add_shouldWorkWhenBlockIsSetFirst() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);
    final SafeFuture<Void> completionFuture = blockBlobSidecarsTracker.getCompletionFuture();

    blockBlobSidecarsTracker.setBlock(block);

    final BlobSidecar toAdd = blobSidecarsForBlock.get(0);
    final Map<UInt64, BlobSidecar> added = new HashMap<>();

    final List<BlobIdentifier> stillMissing =
        blobIdentifiersForBlock.subList(1, blobIdentifiersForBlock.size());

    added.put(toAdd.getIndex(), toAdd);
    blockBlobSidecarsTracker.add(toAdd);

    assertThat(blockBlobSidecarsTracker.getMissingBlobSidecars())
        .containsExactlyInAnyOrderElementsOf(stillMissing);
    SafeFutureAssert.assertThatSafeFuture(completionFuture).isNotCompleted();
    assertThat(blockBlobSidecarsTracker.getBlobSidecars())
        .containsExactlyInAnyOrderEntriesOf(added);
    assertThat(blockBlobSidecarsTracker.getBlock()).isEqualTo(Optional.of(block));
  }

  @Test
  void add_shouldThrowWhenAddingInconsistentBlobSidecar() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(dataStructureUtil.randomSlotAndBlockRoot());
    assertThatThrownBy(() -> blockBlobSidecarsTracker.add(dataStructureUtil.randomBlobSidecar()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void add_shouldAcceptAcceptSameBlobSidecarTwice() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);
    blockBlobSidecarsTracker.setBlock(block);
    blockBlobSidecarsTracker.setBlock(block);
    assertThat(blockBlobSidecarsTracker.getBlock()).isEqualTo(Optional.of(block));
  }

  @Test
  void getMissingBlobSidecars_shouldThrowWhenBlockIsUnknown() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);
    final BlobSidecar toAdd = blobSidecarsForBlock.get(2);

    blockBlobSidecarsTracker.add(toAdd);

    assertThatThrownBy(blockBlobSidecarsTracker::getMissingBlobSidecars)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Block must be known to call this method");
  }

  @Test
  void shouldNotIgnoreExcessiveBlobSidecarWhenBlockIsUnknownAndWePruneItLater() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);

    final BlobSidecar legitBlobSidecar = createBlobSidecar(UInt64.valueOf(2));
    final BlobSidecar excessiveBlobSidecar1 = createBlobSidecar(maxBlobsPerBlock);
    final BlobSidecar excessiveBlobSidecar2 = createBlobSidecar(maxBlobsPerBlock.plus(1));

    final List<BlobSidecar> toAddAltered =
        List.of(legitBlobSidecar, excessiveBlobSidecar1, excessiveBlobSidecar2);

    toAddAltered.forEach(
        blobSidecar -> assertThat(blockBlobSidecarsTracker.add(blobSidecar)).isTrue());

    assertThat(blockBlobSidecarsTracker.getBlobSidecars().values())
        .containsExactlyInAnyOrderElementsOf(toAddAltered);

    blockBlobSidecarsTracker.setBlock(block);

    assertThat(blockBlobSidecarsTracker.getBlobSidecars().values())
        .containsExactlyInAnyOrderElementsOf(List.of(legitBlobSidecar));
  }

  @Test
  void add_shouldIgnoreExcessiveBlobSidecarWhenBlockIsKnown() {
    final BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);

    blockBlobSidecarsTracker.setBlock(block);

    final BlobSidecar legitBlobSidecar = createBlobSidecar(UInt64.valueOf(2));
    final BlobSidecar excessiveBlobSidecar1 = createBlobSidecar(maxBlobsPerBlock);
    final BlobSidecar excessiveBlobSidecar2 = createBlobSidecar(maxBlobsPerBlock.plus(1));

    assertThat(blockBlobSidecarsTracker.add(legitBlobSidecar)).isTrue();
    assertThat(blockBlobSidecarsTracker.add(excessiveBlobSidecar1)).isFalse();
    assertThat(blockBlobSidecarsTracker.add(excessiveBlobSidecar2)).isFalse();

    assertThat(blockBlobSidecarsTracker.getBlobSidecars().size()).isEqualTo(1);
  }

  @Test
  void enableBlockImportOnCompletion_shouldImportOnlyOnceWhenCalled() {
    BlockBlobSidecarsTracker blockBlobSidecarsTracker =
        new BlockBlobSidecarsTracker(slotAndBlockRoot);

    blockBlobSidecarsTracker.setBlock(block);

    final BlockImportChannel blockImportChannel = mock(BlockImportChannel.class);
    when(blockImportChannel.importBlock(block))
        .thenReturn(
            SafeFuture.completedFuture(new BlockImportAndBroadcastValidationResults(null, null)));

    blockBlobSidecarsTracker.enableBlockImportOnCompletion(blockImportChannel);
    blockBlobSidecarsTracker.enableBlockImportOnCompletion(blockImportChannel);

    verifyNoInteractions(blockImportChannel);

    blobSidecarsForBlock.forEach(blockBlobSidecarsTracker::add);

    assertThat(blockBlobSidecarsTracker.isComplete()).isTrue();

    verify(blockImportChannel, times(1)).importBlock(block);
  }

  private BlobSidecar createBlobSidecar(final UInt64 index) {
    return dataStructureUtil
        .createRandomBlobSidecarBuilder()
        .signedBeaconBlockHeader(block.asHeader())
        .index(index)
        .build();
  }
}
