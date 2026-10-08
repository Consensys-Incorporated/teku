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

package tech.pegasys.teku.statetransition.util;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.block.ParentExecutionPayloadDependency;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;

public class PendingBlockPool implements SlotEventsChannel, FinalizedCheckpointChannel {
  private final PendingPool<SignedBeaconBlock> blocksWaitingForParent;
  private final PendingPool<PendingParentExecutionPayloadBlock>
      blocksWaitingForParentExecutionPayload;

  PendingBlockPool(
      final SettableLabelledGauge pendingPoolsSizeGauge,
      final SettableLabelledGauge pendingPoolsBytesGauge,
      final Spec spec,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxBlocksWaitingForParent,
      final int maxBlocksWaitingForParentExecutionPayload,
      final long maxPendingBlockBytes) {
    this.blocksWaitingForParent =
        new PendingPool<>(
            pendingPoolsSizeGauge,
            "blocks",
            spec,
            historicalBlockTolerance,
            futureBlockTolerance,
            maxBlocksWaitingForParent,
            maxPendingBlockBytes,
            PendingBlockPool::getSszSize,
            Optional.of(pendingPoolsBytesGauge),
            block -> block.getMessage().hashTreeRoot(),
            block -> Collections.singleton(block.getParentRoot()),
            SignedBeaconBlock::getSlot);
    this.blocksWaitingForParentExecutionPayload =
        new PendingPool<>(
            pendingPoolsSizeGauge,
            "blocks_waiting_for_parent_execution_payload",
            spec,
            historicalBlockTolerance,
            futureBlockTolerance,
            maxBlocksWaitingForParentExecutionPayload,
            maxPendingBlockBytes,
            PendingParentExecutionPayloadBlock::getSszSize,
            Optional.of(pendingPoolsBytesGauge),
            PendingParentExecutionPayloadBlock::hashTreeRoot,
            pendingBlock ->
                Collections.singleton(pendingBlock.dependency().parentBeaconBlockRoot()),
            PendingParentExecutionPayloadBlock::getSlot);
  }

  public PendingPool<SignedBeaconBlock> getBlocksWaitingForParent() {
    return blocksWaitingForParent;
  }

  @VisibleForTesting
  PendingPool<?> getBlocksWaitingForParentExecutionPayload() {
    return blocksWaitingForParentExecutionPayload;
  }

  public void addForMissingParent(final SignedBeaconBlock block) {
    blocksWaitingForParent.add(block);
  }

  public boolean addForMissingParentExecutionPayload(
      final SignedBeaconBlock block, final ParentExecutionPayloadDependency dependency) {
    final boolean alreadyPending = blocksWaitingForParentExecutionPayload.contains(block.getRoot());
    blocksWaitingForParentExecutionPayload.add(
        new PendingParentExecutionPayloadBlock(block, dependency));
    return !alreadyPending && blocksWaitingForParentExecutionPayload.contains(block.getRoot());
  }

  public boolean contains(final SignedBeaconBlock block) {
    return blocksWaitingForParent.contains(block)
        || blocksWaitingForParentExecutionPayload.contains(block.getRoot());
  }

  public Optional<SignedBeaconBlock> remove(final SignedBeaconBlock block) {
    final Optional<SignedBeaconBlock> removedBlockWaitingForParent =
        removeBlockWaitingForParent(block);
    final Optional<SignedBeaconBlock> removedBlockWaitingForParentExecutionPayload =
        removeBlockWaitingForParentExecutionPayload(block);
    return removedBlockWaitingForParent.or(() -> removedBlockWaitingForParentExecutionPayload);
  }

  public List<SignedBeaconBlock> removeBlocksWaitingForParent(final Bytes32 parentRoot) {
    final List<SignedBeaconBlock> blocksToImport =
        blocksWaitingForParent.getItemsDependingOn(parentRoot, false);
    blocksToImport.forEach(blocksWaitingForParent::remove);
    return blocksToImport;
  }

  public List<SignedBeaconBlock> removeBlocksWaitingForParentExecutionPayload(
      final ParentExecutionPayloadDependency parentExecutionPayloadDependency) {
    final List<PendingParentExecutionPayloadBlock> blocksToImport =
        blocksWaitingForParentExecutionPayload
            .getItemsDependingOn(parentExecutionPayloadDependency.parentBeaconBlockRoot(), false)
            .stream()
            .filter(
                pendingBlock -> pendingBlock.dependency().equals(parentExecutionPayloadDependency))
            .toList();
    blocksToImport.forEach(blocksWaitingForParentExecutionPayload::remove);
    return blocksToImport.stream().map(PendingParentExecutionPayloadBlock::block).toList();
  }

  public List<SignedBeaconBlock> removeBlocksWaitingForParentExecutionPayload(
      final Bytes32 parentRoot) {
    final List<PendingParentExecutionPayloadBlock> blocksToRemove =
        blocksWaitingForParentExecutionPayload.getItemsDependingOn(parentRoot, false);
    blocksToRemove.forEach(blocksWaitingForParentExecutionPayload::remove);
    return blocksToRemove.stream().map(PendingParentExecutionPayloadBlock::block).toList();
  }

  @Override
  public void onSlot(final UInt64 slot) {
    blocksWaitingForParent.onSlot(slot);
    blocksWaitingForParentExecutionPayload.onSlot(slot);
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    blocksWaitingForParent.onNewFinalizedCheckpoint(checkpoint, fromOptimisticBlock);
    blocksWaitingForParentExecutionPayload.onNewFinalizedCheckpoint(
        checkpoint, fromOptimisticBlock);
  }

  private Optional<SignedBeaconBlock> removeBlockWaitingForParent(final SignedBeaconBlock block) {
    if (!blocksWaitingForParent.contains(block)) {
      return Optional.empty();
    }
    blocksWaitingForParent.remove(block);
    return Optional.of(block);
  }

  private Optional<SignedBeaconBlock> removeBlockWaitingForParentExecutionPayload(
      final SignedBeaconBlock block) {
    return blocksWaitingForParentExecutionPayload
        .get(block.getRoot())
        .map(
            pendingBlock -> {
              blocksWaitingForParentExecutionPayload.remove(pendingBlock);
              return pendingBlock.block();
            });
  }

  private static long getSszSize(final SignedBeaconBlock block) {
    return block.getSchema().getSszSize(block.getBackingNode());
  }

  private record PendingParentExecutionPayloadBlock(
      SignedBeaconBlock block, ParentExecutionPayloadDependency dependency) {

    private Bytes32 hashTreeRoot() {
      return block.getMessage().hashTreeRoot();
    }

    private UInt64 getSlot() {
      return block.getSlot();
    }

    private long getSszSize() {
      return PendingBlockPool.getSszSize(block);
    }
  }
}
