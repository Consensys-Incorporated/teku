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

package tech.pegasys.teku.api.datacolumnselector;

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
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.metadata.DataColumnSidecarsAndMetaData;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class DataColumnSidecarSelectorFactory
    extends AbstractSelectorFactory<DataColumnSidecarSelector> {

  private final Spec spec;

  public DataColumnSidecarSelectorFactory(final Spec spec, final CombinedChainDataClient client) {
    super(client);
    this.spec = spec;
  }

  @Override
  public DataColumnSidecarSelector blockRootSelector(final Bytes32 blockRoot) {
    return indices ->
        client
            .getFinalizedSlotByBlockRoot(blockRoot)
            .thenCompose(
                maybeSlot -> {
                  if (maybeSlot.isPresent()) {
                    final SlotAndBlockRoot slotAndBlockRoot =
                        new SlotAndBlockRoot(maybeSlot.get(), blockRoot);
                    return getDataColumnSidecars(maybeSlot.get(), indices)
                        .thenApply(
                            dataColumnSidecars ->
                                addMetaData(dataColumnSidecars, slotAndBlockRoot));
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
                            return getDataColumnSidecarsForBlock(maybeBlock, indices)
                                .thenApply(
                                    dataColumnSidecars ->
                                        addMetaData(dataColumnSidecars, slotAndBlockRoot));
                          });
                });
  }

  @Override
  public DataColumnSidecarSelector headSelector() {
    return indices ->
        client
            .getChainHead()
            .map(
                head ->
                    getDataColumnSidecars(head.getSlot(), indices)
                        .thenApply(
                            dataColumnSidecars ->
                                addMetaData(
                                    dataColumnSidecars, head.getSlot(), head.isOptimistic())))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public DataColumnSidecarSelector genesisSelector() {
    return indices ->
        client
            .getBlockAtSlotExact(GENESIS_SLOT)
            .thenCompose(
                maybeGenesisBlock ->
                    getDataColumnSidecarsForBlock(maybeGenesisBlock, indices)
                        .thenApply(
                            dataColumnSidecars ->
                                addMetaData(
                                    dataColumnSidecars,
                                    GENESIS_SLOT,
                                    false,
                                    true,
                                    client.isFinalized(GENESIS_SLOT))));
  }

  @Override
  public DataColumnSidecarSelector finalizedSelector() {
    return indices ->
        client
            .getLatestFinalized()
            .map(
                anchorPoint ->
                    getDataColumnSidecars(anchorPoint.getSlot(), indices)
                        .thenApply(
                            dataColumnSidecars ->
                                addMetaData(
                                    dataColumnSidecars,
                                    anchorPoint.getSlot(),
                                    client.isChainHeadOptimistic())))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public DataColumnSidecarSelector slotSelector(final UInt64 slot) {
    return indices ->
        getDataColumnSidecars(slot, indices)
            .thenApply(
                dataColumnSidecars ->
                    addMetaData(dataColumnSidecars, slot, client.isChainHeadOptimistic()));
  }

  private SafeFuture<Optional<List<DataColumnSidecar>>> getDataColumnSidecarsForBlock(
      final Optional<SignedBeaconBlock> maybeBlock, final List<UInt64> indices) {
    if (maybeBlock.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final Optional<BeaconBlockBodyDeneb> maybeDenebBlock =
        maybeBlock.get().getMessage().getBody().toVersionDeneb();
    if (maybeDenebBlock.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    final UInt64 slot = maybeBlock.get().getSlot();
    if (spec.atSlot(slot).getMilestone().isLessThan(SpecMilestone.FULU)) {
      return SafeFuture.completedFuture(Optional.empty());
    }

    // Valid FULU block but without blobs => emptyList()
    if (maybeDenebBlock.get().getBlobKzgCommitments().isEmpty()) {
      return SafeFuture.completedFuture(Optional.of(Collections.emptyList()));
    }

    return client.getDataColumnSidecars(slot, indices).thenApply(Optional::of);
  }

  private SafeFuture<Optional<List<DataColumnSidecar>>> getDataColumnSidecars(
      final UInt64 slot, final List<UInt64> indices) {
    if (spec.atSlot(slot).getMilestone().isLessThan(SpecMilestone.FULU)) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    return client.getDataColumnSidecars(slot, indices).thenApply(Optional::of);
  }

  private Optional<DataColumnSidecarsAndMetaData> addMetaData(
      final Optional<List<DataColumnSidecar>> maybeDataColumnSidecarList,
      final SlotAndBlockRoot slotAndBlockRoot) {
    final UInt64 slot = slotAndBlockRoot.getSlot();
    final Bytes32 blockRoot = slotAndBlockRoot.getBlockRoot();
    final Optional<ChainHead> maybeChainHead = client.getChainHead();
    final boolean isFinalized = client.isFinalized(slot);
    final boolean isOptimistic = client.isOptimisticBlock(blockRoot);
    final boolean isCanonical =
        maybeChainHead
            .map(chainHead -> client.isCanonicalBlock(slot, blockRoot, chainHead.getRoot()))
            .orElse(false);
    return addMetaData(maybeDataColumnSidecarList, slot, isOptimistic, isCanonical, isFinalized);
  }

  private Optional<DataColumnSidecarsAndMetaData> addMetaData(
      final Optional<List<DataColumnSidecar>> maybeDataColumnSidecarList,
      final UInt64 slot,
      final boolean isOptimistic) {
    if (maybeDataColumnSidecarList.isEmpty()) {
      return Optional.empty();
    }
    return addMetaData(
        maybeDataColumnSidecarList, slot, isOptimistic, true, client.isFinalized(slot));
  }

  private Optional<DataColumnSidecarsAndMetaData> addMetaData(
      final Optional<List<DataColumnSidecar>> maybeDataColumnSidecars,
      final UInt64 slot,
      final boolean isOptimistic,
      final boolean isCanonical,
      final boolean isFinalized) {
    return maybeDataColumnSidecars.map(
        dataColumnSidecars ->
            new DataColumnSidecarsAndMetaData(
                dataColumnSidecars,
                spec.atSlot(slot).getMilestone(),
                isOptimistic,
                isCanonical,
                isFinalized));
  }
}
