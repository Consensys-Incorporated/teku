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
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.pq.timing.PqTimingRecorder.EventClass;

/**
 * Local PQ gossip and proving timings, exported in the format of the Lighthouse pq-measurement
 * reports so their renderer (scripts/pq-measurement/plot.py) draws Teku runs too.
 *
 * <p>Off unless {@link #start(Run)} is called, in which case every method is a cheap no-op. The
 * session is process-wide, like the STARK backend, because the events come from gossip handlers,
 * the attestation pool and block production.
 */
public final class PqTiming {

  private static final Logger LOG = LogManager.getLogger();

  /** Observer experiments are planned every {@code STRIDE} slots. */
  public static final int STRIDE = 3;

  public static final String RECEIVE_POINT =
      "gossip topic handler entry, after the gossipsub seen cache and before Snappy and SSZ decode";

  private static volatile Session session;

  private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

  private PqTiming() {}

  /** What the run manifest records about this node and its slot timing. */
  public record Run(
      Path output,
      boolean observer,
      String network,
      String version,
      String backend,
      Optional<Integer> proverThreads,
      UInt64 genesisTime,
      Bytes32 genesisValidatorsRoot,
      Duration slot,
      Duration attestationDue,
      Duration committeeCollection,
      Duration aggregateDue,
      Duration initialMerge,
      Function<UInt64, Long> slotStartWallNanos) {}

  /** An observer experiment that proving and merging jobs are attributed to. */
  public record Experiment(
      UInt64 id,
      String scenario,
      UInt64 committeeCount,
      UInt64 activeValidators,
      long deadlineWallNanos) {

    public Experiment withDeadline(final long deadlineWallNanos) {
      return new Experiment(id, scenario, committeeCount, activeValidators, deadlineWallNanos);
    }

    public String sequenceId() {
      return "measurement:" + id;
    }
  }

  private record Scope(Experiment experiment, int round) {}

  /**
   * Starts recording, returning the run's sampling seed.
   *
   * @throws IOException if the run directory or its manifest can't be written
   */
  public static long start(final Run run) throws IOException {
    return start(run, PqTiming::wallClockNanos, System::nanoTime);
  }

  static synchronized long start(
      final Run run, final LongSupplier wallNanos, final LongSupplier monoNanos)
      throws IOException {
    stop();
    final long seed = ThreadLocalRandom.current().nextLong();
    final long startedNanos = wallNanos.getAsLong();
    final String runId = String.format("%d-%016x", startedNanos, seed);
    final PqTimingRecorder recorder =
        PqTimingRecorder.open(
            run.output(), runId, manifest(run, runId, startedNanos, seed), wallNanos, monoNanos);
    session = new Session(recorder, run);
    LOG.info("Recording PQ timing locally in {}", recorder.getDirectory());
    return seed;
  }

  /** Finishes the last chunk and the manifest. */
  public static synchronized void stop() {
    final Session current = session;
    session = null;
    if (current != null) {
      current.recorder.close();
    }
  }

  public static boolean isEnabled() {
    final Session current = session;
    return current != null && !current.recorder.isFailed();
  }

  /** The anchor phase of the observer cadence for a sampling seed. */
  public static long anchorPhase(final long seed) {
    return Long.remainderUnsigned(seed, STRIDE);
  }

  public static void status(final String kind, final Map<String, Object> fields) {
    final Session current = session;
    if (current != null) {
      current.recorder.emit(EventClass.STATUS, kind, fields);
    }
  }

  public static Optional<Long> nowWallNanos() {
    return Optional.ofNullable(session).map(current -> current.recorder.nowWallNanos());
  }

  /** The proof id the other clients record: the SHA-256 of the proof bytes. */
  public static String proofId(final Bytes proof) {
    return proof.isEmpty() ? "" : Hash.sha256(proof).toHexString();
  }

  /** A receipt that records nothing, for messages the export doesn't cover. */
  public static Receipt noReceipt() {
    return Receipt.NOOP;
  }

  /** Stamps a gossip message as received now. Its fields are added once it is decoded. */
  public static Receipt receipt() {
    final Session current = session;
    if (current == null || current.recorder.isFailed()) {
      return Receipt.NOOP;
    }
    return new Receipt(
        current, current.recorder.nowWallNanos(), current.recorder.nowMonoNanos(), null);
  }

  /**
   * A proving or merging job. Jobs started while {@link #inExperiment} runs belong to that
   * experiment and are placed on its anchor slot's timeline.
   */
  public static Job job(final String work, final UInt64 slot, final UInt64 referenceSlot) {
    return job(work, slot, referenceSlot, 0);
  }

  /**
   * A job in merge round {@code round}: work "initial" in a later round is recorded as an
   * improvement. A round of 0 takes the round of the enclosing {@link #inRound}, or 1.
   */
  public static Job job(
      final String work, final UInt64 slot, final UInt64 referenceSlot, final int round) {
    final Session current = session;
    if (current == null || current.recorder.isFailed()) {
      return Job.NOOP;
    }
    final Scope scope = SCOPE.get();
    final Optional<Experiment> experiment = Optional.ofNullable(scope).map(Scope::experiment);
    final int jobRound = round > 0 ? round : scope == null ? 1 : Math.max(1, scope.round());
    return new Job(
        current,
        current.nextJob.incrementAndGet(),
        work.equals("initial") && jobRound > 1 ? "improvement" : work,
        slot,
        experiment.map(Experiment::id).orElse(referenceSlot),
        jobRound,
        experiment,
        current.recorder.nowWallNanos());
  }

  /**
   * Runs {@code action} with the jobs it starts on this thread attributed to {@code experiment}.
   */
  public static <T> T inExperiment(
      final Optional<Experiment> experiment, final int round, final Supplier<T> action) {
    final Scope previous = SCOPE.get();
    SCOPE.set(experiment.map(e -> new Scope(e, round)).orElse(previous));
    try {
      return action.get();
    } finally {
      SCOPE.set(previous);
    }
  }

  /**
   * Runs {@code action} with the jobs it starts on this thread placed in merge round {@code round}.
   */
  public static <T> T inRound(final int round, final Supplier<T> action) {
    final Scope previous = SCOPE.get();
    SCOPE.set(new Scope(previous == null ? null : previous.experiment(), round));
    try {
      return action.get();
    } finally {
      SCOPE.set(previous);
    }
  }

  private static long wallClockNanos() {
    final Instant now = Instant.now();
    return now.getEpochSecond() * 1_000_000_000L + now.getNano();
  }

  private static Map<String, Object> manifest(
      final Run run, final String runId, final long startedNanos, final long seed) {
    final Map<String, Object> timing = new LinkedHashMap<>();
    timing.put("slot_ns", run.slot().toNanos());
    timing.put("attestation_ns", run.attestationDue().toNanos());
    timing.put("committee_collection_ns", run.committeeCollection().toNanos());
    timing.put("aggregate_ns", run.aggregateDue().toNanos());
    timing.put("initial_merge_ns", run.initialMerge().toNanos());
    timing.put("proposal_lag_slots", 2);
    final Map<String, Object> sampling = new LinkedHashMap<>();
    sampling.put("seed", new BigInteger(Long.toUnsignedString(seed)));
    sampling.put("anchor_phase", anchorPhase(seed));
    sampling.put("stride", STRIDE);
    final Map<String, Object> manifest = new LinkedHashMap<>();
    manifest.put("schema_version", 1);
    manifest.put("run_id", runId);
    manifest.put("started_unix_ns", startedNanos);
    manifest.put("observer", run.observer());
    manifest.put("network", run.network());
    manifest.put("genesis_time", run.genesisTime().longValue());
    manifest.put("genesis_slot", 0);
    manifest.put("genesis_validators_root", run.genesisValidatorsRoot().toHexString());
    manifest.put("client", "teku");
    manifest.put("teku_version", run.version());
    manifest.put("prover_backend", run.backend());
    manifest.put("prover_cpu_profile", null);
    manifest.put("prover_threads", run.proverThreads().orElse(null));
    manifest.put("prover_threads_configured", run.proverThreads().orElse(null));
    manifest.put("hardware", PqTimingHardware.describe());
    manifest.put("timing", timing);
    manifest.put("sampling", sampling);
    manifest.put("receive_point", RECEIVE_POINT);
    return manifest;
  }

  private static final class Session {
    private final PqTimingRecorder recorder;
    private final Run run;
    private final AtomicLong nextJob = new AtomicLong();

    private Session(final PqTimingRecorder recorder, final Run run) {
      this.recorder = recorder;
      this.run = run;
    }

    private long slotStartWallNanos(final UInt64 slot) {
      return run.slotStartWallNanos().apply(slot);
    }
  }

  /** A gossip message's first receipt and, once validated, its outcome. */
  public static final class Receipt {
    static final Receipt NOOP = new Receipt(null, 0, 0, null);

    private final Session session;
    private final long wallNanos;
    private final long monoNanos;
    private Described described;

    private Receipt(
        final Session session,
        final long wallNanos,
        final long monoNanos,
        final Described described) {
      this.session = session;
      this.wallNanos = wallNanos;
      this.monoNanos = monoNanos;
      this.described = described;
    }

    private record Described(String kind, String messageId, UInt64 slot) {}

    /**
     * Records the receipt of a decoded attestation ({@code kind} "individual") or aggregate
     * ("aggregate").
     */
    public void received(
        final String kind,
        final Bytes messageId,
        final UInt64 slot,
        final Optional<UInt64> committee,
        final Optional<Integer> subnet,
        final int signers,
        final int messageBytes,
        final Bytes32 voteDataRoot) {
      if (session == null) {
        return;
      }
      described = new Described(kind, messageId.toHexString(), slot);
      final Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("message_kind", kind);
      fields.put("message_id", described.messageId());
      fields.put("source", "network");
      fields.put("reference_slot", slot.longValue());
      fields.put("slot", slot.longValue());
      fields.put("committee", committee.map(UInt64::longValue).orElse(null));
      fields.put("subnet", subnet.orElse(null));
      fields.put("message_bytes", messageBytes);
      fields.put("byte_basis", "decoded_ssz");
      fields.put("signer_count", signers);
      fields.put("vote_data_root", voteDataRoot.toHexString());
      session.recorder.emit(EventClass.RECEIPT, "message_received", wallNanos, monoNanos, fields);
    }

    /**
     * Records the validation outcome: accepted, rejected, ignored or deferred. Nothing is recorded
     * for a message that was never described.
     */
    public void validated(final String outcome) {
      if (session == null || described == null) {
        return;
      }
      final long now = session.recorder.nowMonoNanos();
      final Map<String, Object> fields = new LinkedHashMap<>();
      fields.put("message_kind", described.kind());
      fields.put("message_id", described.messageId());
      fields.put("source", "network");
      fields.put("reference_slot", described.slot().longValue());
      fields.put("slot", described.slot().longValue());
      fields.put("outcome", outcome);
      fields.put("receipt_to_validation_ns", now - monoNanos);
      session.recorder.emit(
          EventClass.VALIDATION, "validated", session.recorder.nowWallNanos(), now, fields);
    }
  }

  /** A proving or merging job, recorded as proof_started and proof_finished. */
  public static final class Job {
    static final Job NOOP = new Job(null, 0, "", UInt64.ZERO, UInt64.ZERO, 1, Optional.empty(), 0);

    private final Session session;
    private final long id;
    private final String work;
    private final UInt64 slot;
    private final UInt64 referenceSlot;
    private final int round;
    private final Optional<Experiment> experiment;
    private final long enqueuedWallNanos;

    private Job(
        final Session session,
        final long id,
        final String work,
        final UInt64 slot,
        final UInt64 referenceSlot,
        final int round,
        final Optional<Experiment> experiment,
        final long enqueuedWallNanos) {
      this.session = session;
      this.id = id;
      this.work = work;
      this.slot = slot;
      this.referenceSlot = referenceSlot;
      this.round = round;
      this.experiment = experiment;
      this.enqueuedWallNanos = enqueuedWallNanos;
    }

    public boolean isProduction() {
      return session != null && experiment.isEmpty();
    }

    /** Times {@code backend}, recording proof_started and proof_finished around it. */
    public Bytes run(final Map<String, Object> fields, final Supplier<Bytes> backend) {
      if (session == null) {
        return backend.get();
      }
      final Map<String, Object> started = new LinkedHashMap<>();
      started.put("queue_wait_ns", session.recorder.nowWallNanos() - enqueuedWallNanos);
      started.putAll(fields);
      emit("proof_started", "started", started);
      final long start = session.recorder.nowMonoNanos();
      Bytes proof = Bytes.EMPTY;
      String outcome = "failed";
      try {
        proof = backend.get();
        outcome = "success";
        return proof;
      } finally {
        final long elapsed = session.recorder.nowMonoNanos() - start;
        final long deadline = deadlineWallNanos();
        final Map<String, Object> finished = new LinkedHashMap<>();
        finished.put("backend_duration_ns", elapsed);
        finished.put("late", deadline != 0 && session.recorder.nowWallNanos() >= deadline);
        finished.put("proof_bytes", proof.size());
        finished.put("proof_id", proofId(proof));
        finished.putAll(fields);
        emit("proof_finished", outcome, finished);
      }
    }

    public void emit(final String kind, final String outcome, final Map<String, Object> fields) {
      if (session == null) {
        return;
      }
      final Map<String, Object> event = new LinkedHashMap<>(fields);
      event.put("outcome", outcome);
      event.put("job_id", id);
      event.put("work", work);
      event.put("slot", slot.longValue());
      event.put("reference_slot", referenceSlot.longValue());
      event.put("round", round);
      event.put("previous_recursive_depth", 0);
      event.put("enqueued_wall_ns", enqueuedWallNanos);
      if (experiment.isPresent()) {
        event.put("origin", "measurement");
        event.put("experiment_id", experiment.get().id().longValue());
        event.put("sequence_id", experiment.get().sequenceId());
        event.put("scenario", experiment.get().scenario());
        event.put("committee_count", experiment.get().committeeCount().longValue());
        event.put("active_validators", experiment.get().activeValidators().longValue());
      } else {
        event.put("origin", "production");
        event.put("experiment_id", null);
        event.put("sequence_id", "production:" + slot);
        event.put("scenario", "production");
      }
      session.recorder.emit(
          kind.equals("proposal_snapshot") ? EventClass.STATUS : EventClass.WORK, kind, event);
    }

    private long deadlineWallNanos() {
      if (experiment.isPresent()) {
        return experiment.get().deadlineWallNanos();
      }
      if (!work.equals("committee")) {
        return 0;
      }
      return session.slotStartWallNanos(slot) + session.run.aggregateDue().toNanos();
    }
  }
}
