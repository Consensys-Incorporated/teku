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

package tech.pegasys.teku.api.blockselector;

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlockSelectorFactory extends AbstractSelectorFactory<BlockSelector> {

  private final Spec spec;

  public BlockSelectorFactory(final Spec spec, final CombinedChainDataClient client) {
    super(client);
    this.spec = spec;
  }

  @Override
  public BlockSelector blockRootSelector(final Bytes32 blockRoot) {
    return () ->
        optionalToList(client.getBlockByBlockRoot(blockRoot).thenApply(this::lookupBlockData));
  }

  @Override
  public BlockSelector headSelector() {
    return () ->
        optionalToList(
            client
                .getChainHead()
                .map(this::fromChainHead)
                .orElse(SafeFuture.completedFuture(Optional.empty())));
  }

  @Override
  public BlockSelector finalizedSelector() {
    return () ->
        optionalToList(
            SafeFuture.completedFuture(
                // Finalized checkpoint is always canonical
                lookupCanonicalBlockData(
                    client.getFinalizedBlock(),
                    // The finalized checkpoint may change because of optimistically imported blocks
                    // at the head and if the head isn't optimistic, the finalized block can't be
                    // optimistic.
                    client.isChainHeadOptimistic())));
  }

  @Override
  public BlockSelector genesisSelector() {
    return () ->
        optionalToList(
            client
                .getBlockAtSlotExact(GENESIS_SLOT)
                .thenApply(maybeBlock -> lookupCanonicalBlockData(maybeBlock, false)));
  }

  @Override
  public BlockSelector slotSelector(final UInt64 slot) {
    return () -> optionalToList(forSlot(client.getChainHead(), slot));
  }

  public BlockSelector nonCanonicalBlocksSelector(final UInt64 slot) {
    return () -> {
      final Optional<ChainHead> maybeChainHead = client.getChainHead();
      if (maybeChainHead.isEmpty()) {
        return SafeFuture.completedFuture(Collections.emptyList());
      }
      final ChainHead chainHead = maybeChainHead.get();
      return client.getAllBlocksAtSlot(slot, chainHead);
    };
  }

  private SafeFuture<Optional<BlockAndMetaData>> fromChainHead(final ChainHead head) {
    return head.getBlock()
        .thenApply(maybeBlock -> lookupCanonicalBlockData(maybeBlock, head.isOptimistic()));
  }

  private SafeFuture<Optional<BlockAndMetaData>> forSlot(
      final Optional<ChainHead> maybeHead, final UInt64 slot) {
    return maybeHead
        .map(head -> forSlot(head, slot))
        .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  private SafeFuture<Optional<BlockAndMetaData>> forSlot(final ChainHead head, final UInt64 slot) {
    return client
        .getBlockAtSlotExact(slot, head.getRoot())
        .thenApply(maybeBlock -> lookupCanonicalBlockData(maybeBlock, head.isOptimistic()));
  }

  private SafeFuture<List<BlockAndMetaData>> optionalToList(
      final SafeFuture<Optional<BlockAndMetaData>> future) {
    return future.thenApply(
        maybeBlock -> maybeBlock.map(List::of).orElseGet(Collections::emptyList));
  }

  private Optional<BlockAndMetaData> lookupBlockData(final Optional<SignedBeaconBlock> maybeBlock) {
    // Ensure we use the same chain head when calculating metadata to ensure a consistent view.
    final Optional<ChainHead> chainHead = client.getChainHead();
    if (maybeBlock.isEmpty() || chainHead.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(lookupBlockData(maybeBlock.get(), chainHead.get()));
  }

  private Optional<BlockAndMetaData> lookupCanonicalBlockData(
      final Optional<SignedBeaconBlock> maybeBlock, final boolean isOptimistic) {
    return maybeBlock.map(
        block -> lookupBlockData(block, isOptimistic, true, client.isFinalized(block.getSlot())));
  }

  private BlockAndMetaData lookupBlockData(
      final SignedBeaconBlock block, final ChainHead chainHead) {
    return lookupBlockData(
        block,
        // If the chain head is optimistic that will "taint" whether the block is canonical
        chainHead.isOptimistic() || client.isOptimisticBlock(block.getRoot()),
        client.isCanonicalBlock(block.getSlot(), block.getRoot(), chainHead.getRoot()),
        client.isFinalized(block.getSlot()));
  }

  private BlockAndMetaData lookupBlockData(
      final SignedBeaconBlock block,
      final boolean isOptimistic,
      final boolean isCanonical,
      final boolean finalized) {
    return new BlockAndMetaData(
        block, spec.atSlot(block.getSlot()).getMilestone(), isOptimistic, isCanonical, finalized);
  }
}
