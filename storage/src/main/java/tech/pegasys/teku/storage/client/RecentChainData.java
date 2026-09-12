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

package tech.pegasys.teku.storage.client;

import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import tech.pegasys.teku.dataproviders.lookup.BlindedExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.BlockProvider;
import tech.pegasys.teku.dataproviders.lookup.EarliestBlobSidecarSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.ExecutionPayloadProvider;
import tech.pegasys.teku.dataproviders.lookup.RecentlyValidatedDataColumnSlotProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlobSidecarProvider;
import tech.pegasys.teku.dataproviders.lookup.SingleBlockProvider;
import tech.pegasys.teku.dataproviders.lookup.StateAndBlockSummaryProvider;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.MinimalBeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceReorgContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteUpdater;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil.BlockTimeliness;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.FinalizedCheckpointChannel;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.store.EmptyStoreResults;
import tech.pegasys.teku.storage.store.StoreBuilder;
import tech.pegasys.teku.storage.store.StoreConfig;
import tech.pegasys.teku.storage.store.UpdatableStore;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreUpdateHandler;

/** This class is the ChainStorage client-side logic */
public abstract class RecentChainData
    implements StoreUpdateHandler, ValidatorIsConnectedProvider, ForkChoiceReorgContext {

  private static final Logger LOG = LogManager.getLogger();

  private final BlockProvider blockProvider;
  private final ExecutionPayloadProvider executionPayloadProvider;
  private final BlindedExecutionPayloadProvider blindedExecutionPayloadProvider;
  private final StateAndBlockSummaryProvider stateProvider;
  private final EarliestBlobSidecarSlotProvider earliestBlobSidecarSlotProvider;
  protected final FinalizedCheckpointChannel finalizedCheckpointChannel;
  protected final StorageUpdateChannel storageUpdateChannel;
  protected final VoteUpdateChannel voteUpdateChannel;
  protected final AsyncRunner asyncRunner;
  protected final MetricsSystem metricsSystem;
  private final ChainHeadChannel chainHeadChannel;
  private final StoreConfig storeConfig;
  protected final Spec spec;

  private final AtomicBoolean storeInitialized = new AtomicBoolean(false);
  private final SafeFuture<Void> storeInitializedFuture = new SafeFuture<>();
  private final SafeFuture<Void> bestBlockInitialized = new SafeFuture<>();
  private final Counter reorgCounter;

  private volatile UpdatableStore store;
  private volatile Optional<GenesisData> genesisData = Optional.empty();
  private final Map<Bytes4, SpecMilestone> forkDigestToMilestone = new ConcurrentHashMap<>();
  private final Map<SpecMilestone, Bytes4> milestoneToForkDigest = new ConcurrentHashMap<>();
  private final Map<BlobParameters, Bytes4> bpoForkToForkDigest = new ConcurrentHashMap<>();
  private final Map<Bytes4, BlobParameters> forkDigestToBpoFork = new ConcurrentHashMap<>();
  private volatile Optional<ChainHead> chainHead = Optional.empty();
  private volatile UInt64 genesisTime;

  private final SingleBlockProvider validatedBlockProvider;
  private final SingleBlobSidecarProvider validatedBlobSidecarProvider;
  private final RecentlyValidatedDataColumnSlotProvider validatedSlotProvider;
  private final BlockTimelinessTracker blockTimelinessTracker;

  private final ValidatorIsConnectedProvider validatorIsConnectedProvider;

  RecentChainData(
      final AsyncRunner asyncRunner,
      final MetricsSystem metricsSystem,
      final StoreConfig storeConfig,
      final BlockProvider blockProvider,
      final ExecutionPayloadProvider executionPayloadProvider,
      final BlindedExecutionPayloadProvider blindedExecutionPayloadProvider,
      final SingleBlockProvider validatedBlockProvider,
      final SingleBlobSidecarProvider validatedBlobSidecarProvider,
      final RecentlyValidatedDataColumnSlotProvider validatedSlotProvider,
      final StateAndBlockSummaryProvider stateProvider,
      final EarliestBlobSidecarSlotProvider earliestBlobSidecarSlotProvider,
      final StorageUpdateChannel storageUpdateChannel,
      final VoteUpdateChannel voteUpdateChannel,
      final FinalizedCheckpointChannel finalizedCheckpointChannel,
      final ChainHeadChannel chainHeadChannel,
      final ValidatorIsConnectedProvider validatorIsConnectedProvider,
      final Spec spec) {
    this.asyncRunner = asyncRunner;
    this.metricsSystem = metricsSystem;
    this.storeConfig = storeConfig;
    this.blockProvider = blockProvider;
    this.executionPayloadProvider = executionPayloadProvider;
    this.blindedExecutionPayloadProvider = blindedExecutionPayloadProvider;
    this.stateProvider = stateProvider;
    this.validatedBlockProvider = validatedBlockProvider;
    this.validatedBlobSidecarProvider = validatedBlobSidecarProvider;
    this.validatedSlotProvider = validatedSlotProvider;
    this.earliestBlobSidecarSlotProvider = earliestBlobSidecarSlotProvider;
    this.voteUpdateChannel = voteUpdateChannel;
    this.chainHeadChannel = chainHeadChannel;
    this.storageUpdateChannel = storageUpdateChannel;
    this.finalizedCheckpointChannel = finalizedCheckpointChannel;
    reorgCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.BEACON,
            "reorgs_total",
            "Total occurrences of reorganizations of the chain");
    this.validatorIsConnectedProvider = validatorIsConnectedProvider;
    this.spec = spec;
    final int epochsForTimeliness =
        Math.max(spec.getGenesisSpecConfig().getReorgMaxEpochsSinceFinalization(), 3);
    this.blockTimelinessTracker =
        new BlockTimelinessTracker(
            spec,
            this::getGenesisTimeMillis,
            LimitedMap.createSynchronizedNatural(
                spec.getGenesisSpec().getSlotsPerEpoch() * epochsForTimeliness));
  }

  public void subscribeStoreInitialized(final Runnable runnable) {
    storeInitializedFuture.always(runnable);
  }

  public void subscribeBestBlockInitialized(final Runnable runnable) {
    bestBlockInitialized.always(runnable);
  }

  public void initializeFromGenesis(final BeaconState genesisState, final UInt64 currentTime) {
    final AnchorPoint genesis = AnchorPoint.fromGenesisState(spec, genesisState);
    initializeFromAnchorPoint(genesis, currentTime);
  }

  public void initializeFromAnchorPoint(final AnchorPoint anchorPoint, final UInt64 currentTime) {
    final UpdatableStore store =
        StoreBuilder.create()
            .onDiskStoreData(StoreBuilder.forkChoiceStoreBuilder(spec, anchorPoint, currentTime))
            .asyncRunner(asyncRunner)
            .metricsSystem(metricsSystem)
            .specProvider(spec)
            .blockProvider(blockProvider)
            .executionPayloadProvider(executionPayloadProvider)
            .blindedExecutionPayloadProvider(blindedExecutionPayloadProvider)
            .stateProvider(stateProvider)
            .earliestBlobSidecarSlotProvider(earliestBlobSidecarSlotProvider)
            .storeConfig(storeConfig)
            .build();

    final boolean result = setStore(store);
    if (!result) {
      throw new IllegalStateException(
          "Failed to initialize from state: store has already been initialized");
    }

    // Set the head to the anchor point
    updateHead(anchorPoint.getRoot(), anchorPoint.getEpochStartSlot());
    storageUpdateChannel.onChainInitialized(anchorPoint);
  }

  public UInt64 getGenesisTime() {
    return genesisTime;
  }

  public UInt64 getGenesisTimeMillis() {
    return secondsToMillis(genesisTime);
  }

  public UInt64 computeTimeAtSlot(final UInt64 slot) {
    return spec.computeTimeAtSlot(slot, genesisTime);
  }

  /**
   * True when our head is close enough to {@code slot} that we can still be considered in sync with
   * it. The tolerance is {@code MAX_SEED_LOOKAHEAD} epochs, the point beyond which we can no longer
   * cheaply compute the state at {@code slot} from our head.
   */
  public boolean isHeadCloseToSlot(final UInt64 slot) {
    final SpecVersion specVersion = spec.atSlot(slot);
    final int maxLookaheadSlots =
        specVersion.getSlotsPerEpoch() * specVersion.getConfig().getMaxSeedLookahead();
    return slot.minusMinZero(getHeadSlot()).isLessThanOrEqualTo(maxLookaheadSlots);
  }

  @VisibleForTesting
  boolean isCloseToInSync(final UInt64 currentTimeSeconds) {
    final MiscHelpers miscHelpers = spec.getGenesisSpec().miscHelpers();
    return isHeadCloseToSlot(miscHelpers.computeSlotAtTime(getGenesisTime(), currentTimeSeconds));
  }

  public boolean isCloseToInSync() {
    if (store == null) {
      return false;
    }
    return isCloseToInSync(store.getTimeInSeconds());
  }

  public Optional<GenesisData> getGenesisData() {
    return genesisData;
  }

  public Optional<SpecMilestone> getMilestoneByForkDigest(final Bytes4 forkDigest) {
    return Optional.ofNullable(forkDigestToMilestone.get(forkDigest));
  }

  public Optional<Bytes4> getForkDigestByMilestone(final SpecMilestone milestone) {
    return Optional.ofNullable(milestoneToForkDigest.get(milestone));
  }

  public Optional<Bytes4> getForkDigestByBpoFork(final BlobParameters blobParameters) {
    return Optional.ofNullable(bpoForkToForkDigest.get(blobParameters));
  }

  public Optional<BlobParameters> getBpoForkByForkDigest(final Bytes4 forkDigest) {
    return Optional.ofNullable(forkDigestToBpoFork.get(forkDigest));
  }

  public boolean isPreGenesis() {
    return this.store == null;
  }

  /**
   * @return true if the chain head has been set, false otherwise.
   */
  public boolean isPreForkChoice() {
    return chainHead.isEmpty();
  }

  boolean setStore(final UpdatableStore store) {
    if (!storeInitialized.compareAndSet(false, true)) {
      return false;
    }
    this.store = store;
    this.store.startMetrics();

    // Set data that depends on the genesis state
    this.genesisTime = this.store.getGenesisTime();
    final BeaconState anchorState = store.getLatestFinalized().getState();
    final Bytes32 genesisValidatorsRoot = anchorState.getGenesisValidatorsRoot();
    this.genesisData =
        Optional.of(new GenesisData(anchorState.getGenesisTime(), genesisValidatorsRoot));
    spec.getForkSchedule()
        .getActiveMilestones()
        .forEach(
            forkAndMilestone -> {
              final Bytes4 forkDigest =
                  spec.computeForkDigest(
                      genesisValidatorsRoot, forkAndMilestone.getFork().getEpoch());
              final SpecMilestone milestone = forkAndMilestone.getSpecMilestone();
              this.forkDigestToMilestone.put(forkDigest, milestone);
              this.milestoneToForkDigest.put(milestone, forkDigest);
            });
    // BPO
    spec.getBpoForks()
        .forEach(
            blobParameters -> {
              final Bytes4 forkDigest =
                  spec.computeForkDigest(genesisValidatorsRoot, blobParameters.epoch());
              final SpecMilestone milestone =
                  spec.getForkSchedule().getSpecMilestoneAtEpoch(blobParameters.epoch());
              this.forkDigestToMilestone.put(forkDigest, milestone);
              this.bpoForkToForkDigest.put(blobParameters, forkDigest);
              this.forkDigestToBpoFork.put(forkDigest, blobParameters);
            });

    storeInitializedFuture.complete(null);
    return true;
  }

  @Override
  public UpdatableStore getStore() {
    return store;
  }

  public NavigableMap<UInt64, Bytes32> getAncestorRootsOnHeadChain(
      final UInt64 startSlot, final UInt64 step, final UInt64 count) {
    return chainHead
        .map(
            head ->
                spec.getAncestors(
                    store.getForkChoiceStrategy(), head.getRoot(), startSlot, step, count))
        .orElseGet(TreeMap::new);
  }

  public NavigableMap<UInt64, Bytes32> getAncestorsOnFork(
      final UInt64 startSlot, final Bytes32 root) {
    return spec.getAncestorsOnFork(store.getForkChoiceStrategy(), root, startSlot);
  }

  public Optional<ForkChoiceStrategy> getUpdatableForkChoiceStrategy() {
    return Optional.ofNullable(store).map(UpdatableStore::getForkChoiceStrategy);
  }

  public Optional<ReadOnlyForkChoiceStrategy> getForkChoiceStrategy() {
    return Optional.ofNullable(store).map(ReadOnlyStore::getForkChoiceStrategy);
  }

  public StoreTransaction startStoreTransaction() {
    return store.startTransaction(storageUpdateChannel, this);
  }

  public VoteUpdater startVoteUpdate() {
    return store.startVoteUpdate(voteUpdateChannel);
  }

  // NETWORKING RELATED INFORMATION METHODS:

  /**
   * Set the block that is the current chain head according to fork-choice processing using the full
   * node identity. In Gloas, the same block root may have PENDING, EMPTY, and FULL nodes.
   *
   * @param headNode The fork choice node identifying the new head
   * @param currentSlot The current slot - the slot at which the new head was selected
   */
  public void updateHead(final ForkChoiceNode headNode, final UInt64 currentSlot) {
    updateHeadInternal(headNode.blockRoot(), headNode.payloadStatus(), currentSlot);
  }

  /**
   * Set the block that is the current chain head according to fork-choice processing.
   *
   * @param root The new head block root
   * @param currentSlot The current slot - the slot at which the new head was selected
   */
  public void updateHead(final Bytes32 root, final UInt64 currentSlot) {
    updateHeadInternal(root, Optional.empty(), currentSlot);
  }

  private void updateHeadInternal(
      final Bytes32 root, final ForkChoicePayloadStatus payloadStatus, final UInt64 currentSlot) {
    updateHeadInternal(root, Optional.of(payloadStatus), currentSlot);
  }

  private void updateHeadInternal(
      final Bytes32 root,
      final Optional<ForkChoicePayloadStatus> maybePayloadStatus,
      final UInt64 currentSlot) {
    synchronized (this) {
      final Optional<ChainHead> originalChainHead = chainHead;

      final ReadOnlyForkChoiceStrategy forkChoiceStrategy = store.getForkChoiceStrategy();
      final Optional<ProtoNodeData> maybeBlockData =
          maybePayloadStatus
              .map(status -> forkChoiceStrategy.getBlockData(root, status))
              .orElseGet(() -> forkChoiceStrategy.getBlockData(root));
      if (maybeBlockData.isEmpty()) {
        LOG.error(
            "Unable to update head block as of slot {}. Unknown block: {}", currentSlot, root);
        return;
      }
      final ChainHead newChainHead = createNewChainHead(root, currentSlot, maybeBlockData.get());
      if (newChainHead.isSameHeadAs(originalChainHead)) {
        LOG.trace("Skipping head update because new head is same as previous head");
        return;
      }

      this.chainHead = Optional.of(newChainHead);
      final Optional<ReorgContext> optionalReorgContext =
          computeReorgContext(forkChoiceStrategy, originalChainHead, newChainHead);
      final boolean epochTransition =
          originalChainHead
              .map(
                  previousChainHead ->
                      spec.computeEpochAtSlot(previousChainHead.getSlot())
                          .isLessThan(spec.computeEpochAtSlot(newChainHead.getSlot())))
              .orElse(false);
      final BeaconStateUtil beaconStateUtil = spec.atSlot(currentSlot).getBeaconStateUtil();

      chainHeadChannel.chainHeadUpdated(
          newChainHead.getSlot(),
          newChainHead.getStateRoot(),
          newChainHead.getRoot(),
          epochTransition,
          newChainHead.isOptimistic(),

          // Chain head must be or descend from the justified checkpoint so we know if the previous
          // duty dependent root isn't available from protoarray it must be the parent of the
          // finalized block and the current duty dependent root must be available.
          // See comments on beaconStateUtil.getPreviousDutyDependentRoot for reasoning.
          // The exception is when we have just started from an initial state and block history
          // isn't available, in which case the dependentRoot can safely be set to the finalized
          // block and will remain consistent.
          beaconStateUtil
              .getPreviousDutyDependentRoot(forkChoiceStrategy, newChainHead)
              .orElseGet(this::getFinalizedBlockParentRoot),
          beaconStateUtil
              .getCurrentDutyDependentRoot(forkChoiceStrategy, newChainHead)
              .orElseGet(this::getFinalizedBlockParentRoot),
          maybePayloadStatus,
          optionalReorgContext);
    }
    bestBlockInitialized.complete(null);
  }

  private Optional<ReorgContext> computeReorgContext(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Optional<ChainHead> originalChainHead,
      final ChainHead newChainHead) {
    final Optional<ReorgContext> optionalReorgContext;
    if (originalChainHead
        .map(head -> hasReorgedFrom(head.getRoot(), head.getSlot()))
        .orElse(false)) {
      final ChainHead previousChainHead = originalChainHead.get();

      final SlotAndBlockRoot commonAncestorSlotAndBlockRoot =
          forkChoiceStrategy
              .findCommonAncestor(previousChainHead.getRoot(), newChainHead.getRoot())
              .orElseGet(() -> store.getFinalizedCheckpoint().toSlotAndBlockRoot(spec));

      reorgCounter.inc();
      optionalReorgContext =
          ReorgContext.of(
              previousChainHead.getRoot(),
              previousChainHead.getSlot(),
              previousChainHead.getStateRoot(),
              commonAncestorSlotAndBlockRoot.getSlot(),
              commonAncestorSlotAndBlockRoot.getBlockRoot());
    } else {
      optionalReorgContext = ReorgContext.empty();
    }
    return optionalReorgContext;
  }

  private ChainHead createNewChainHead(
      final Bytes32 root, final UInt64 currentSlot, final ProtoNodeData headBlockData) {
    final SafeFuture<StateAndBlockSummary> headBlockAndState =
        store
            .retrieveStateAndBlockSummary(root)
            .thenApply(
                maybeHead ->
                    maybeHead.orElseThrow(
                        () ->
                            new IllegalStateException(
                                String.format(
                                    "Unable to update head block as of slot %s.  Block is unavailable: %s.",
                                    currentSlot, root))));
    return ChainHead.create(headBlockData, headBlockAndState);
  }

  private Bytes32 getFinalizedBlockParentRoot() {
    return getForkChoiceStrategy()
        .orElseThrow()
        .blockParentRoot(store.getFinalizedCheckpoint().getRoot())
        .orElseThrow(() -> new IllegalStateException("Finalized block is unknown"));
  }

  private boolean hasReorgedFrom(
      final Bytes32 originalHeadRoot, final UInt64 originalChainTipSlot) {
    // Get the block root in effect at the old chain tip on the current chain. Depending on
    // updateHeadForEmptySlots that chain "tip" may be the fork choice slot (when true) or the
    // latest block (when false). If this is a different fork to the previous chain the root at
    // originalChainTipSlot will be different from originalHeadRoot. If it's an extension of the
    // same chain it will match.
    return getBlockRootInEffectBySlot(originalChainTipSlot)
        .map(rootAtOldBestSlot -> !rootAtOldBestSlot.equals(originalHeadRoot))
        .orElse(true);
  }

  /**
   * Return the current slot based on our Store's time.
   *
   * @return The current slot.
   */
  public Optional<UInt64> getCurrentSlot() {
    if (isPreGenesis()) {
      return Optional.empty();
    }
    return Optional.of(spec.getCurrentSlot(store));
  }

  public Optional<UInt64> getCurrentEpoch() {
    return getCurrentSlot().map(spec::computeEpochAtSlot);
  }

  /**
   * @return The current spec version according to the recorded time
   */
  public SpecVersion getCurrentSpec() {
    return getCurrentSlot().map(spec::atSlot).orElseGet(spec::getGenesisSpec);
  }

  public Spec getSpec() {
    return spec;
  }

  public Optional<Fork> getNextFork(final Fork fork) {
    return spec.getForkSchedule().getNextFork(fork.getEpoch());
  }

  public Bytes4 getForkDigest(
      final SpecMilestone milestone, final Optional<BlobParameters> maybeBpoFork) {
    return maybeBpoFork
        // either fork digest for the BPO fork or for the hard fork
        .flatMap(
            bpoFork -> {
              final Fork fork = spec.getForkSchedule().getFork(milestone);
              if (bpoFork.epoch().isGreaterThan(fork.getEpoch())) {
                return getForkDigestByBpoFork(bpoFork);
              }
              return getForkDigestByMilestone(milestone);
            })
        // default to fork digest for the hard fork
        .or(() -> getForkDigestByMilestone(milestone))
        .orElseThrow();
  }

  public Bytes4 getForkDigest(final UInt64 epoch) {
    final SpecMilestone milestone = spec.getForkSchedule().getSpecMilestoneAtEpoch(epoch);
    final Optional<BlobParameters> maybeBpoFork = spec.getBpoFork(epoch);
    return getForkDigest(milestone, maybeBpoFork);
  }

  public Optional<Bytes4> getNextForkDigest(final UInt64 epoch) {
    final Optional<Fork> maybeNextFork = spec.getForkSchedule().getNextFork(epoch);
    return spec.getNextBpoFork(epoch)
        // either fork digest for the next BPO fork or for the next hard fork
        .flatMap(
            nextBpoFork ->
                maybeNextFork
                    .flatMap(
                        nextFork -> {
                          if (nextBpoFork.epoch().isLessThan(nextFork.getEpoch())) {
                            return getForkDigestByBpoFork(nextBpoFork);
                          }
                          final SpecMilestone milestone =
                              spec.getForkSchedule().getSpecMilestoneAtEpoch(nextFork.getEpoch());
                          return getForkDigestByMilestone(milestone);
                        })
                    .or(() -> getForkDigestByBpoFork(nextBpoFork)))
        // default to fork digest for the next hard fork
        .or(
            () ->
                maybeNextFork.flatMap(
                    nextFork -> {
                      final SpecMilestone milestone =
                          spec.getForkSchedule().getSpecMilestoneAtEpoch(nextFork.getEpoch());
                      return getForkDigestByMilestone(milestone);
                    }));
  }

  private Optional<Fork> getCurrentFork() {
    return getCurrentEpoch().map(spec.getForkSchedule()::getFork);
  }

  private Fork getFork(final UInt64 epoch) {
    return spec.getForkSchedule().getFork(epoch);
  }

  /**
   * Returns the fork info that applies based on the current slot as calculated from the current
   * time, regardless of where the sync progress is up to.
   *
   * @return fork info based on the current time, not head block
   */
  public Optional<ForkInfo> getCurrentForkInfo() {
    return genesisData
        .map(GenesisData::getGenesisValidatorsRoot)
        .flatMap(
            validatorsRoot -> getCurrentFork().map(fork -> new ForkInfo(fork, validatorsRoot)));
  }

  public Optional<ForkInfo> getForkInfo(final UInt64 epoch) {
    return genesisData
        .map(GenesisData::getGenesisValidatorsRoot)
        .map(validatorsRoot -> new ForkInfo(getFork(epoch), validatorsRoot));
  }

  /**
   * @return fork digest based on the current time, not head block
   */
  public Optional<Bytes4> getCurrentForkDigest() {
    return getCurrentEpoch().map(this::getForkDigest);
  }

  /** Retrieves the block chosen by fork choice to build and attest on */
  public Optional<Bytes32> getBestBlockRoot() {
    return chainHead.map(MinimalBeaconBlockSummary::getRoot);
  }

  /**
   * @return The head of the chain.
   */
  public Optional<ChainHead> getChainHead() {
    return chainHead;
  }

  public boolean isChainHeadOptimistic() {
    return chainHead.map(ChainHead::isOptimistic).orElse(false);
  }

  /**
   * @return The block at the head of the chain.
   */
  public Optional<MinimalBeaconBlockSummary> getHeadBlock() {
    return chainHead.map(a -> a);
  }

  /** Retrieves the state of the block chosen by fork choice to build and attest on */
  public Optional<SafeFuture<BeaconState>> getBestState() {
    return chainHead.map(ChainHead::getState);
  }

  /** Retrieves the slot of the block chosen by fork choice to build and attest on */
  public UInt64 getHeadSlot() {
    return chainHead.map(MinimalBeaconBlockSummary::getSlot).orElse(UInt64.ZERO);
  }

  public boolean containsBlock(final Bytes32 root) {
    return Optional.ofNullable(store).map(s -> s.containsBlock(root)).orElse(false);
  }

  public boolean containsExecutionPayload(final Bytes32 blockRoot) {
    return Optional.ofNullable(store)
        .map(s -> s.getExecutionPayloadIfAvailable(blockRoot).isPresent())
        .orElse(false);
  }

  public Optional<UInt64> getSlotForBlockRoot(final Bytes32 root) {
    return getForkChoiceStrategy().flatMap(forkChoice -> forkChoice.blockSlot(root));
  }

  public Optional<UInt64> getRecentlyValidatedSlotByBlockRoot(final Bytes32 root) {
    return validatedSlotProvider.getSlot(root);
  }

  public Optional<Bytes32> getExecutionBlockHashForBlockRoot(final Bytes32 root) {
    return getForkChoiceStrategy().flatMap(forkChoice -> forkChoice.executionBlockHash(root));
  }

  public Optional<UInt64> getExecutionGasLimitForBlockRootAndHash(
      final Bytes32 blockRoot, final Bytes32 blockHash) {
    return getForkChoiceStrategy()
        .flatMap(
            forkChoice -> forkChoice.getExecutionGasLimitForBlockRootAndHash(blockRoot, blockHash));
  }

  public Optional<Bytes32> getExecutionBlockHashForBlock(final ForkChoiceNode block) {
    return getForkChoiceStrategy()
        .flatMap(forkChoice -> forkChoice.getNodeData(block))
        .map(ProtoNodeData::getExecutionBlockHash);
  }

  public Optional<List<BlobSidecar>> getBlobSidecars(final SlotAndBlockRoot slotAndBlockRoot) {
    return Optional.ofNullable(store)
        .flatMap(s -> store.getBlobSidecarsIfAvailable(slotAndBlockRoot));
  }

  public Optional<BlobSidecar> getRecentlyValidatedBlobSidecar(
      final Bytes32 blockRoot, final UInt64 index) {
    return validatedBlobSidecarProvider.getBlobSidecar(blockRoot, index);
  }

  public SafeFuture<Optional<BeaconBlock>> retrieveBlockByRoot(final Bytes32 root) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_BLOCK_FUTURE;
    }
    return store.retrieveBlock(root);
  }

  public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlockByRoot(final Bytes32 root) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_SIGNED_BLOCK_FUTURE;
    }
    return store.retrieveSignedBlock(root);
  }

  public SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(final Bytes32 blockRoot) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_SIGNED_BLOCK_AND_STATE_FUTURE;
    }
    return store.retrieveBlockAndState(blockRoot);
  }

  public Optional<SignedBeaconBlock> getRecentlyValidatedSignedBlockByRoot(final Bytes32 root) {
    return validatedBlockProvider.getBlock(root);
  }

  public SafeFuture<Optional<SignedExecutionPayloadEnvelope>>
      retrieveSignedExecutionPayloadByBlockRoot(final Bytes32 beaconBlockRoot) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_SIGNED_EXECUTION_PAYLOAD_ENVELOPE_FUTURE;
    }
    return store.retrieveSignedExecutionPayload(beaconBlockRoot);
  }

  public SafeFuture<Optional<SignedBlindedExecutionPayloadEnvelope>>
      retrieveSignedBlindedExecutionPayloadByBlockRoot(final Bytes32 beaconBlockRoot) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_SIGNED_BLINDED_EXECUTION_PAYLOAD_ENVELOPE_FUTURE;
    }
    return store.retrieveSignedBlindedExecutionPayload(beaconBlockRoot);
  }

  public SafeFuture<Optional<BeaconState>> retrieveBlockState(final Bytes32 blockRoot) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_STATE_FUTURE;
    }
    return store.retrieveBlockState(blockRoot);
  }

  public SafeFuture<Optional<BeaconState>> retrieveBlockState(
      final SlotAndBlockRoot slotAndBlockRoot) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_STATE_FUTURE;
    }
    return store.retrieveBlockState(slotAndBlockRoot);
  }

  public SafeFuture<Optional<BeaconState>> retrieveStateInEffectAtSlot(final UInt64 slot) {
    Optional<Bytes32> rootAtSlot = getBlockRootInEffectBySlot(slot);
    if (rootAtSlot.isEmpty()) {
      return EmptyStoreResults.EMPTY_STATE_FUTURE;
    }
    return store.retrieveBlockState(rootAtSlot.get());
  }

  public SafeFuture<Optional<UInt64>> retrieveEarliestBlobSidecarSlot() {
    if (store == null) {
      return EmptyStoreResults.NO_EARLIEST_BLOB_SIDECAR_SLOT_FUTURE;
    }
    return store.retrieveEarliestBlobSidecarSlot();
  }

  public Optional<Bytes32> getBlockRootInEffectBySlot(final UInt64 slot) {
    return chainHead.flatMap(head -> getBlockRootInEffectBySlot(slot, head.getRoot()));
  }

  public Optional<Bytes32> getBlockRootInEffectBySlot(
      final UInt64 slot, final Bytes32 headBlockRoot) {
    return getForkChoiceStrategy()
        .flatMap(strategy -> spec.getAncestor(strategy, headBlockRoot, slot));
  }

  public UInt64 getFinalizedEpoch() {
    return getFinalizedCheckpoint().map(Checkpoint::getEpoch).orElse(UInt64.ZERO);
  }

  public Optional<Checkpoint> getFinalizedCheckpoint() {
    return store == null ? Optional.empty() : Optional.of(store.getFinalizedCheckpoint());
  }

  public Optional<Checkpoint> getJustifiedCheckpoint() {
    return store == null ? Optional.empty() : Optional.of(store.getJustifiedCheckpoint());
  }

  public boolean isJustifiedCheckpointFullyValidated() {
    return store != null
        && store.getForkChoiceStrategy().isFullyValidated(store.getJustifiedCheckpoint().getRoot());
  }

  /**
   * Returns empty if the block is unknown, or an optional indicating whether the block is
   * optimistically imported or not.
   *
   * @param blockRoot the block to check
   * @return empty if the block is unknown, Optional(true) when the block is optimistically imported
   *     and Optional(false) when imported and fully validated.
   */
  public Optional<Boolean> isBlockOptimistic(final Bytes32 blockRoot) {
    return getForkChoiceStrategy().flatMap(forkChoice -> forkChoice.isOptimistic(blockRoot));
  }

  @Override
  public void onNewFinalizedCheckpoint(
      final Checkpoint finalizedCheckpoint, final boolean fromOptimisticBlock) {
    finalizedCheckpointChannel.onNewFinalizedCheckpoint(finalizedCheckpoint, fromOptimisticBlock);
  }

  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(final Checkpoint checkpoint) {
    if (store == null) {
      return EmptyStoreResults.EMPTY_STATE_FUTURE;
    }

    return store.retrieveCheckpointState(checkpoint);
  }

  public List<ProtoNodeData> getChainHeads() {
    return getForkChoiceStrategy()
        .map(ReadOnlyForkChoiceStrategy::getChainHeads)
        .orElse(Collections.emptyList());
  }

  public List<Bytes32> getAllBlockRootsAtSlot(final UInt64 slot) {
    return getForkChoiceStrategy()
        .map(forkChoiceStrategy -> forkChoiceStrategy.getBlockRootsAtSlot(slot))
        .orElse(Collections.emptyList());
  }

  public ForkChoiceNode getProposerHead(final ForkChoiceNode headRoot, final UInt64 slot) {
    return spec.atSlot(slot).getForkChoiceUtil().getProposerHead(this, headRoot, slot);
  }

  public boolean shouldOverrideForkChoiceUpdate(final Bytes32 headRoot, final UInt64 headSlot) {
    return spec.atSlot(headSlot)
        .getForkChoiceUtil()
        .shouldOverrideForkChoiceUpdate(this, headRoot, headSlot);
  }

  @Override
  public boolean isValidatorConnected(final int validatorIndex, final UInt64 slot) {
    return validatorIsConnectedProvider.isValidatorConnected(validatorIndex, slot);
  }

  @Override
  public SafeFuture<Boolean> isBlockProposerConnected(final UInt64 slot) {
    return validatorIsConnectedProvider.isBlockProposerConnected(slot);
  }

  public void setBlockTimelinessFromArrivalTime(
      final SignedBeaconBlock block, final UInt64 arrivalTime) {
    blockTimelinessTracker.setBlockTimelinessFromArrivalTime(block, arrivalTime);
  }

  public void setBlockTimelinessIfEmpty(final SignedBeaconBlock block) {
    blockTimelinessTracker.setBlockTimelinessFromArrivalTime(block, store.getTimeInMillis());
  }

  public void setBlockTimelinessAfterDataAvailability(
      final SignedBeaconBlock block, final UInt64 dataAvailableTimeMillis) {
    blockTimelinessTracker.setBlockTimelinessAfterDataAvailability(block, dataAvailableTimeMillis);
  }

  @Override
  public Optional<BlockTimeliness> getBlockTimeliness(final Bytes32 root) {
    return blockTimelinessTracker.getBlockTimeliness(root);
  }

  public boolean isBlockLate(final Bytes32 root) {
    return blockTimelinessTracker.isBlockLate(root);
  }

  @Override
  public BeaconState processSlots(final BeaconState state, final UInt64 slot)
      throws SlotProcessingException, EpochProcessingException {
    return spec.processSlots(state, slot);
  }

  public Optional<UInt64> getCustodyGroupCount() {
    return store.getCustodyGroupCount();
  }
}
