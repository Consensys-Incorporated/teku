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

import static tech.pegasys.teku.spec.config.Constants.MAX_SLOTS_TO_TRACK_PROPOSER_PREFERENCES;
import static tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider.DEFAULT_MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS;
import static tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider.DEFAULT_MIN_WAIT_MILLIS;
import static tech.pegasys.teku.statetransition.util.RPCFetchDelayProvider.DEFAULT_TARGET_WAIT_MILLIS;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.SettableLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackerFactory;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;
import tech.pegasys.teku.statetransition.block.BlockImportChannel;
import tech.pegasys.teku.statetransition.block.FutureBlockPool;
import tech.pegasys.teku.statetransition.datacolumns.CurrentSlotProvider;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarELManager;
import tech.pegasys.teku.statetransition.datacolumns.util.DataColumnSidecarELManagerImpl;
import tech.pegasys.teku.statetransition.execution.PendingExecutionPayloadBid;
import tech.pegasys.teku.statetransition.execution.PendingProposerPreferences;
import tech.pegasys.teku.statetransition.validation.BlobSidecarGossipValidator;
import tech.pegasys.teku.statetransition.validation.DataColumnSidecarGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class PoolFactory {

  private static final UInt64 DEFAULT_HISTORICAL_SLOT_TOLERANCE = UInt64.valueOf(320);

  private static final int DEFAULT_MAX_BLOCKS = 5000;
  private static final int DEFAULT_MAX_BLOCKS_PENDING_PARENT_EXECUTION_PAYLOAD = 1024;
  // Two mainnet epochs for the current lookahead, with 4x overhead for dependent-root reorgs
  private static final int DEFAULT_MAX_PENDING_PROPOSER_PREFERENCES = 256;
  private static final int DEFAULT_MAX_PENDING_EXECUTION_PAYLOAD_BIDS = 1000;
  // Gloas blocks retain roughly 4-5x their SSZ size on the heap, so this bounds each pending block
  // pool to a few hundred MB while leaving headroom over observed usage
  private static final int DEFAULT_PENDING_BLOCK_BYTES_MULTIPLIER = 4;
  // Honest nodes only see a block or two ahead of the current slot, while future blocks are queued
  // before their signature is verified, so keep the budget small
  private static final int DEFAULT_FUTURE_BLOCK_BYTES_MULTIPLIER = 4;
  private static final int DEFAULT_MAX_FUTURE_BLOCKS_PER_SLOT = 4;
  private static final int EL_RECOVERY_TASKS_LIMIT = 10;
  private static final Duration EL_BLOBS_FETCHING_DELAY = Duration.ofMillis(500);
  private static final int EL_BLOBS_FETCHING_MAX_RETRIES = 3;

  private final SettableLabelledGauge pendingPoolsSizeGauge;
  private final SettableLabelledGauge pendingPoolsBytesGauge;
  private final SettableLabelledGauge blockBlobSidecarsTrackersPoolSizeGauge;
  private final LabelledMetric<Counter> blockBlobSidecarsTrackersPoolStats;
  private final LabelledMetric<Counter> futureBlocksResultCounter;

  public PoolFactory(final MetricsSystem metricsSystem) {
    this.pendingPoolsSizeGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "pending_pool_size",
            "Number of items in pending pool",
            "type");

    this.pendingPoolsBytesGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "pending_pool_bytes",
            "Total SSZ size in bytes of blocks in pending pool",
            "type");

    this.blockBlobSidecarsTrackersPoolSizeGauge =
        SettableLabelledGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "block_blobs_trackers_pool_size",
            "Number of items in block-blobs trackers pool",
            "type");

    this.blockBlobSidecarsTrackersPoolStats =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.BEACON,
            "block_blobs_trackers_pool_stats_total",
            "Block-blobs trackers pool statistics",
            "type",
            "subtype");

    this.futureBlocksResultCounter = FutureBlockPool.createResultCounter(metricsSystem);
  }

  public PendingPool<SignedBeaconBlock> createPendingPoolForBlocks(final Spec spec) {
    return createPendingPoolForBlocks(
        spec,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_BLOCKS);
  }

  public PendingPool<SignedBeaconBlock> createPendingPoolForBlocks(
      final Spec spec,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxItems) {
    return new PendingPool<>(
        pendingPoolsSizeGauge,
        "blocks",
        spec,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxItems,
        getMaxPendingBlockBytes(spec),
        block -> block.getSchema().getSszSize(block.getBackingNode()),
        Optional.of(pendingPoolsBytesGauge),
        block -> block.getMessage().hashTreeRoot(),
        block -> Collections.singleton(block.getParentRoot()),
        SignedBeaconBlock::getSlot);
  }

  public PendingBlockPool createPendingBlockPool(final Spec spec) {
    return createPendingBlockPool(
        spec,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_BLOCKS,
        DEFAULT_MAX_BLOCKS_PENDING_PARENT_EXECUTION_PAYLOAD);
  }

  public PendingBlockPool createPendingBlockPool(
      final Spec spec,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxItems) {
    return createPendingBlockPool(
        spec, historicalBlockTolerance, futureBlockTolerance, maxItems, maxItems);
  }

  public PendingBlockPool createPendingBlockPool(
      final Spec spec,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxBlocksWaitingForParent,
      final int maxBlocksWaitingForParentExecutionPayload) {
    return new PendingBlockPool(
        pendingPoolsSizeGauge,
        pendingPoolsBytesGauge,
        spec,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxBlocksWaitingForParent,
        maxBlocksWaitingForParentExecutionPayload,
        getMaxPendingBlockBytes(spec));
  }

  public FutureBlockPool createFutureBlockPool(
      final Spec spec, final SettableLabelledGauge futureItemsCounter) {
    return new FutureBlockPool(
        DEFAULT_MAX_FUTURE_BLOCKS_PER_SLOT,
        (long) spec.getNetworkingConfig().getMaxPayloadSize()
            * DEFAULT_FUTURE_BLOCK_BYTES_MULTIPLIER,
        block -> block.getSchema().getSszSize(block.getBackingNode()),
        futureItemsCounter,
        futureBlocksResultCounter);
  }

  private long getMaxPendingBlockBytes(final Spec spec) {
    return (long) spec.getNetworkingConfig().getMaxPayloadSize()
        * DEFAULT_PENDING_BLOCK_BYTES_MULTIPLIER;
  }

  public PendingAttestationPool createPendingAttestationPool(
      final Spec spec, final int maxQueueSize) {
    return createPendingAttestationPool(spec, maxQueueSize, maxQueueSize);
  }

  public PendingAttestationPool createPendingAttestationPool(
      final Spec spec,
      final int maxAttestationsWaitingForBlock,
      final int maxAttestationsWaitingForFullPayload) {
    return new PendingAttestationPool(
        pendingPoolsSizeGauge,
        spec,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        maxAttestationsWaitingForBlock,
        maxAttestationsWaitingForFullPayload);
  }

  public PendingPool<ValidatableAttestation> createPendingPoolForAttestations(
      final Spec spec, final int maxQueueSize) {

    return new PendingPool<>(
        pendingPoolsSizeGauge,
        "attestations",
        spec,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        maxQueueSize,
        ValidatableAttestation::hashTreeRoot,
        ValidatableAttestation::getDependentBlockRoots,
        ValidatableAttestation::getEarliestSlotForForkChoiceProcessing);
  }

  public PendingPool<PayloadAttestationMessage> createPendingPoolForPayloadAttestations(
      final Spec spec, final int maxQueueSize) {
    return new PendingPool<>(
        pendingPoolsSizeGauge,
        "payload_attestations",
        spec,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        maxQueueSize,
        PayloadAttestationMessage::hashTreeRoot,
        payloadAttestation ->
            Collections.singletonList(payloadAttestation.getData().getBeaconBlockRoot()),
        payloadAttestation -> payloadAttestation.getData().getSlot());
  }

  public PendingPool<PendingExecutionPayloadBid> createPendingPoolForExecutionPayloadBids(
      final Spec spec) {
    return createPendingPoolForExecutionPayloadBids(
        spec, DEFAULT_MAX_PENDING_EXECUTION_PAYLOAD_BIDS);
  }

  public PendingPool<PendingExecutionPayloadBid> createPendingPoolForExecutionPayloadBids(
      final Spec spec, final int maxQueueSize) {
    return new PendingPool<>(
        pendingPoolsSizeGauge,
        "execution_payload_bids",
        spec,
        UInt64.ZERO,
        UInt64.ONE,
        maxQueueSize,
        pendingBid -> pendingBid.signedBid().hashTreeRoot(),
        pendingBid ->
            Collections.singletonList(pendingBid.signedBid().getMessage().getParentBlockRoot()),
        pendingBid -> pendingBid.signedBid().getMessage().getSlot());
  }

  public PendingPool<PendingProposerPreferences> createPendingPoolForProposerPreferences(
      final Spec spec) {
    return createPendingPoolForProposerPreferences(spec, DEFAULT_MAX_PENDING_PROPOSER_PREFERENCES);
  }

  public PendingPool<PendingProposerPreferences> createPendingPoolForProposerPreferences(
      final Spec spec, final int maxQueueSize) {
    return new PendingPool<>(
        pendingPoolsSizeGauge,
        "proposer_preferences",
        spec,
        UInt64.ZERO,
        UInt64.valueOf(MAX_SLOTS_TO_TRACK_PROPOSER_PREFERENCES),
        maxQueueSize,
        proposerPreferences -> proposerPreferences.signedProposerPreferences().hashTreeRoot(),
        proposerPreferences ->
            Collections.singletonList(
                proposerPreferences.signedProposerPreferences().getMessage().getDependentRoot()),
        preferences -> preferences.signedProposerPreferences().getMessage().getProposalSlot());
  }

  public <T> PendingPool<T> createNoOpPendingPool(final Spec spec) {
    return new NoOpPendingPool<>(
        pendingPoolsSizeGauge,
        "no_op",
        spec,
        UInt64.ZERO,
        UInt64.ZERO,
        0,
        __ -> Bytes32.ZERO,
        __ -> Collections.emptyList(),
        __ -> UInt64.ZERO);
  }

  public BlockBlobSidecarsTrackersPoolImpl createPoolForBlockBlobSidecarsTrackers(
      final BlockImportChannel blockImportChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Function<BlobSidecar, SafeFuture<Void>> blobSidecarGossipPublisher) {
    return createPoolForBlockBlobSidecarsTrackers(
        blockImportChannel,
        spec,
        timeProvider,
        asyncRunner,
        recentChainData,
        executionLayer,
        gossipValidatorSupplier,
        blobSidecarGossipPublisher,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        DEFAULT_MAX_BLOCKS);
  }

  public DataColumnSidecarELManager createDataColumnSidecarELManager(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final BiConsumer<List<DataColumnSidecar>, RemoteOrigin> dataColumnSidecarPublisher,
      final DataColumnSidecarGossipValidator dataColumnSidecarGossipValidator,
      final CustodyGroupCountManager custodyGroupCountManager,
      final MetricsSystem metricsSystem,
      final TimeProvider timeProvider) {
    return new DataColumnSidecarELManagerImpl(
        spec,
        asyncRunner,
        recentChainData,
        executionLayer,
        DEFAULT_HISTORICAL_SLOT_TOLERANCE,
        FutureItems.DEFAULT_FUTURE_SLOT_TOLERANCE,
        EL_RECOVERY_TASKS_LIMIT,
        dataColumnSidecarPublisher,
        custodyGroupCountManager,
        metricsSystem,
        timeProvider,
        EL_BLOBS_FETCHING_DELAY,
        EL_BLOBS_FETCHING_MAX_RETRIES,
        dataColumnSidecarGossipValidator);
  }

  public BlockBlobSidecarsTrackersPoolImpl createPoolForBlockBlobSidecarsTrackers(
      final BlockImportChannel blockImportChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Function<BlobSidecar, SafeFuture<Void>> blobSidecarGossipPublisher,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxTrackers) {

    final RPCFetchDelayProvider rpcFetchDelayProvider =
        RPCFetchDelayProvider.create(
            spec,
            timeProvider,
            recentChainData,
            CurrentSlotProvider.create(spec, recentChainData.getStore()),
            DEFAULT_MAX_WAIT_RELATIVE_TO_ATT_DUE_MILLIS,
            DEFAULT_MIN_WAIT_MILLIS,
            DEFAULT_TARGET_WAIT_MILLIS);

    return new BlockBlobSidecarsTrackersPoolImpl(
        blockImportChannel,
        blockBlobSidecarsTrackersPoolSizeGauge,
        blockBlobSidecarsTrackersPoolStats,
        spec,
        asyncRunner,
        recentChainData,
        executionLayer,
        gossipValidatorSupplier,
        blobSidecarGossipPublisher,
        rpcFetchDelayProvider,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxTrackers);
  }

  @VisibleForTesting
  BlockBlobSidecarsTrackersPoolImpl createPoolForBlockBlobSidecarsTrackers(
      final BlockImportChannel blockImportChannel,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final RecentChainData recentChainData,
      final ExecutionLayerChannel executionLayer,
      final Supplier<BlobSidecarGossipValidator> gossipValidatorSupplier,
      final Function<BlobSidecar, SafeFuture<Void>> blobSidecarGossipPublisher,
      final RPCFetchDelayProvider rpcFetchDelayProvider,
      final UInt64 historicalBlockTolerance,
      final UInt64 futureBlockTolerance,
      final int maxItems,
      final BlockBlobSidecarsTrackerFactory trackerFactory) {
    return new BlockBlobSidecarsTrackersPoolImpl(
        blockImportChannel,
        blockBlobSidecarsTrackersPoolSizeGauge,
        blockBlobSidecarsTrackersPoolStats,
        spec,
        asyncRunner,
        recentChainData,
        executionLayer,
        gossipValidatorSupplier,
        blobSidecarGossipPublisher,
        rpcFetchDelayProvider,
        historicalBlockTolerance,
        futureBlockTolerance,
        maxItems,
        trackerFactory);
  }
}
