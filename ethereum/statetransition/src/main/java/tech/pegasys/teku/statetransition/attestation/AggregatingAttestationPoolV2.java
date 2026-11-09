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

package tech.pegasys.teku.statetransition.attestation;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.statetransition.attestation.utils.AggregatingAttestationPoolProfiler;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.PooledAttestationWithRewardInfo;
import tech.pegasys.teku.statetransition.attestation.utils.RewardBasedAttestationSorter.RewardBasedAttestationSorterFactory;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Maintains a pool of attestations. Attestations can be retrieved either for inclusion in a block
 * or as an aggregate to publish as part of the naive attestation aggregation algorithm. In both
 * cases the returned attestations are aggregated to maximize the number of validators that can be
 * included.
 *
 * <p>This V2 implementation uses concurrent collections to reduce contention.
 */
public class AggregatingAttestationPoolV2 extends AggregatingAttestationPool {
  private static final Logger LOG = LogManager.getLogger();

  private final ConcurrentMap<Bytes, MatchingDataAttestationGroupV2> attestationGroupByDataHash =
      new ConcurrentHashMap<>();

  private final ConcurrentNavigableMap<UInt64, Set<Bytes>> dataHashBySlot =
      new ConcurrentSkipListMap<>();

  private final SettableGauge sizeGauge;
  private final int maximumAttestationCount;
  private final AggregatingAttestationPoolProfiler aggregatingAttestationPoolProfiler;

  private final long maxBlockAggregationTimeNanos;
  private final long maxTotalBlockAggregationTimeMillis;

  private final LongSupplier nanosSupplier;

  private final AtomicInteger size = new AtomicInteger(0);

  private final RewardBasedAttestationSorterFactory rewardBasedAttestationSorterFactory;

  private final AtomicReference<Optional<UInt64>> earliestTrackedOnChainAttestationInclusionSlot =
      new AtomicReference<>(Optional.empty());

  public AggregatingAttestationPoolV2(
      final Spec spec,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final int maximumAttestationCount,
      final AggregatingAttestationPoolProfiler aggregatingAttestationPoolProfiler,
      final int maxBlockAggregationTimeMillis,
      final int maxTotalBlockAggregationTimeMillis) {
    super(spec, recentChainData);
    this.sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "attestation_pool_size",
            "The number of attestations available to be included in proposed blocks");
    this.maximumAttestationCount = maximumAttestationCount;
    this.aggregatingAttestationPoolProfiler = aggregatingAttestationPoolProfiler;
    this.maxBlockAggregationTimeNanos = maxBlockAggregationTimeMillis * 1_000_000L;
    this.maxTotalBlockAggregationTimeMillis = maxTotalBlockAggregationTimeMillis * 1_000_000L;
    this.nanosSupplier = System::nanoTime;
    this.rewardBasedAttestationSorterFactory = RewardBasedAttestationSorterFactory.DEFAULT;
  }

  @VisibleForTesting
  public AggregatingAttestationPoolV2(
      final Spec spec,
      final RecentChainData recentChainData,
      final MetricsSystem metricsSystem,
      final int maximumAttestationCount,
      final LongSupplier nanosSupplier,
      final RewardBasedAttestationSorterFactory rewardBasedAttestationSorterFactory,
      final int maxBlockAggregationTimeMillis,
      final int maxTotalBlockAggregationTimeMillis) {
    super(spec, recentChainData);
    this.sizeGauge =
        SettableGauge.create(
            metricsSystem,
            TekuMetricCategory.BEACON,
            "attestation_pool_size",
            "The number of attestations available to be included in proposed blocks");
    this.maximumAttestationCount = maximumAttestationCount;
    this.aggregatingAttestationPoolProfiler = AggregatingAttestationPoolProfiler.NOOP;
    this.maxBlockAggregationTimeNanos =
        maxBlockAggregationTimeMillis * 1_000_000L; // Integer.MAX_VALUE * 1_000_000L
    this.maxTotalBlockAggregationTimeMillis =
        maxTotalBlockAggregationTimeMillis * 1_000_000L; // Integer.MAX_VALUE * 1_000_000L
    this.nanosSupplier = nanosSupplier;
    this.rewardBasedAttestationSorterFactory = rewardBasedAttestationSorterFactory;
  }

  @Override
  public void add(final ValidatableAttestation attestation) {
    final Supplier<Optional<BeaconState>> cachingStateSupplier =
        Suppliers.memoize(() -> retrieveStateForAttestation(attestation.getData()));

    if (!ensureCommitteesSizeInAttestation(attestation, cachingStateSupplier)) {
      LOG.debug(
          "Committees size couldn't be retrieved for attestation at slot {}, block root {} and target root {}. Will NOT add this attestation to the pool.",
          attestation.getData().getSlot(),
          attestation.getData().getBeaconBlockRoot(),
          attestation.getData().getTarget().getRoot());
      return;
    }

    final Optional<List<UInt64>> validatorIndices =
        getValidatorIndices(attestation, cachingStateSupplier);

    if (validatorIndices.isEmpty()) {
      LOG.debug(
          "Validator indices couldn't be retrieved for attestation at slot {}, block root {} and target root {}. Will NOT add this attestation to the pool.",
          attestation.getData().getSlot(),
          attestation.getData().getBeaconBlockRoot(),
          attestation.getData().getTarget().getRoot());
      return;
    }

    getOrCreateAttestationGroup(attestation.getData(), attestation.getCommitteesSize())
        .ifPresent(
            attestationGroup ->
                attestationGroup.add(
                    PooledAttestation.fromValidatableAttestation(
                        attestation, validatorIndices.get()),
                    attestation.getCommitteeShufflingSeed()));
  }

  private Optional<List<UInt64>> getValidatorIndices(
      final ValidatableAttestation attestation,
      final Supplier<Optional<BeaconState>> stateSupplier) {
    return attestation
        .getIndexedAttestation()
        .map(IndexedAttestationLight::attestingIndices)
        .or(
            () ->
                stateSupplier
                    .get()
                    .map(
                        state ->
                            spec.atSlot(attestation.getData().getSlot())
                                .getAttestationUtil()
                                .getAttestingIndices(state, attestation.getAttestation())));
  }

  /**
   * @param committeesSize Required for aggregating attestations as per <a
   *     href="https://eips.ethereum.org/EIPS/eip-7549">EIP-7549</a>
   */
  private Optional<MatchingDataAttestationGroupV2> getOrCreateAttestationGroup(
      final AttestationData attestationData, final Optional<Int2IntMap> committeesSize) {

    final Bytes dataHash = attestationData.hashTreeRoot();

    dataHashBySlot
        .computeIfAbsent(attestationData.getSlot(), __ -> ConcurrentHashMap.newKeySet())
        .add(dataHash);

    final MatchingDataAttestationGroupV2 attestationGroup =
        attestationGroupByDataHash.computeIfAbsent(
            dataHash,
            __ ->
                new MatchingDataAttestationGroupV2(
                    spec, nanosSupplier, attestationData, committeesSize));

    return Optional.of(attestationGroup);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final int currentActualSize =
        attestationGroupByDataHash.values().stream()
            .mapToInt(MatchingDataAttestationGroupV2::size)
            .sum();

    size.set(currentActualSize);
    sizeGauge.set(currentActualSize);

    LOG.trace("Attestation pool size recalculated to {}", currentActualSize);

    if (slot.isGreaterThan(ATTESTATION_RETENTION_SLOTS)) {
      final UInt64 firstValidAttestationSlot = slot.minus(ATTESTATION_RETENTION_SLOTS);
      removeAttestationsPriorToSlot(firstValidAttestationSlot);
    }

    int sizeForPruningCheck = currentActualSize; // Use the size calculated at the start of onSlot
    while (dataHashBySlot.size() > 1 && sizeForPruningCheck > maximumAttestationCount) {
      LOG.trace(
          "Attestation cache at {} (pre-prune estimate) exceeds {}. Pruning...",
          sizeForPruningCheck,
          maximumAttestationCount);
      final UInt64 oldestSlot = dataHashBySlot.firstKey();
      if (oldestSlot == null) {
        break;
      }

      // Estimate the size reduction (since removeAttestationsPriorToSlot no longer updates 'size')
      // This is tricky because group.size() is approximate.
      // We might need to actually get the groups to be removed and sum their sizes *before*
      // removal.
      int estimatedRemovalCount = 0;
      final Set<Bytes> hashesToRemove =
          dataHashBySlot.getOrDefault(oldestSlot.plus(1), Set.of()); // Check slot *after* oldest
      for (final Bytes hash : hashesToRemove) {
        MatchingDataAttestationGroupV2 group = attestationGroupByDataHash.get(hash);
        if (group != null) {
          estimatedRemovalCount += group.size();
        }
      }

      removeAttestationsPriorToSlot(oldestSlot.plus(1)); // Remove the items

      if (estimatedRemovalCount == 0) {
        // If we estimated 0 removed, or failed to find the slot, break to avoid potential infinite
        // loop
        LOG.warn(
            "Failed to prune oldest slot {} or estimated 0 removals. Skipping further pruning this cycle.",
            oldestSlot);
        break;
      }
      sizeForPruningCheck -= estimatedRemovalCount;
    }

    aggregatingAttestationPoolProfiler.execute(spec, slot, recentChainData, this);
  }

  private void removeAttestationsPriorToSlot(final UInt64 firstValidAttestationSlot) {
    final NavigableMap<UInt64, Set<Bytes>> headMap =
        dataHashBySlot.headMap(firstValidAttestationSlot, false);
    final List<UInt64> slotsToRemove = List.copyOf(headMap.keySet());

    if (slotsToRemove.isEmpty()) {
      return;
    }

    LOG.trace(
        "Pruning attestations before slot {}. Slots to remove: {}",
        firstValidAttestationSlot,
        slotsToRemove.size());

    for (final UInt64 slot : slotsToRemove) {
      final Set<Bytes> dataHashes = dataHashBySlot.remove(slot);
      if (dataHashes != null) {
        dataHashes.forEach(attestationGroupByDataHash::remove);
      }
    }
  }

  @Override
  public void onAttestationsIncludedInBlock(
      final UInt64 slot, final Iterable<Attestation> attestations) {
    attestations.forEach(attestation -> onAttestationIncludedInBlock(slot, attestation));
    earliestTrackedOnChainAttestationInclusionSlot.compareAndExchange(
        Optional.empty(), Optional.of(slot));
  }

  private void onAttestationIncludedInBlock(final UInt64 slot, final Attestation attestation) {
    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, attestation);
    if (!ensureCommitteesSizeInAttestation(validatableAttestation)) {
      LOG.debug(
          "Attestation at slot {}, block root {} and target root {} has no committee size. Unable to call onAttestationIncludedInBlock.",
          attestation.getData().getSlot(),
          attestation.getData().getBeaconBlockRoot(),
          attestation.getData().getTarget().getRoot());
      return;
    }
    getOrCreateAttestationGroup(attestation.getData(), validatableAttestation.getCommitteesSize())
        .ifPresent(
            attestationGroup -> {
              // MatchingDataAttestationGroupV2 must handle concurrency internally
              final int numRemoved =
                  attestationGroup.onAttestationIncludedInBlock(slot, attestation);
              if (numRemoved > 0) {
                updateSize(-numRemoved);
              }
            });
  }

  private void updateSize(final int delta) {
    if (delta != 0) {
      final int currentSize = size.addAndGet(delta);
      sizeGauge.set(currentSize);
    }
  }

  @Override
  public int getSize() {
    return size.get();
  }

  private static Predicate<PooledAttestationWithRewardInfo> distinctByDataRoot() {
    final Map<Bytes32, Boolean> seen = new ConcurrentHashMap<>();
    return t -> seen.putIfAbsent(t.getAttestation().data().hashTreeRoot(), true) == null;
  }

  private Predicate<PooledAttestationWithData> previousEpochLimitFilter(
      final BeaconState stateAtBlockSlot) {
    final int previousEpochLimit = spec.getPreviousEpochAttestationCapacity(stateAtBlockSlot);
    if (previousEpochLimit == Integer.MAX_VALUE) {
      return attestation -> true; // No limit, accept all
    }

    // this should be used only when the previous epoch is phase0
    final UInt64 currentEpoch = spec.getCurrentEpoch(stateAtBlockSlot);
    final AtomicInteger prevEpochCount = new AtomicInteger(0);

    return attestation -> {
      if (spec.computeEpochAtSlot(attestation.data().getSlot()).isLessThan(currentEpoch)) {
        final int currentCount = prevEpochCount.getAndIncrement();
        return currentCount < previousEpochLimit;
      }
      return true;
    };
  }

  @Override
  public SszList<Attestation> getAttestationsForBlock(
      final BeaconState stateAtBlockSlot, final AttestationForkChecker forkChecker) {
    final Predicate<PooledAttestationWithData> previousEpochLimitFilter =
        previousEpochLimitFilter(stateAtBlockSlot);

    final RewardBasedAttestationSorter rewardBasedAttestationSorter =
        rewardBasedAttestationSorterFactory.create(spec, stateAtBlockSlot);
    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(stateAtBlockSlot.getSlot()).getSchemaDefinitions();

    final SszListSchema<Attestation, ?> attestationsSchema =
        schemaDefinitions.getBeaconBlockBodySchema().getAttestationsSchema();

    // The per-block attestation count limit comes from the spec config rather than the SSZ list
    // bound: from Gloas the attestations list is a progressive list with no SSZ max length, so
    // attestationsSchema.getMaxLength() is unbounded. For Electra and later (incl. Gloas) the limit
    // is MAX_ATTESTATIONS_ELECTRA; before Electra it is MAX_ATTESTATIONS. For bounded forks this
    // equals the list's max length.
    final SpecConfig config = spec.atSlot(stateAtBlockSlot.getSlot()).getConfig();
    final int blockAttestationCapacity =
        config
            .toVersionElectra()
            .map(SpecConfigElectra::getMaxAttestationsElectra)
            .orElseGet(config::getMaxAttestations);

    final AttestationSchema<Attestation> attestationSchema =
        schemaDefinitions.getAttestationSchema();

    final boolean blockRequiresAttestationsWithCommitteeBits =
        attestationSchema.requiresCommitteeBits();

    final long nowNanos = nanosSupplier.getAsLong();
    final long totalTimeLimitNanos = nowNanos + maxTotalBlockAggregationTimeMillis;
    final long aggregationTimeLimit = nowNanos + maxBlockAggregationTimeNanos;

    /* -- Aggregation phase -- */

    final List<PooledAttestationWithData> aggregates = new ArrayList<>(32);

    computeAggregation(
        stateAtBlockSlot,
        forkChecker,
        blockRequiresAttestationsWithCommitteeBits,
        previousEpochLimitFilter,
        aggregationTimeLimit,
        false,
        aggregates);

    LOG.debug(
        "Aggregation phase took {} ms. Produced {} aggregations.",
        () -> (nanosSupplier.getAsLong() - nowNanos) / 1_000_000,
        aggregates::size);

    if (aggregationTimeLimit > nanosSupplier.getAsLong()) {
      // we have time left to consider groups containing single attestation only

      computeAggregation(
          stateAtBlockSlot,
          forkChecker,
          blockRequiresAttestationsWithCommitteeBits,
          previousEpochLimitFilter,
          aggregationTimeLimit,
          true,
          aggregates);

      LOG.debug(
          "Aggregation including SA took {} ms. Final produced {} aggregations.",
          () -> (nanosSupplier.getAsLong() - nowNanos) / 1_000_000,
          aggregates::size);
    }

    /* -- Sorting phase -- */

    final List<PooledAttestationWithRewardInfo> sortedAggregates =
        rewardBasedAttestationSorter.sort(aggregates, blockAttestationCapacity);

    /* -- FillUp phase -- */

    final Predicate<PooledAttestationWithRewardInfo> distinctPredicate = distinctByDataRoot();
    final Stream<Optional<PooledAttestationWithRewardInfo>> toBeFilledUpAggregates =
        sortedAggregates.stream()
            .map(
                aggregate ->
                    distinctPredicate.test(aggregate) ? Optional.of(aggregate) : Optional.empty());

    final List<Optional<PooledAttestationWithRewardInfo>> filledUpAggregates =
        toBeFilledUpAggregates
            .peek(
                maybeAttestation ->
                    maybeAttestation.ifPresent(
                        attestation ->
                            aggregatingAttestationPoolProfiler.onPreFillUp(
                                stateAtBlockSlot, attestation)))
            .map(
                maybeAttestation ->
                    maybeAttestation.map(
                        attestation -> fillUpAttestation(attestation, totalTimeLimitNanos)))
            .peek(
                maybeAttestation ->
                    maybeAttestation.ifPresent(
                        attestation ->
                            aggregatingAttestationPoolProfiler.onPostFillUp(
                                stateAtBlockSlot, attestation)))
            .toList();

    /* -- Final conversion phase -- */

    final SszList<Attestation> result =
        IntStream.range(0, sortedAggregates.size())
            .mapToObj(i -> filledUpAggregates.get(i).orElse(sortedAggregates.get(i)))
            .map(a -> a.getAttestation().toAttestation(attestationSchema))
            .collect(attestationsSchema.collector());

    LOG.debug(
        "getAttestationsForBlock took {} ms.",
        () -> (nanosSupplier.getAsLong() - nowNanos) / 1_000_000);

    return result;
  }

  private void computeAggregation(
      final BeaconState stateAtBlockSlot,
      final AttestationForkChecker forkChecker,
      final boolean blockRequiresAttestationsWithCommitteeBits,
      final Predicate<PooledAttestationWithData> previousEpochLimitFilter,
      final long aggregationTimeLimit,
      final boolean singleAttestationsOnlyAggregate,
      final List<PooledAttestationWithData> destinationAggregates) {
    final Optional<UInt64> earliestSlot = earliestTrackedOnChainAttestationInclusionSlot.get();

    if (earliestSlot.isEmpty()) {
      // the node just went online, so we can assume the pool is almost empty
      return;
    }

    dataHashBySlot
        .subMap(earliestSlot.get(), true, stateAtBlockSlot.getSlot(), false)
        .descendingMap() // Safe view
        .values()
        .stream()
        .flatMap(
            dataHashSetForSlot ->
                streamAggregatesForDataHashesBySlot(
                    dataHashSetForSlot,
                    stateAtBlockSlot,
                    forkChecker,
                    blockRequiresAttestationsWithCommitteeBits,
                    aggregationTimeLimit,
                    singleAttestationsOnlyAggregate))
        .filter(previousEpochLimitFilter)
        .forEach(destinationAggregates::add);
  }

  private PooledAttestationWithRewardInfo fillUpAttestation(
      final PooledAttestationWithRewardInfo attestationWithRewards, final long timeLimitNanos) {
    if (nanosSupplier.getAsLong() > timeLimitNanos) {
      LOG.debug("Time limit reached, skipping fillUpAttestation");
      return attestationWithRewards;
    }

    var attestation = attestationWithRewards.getAttestation();
    return Optional.ofNullable(attestationGroupByDataHash.get(attestation.data().hashTreeRoot()))
        .map(
            group ->
                attestationWithRewards.withAttestation(
                    group.fillUpAggregation(attestation, timeLimitNanos)))
        .orElse(attestationWithRewards);
  }

  private Stream<PooledAttestationWithData> streamAggregatesForDataHashesBySlot(
      final Set<Bytes> dataHashSetForSlot,
      final BeaconState stateAtBlockSlot,
      final AttestationForkChecker forkChecker,
      final boolean blockRequiresAttestationsWithCommitteeBits,
      final long baseAggregationTimeLimitNanos,
      final boolean singleAttestationsOnlyAggregate) {

    return dataHashSetForSlot.stream()
        .map(attestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .filter(group -> group.isValid(stateAtBlockSlot, spec))
        .filter(forkChecker::areAttestationsFromCorrectForkV2)
        .flatMap(
            group ->
                singleAttestationsOnlyAggregate
                    ? group.streamSingleAttestationsForBlockProduction(
                        baseAggregationTimeLimitNanos)
                    : group.streamAggregatesForBlockProduction(baseAggregationTimeLimitNanos))
        .filter(
            attestation ->
                attestation.pooledAttestation().bits().requiresCommitteeBits()
                    == blockRequiresAttestationsWithCommitteeBits);
  }

  @Override
  public List<Attestation> getAttestations(
      final Optional<UInt64> maybeSlot, final Optional<UInt64> maybeCommitteeIndex) {

    final Predicate<Map.Entry<UInt64, Set<Bytes>>> filterForSlot =
        (entry) -> maybeSlot.map(slot -> entry.getKey().equals(slot)).orElse(true);

    final UInt64 slot = maybeSlot.orElse(recentChainData.getCurrentSlot().orElse(UInt64.ZERO));
    final SchemaDefinitions schemaDefinitions = spec.atSlot(slot).getSchemaDefinitions();
    final AttestationSchema<Attestation> attestationSchema =
        schemaDefinitions.getAttestationSchema();
    final boolean requiresCommitteeBits = attestationSchema.requiresCommitteeBits();

    return dataHashBySlot.descendingMap().entrySet().stream()
        .filter(filterForSlot)
        .map(Map.Entry::getValue)
        .flatMap(Collection::stream)
        .map(attestationGroupByDataHash::get)
        .filter(Objects::nonNull)
        .flatMap(
            matchingDataAttestationGroup ->
                matchingDataAttestationGroup.streamForApiRequest(
                    maybeCommitteeIndex, requiresCommitteeBits))
        .map(pooledAttestation -> pooledAttestation.toAttestation(attestationSchema))
        .toList();
  }

  @Override
  public Optional<Attestation> createAggregateFor(
      final Bytes32 attestationHashTreeRoot, final Optional<UInt64> committeeIndex) {

    final MatchingDataAttestationGroupV2 group =
        attestationGroupByDataHash.get(attestationHashTreeRoot);
    if (group == null) {
      return Optional.empty();
    }

    final SchemaDefinitions schemaDefinitions =
        spec.atSlot(group.getAttestationData().getSlot()).getSchemaDefinitions();
    final AttestationSchema<Attestation> attestationSchema =
        schemaDefinitions.getAttestationSchema();

    return group
        .streamForAggregationProduction(committeeIndex, Long.MAX_VALUE)
        .findFirst()
        .map(pooledAttestation -> pooledAttestation.toAttestation(attestationSchema));
  }

  @Override
  public void onReorg(final UInt64 commonAncestorSlot) {
    attestationGroupByDataHash.values().forEach(group -> group.onReorg(commonAncestorSlot));
  }
}
