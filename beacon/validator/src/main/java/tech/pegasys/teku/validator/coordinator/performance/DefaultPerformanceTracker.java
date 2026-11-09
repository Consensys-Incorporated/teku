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

package tech.pegasys.teku.validator.coordinator.performance;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.SingleAttestation;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.statetransition.attestation.utils.AttestationBits;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;

public class DefaultPerformanceTracker implements PerformanceTracker {
  private static final Logger LOG = LogManager.getLogger();

  @VisibleForTesting
  final NavigableMap<UInt64, Set<SlotAndBlockRoot>> producedBlocksByEpoch =
      new ConcurrentSkipListMap<>();

  final NavigableMap<UInt64, Set<Attestation>> producedAttestationsByEpoch =
      new ConcurrentSkipListMap<>();

  final NavigableMap<UInt64, AtomicInteger> blockProductionAttemptsByEpoch =
      new ConcurrentSkipListMap<>();

  public static final UInt64 ATTESTATION_INCLUSION_RANGE = UInt64.valueOf(2);

  private final CombinedChainDataClient combinedChainDataClient;
  private final StatusLogger statusLogger;
  private final ValidatorPerformanceMetrics validatorPerformanceMetrics;
  private final ValidatorPerformanceTrackingMode mode;
  private final ActiveValidatorTracker validatorTracker;
  private final SyncCommitteePerformanceTracker syncCommitteePerformanceTracker;
  private final Spec spec;
  private final SettableGauge timingsSettableGauge;

  private volatile Optional<UInt64> nodeStartEpoch = Optional.empty();
  private final AtomicReference<UInt64> latestAnalyzedEpoch = new AtomicReference<>(UInt64.ZERO);

  public DefaultPerformanceTracker(
      final CombinedChainDataClient combinedChainDataClient,
      final StatusLogger statusLogger,
      final ValidatorPerformanceMetrics validatorPerformanceMetrics,
      final ValidatorPerformanceTrackingMode mode,
      final ActiveValidatorTracker validatorTracker,
      final SyncCommitteePerformanceTracker syncCommitteePerformanceTracker,
      final Spec spec,
      final SettableGauge timingsSettableGauge) {
    this.combinedChainDataClient = combinedChainDataClient;
    this.statusLogger = statusLogger;
    this.validatorPerformanceMetrics = validatorPerformanceMetrics;
    this.mode = mode;
    this.validatorTracker = validatorTracker;
    this.syncCommitteePerformanceTracker = syncCommitteePerformanceTracker;
    this.spec = spec;
    this.timingsSettableGauge = timingsSettableGauge;
  }

  @Override
  public void start(final UInt64 nodeStartSlot) {
    this.nodeStartEpoch = Optional.of(spec.computeEpochAtSlot(nodeStartSlot));
  }

  @Override
  public void onSlot(final UInt64 slot) {
    // Ensure a consistent view as the field is volatile.
    final Optional<UInt64> nodeStartEpoch = this.nodeStartEpoch;
    if (nodeStartEpoch.isEmpty() || combinedChainDataClient.getChainHead().isEmpty()) {
      return;
    }

    if (slot.mod(spec.getSlotsPerEpoch(slot)).isGreaterThan(UInt64.ZERO)) {
      return;
    }

    UInt64 currentEpoch = spec.computeEpochAtSlot(slot);
    if (currentEpoch.isLessThanOrEqualTo(
        latestAnalyzedEpoch.getAndUpdate(
            val -> val.isLessThan(currentEpoch) ? currentEpoch : val))) {
      return;
    }

    final long startTime = System.currentTimeMillis();
    final List<SafeFuture<?>> reportingTasks = new ArrayList<>();
    // Output attestation performance information for current epoch - 2 since attestations can be
    // included in both the epoch they were produced in or in the one following.
    if (currentEpoch.isGreaterThanOrEqualTo(
        nodeStartEpoch.get().plus(ATTESTATION_INCLUSION_RANGE))) {
      reportingTasks.add(reportAttestationPerformance(currentEpoch));
    }

    // Nothing to report until epoch 0 is complete
    if (!currentEpoch.isZero()) {
      reportingTasks.add(reportBlockPerformance(currentEpoch));
    }

    // Nothing to report until epoch 0 is complete
    if (!currentEpoch.isZero()) {
      reportingTasks.add(reportSyncCommitteePerformance(currentEpoch));
    }

    SafeFuture.allOf(reportingTasks.toArray(SafeFuture[]::new))
        .handleException(error -> LOG.error("Failed to report performance metrics", error))
        .alwaysRun(
            () -> {
              if (!reportingTasks.isEmpty()) {
                timingsSettableGauge.set(System.currentTimeMillis() - startTime);
              }
            })
        .join();
  }

  private SafeFuture<?> reportBlockPerformance(final UInt64 currentEpoch) {
    final UInt64 blockProductionEpoch = currentEpoch.minus(1);
    return getBlockPerformanceForEpoch(blockProductionEpoch)
        .thenAccept(
            blockPerformance -> {
              if (blockPerformance.numberOfExpectedBlocks > 0) {
                if (mode.isLoggingEnabled()) {
                  statusLogger.performance(blockPerformance.toString());
                }

                if (mode.isMetricsEnabled()) {
                  validatorPerformanceMetrics.updateBlockPerformanceMetrics(blockPerformance);
                }
              }
            })
        .alwaysRun(
            () -> {
              producedBlocksByEpoch.headMap(blockProductionEpoch, true).clear();
              blockProductionAttemptsByEpoch.headMap(blockProductionEpoch, true).clear();
            });
  }

  private SafeFuture<?> reportSyncCommitteePerformance(final UInt64 currentEpoch) {
    return syncCommitteePerformanceTracker
        .calculatePerformance(currentEpoch.minus(1))
        .thenAccept(
            syncCommitteePerformance -> {
              if (syncCommitteePerformance.getNumberOfExpectedMessages() > 0) {
                if (mode.isLoggingEnabled()) {
                  statusLogger.performance(syncCommitteePerformance.toString());
                }

                if (mode.isMetricsEnabled()) {
                  validatorPerformanceMetrics.updateSyncCommitteePerformance(
                      syncCommitteePerformance);
                }
              }
            });
  }

  private SafeFuture<?> reportAttestationPerformance(final UInt64 currentEpoch) {
    UInt64 analyzedEpoch = currentEpoch.minus(ATTESTATION_INCLUSION_RANGE);
    return getAttestationPerformanceForEpoch(currentEpoch, analyzedEpoch)
        .thenAccept(
            attestationPerformance -> {

              // suppress performance metric output when not relevant
              if (mode.isLoggingEnabled()
                  && attestationPerformance.numberOfExpectedAttestations > 0) {
                statusLogger.performance(attestationPerformance.toString());
              }

              if (mode.isMetricsEnabled()) {
                validatorPerformanceMetrics.updateAttestationPerformanceMetrics(
                    attestationPerformance);
              }
            })
        .alwaysRun(() -> producedAttestationsByEpoch.headMap(analyzedEpoch, true).clear());
  }

  private SafeFuture<BlockPerformance> getBlockPerformanceForEpoch(final UInt64 currentEpoch) {
    return combinedChainDataClient
        .getChainHead()
        .orElseThrow()
        .asStateAndBlockSummary()
        .thenApply(
            chainHead -> {
              int numberOfBlockProductionAttempts = 0;
              AtomicInteger blocksAtEpoch = blockProductionAttemptsByEpoch.get(currentEpoch);
              if (blocksAtEpoch != null) {
                numberOfBlockProductionAttempts = blocksAtEpoch.get();
              }

              Collection<SlotAndBlockRoot> producedBlocks = Collections.emptyList();
              if (producedBlocksByEpoch.get(currentEpoch) != null) {
                producedBlocks = producedBlocksByEpoch.get(currentEpoch);
              }
              final BeaconState state = chainHead.getState();
              final long numberOfIncludedBlocks =
                  producedBlocks.stream()
                      .filter(
                          producedBlock ->
                              // Chain head root itself isn't available in state history
                              producedBlock.getBlockRoot().equals(chainHead.getRoot())
                                  || isInHistoricBlockRoots(state, producedBlock))
                      .count();

              int numberOfProducedBlocks = producedBlocks.size();
              return new BlockPerformance(
                  currentEpoch,
                  numberOfBlockProductionAttempts,
                  (int) numberOfIncludedBlocks,
                  numberOfProducedBlocks);
            });
  }

  private boolean isInHistoricBlockRoots(
      final BeaconState state, final SlotAndBlockRoot producedBlock) {
    LOG.debug(
        "Checking if block {} is in historic block roots of the state {}",
        producedBlock,
        state.hashTreeRoot());
    return producedBlock.getSlot().isLessThan(state.getSlot())
        && spec.getBlockRootAtSlot(state, producedBlock.getSlot())
            .equals(producedBlock.getBlockRoot());
  }

  private SafeFuture<AttestationPerformance> getAttestationPerformanceForEpoch(
      final UInt64 currentEpoch, final UInt64 analyzedEpoch) {
    checkArgument(
        analyzedEpoch.isLessThanOrEqualTo(currentEpoch.minus(ATTESTATION_INCLUSION_RANGE)),
        "Epoch to analyze attestation performance must be at least 2 epochs less than the current epoch");

    // Attestations can be included in either the epoch they were produced in or in
    // the following epoch. Thus, the most recent epoch for which we can evaluate attestation
    // performance is current epoch - 2.
    final UInt64 analysisRangeEndEpoch = analyzedEpoch.plus(ATTESTATION_INCLUSION_RANGE);

    // Get included attestations for the given epochs in a map from slot to attestations
    // included in block.
    final SafeFuture<Map<UInt64, List<Attestation>>> attestationsIncludedOnChainFuture =
        getAttestationsIncludedInEpochs(analyzedEpoch, analysisRangeEndEpoch);

    // Get sent attestations in range
    final Set<Attestation> producedAttestations =
        producedAttestationsByEpoch.getOrDefault(analyzedEpoch, Collections.emptySet());
    return combinedChainDataClient
        .getBestState()
        .orElseThrow()
        .thenCombine(
            attestationsIncludedOnChainFuture,
            (state, attestationsIncludedOnChain) ->
                calculateAttestationPerformance(
                    analyzedEpoch, state, producedAttestations, attestationsIncludedOnChain));
  }

  private AttestationPerformance calculateAttestationPerformance(
      final UInt64 analyzedEpoch,
      final BeaconState state,
      final Set<Attestation> producedAttestations,
      final Map<UInt64, List<Attestation>> attestationsIncludedOnChain) {
    final IntList inclusionDistances = new IntArrayList();
    int correctTargetCount = 0;
    int correctHeadBlockCount = 0;

    // Pre-process attestations included on chain to group them by
    // data hash to inclusion slot to aggregation bitlist
    final Map<Bytes32, NavigableMap<UInt64, AttestationBits>> slotAndBitlistsByAttestationDataHash =
        new HashMap<>();
    for (final Map.Entry<UInt64, List<Attestation>> entry :
        attestationsIncludedOnChain.entrySet()) {
      for (final Attestation attestation : entry.getValue()) {
        final Optional<Int2IntMap> committeesSize = getCommitteesSize(attestation, state);
        final Bytes32 attestationDataHash = attestation.getData().hashTreeRoot();
        final NavigableMap<UInt64, AttestationBits> slotToBitlists =
            slotAndBitlistsByAttestationDataHash.computeIfAbsent(
                attestationDataHash, __ -> new TreeMap<>());
        slotToBitlists.merge(
            entry.getKey(),
            AttestationBits.of(attestation, committeesSize),
            (firstBitsAggregator, secondBitsAggregator) -> {
              firstBitsAggregator.or(secondBitsAggregator);
              return firstBitsAggregator;
            });
      }
    }

    for (final Attestation sentAttestation : producedAttestations) {
      final Attestation attestation = convertSingleAttestation(state, sentAttestation);
      final Bytes32 attestationDataHash = attestation.getData().hashTreeRoot();
      final UInt64 attestationSlot = attestation.getData().getSlot();
      if (!slotAndBitlistsByAttestationDataHash.containsKey(attestationDataHash)) {
        continue;
      }
      final NavigableMap<UInt64, AttestationBits> slotAndBitlists =
          slotAndBitlistsByAttestationDataHash.get(attestationDataHash);
      for (UInt64 slot : slotAndBitlists.keySet()) {
        if (slotAndBitlists.get(slot).isSuperSetOf(attestation)) {
          inclusionDistances.add(slot.minus(attestationSlot).intValue());
          break;
        }
      }

      // Check if the attestation had correct target
      final Bytes32 attestationTargetRoot = attestation.getData().getTarget().getRoot();
      if (attestationTargetRoot.equals(spec.getBlockRoot(state, analyzedEpoch))) {
        correctTargetCount++;

        // Check if the attestation had correct head block root
        final Bytes32 attestationHeadBlockRoot = attestation.getData().getBeaconBlockRoot();
        if (attestationHeadBlockRoot.equals(spec.getBlockRootAtSlot(state, attestationSlot))) {
          correctHeadBlockCount++;
        }
      }
    }

    final IntSummaryStatistics inclusionDistanceStatistics =
        inclusionDistances.intStream().summaryStatistics();

    // IntSummaryStatistics returns Integer.MIN and MAX when the summarized integer list
    // is empty.
    final int numberOfProducedAttestations = producedAttestations.size();
    return numberOfProducedAttestations > 0
        ? new AttestationPerformance(
            analyzedEpoch,
            validatorTracker.getNumberOfValidatorsForEpoch(analyzedEpoch),
            numberOfProducedAttestations,
            (int) inclusionDistanceStatistics.getCount(),
            inclusionDistanceStatistics.getMax(),
            inclusionDistanceStatistics.getMin(),
            inclusionDistanceStatistics.getAverage(),
            correctTargetCount,
            correctHeadBlockCount)
        : AttestationPerformance.empty(
            analyzedEpoch, validatorTracker.getNumberOfValidatorsForEpoch(analyzedEpoch));
  }

  private Attestation convertSingleAttestation(
      final BeaconState state, final Attestation attestation) {
    if (attestation.isSingleAttestation()) {
      final SingleAttestation singleAttestation = attestation.toSingleAttestationRequired();
      final AttestationUtil attestationUtil =
          spec.atSlot(singleAttestation.getData().getSlot()).getAttestationUtil();
      return attestationUtil.convertSingleAttestationToAggregated(state, singleAttestation);
    }
    return attestation;
  }

  private Optional<Int2IntMap> getCommitteesSize(
      final Attestation attestation, final BeaconState state) {
    if (!attestation.requiresCommitteeBits()) {
      return Optional.empty();
    }
    return Optional.of(spec.getBeaconCommitteesSize(state, attestation.getData().getSlot()));
  }

  private SafeFuture<Set<BeaconBlock>> getBlocksInEpochs(
      final UInt64 startEpochInclusive, final UInt64 endEpochExclusive) {
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(startEpochInclusive);
    final UInt64 inclusiveEndEpochEndSlot =
        spec.computeStartSlotAtEpoch(endEpochExclusive).decrement();

    final Set<BeaconBlock> blocksInEpoch = new HashSet<>();
    final AtomicReference<UInt64> currSlot = new AtomicReference<>(inclusiveEndEpochEndSlot);
    return SafeFuture.asyncDoWhile(
            () -> fillBlockInEffectAtSlot(blocksInEpoch, epochStartSlot, currSlot))
        .thenApply(__ -> blocksInEpoch);
  }

  private SafeFuture<Boolean> fillBlockInEffectAtSlot(
      final Set<BeaconBlock> blocksInEpoch,
      final UInt64 epochStartSlot,
      final AtomicReference<UInt64> currSlot) {
    if (currSlot.get().isGreaterThanOrEqualTo(epochStartSlot)) {
      return combinedChainDataClient
          .getBlockInEffectAtSlot(currSlot.get())
          .thenApply(maybeSignedBlock -> maybeSignedBlock.map(SignedBeaconBlock::getMessage))
          .thenPeek(
              maybeBlock ->
                  maybeBlock.ifPresent(
                      block -> {
                        blocksInEpoch.add(block);
                        currSlot.set(currSlot.get().minusMinZero(1));
                      }))
          .thenApply(
              maybeBlock ->
                  maybeBlock.map(block -> !block.getSlot().equals(UInt64.ZERO)).orElse(false));

    } else {
      return SafeFuture.completedFuture(false);
    }
  }

  private SafeFuture<Map<UInt64, List<Attestation>>> getAttestationsIncludedInEpochs(
      final UInt64 startEpochInclusive, final UInt64 endEpochExclusive) {
    return getBlocksInEpochs(startEpochInclusive, endEpochExclusive)
        .thenApply(
            beaconBlocks ->
                beaconBlocks.stream()
                    .collect(
                        Collectors.toMap(
                            BeaconBlock::getSlot,
                            block -> block.getBody().getAttestations().asList())));
  }

  @Override
  public void saveProducedAttestation(final Attestation attestation) {
    final UInt64 epoch = spec.computeEpochAtSlot(attestation.getData().getSlot());
    final Set<Attestation> attestationsInEpoch =
        producedAttestationsByEpoch.computeIfAbsent(epoch, __ -> concurrentSet());
    attestationsInEpoch.add(attestation);
  }

  @Override
  public void saveProducedBlock(final SlotAndBlockRoot slotAndBlockRoot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slotAndBlockRoot.getSlot());
    final Set<SlotAndBlockRoot> blocksInEpoch =
        producedBlocksByEpoch.computeIfAbsent(epoch, __ -> concurrentSet());
    blocksInEpoch.add(slotAndBlockRoot);
  }

  @Override
  public void reportBlockProductionAttempt(final UInt64 epoch) {
    final AtomicInteger numberOfBlockProductionAttempts =
        blockProductionAttemptsByEpoch.computeIfAbsent(epoch, __ -> new AtomicInteger(0));
    numberOfBlockProductionAttempts.incrementAndGet();
  }

  @Override
  public void saveExpectedSyncCommitteeParticipant(
      final int validatorIndex,
      final IntSet syncCommitteeIndices,
      final UInt64 subscribeUntilEpoch) {
    syncCommitteePerformanceTracker.saveExpectedSyncCommitteeParticipant(
        validatorIndex, syncCommitteeIndices, subscribeUntilEpoch);
  }

  @Override
  public void saveProducedSyncCommitteeMessage(final SyncCommitteeMessage message) {
    syncCommitteePerformanceTracker.saveProducedSyncCommitteeMessage(message);
  }

  static long getPercentage(final long numerator, final long denominator) {
    return (long) (numerator * 100.0 / denominator + 0.5);
  }

  private <T> Set<T> concurrentSet() {
    return Collections.newSetFromMap(new ConcurrentHashMap<>());
  }
}
