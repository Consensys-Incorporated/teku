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

import static tech.pegasys.teku.spec.config.SpecConfig.GENESIS_SLOT;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.metadata.BlobSidecarsAndMetaData;
import tech.pegasys.teku.storage.client.BlobSidecarReconstructionProvider;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobSidecarSelectorFactory extends AbstractSelectorFactory<BlobSidecarSelector> {
  private final Spec spec;
  private final BlobSidecarReconstructionProvider blobSidecarReconstructionProvider;

  public BlobSidecarSelectorFactory(
      final Spec spec,
      final CombinedChainDataClient client,
      final BlobSidecarReconstructionProvider blobSidecarReconstructionProvider) {
    super(client);
    this.spec = spec;
    this.blobSidecarReconstructionProvider = blobSidecarReconstructionProvider;
  }

  @Override
  public BlobSidecarSelector blockRootSelector(final Bytes32 blockRoot) {
    return indices ->
        client
            .getFinalizedSlotByBlockRoot(blockRoot)
            .thenCompose(
                maybeSlot -> {
                  if (maybeSlot.isPresent()) {
                    final SlotAndBlockRoot slotAndBlockRoot =
                        new SlotAndBlockRoot(maybeSlot.get(), blockRoot);
                    return getBlobSidecars(slotAndBlockRoot, indices)
                        .thenApply(blobSidecars -> addMetaData(blobSidecars, slotAndBlockRoot));
                  }
                  return client
                      .getBlockByBlockRoot(blockRoot)
                      .thenCompose(
                          maybeBlock -> {
                            if (maybeBlock.isEmpty()) {
                              return SafeFuture.completedFuture(Optional.empty());
                            }
                            final SignedBeaconBlock block = maybeBlock.get();
                            final SlotAndBlockRoot slotAndBlockRoot =
                                new SlotAndBlockRoot(block.getSlot(), blockRoot);
                            return getBlobSidecarsForBlock(maybeBlock, indices)
                                .thenApply(
                                    blobSidecars -> addMetaData(blobSidecars, slotAndBlockRoot));
                          });
                });
  }

  @Override
  public BlobSidecarSelector headSelector() {
    return indices ->
        client
            .getChainHead()
            .map(
                head ->
                    getBlobSidecars(head.getSlotAndBlockRoot(), indices)
                        .thenApply(
                            blobSidecars ->
                                addMetaData(
                                    blobSidecars, head.getSlotAndBlockRoot(), head.isOptimistic())))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public BlobSidecarSelector genesisSelector() {
    return indices ->
        client
            .getBlockAtSlotExact(GENESIS_SLOT)
            .thenCompose(
                maybeGenesisBlock ->
                    getBlobSidecarsForBlock(maybeGenesisBlock, indices)
                        .thenApply(
                            blobSidecars ->
                                addMetaData(
                                    blobSidecars,
                                    GENESIS_SLOT,
                                    false,
                                    true,
                                    client.isFinalized(GENESIS_SLOT))));
  }

  @Override
  public BlobSidecarSelector finalizedSelector() {
    return indices ->
        client
            .getLatestFinalized()
            .map(
                anchorPoint ->
                    getBlobSidecars(anchorPoint.getSlotAndBlockRoot(), indices)
                        .thenApply(
                            blobSidecars ->
                                addMetaData(
                                    blobSidecars,
                                    anchorPoint.getSlotAndBlockRoot(),
                                    client.isChainHeadOptimistic())))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public BlobSidecarSelector slotSelector(final UInt64 slot) {
    return indices -> {
      if (client.isFinalized(slot)) {
        return getBlobSidecars(slot, indices)
            .thenApply(
                blobSidecars ->
                    addMetaData(blobSidecars, slot, client.isChainHeadOptimistic(), true, true));
      }
      return client
          .getBlockAtSlotExact(slot)
          .thenCompose(
              maybeBlock ->
                  getBlobSidecarsForBlock(maybeBlock, indices)
                      .thenApply(
                          blobSidecars ->
                              addMetaData(
                                  blobSidecars,
                                  slot,
                                  client.isChainHeadOptimistic(),
                                  false,
                                  client.isFinalized(slot))));
    };
  }

  public BlobSidecarSelector slotSelectorForAll(final UInt64 slot) {
    return indices ->
        client
            .getAllBlobSidecars(slot, indices)
            .thenApply(
                blobSidecars ->
                    blobSidecars.isEmpty()
                        ? Optional.empty()
                        : addMetaData(
                            // We don't care about metadata since the api (teku only) that
                            // consumes the return value doesn't use it
                            Optional.of(blobSidecars), new SlotAndBlockRoot(slot, Bytes32.ZERO)));
  }

  private SafeFuture<Optional<List<BlobSidecar>>> getBlobSidecarsForBlock(
      final Optional<SignedBeaconBlock> maybeBlock, final List<UInt64> indices) {
    if (maybeBlock.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final SignedBeaconBlock block = maybeBlock.get();
    return block
        .getMessage()
        .getBody()
        .toVersionDeneb()
        .map(
            blockBodyDeneb ->
                blockBodyDeneb
                    .getOptionalBlobKzgCommitments()
                    .map(
                        blobKzgCommitments -> {
                          if (blobKzgCommitments.isEmpty()) {
                            return SafeFuture.completedFuture(
                                Optional.of(Collections.<BlobSidecar>emptyList()));
                          }
                          return getBlobSidecars(block.getSlotAndBlockRoot(), indices);
                        })
                    // in ePBS, we don't have commitments in the block (only the root) so for
                    // simplicity just querying the data
                    .orElseGet(() -> getBlobSidecars(block.getSlotAndBlockRoot(), indices)))
        .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  private SafeFuture<Optional<List<BlobSidecar>>> getBlobSidecars(
      final SlotAndBlockRoot slotAndBlockRoot, final List<UInt64> indices) {
    if (spec.atSlot(slotAndBlockRoot.getSlot())
        .getMilestone()
        .isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return blobSidecarReconstructionProvider
          .reconstructBlobSidecars(slotAndBlockRoot, indices)
          .thenApply(Optional::of);
    }
    return client
        .getBlobSidecars(slotAndBlockRoot, indices)
        .thenCompose(
            blobSidecars -> {
              if (blobSidecars.isEmpty()) {
                // attempt retrieving from archive (when enabled)
                return client.getArchivedBlobSidecars(slotAndBlockRoot, indices);
              }
              return SafeFuture.completedFuture(blobSidecars);
            })
        .thenApply(Optional::of);
  }

  private SafeFuture<Optional<List<BlobSidecar>>> getBlobSidecars(
      final UInt64 slot, final List<UInt64> indices) {
    if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
      return blobSidecarReconstructionProvider
          .reconstructBlobSidecars(slot, indices)
          .thenApply(Optional::of);
    }
    return client
        .getBlobSidecars(slot, indices)
        .thenCompose(
            blobSidecars -> {
              if (blobSidecars.isEmpty()) {
                // attempt retrieving from archive (when enabled)
                return client.getArchivedBlobSidecars(slot, indices);
              }
              return SafeFuture.completedFuture(blobSidecars);
            })
        .thenApply(Optional::of);
  }

  private Optional<BlobSidecarsAndMetaData> addMetaData(
      final Optional<List<BlobSidecar>> maybeBlobSidecarList,
      final SlotAndBlockRoot slotAndBlockRoot) {
    if (maybeBlobSidecarList.isEmpty()) {
      return Optional.empty();
    }

    final UInt64 slot = slotAndBlockRoot.getSlot();
    final Bytes32 blockRoot = slotAndBlockRoot.getBlockRoot();
    final Optional<ChainHead> maybeChainHead = client.getChainHead();
    final boolean isFinalized = client.isFinalized(slot);
    boolean isOptimistic;
    boolean isCanonical = false;

    if (maybeChainHead.isPresent()) {
      ChainHead chainHead = maybeChainHead.get();
      isOptimistic = chainHead.isOptimistic() || client.isOptimisticBlock(blockRoot);
      isCanonical = client.isCanonicalBlock(slot, blockRoot, chainHead.getRoot());
    } else {
      // If there's no chain head, we assume the block is not optimistic and not canonical
      isOptimistic = client.isOptimisticBlock(blockRoot);
    }
    return addMetaData(maybeBlobSidecarList, slot, isOptimistic, isCanonical, isFinalized);
  }

  private Optional<BlobSidecarsAndMetaData> addMetaData(
      final Optional<List<BlobSidecar>> maybeBlobSidecarList,
      final SlotAndBlockRoot slotAndBlockRoot,
      final boolean isOptimistic) {
    if (maybeBlobSidecarList.isEmpty()) {
      return Optional.empty();
    }
    return addMetaData(
        maybeBlobSidecarList,
        slotAndBlockRoot.getSlot(),
        isOptimistic,
        true,
        client.isFinalized(slotAndBlockRoot.getSlot()));
  }

  private Optional<BlobSidecarsAndMetaData> addMetaData(
      final Optional<List<BlobSidecar>> maybeBlobSidecarList,
      final UInt64 blockSlot,
      final boolean executionOptimistic,
      final boolean canonical,
      final boolean finalized) {
    return maybeBlobSidecarList.map(
        blobSidecarList ->
            new BlobSidecarsAndMetaData(
                blobSidecarList,
                spec.atSlot(blockSlot).getMilestone(),
                executionOptimistic,
                canonical,
                finalized));
  }
}
