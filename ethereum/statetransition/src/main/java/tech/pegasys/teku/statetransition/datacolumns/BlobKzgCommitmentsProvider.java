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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

/**
 * Root-keyed source of blob KZG commitments for data column sidecar validation.
 *
 * <p>A beacon block root commits to the block body, and the body contains the {@code
 * blob_kzg_commitments}; consequently a root to commitments entry is immutable and can be populated
 * before full block import without conflict risk.
 *
 * <p>When a data column sidecar already carries commitments, as in Fulu, those commitments are
 * returned directly without caching. The cache is only a temporary pre-import source for forks
 * where the sidecar omits commitments, as in Gloas. Store lookup is retained as a lazy fallback for
 * cache misses.
 */
public class BlobKzgCommitmentsProvider
    implements ReceivedBlockEventsChannel, FinalizedCheckpointChannel {

  private final Spec spec;
  private final CombinedChainDataClient combinedChainDataClient;
  private final Map<Bytes32, BlobKzgCommitmentsEntry> commitmentsByRoot;

  public BlobKzgCommitmentsProvider(
      final Spec spec,
      final CombinedChainDataClient combinedChainDataClient,
      final int maxCacheSize) {
    checkArgument(maxCacheSize > 0, "maxCacheSize must be positive");
    this.spec = spec;
    this.combinedChainDataClient = combinedChainDataClient;
    this.commitmentsByRoot = LimitedMap.createSynchronizedLRU(maxCacheSize);
  }

  public void onNewBlock(final SignedBeaconBlock block) {
    block
        .getMessage()
        .getBody()
        .getOptionalBlobKzgCommitments()
        .ifPresent(
            commitments ->
                commitmentsByRoot.put(
                    block.getRoot(), new BlobKzgCommitmentsEntry(block.getSlot(), commitments)));
  }

  public SafeFuture<Optional<SszList<SszKZGCommitment>>> getBlobKzgCommitments(
      final DataColumnSidecar dataColumnSidecar) {
    return dataColumnSidecar
        .getMaybeKzgCommitments()
        .map(commitments -> SafeFuture.completedFuture(Optional.of(commitments)))
        .orElseGet(() -> getBlobKzgCommitments(dataColumnSidecar.getBeaconBlockRoot()));
  }

  @VisibleForTesting
  public SafeFuture<Optional<SszList<SszKZGCommitment>>> getBlobKzgCommitments(
      final Bytes32 blockRoot) {
    final BlobKzgCommitmentsEntry cachedEntry = commitmentsByRoot.get(blockRoot);
    if (cachedEntry != null) {
      return SafeFuture.completedFuture(Optional.of(cachedEntry.commitments()));
    }

    final Optional<SszList<SszKZGCommitment>> anchorCommitments =
        combinedChainDataClient
            .getLatestFinalized()
            .filter(anchor -> anchor.getRoot().equals(blockRoot))
            .flatMap(anchor -> anchor.getState().toVersionGloas())
            .map(BeaconStateGloas::getLatestExecutionPayloadBid)
            .map(bid -> bid.getBlobKzgCommitments());
    if (anchorCommitments.isPresent()) {
      return SafeFuture.completedFuture(anchorCommitments);
    }

    return combinedChainDataClient
        .getBlockByBlockRoot(blockRoot)
        .thenApply(
            maybeBlock -> {
              maybeBlock.ifPresent(this::onNewBlock);
              return maybeBlock.flatMap(
                  block -> block.getMessage().getBody().getOptionalBlobKzgCommitments());
            });
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {
    // Cache gossip blocks before import so sidecar validation can use them while import is in
    // flight.
    onNewBlock(block);
  }

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    // Imported blocks may arrive from RPC or API outside DAS pre-sampling.
    onNewBlock(block);
    // Keep this block's commitments for late-arriving sidecars. The parent root can be evicted
    // once a child imports because its import-time data-column validation window has passed; store
    // fallback still covers later historical/archive lookups.
    commitmentsByRoot.remove(block.getParentRoot());
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint checkpoint, final boolean fromOptimisticBlock) {
    final UInt64 finalizedSlot = checkpoint.getEpochStartSlot(spec);
    synchronized (commitmentsByRoot) {
      commitmentsByRoot
          .entrySet()
          .removeIf(entry -> entry.getValue().slot().isLessThanOrEqualTo(finalizedSlot));
    }
  }

  private record BlobKzgCommitmentsEntry(UInt64 slot, SszList<SszKZGCommitment> commitments) {}
}
