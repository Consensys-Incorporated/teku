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

package tech.pegasys.teku.statetransition.pq.timing;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkBackend;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProofVerifier;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.XmssSignatureVerifier;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPoolPq;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPoolPq.MeasuredProof;
import tech.pegasys.teku.statetransition.pq.timing.PqTiming.Experiment;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Starts the PQ timing export once the chain's genesis is known and, in observer mode, runs the
 * timing experiments of the other clients' {@code --pq-measurement}: every {@link PqTiming#STRIDE}
 * slots it proves one committee and merges one block proof from live gossip, rotating through three
 * scenarios, and never publishes the result.
 *
 * <ul>
 *   <li>committee_only: proves a committee of the anchor slot at the collection cutoff.
 *   <li>proposer_only: merges committee proofs for a block two slots after the anchor slot as the
 *       pool does ahead of a local proposal (the anchor slot's at the start of the slot before,
 *       then every committee the block can include once that slot's aggregates are due), and then
 *       as block production does.
 *   <li>combined: both, with the committee proof one slot later so it overlaps the merging.
 * </ul>
 */
public class PqTimingController implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  /** AGGREGATION_CUTOFF_BPS, from the specification change the other clients measure against. */
  static final int COLLECTION_CUTOFF_BPS = 4000;

  static final String[] SCENARIOS = {"committee_only", "proposer_only", "combined"};

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final AggregatingAttestationPoolPq attestationPool;
  private final AsyncRunner asyncRunner;
  private final Path output;
  private final boolean measure;
  private final String network;
  private final String version;
  private final Optional<Integer> proverThreads;
  private final BooleanSupplier isSyncing;
  private final IntSupplier peerCount;
  private final StarkProofVerifier verifier =
      StarkBackend.verifier(XmssSignatureVerifier.LEAN_XMSS);
  private final long[] draws = new long[SCENARIOS.length];

  private boolean started;
  private long seed;
  private Optional<Boolean> lastSynced = Optional.empty();
  private int lastPeers = -1;

  public PqTimingController(
      final Spec spec,
      final RecentChainData recentChainData,
      final AggregatingAttestationPoolPq attestationPool,
      final AsyncRunner asyncRunner,
      final Path output,
      final boolean measure,
      final String network,
      final String version,
      final Optional<Integer> proverThreads,
      final BooleanSupplier isSyncing,
      final IntSupplier peerCount) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.attestationPool = attestationPool;
    this.asyncRunner = asyncRunner;
    this.output = output;
    this.measure = measure;
    this.network = network;
    this.version = version;
    this.proverThreads = proverThreads;
    this.isSyncing = isSyncing;
    this.peerCount = peerCount;
  }

  public void stop() {
    PqTiming.stop();
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    if (!started && !start()) {
      return;
    }
    if (!measure || !PqTiming.isEnabled()) {
      return;
    }
    final boolean synced = !isSyncing.getAsBoolean();
    final int peers = peerCount.getAsInt();
    if (!lastSynced.equals(Optional.of(synced)) || peers != lastPeers) {
      final Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("synced", synced);
      fields.put("connected_peers", peers);
      fields.put("slot", slot.longValue());
      PqTiming.status("network_status", fields);
      lastSynced = Optional.of(synced);
      lastPeers = peers;
    }
    // Planned a slot ahead, so the inputs are chosen before any of them arrive
    final UInt64 anchor = slot.increment();
    if (anchor.mod(PqTiming.STRIDE).longValue() == PqTiming.anchorPhase(seed)) {
      plan(anchor, synced);
    }
  }

  private boolean start() {
    if (recentChainData.isPreGenesis() || recentChainData.getGenesisData().isEmpty()) {
      return false;
    }
    started = true;
    final SpecVersion pq = spec.forMilestone(SpecMilestone.PQ);
    final Duration slotDuration = Duration.ofMillis(pq.getConfig().getSlotDurationMillis());
    final Duration aggregateDue = Duration.ofMillis(pq.getForkChoiceUtil().getAggregateDueMillis());
    try {
      seed =
          PqTiming.start(
              new PqTiming.Run(
                  output,
                  measure,
                  network,
                  version,
                  StarkBackend.isLeanVm() ? "leanvm" : "concatenation",
                  proverThreads,
                  recentChainData.getGenesisTime(),
                  recentChainData.getGenesisData().get().getGenesisValidatorsRoot(),
                  slotDuration,
                  Duration.ofMillis(pq.getForkChoiceUtil().getAttestationDueMillis()),
                  // Teku's validator client asks for an aggregate's proof when it is due
                  measure ? collectionCutoff(slotDuration) : aggregateDue,
                  aggregateDue,
                  // the block proof is merged ahead at the start of the slot before the block
                  slotDuration,
                  this::slotStartWallNanos));
    } catch (final IOException | RuntimeException e) {
      LOG.error("Could not start the PQ timing export in {}", output, e);
    }
    return true;
  }

  private static Duration collectionCutoff(final Duration slotDuration) {
    return slotDuration.multipliedBy(COLLECTION_CUTOFF_BPS).dividedBy(10_000);
  }

  private long slotStartWallNanos(final UInt64 slot) {
    return spec.computeTimeMillisAtSlot(slot, recentChainData.getGenesisTimeMillis()).longValue()
        * 1_000_000L;
  }

  private SafeFuture<Void> atWallNanos(final long wallNanos) {
    final long now = PqTiming.nowWallNanos().orElse(wallNanos);
    return asyncRunner.getDelayedFuture(Duration.ofNanos(Math.max(0, wallNanos - now)));
  }

  private void skip(final UInt64 anchor, final String scenario, final String reason) {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("experiment_id", anchor.longValue());
    fields.put("reference_slot", anchor.longValue());
    fields.put("scenario", scenario);
    fields.put("outcome", reason);
    PqTiming.status("experiment_planned", fields);
  }

  private void plan(final UInt64 anchor, final boolean synced) {
    final int scenarioIndex = (int) anchor.dividedBy(PqTiming.STRIDE).mod(3).longValue();
    final String scenario = SCENARIOS[scenarioIndex];
    if (!synced) {
      skip(anchor, scenario, "syncing");
      return;
    }
    if (!spec.atSlot(anchor).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.PQ)) {
      skip(anchor, scenario, "pre_pq");
      return;
    }
    final Optional<SafeFuture<BeaconState>> headState = recentChainData.getBestState();
    if (headState.isEmpty()) {
      skip(anchor, scenario, "missing_geometry");
      return;
    }
    final boolean withCommittee = !scenario.equals("proposer_only");
    final boolean withProposal = !scenario.equals("committee_only");
    final long draw = draws[scenarioIndex];
    if (withCommittee) {
      draws[scenarioIndex]++;
    }
    headState
        .get()
        .thenAccept(
            state -> {
              final UInt64 committeeSlot =
                  scenario.equals("combined") ? anchor.increment() : anchor;
              final UInt64 epoch = spec.computeEpochAtSlot(committeeSlot);
              final UInt64 committeeCount = spec.getCommitteeCountPerSlot(state, epoch);
              if (committeeCount.isZero()) {
                skip(anchor, scenario, "missing_geometry");
                return;
              }
              final Experiment experiment =
                  new Experiment(
                      anchor,
                      scenario,
                      committeeCount,
                      UInt64.valueOf(spec.getActiveValidatorIndices(state, epoch).size()),
                      0);
              final int committee =
                  (int) Long.remainderUnsigned(seed + draw, committeeCount.longValue());
              final Map<String, Object> fields = new HashMap<>();
              fields.put("experiment_id", anchor.longValue());
              fields.put("sequence_id", experiment.sequenceId());
              fields.put("reference_slot", anchor.longValue());
              fields.put("scenario", scenario);
              fields.put("outcome", "planned");
              fields.put("committee_count", committeeCount.longValue());
              fields.put("active_validators", experiment.activeValidators().longValue());
              fields.put(
                  "head_root",
                  recentChainData.getBestBlockRoot().map(Object::toString).orElse(null));
              fields.put("target_slot", withProposal ? anchor.plus(2).longValue() : null);
              fields.put("committee_slot", withCommittee ? committeeSlot.longValue() : null);
              fields.put("committee", withCommittee ? committee : null);
              if (withCommittee) {
                fields.put("committee_draw", draw);
              }
              PqTiming.status("experiment_planned", fields);
              if (withCommittee) {
                proveCommittee(experiment, committeeSlot, committee).finishError(LOG);
              }
              if (withProposal) {
                mergeProposal(experiment, anchor.plus(2)).finishError(LOG);
              }
            })
        .finishError(LOG);
  }

  private SafeFuture<Void> proveCommittee(
      final Experiment experiment, final UInt64 slot, final int committee) {
    final long cutoff = slotStartWallNanos(slot) + collectionCutoff(slotDuration(slot)).toNanos();
    final Experiment scoped =
        experiment.withDeadline(
            slotStartWallNanos(slot)
                + spec.atSlot(slot).getForkChoiceUtil().getAggregateDueMillis() * 1_000_000L);
    return atWallNanos(cutoff)
        .thenRun(
            () -> {
              cutoff(experiment, slot, "committee", cutoff);
              final Optional<MeasuredProof> proof =
                  PqTiming.inExperiment(
                      Optional.of(scoped),
                      1,
                      () -> attestationPool.proveCommitteeForMeasurement(slot, committee));
              if (proof.isEmpty()) {
                outcome(experiment, slot, "committee", "no_useful_inputs");
                return;
              }
              ready(scoped, slot, slot, "committee", proof.get());
            });
  }

  private SafeFuture<Void> mergeProposal(final Experiment experiment, final UInt64 target) {
    final Experiment scoped = experiment.withDeadline(slotStartWallNanos(target));
    final UInt64 before = target.decrement();
    return atWallNanos(slotStartWallNanos(before))
        .thenCompose(
            __ ->
                recentChainData
                    .getBestState()
                    .orElse(SafeFuture.failedFuture(new IllegalStateException("No head state"))))
        .thenAccept(
            headState -> {
              cutoff(experiment, target, "initial", PqTiming.nowWallNanos().orElse(0L));
              PqTiming.inExperiment(
                      Optional.of(scoped),
                      1,
                      () -> attestationPool.mergeAheadForMeasurement(target, headState))
                  .ifPresent(proof -> ready(scoped, target, experiment.id(), "initial", proof));
            })
        .thenCompose(
            __ ->
                atWallNanos(
                    slotStartWallNanos(before) + spec.getAggregateDueMillis(before) * 1_000_000L))
        .thenCompose(__ -> mergeEveryCommitteeAhead(experiment, scoped, target))
        .thenCompose(__ -> atWallNanos(slotStartWallNanos(target)))
        .thenCompose(
            __ ->
                recentChainData
                    .getBestState()
                    .orElse(SafeFuture.failedFuture(new IllegalStateException("No head state"))))
        .thenAccept(
            headState -> {
              final BeaconState blockState;
              try {
                blockState =
                    headState.getSlot().isLessThan(target)
                        ? spec.processSlots(headState, target)
                        : headState;
              } catch (final SlotProcessingException | EpochProcessingException e) {
                outcome(experiment, target, "initial", "missing_context");
                return;
              }
              PqTiming.inExperiment(
                      Optional.of(scoped),
                      1,
                      () -> attestationPool.mergeForBlockMeasurement(blockState))
                  .ifPresent(proof -> ready(scoped, target, experiment.id(), "initial", proof));
            })
        .exceptionally(
            error -> {
              outcome(experiment, target, "initial", "missing_context");
              LOG.debug("PQ timing experiment {} could not merge", experiment.id(), error);
              return null;
            });
  }

  /**
   * As the pool does ahead of a local proposal once the aggregates are due: passes of the merge of
   * every committee the block can include, while one merges something and another fits before the
   * block's slot.
   */
  private SafeFuture<Void> mergeEveryCommitteeAhead(
      final Experiment experiment, final Experiment scoped, final UInt64 target) {
    return recentChainData
        .getBestState()
        .orElse(SafeFuture.failedFuture(new IllegalStateException("No head state")))
        .thenCompose(
            headState -> {
              final long start = PqTiming.nowWallNanos().orElse(0L);
              final Optional<MeasuredProof> proof =
                  PqTiming.inExperiment(
                      Optional.of(scoped),
                      1,
                      () ->
                          attestationPool.mergeEveryCommitteeAheadForMeasurement(
                              target, headState));
              if (proof.isEmpty()) {
                return SafeFuture.COMPLETE;
              }
              ready(scoped, target, experiment.id(), "initial", proof.get());
              final long now = PqTiming.nowWallNanos().orElse(0L);
              if (now + (now - start) < slotStartWallNanos(target)) {
                return mergeEveryCommitteeAhead(experiment, scoped, target);
              }
              return SafeFuture.COMPLETE;
            });
  }

  /** Verifies an experiment's proof and records that it is ready. */
  private void ready(
      final Experiment experiment,
      final UInt64 slot,
      final UInt64 referenceSlot,
      final String work,
      final MeasuredProof proof) {
    final long start = System.nanoTime();
    final boolean valid = verifier.verifyAggregates(proof.statements(), proof.proof());
    final long verification = System.nanoTime() - start;
    if (!valid) {
      outcome(experiment, slot, work, "invalid_proof");
      return;
    }
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("proof_id", PqTiming.proofId(proof.proof()));
    fields.put("claims", proof.claims());
    fields.put("records", proof.records());
    fields.put("recursive_depth", proof.round());
    fields.put("backend_call", true);
    fields.put("verification_duration_ns", verification);
    PqTiming.inExperiment(
        Optional.of(experiment),
        Math.max(1, proof.round()),
        () -> {
          PqTiming.job(work, slot, referenceSlot).emit("candidate_ready", "ready", fields);
          return null;
        });
  }

  private void outcome(
      final Experiment experiment, final UInt64 slot, final String work, final String outcome) {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("experiment_id", experiment.id().longValue());
    fields.put("slot", slot.longValue());
    fields.put("work", work);
    fields.put("outcome", outcome);
    PqTiming.status("job_outcome", fields);
  }

  private void cutoff(
      final Experiment experiment, final UInt64 slot, final String work, final long wallNanos) {
    final Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("experiment_id", experiment.id().longValue());
    fields.put("slot", slot.longValue());
    fields.put("work", work);
    fields.put("scheduled_cutoff_wall_ns", wallNanos);
    PqTiming.status("input_cutoff", fields);
  }

  private Duration slotDuration(final UInt64 slot) {
    return Duration.ofMillis(spec.getSlotDurationMillis(slot));
  }
}
