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

package tech.pegasys.teku.storage.store;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.dataproviders.lookup.BlindedExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.ExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.storage.api.OnDiskStoreData;
import tech.pegasys.teku.storage.api.StoredBlockMetadata;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;

public class StoreBuilder {
  private AsyncRunner asyncRunner;
  private MetricsSystem metricsSystem;
  private Spec spec;
  private BlockProvider blockProvider;
  private ExecutionPayloadProvider executionPayloadProvider = ExecutionPayloadProvider.NOOP;
  private BlindedExecutionPayloadProvider blindedExecutionPayloadProvider =
      BlindedExecutionPayloadProvider.NOOP;
  private StateAndBlockSummaryProvider stateAndBlockProvider;
  private EarliestBlobSidecarSlotProvider earliestBlobSidecarSlotProvider;
  private Optional<Bytes32> latestCanonicalBlockRoot;
  private StoreConfig storeConfig = StoreConfig.createDefault();

  private final Map<Bytes32, StoredBlockMetadata> blockInfoByRoot = new HashMap<>();
  private Optional<Checkpoint> anchor = Optional.empty();
  private UInt64 time;
  private UInt64 genesisTime;
  private AnchorPoint latestFinalized;
  private Checkpoint justifiedCheckpoint;
  private Checkpoint bestJustifiedCheckpoint;
  private Map<UInt64, VoteTracker> votes;
  private Set<Bytes32> unsatisfiedInclusionListBlocks = Set.of();
  private Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload =
      Optional.empty();
  private ForkChoiceStrategy forkChoiceStrategy = null;
  private Optional<UInt64> custodyGroupCount = Optional.empty();

  private StoreBuilder() {}

  public static StoreBuilder create() {
    return new StoreBuilder();
  }

  public static OnDiskStoreData forkChoiceStoreBuilder(
      final Spec spec, final AnchorPoint anchor, final UInt64 currentTime) {
    final UInt64 genesisTime = anchor.getState().getGenesisTime();
    final UInt64 blockSlot = anchor.getBlockSlot();
    final UInt64 blockTime = spec.computeTimeAtSlot(blockSlot, genesisTime).max(currentTime);

    Map<Bytes32, StoredBlockMetadata> blockInfo = new HashMap<>();
    blockInfo.put(
        anchor.getRoot(),
        new StoredBlockMetadata(
            blockSlot,
            anchor.getRoot(),
            anchor.getParentRoot(),
            anchor.getState().hashTreeRoot(),
            anchor.getExecutionBlockNumber(),
            anchor.getExecutionBlockHash(),
            anchor.getExecutionGasLimit(),
            Optional.of(spec.calculateBlockCheckpoints(anchor.getState())),
            StoredBlockMetadata.extractGloasForkChoiceRebuildData(anchor)));

    return new OnDiskStoreData(
        blockTime,
        Optional.of(anchor.getCheckpoint()),
        genesisTime,
        anchor,
        Optional.empty(),
        anchor.getCheckpoint(),
        anchor.getCheckpoint(),
        blockInfo,
        new HashMap<>(),
        Set.of(),
        Optional.empty(),
        Optional.empty());
  }

  public StoreBuilder onDiskStoreData(final OnDiskStoreData data) {
    return time(data.time())
        .anchor(data.anchor())
        .genesisTime(data.genesisTime())
        .custodyGroupCount(data.custodyGroupCount())
        .latestFinalized(data.latestFinalized())
        .finalizedOptimisticTransitionPayload(data.finalizedOptimisticTransitionPayload())
        .justifiedCheckpoint(data.justifiedCheckpoint())
        .bestJustifiedCheckpoint(data.bestJustifiedCheckpoint())
        .blockInformation(data.blockInformation())
        .votes(data.votes())
        .unsatisfiedInclusionListBlocks(data.unsatisfiedInclusionListBlocks())
        .latestCanonicalBlockRoot(data.latestCanonicalBlockRoot());
  }

  public UpdatableStore build() {
    assertValid();

    if (forkChoiceStrategy != null) {
      return Store.create(
          asyncRunner,
          metricsSystem,
          spec,
          blockProvider,
          executionPayloadProvider,
          blindedExecutionPayloadProvider,
          stateAndBlockProvider,
          earliestBlobSidecarSlotProvider,
          anchor,
          time,
          genesisTime,
          latestFinalized,
          finalizedOptimisticTransitionPayload,
          justifiedCheckpoint,
          bestJustifiedCheckpoint,
          votes,
          storeConfig,
          forkChoiceStrategy,
          custodyGroupCount);
    }
    return Store.create(
        asyncRunner,
        metricsSystem,
        spec,
        blockProvider,
        executionPayloadProvider,
        blindedExecutionPayloadProvider,
        stateAndBlockProvider,
        earliestBlobSidecarSlotProvider,
        anchor,
        time,
        genesisTime,
        latestFinalized,
        finalizedOptimisticTransitionPayload,
        justifiedCheckpoint,
        bestJustifiedCheckpoint,
        blockInfoByRoot,
        latestCanonicalBlockRoot,
        votes,
        unsatisfiedInclusionListBlocks,
        storeConfig,
        custodyGroupCount);
  }

  private void assertValid() {
    checkState(asyncRunner != null, "Async runner must be defined");
    checkState(metricsSystem != null, "Metrics system must be defined");
    checkState(spec != null, "SpecProvider must be defined");
    checkState(blockProvider != null, "Block provider must be defined");
    checkState(stateAndBlockProvider != null, "StateAndBlockProvider must be defined");
    checkState(time != null, "Time must be defined");
    checkState(genesisTime != null, "Genesis time must be defined");
    checkState(justifiedCheckpoint != null, "Justified checkpoint must be defined");
    checkState(bestJustifiedCheckpoint != null, "Best justified checkpoint must be defined");
    checkState(latestFinalized != null, "Latest finalized anchor must be defined");
    checkState(votes != null, "Votes must be defined");
    checkState(!blockInfoByRoot.isEmpty(), "Block data must be supplied");
  }

  public StoreBuilder asyncRunner(final AsyncRunner asyncRunner) {
    this.asyncRunner = asyncRunner;
    return this;
  }

  public StoreBuilder metricsSystem(final MetricsSystem metricsSystem) {
    checkNotNull(metricsSystem);
    this.metricsSystem = metricsSystem;
    return this;
  }

  public StoreBuilder specProvider(final Spec spec) {
    checkNotNull(spec);
    this.spec = spec;
    return this;
  }

  public StoreBuilder storeConfig(final StoreConfig storeConfig) {
    checkNotNull(storeConfig);
    this.storeConfig = storeConfig;
    return this;
  }

  public StoreBuilder blockProvider(final BlockProvider blockProvider) {
    checkNotNull(blockProvider);
    this.blockProvider = blockProvider;
    return this;
  }

  public StoreBuilder executionPayloadProvider(
      final ExecutionPayloadProvider executionPayloadProvider) {
    checkNotNull(executionPayloadProvider);
    this.executionPayloadProvider = executionPayloadProvider;
    return this;
  }

  public StoreBuilder blindedExecutionPayloadProvider(
      final BlindedExecutionPayloadProvider blindedExecutionPayloadProvider) {
    checkNotNull(blindedExecutionPayloadProvider);
    this.blindedExecutionPayloadProvider = blindedExecutionPayloadProvider;
    return this;
  }

  public StoreBuilder stateProvider(final StateAndBlockSummaryProvider stateProvider) {
    checkNotNull(stateProvider);
    this.stateAndBlockProvider = stateProvider;
    return this;
  }

  public StoreBuilder earliestBlobSidecarSlotProvider(
      final EarliestBlobSidecarSlotProvider earliestBlobSidecarSlotProvider) {
    checkNotNull(earliestBlobSidecarSlotProvider);
    this.earliestBlobSidecarSlotProvider = earliestBlobSidecarSlotProvider;
    return this;
  }

  public StoreBuilder latestCanonicalBlockRoot(final Optional<Bytes32> latestCanonicalBlockRoot) {
    checkNotNull(latestCanonicalBlockRoot);
    this.latestCanonicalBlockRoot = latestCanonicalBlockRoot;
    return this;
  }

  public StoreBuilder anchor(final Checkpoint anchorPoint) {
    checkNotNull(anchorPoint);
    return anchor(Optional.of(anchorPoint));
  }

  public StoreBuilder anchor(final Optional<Checkpoint> anchorPoint) {
    checkNotNull(anchorPoint);
    this.anchor = anchorPoint;
    return this;
  }

  public StoreBuilder time(final UInt64 time) {
    checkNotNull(time);
    this.time = time;
    return this;
  }

  public StoreBuilder genesisTime(final UInt64 genesisTime) {
    checkNotNull(genesisTime);
    this.genesisTime = genesisTime;
    return this;
  }

  public StoreBuilder custodyGroupCount(final Optional<UInt64> custodyGroupCount) {
    checkNotNull(custodyGroupCount);
    this.custodyGroupCount = custodyGroupCount;
    return this;
  }

  public StoreBuilder justifiedCheckpoint(final Checkpoint justifiedCheckpoint) {
    checkNotNull(justifiedCheckpoint);
    this.justifiedCheckpoint = justifiedCheckpoint;
    return this;
  }

  public StoreBuilder latestFinalized(final AnchorPoint latestFinalized) {
    checkNotNull(latestFinalized);
    this.latestFinalized = latestFinalized;
    return this;
  }

  public StoreBuilder finalizedOptimisticTransitionPayload(
      final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload) {
    checkNotNull(finalizedOptimisticTransitionPayload);
    this.finalizedOptimisticTransitionPayload = finalizedOptimisticTransitionPayload;
    return this;
  }

  public StoreBuilder bestJustifiedCheckpoint(final Checkpoint bestJustifiedCheckpoint) {
    checkNotNull(bestJustifiedCheckpoint);
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    return this;
  }

  public StoreBuilder blockInformation(final Map<Bytes32, StoredBlockMetadata> blockInformation) {
    this.blockInfoByRoot.putAll(blockInformation);
    return this;
  }

  @VisibleForTesting
  StoreBuilder forkChoiceStrategy(final ForkChoiceStrategy forkChoiceStrategy) {
    this.forkChoiceStrategy = forkChoiceStrategy;
    return this;
  }

  public StoreBuilder votes(final Map<UInt64, VoteTracker> votes) {
    checkNotNull(votes);
    this.votes = votes;
    return this;
  }

  public StoreBuilder unsatisfiedInclusionListBlocks(
      final Set<Bytes32> unsatisfiedInclusionListBlocks) {
    checkNotNull(unsatisfiedInclusionListBlocks);
    this.unsatisfiedInclusionListBlocks = unsatisfiedInclusionListBlocks;
    return this;
  }
}
