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

import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.api.AbstractSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZGCommitment;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.Blob;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.deneb.BeaconBlockBodyDeneb;
import tech.pegasys.teku.spec.datastructures.metadata.BlobsAndMetaData;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.util.SlotAndBlockRootAndBlobIndex;
import tech.pegasys.teku.spec.logic.versions.deneb.helpers.MiscHelpersDeneb;
import tech.pegasys.teku.storage.client.BlobReconstructionProvider;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class BlobSelectorFactory extends AbstractSelectorFactory<BlobSelector> {
  private final Spec spec;
  private final BlobReconstructionProvider blobReconstructionProvider;
  private final Function<KZGCommitment, Bytes32> commitmentToVersionedHash;

  public BlobSelectorFactory(
      final Spec spec,
      final CombinedChainDataClient client,
      final BlobReconstructionProvider blobReconstructionProvider) {
    super(client);
    this.spec = spec;
    this.blobReconstructionProvider = blobReconstructionProvider;
    this.commitmentToVersionedHash =
        kzgCommitment -> {
          final MiscHelpersDeneb miscHelpersDeneb =
              MiscHelpersDeneb.required(spec.forMilestone(SpecMilestone.DENEB).miscHelpers());
          return miscHelpersDeneb.kzgCommitmentToVersionedHash(kzgCommitment).get();
        };
  }

  @Override
  public BlobSelector blockRootSelector(final Bytes32 blockRoot) {
    return versionedHashes -> {
      return client
          .getBlockByBlockRoot(blockRoot)
          .thenCompose(maybeSignedBlock -> fetchBlobsFromBlock(maybeSignedBlock, versionedHashes));
    };
  }

  @Override
  public BlobSelector headSelector() {
    return versionedHashes ->
        client
            .getChainHead()
            .map(
                chainHead ->
                    chainHead
                        .getBlock()
                        .thenCompose(
                            maybeSignedBlock ->
                                fetchBlobsFromBlock(maybeSignedBlock, versionedHashes)))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  @Override
  public BlobSelector finalizedSelector() {
    return versionedHashes -> fetchBlobsFromBlock(client.getFinalizedBlock(), versionedHashes);
  }

  @Override
  public BlobSelector genesisSelector() {
    return versionedHashes ->
        client
            .getBlockAtSlotExact(GENESIS_SLOT)
            .thenCompose(
                maybeSignedBlock -> fetchBlobsFromBlock(maybeSignedBlock, versionedHashes));
  }

  @Override
  public BlobSelector slotSelector(final UInt64 slot) {
    return versionedHashes ->
        client
            .getChainHead()
            .map(
                chainHead ->
                    client
                        .getBlockAtSlotExact(slot, chainHead.getRoot())
                        .thenCompose(
                            maybeSignedBlock ->
                                fetchBlobsFromBlock(maybeSignedBlock, versionedHashes)))
            .orElse(SafeFuture.completedFuture(Optional.empty()));
  }

  private SafeFuture<Optional<BlobsAndMetaData>> fetchBlobsFromBlock(
      final Optional<SignedBeaconBlock> maybeSignedBlock, final List<Bytes32> versionedHashes) {
    final Optional<ChainHead> maybeChainHead = client.getChainHead();
    if (maybeSignedBlock.isEmpty() || maybeChainHead.isEmpty()) {
      return SafeFuture.completedFuture(Optional.empty());
    }
    final Collection<SlotAndBlockRootAndBlobIndex> identifiers =
        toBlobIdentifiers(versionedHashes, maybeSignedBlock);
    final ChainHead chainHead = maybeChainHead.get();
    final BeaconBlock block = maybeSignedBlock.get().getMessage();
    final boolean isCanonical =
        client.isCanonicalBlock(block.getSlot(), block.getRoot(), chainHead.getRoot());
    final boolean isOptimistic =
        chainHead.isOptimistic() || client.isOptimisticBlock(block.getRoot());
    final boolean isFinalized = client.isFinalized(block.getSlot());
    if (identifiers.isEmpty()) {
      return SafeFuture.completedFuture(
          addMetaData(
              Optional.of(List.of()), block.getSlot(), isOptimistic, isCanonical, isFinalized));
    } else {
      final SafeFuture<List<Blob>> blobsFuture;
      if (spec.atSlot(block.getSlot()).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.FULU)) {
        blobsFuture =
            blobReconstructionProvider.reconstructBlobs(
                block.getSlotAndBlockRoot(),
                identifiers.stream().map(SlotAndBlockRootAndBlobIndex::getBlobIndex).toList(),
                isCanonical);
      } else {
        blobsFuture =
            client
                .getBlobSidecars(
                    block.getSlotAndBlockRoot(),
                    identifiers.stream().map(SlotAndBlockRootAndBlobIndex::getBlobIndex).toList())
                .thenApply(
                    blobSidecars -> blobSidecars.stream().map(BlobSidecar::getBlob).toList());
      }
      return blobsFuture.thenApply(
          blobs ->
              addMetaData(
                  Optional.of(blobs), block.getSlot(), isOptimistic, isCanonical, isFinalized));
    }
  }

  @VisibleForTesting
  Map<Bytes32, SlotAndBlockRootAndBlobIndex> blockToVersionedHashBlobs(
      final Optional<SignedBeaconBlock> block) {
    if (block.isEmpty()
        || spec.atSlot(block.get().getSlot()).getMilestone().isLessThan(SpecMilestone.DENEB)) {
      return Collections.emptyMap();
    }

    final SszList<SszKZGCommitment> blobKzgCommitments =
        BeaconBlockBodyDeneb.required(block.get().getMessage().getBody()).getBlobKzgCommitments();
    return IntStream.range(0, blobKzgCommitments.size())
        .mapToObj(index -> Pair.of(index, blobKzgCommitments.get(index)))
        .collect(
            Collectors.toConcurrentMap(
                commitmentPair ->
                    commitmentToVersionedHash.apply(commitmentPair.getValue().getKZGCommitment()),
                commitmentPair ->
                    new SlotAndBlockRootAndBlobIndex(
                        block.get().getSlot(),
                        block.get().getRoot(),
                        UInt64.valueOf(commitmentPair.getKey())),
                // we often have duplicates in testnets
                (existing, replacement) -> replacement));
  }

  @VisibleForTesting
  Collection<SlotAndBlockRootAndBlobIndex> toBlobIdentifiers(
      final List<Bytes32> versionedHashes, final Optional<SignedBeaconBlock> block) {
    final Map<Bytes32, SlotAndBlockRootAndBlobIndex> blockToVersionedHashBlobs =
        blockToVersionedHashBlobs(block);
    if (blockToVersionedHashBlobs.isEmpty()) {
      return Collections.emptyList();
    }

    if (versionedHashes.isEmpty()) {
      return blockToVersionedHashBlobs.values();
    }

    return versionedHashes.stream()
        .map(key -> Optional.ofNullable(blockToVersionedHashBlobs.get(key)))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

  private Optional<BlobsAndMetaData> addMetaData(
      final Optional<List<Blob>> maybeBlobList,
      final UInt64 blockSlot,
      final boolean executionOptimistic,
      final boolean canonical,
      final boolean finalized) {
    return maybeBlobList.map(
        blobList ->
            new BlobsAndMetaData(
                blobList,
                spec.atSlot(blockSlot).getMilestone(),
                executionOptimistic,
                canonical,
                finalized));
  }
}
