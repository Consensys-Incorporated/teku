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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.statetransition.pq.timing.PqTiming.Experiment;

class PqTimingTest {

  private static final long SECOND = 1_000_000_000L;
  private static final long GENESIS_TIME = 1_700_000_000L;

  @TempDir Path root;

  private final AtomicLong wallNanos = new AtomicLong(GENESIS_TIME * SECOND);
  private final AtomicLong monoNanos = new AtomicLong();

  @AfterEach
  void tearDown() {
    PqTiming.stop();
  }

  @Test
  void isOffUntilStarted() {
    assertThat(PqTiming.isEnabled()).isFalse();
    assertThat(PqTiming.nowWallNanos()).isEmpty();

    // nothing to record to, and the work still runs
    PqTiming.receipt()
        .received(
            "individual",
            Bytes.of(1),
            UInt64.ONE,
            Optional.empty(),
            Optional.empty(),
            1,
            10,
            Bytes32.ZERO);
    assertThat(PqTiming.job("committee", UInt64.ONE, UInt64.ONE).run(Map.of(), () -> Bytes.of(7)))
        .isEqualTo(Bytes.of(7));
    PqTiming.status("network_status", Map.of());
  }

  @Test
  void writesTheRunManifest() throws IOException {
    final long seed = start(false);

    final JsonNode manifest = stopAndReadManifest();
    assertThat(manifest.get("schema_version").asInt()).isEqualTo(1);
    assertThat(manifest.get("client").asText()).isEqualTo("teku");
    assertThat(manifest.get("observer").asBoolean()).isFalse();
    assertThat(manifest.get("genesis_time").asLong()).isEqualTo(GENESIS_TIME);
    assertThat(manifest.get("genesis_slot").asLong()).isZero();
    assertThat(manifest.get("prover_backend").asText()).isEqualTo("leanvm");
    assertThat(manifest.get("prover_threads").asInt()).isEqualTo(16);
    assertThat(manifest.get("timing").get("slot_ns").asLong()).isEqualTo(12 * SECOND);
    assertThat(manifest.get("timing").get("attestation_ns").asLong()).isEqualTo(3 * SECOND);
    assertThat(manifest.get("timing").get("committee_collection_ns").asLong())
        .isEqualTo(6 * SECOND);
    assertThat(manifest.get("timing").get("aggregate_ns").asLong()).isEqualTo(6 * SECOND);
    assertThat(manifest.get("timing").get("initial_merge_ns").asLong()).isEqualTo(12 * SECOND);
    assertThat(manifest.get("sampling").get("anchor_phase").asLong())
        .isEqualTo(PqTiming.anchorPhase(seed));
    assertThat(manifest.get("run_id").asText())
        .isEqualTo(String.format("%d-%016x", GENESIS_TIME * SECOND, seed))
        .isEqualTo(runDirectory().getFileName().toString());
    assertThat(manifest.get("hardware").get("available_parallelism").asInt()).isPositive();
  }

  @Test
  void recordsAReceiptWhenStampedAndItsValidation() throws IOException {
    start(false);
    final PqTiming.Receipt receipt = PqTiming.receipt();
    wallNanos.addAndGet(SECOND);
    monoNanos.addAndGet(SECOND);

    receipt.received(
        "aggregate",
        Bytes.fromHexString("0xabcd"),
        UInt64.valueOf(5),
        Optional.of(UInt64.valueOf(3)),
        Optional.empty(),
        7,
        1234,
        Bytes32.fromHexStringLenient("0x01"));
    wallNanos.addAndGet(SECOND);
    monoNanos.addAndGet(SECOND);
    receipt.validated("accepted");

    final List<JsonNode> events = stopAndReadEvents();
    assertThat(events).hasSize(2);
    final JsonNode received = events.get(0);
    assertThat(received.get("kind").asText()).isEqualTo("message_received");
    // stamped on receipt, before decoding
    assertThat(received.get("wall_ns").asLong()).isEqualTo(GENESIS_TIME * SECOND);
    assertThat(received.get("mono_ns").asLong()).isZero();
    assertThat(received.get("message_kind").asText()).isEqualTo("aggregate");
    assertThat(received.get("message_id").asText()).isEqualTo("0xabcd");
    assertThat(received.get("source").asText()).isEqualTo("network");
    assertThat(received.get("reference_slot").asLong()).isEqualTo(5);
    assertThat(received.get("committee").asLong()).isEqualTo(3);
    assertThat(received.get("subnet").isNull()).isTrue();
    assertThat(received.get("signer_count").asInt()).isEqualTo(7);
    assertThat(received.get("message_bytes").asInt()).isEqualTo(1234);
    assertThat(received.get("vote_data_root").asText())
        .isEqualTo(Bytes32.fromHexStringLenient("0x01").toHexString());

    final JsonNode validated = events.get(1);
    assertThat(validated.get("kind").asText()).isEqualTo("validated");
    assertThat(validated.get("message_id").asText()).isEqualTo("0xabcd");
    assertThat(validated.get("outcome").asText()).isEqualTo("accepted");
    assertThat(validated.get("receipt_to_validation_ns").asLong()).isEqualTo(2 * SECOND);
  }

  @Test
  void recordsNoValidationForAMessageNeverDescribed() throws IOException {
    start(false);

    PqTiming.receipt().validated("rejected");
    PqTiming.noReceipt().validated("accepted");

    assertThat(stopAndReadEvents()).isEmpty();
  }

  @Test
  void timesProductionJobs() throws IOException {
    start(false);
    // slot 3's aggregate is due 6s into it
    wallNanos.set((GENESIS_TIME + 3 * 12 + 7) * SECOND);
    final PqTiming.Job job = PqTiming.job("committee", UInt64.valueOf(3), UInt64.valueOf(3));
    assertThat(job.isProduction()).isTrue();

    final Bytes proof =
        job.run(
            Map.of("committee", 2),
            () -> {
              monoNanos.addAndGet(70_000_000L);
              return Bytes.of(1, 2, 3);
            });

    assertThat(proof).isEqualTo(Bytes.of(1, 2, 3));
    final List<JsonNode> events = stopAndReadEvents();
    assertThat(events)
        .extracting(event -> event.get("kind").asText())
        .containsExactly("proof_started", "proof_finished");
    final JsonNode finished = events.get(1);
    assertThat(finished.get("outcome").asText()).isEqualTo("success");
    assertThat(finished.get("work").asText()).isEqualTo("committee");
    assertThat(finished.get("origin").asText()).isEqualTo("production");
    assertThat(finished.get("scenario").asText()).isEqualTo("production");
    assertThat(finished.get("sequence_id").asText()).isEqualTo("production:3");
    assertThat(finished.get("experiment_id").isNull()).isTrue();
    assertThat(finished.get("round").asInt()).isEqualTo(1);
    assertThat(finished.get("committee").asInt()).isEqualTo(2);
    assertThat(finished.get("backend_duration_ns").asLong()).isEqualTo(70_000_000L);
    assertThat(finished.get("late").asBoolean()).isTrue();
    assertThat(finished.get("proof_bytes").asInt()).isEqualTo(3);
    assertThat(finished.get("proof_id").asText())
        .isEqualTo(Hash.sha256(Bytes.of(1, 2, 3)).toHexString());
  }

  @Test
  void recordsAFailedJob() throws IOException {
    start(false);

    assertThatThrownBy(
            () ->
                PqTiming.job("initial", UInt64.valueOf(9), UInt64.valueOf(7))
                    .run(
                        Map.of(),
                        () -> {
                          throw new IllegalStateException("sidecar gone");
                        }))
        .isInstanceOf(IllegalStateException.class);

    final JsonNode finished = stopAndReadEvents().get(1);
    assertThat(finished.get("outcome").asText()).isEqualTo("failed");
    assertThat(finished.get("late").asBoolean()).isFalse();
    assertThat(finished.get("proof_id").asText()).isEmpty();
  }

  @Test
  void laterMergeRoundsAreImprovements() throws IOException {
    start(false);

    PqTiming.job("initial", UInt64.valueOf(9), UInt64.valueOf(7), 2)
        .emit("candidate_ready", "ready", Map.of());
    PqTiming.inRound(
        3,
        () -> {
          PqTiming.job("initial", UInt64.valueOf(9), UInt64.valueOf(7))
              .emit("candidate_ready", "ready", Map.of());
          return null;
        });
    PqTiming.job("initial", UInt64.valueOf(9), UInt64.valueOf(7))
        .emit("candidate_ready", "ready", Map.of());

    assertThat(stopAndReadEvents())
        .extracting(event -> event.get("work").asText() + event.get("round").asInt())
        .containsExactly("improvement2", "improvement3", "initial1");
  }

  @Test
  void attributesJobsToTheExperimentTheyRunIn() throws IOException {
    start(true);
    final Experiment experiment =
        new Experiment(UInt64.valueOf(30), "combined", UInt64.valueOf(4), UInt64.valueOf(64), 0);

    final PqTiming.Job job =
        PqTiming.inExperiment(
            Optional.of(experiment),
            1,
            () -> PqTiming.job("committee", UInt64.valueOf(31), UInt64.valueOf(31)));
    job.run(Map.of(), () -> Bytes.of(1));
    // outside the experiment again
    PqTiming.job("committee", UInt64.valueOf(31), UInt64.valueOf(31))
        .emit("candidate_ready", "ready", Map.of());

    final List<JsonNode> events = stopAndReadEvents();
    assertThat(job.isProduction()).isFalse();
    final JsonNode finished = events.get(1);
    assertThat(finished.get("origin").asText()).isEqualTo("measurement");
    assertThat(finished.get("experiment_id").asLong()).isEqualTo(30);
    assertThat(finished.get("sequence_id").asText()).isEqualTo("measurement:30");
    assertThat(finished.get("scenario").asText()).isEqualTo("combined");
    assertThat(finished.get("committee_count").asLong()).isEqualTo(4);
    assertThat(finished.get("active_validators").asLong()).isEqualTo(64);
    // placed on the anchor slot's timeline
    assertThat(finished.get("slot").asLong()).isEqualTo(31);
    assertThat(finished.get("reference_slot").asLong()).isEqualTo(30);
    assertThat(events.get(2).get("origin").asText()).isEqualTo("production");
  }

  @Test
  void proposalSnapshotsAreStatusEvents() throws IOException {
    start(false);

    PqTiming.job("initial", UInt64.valueOf(9), UInt64.valueOf(7))
        .emit("proposal_snapshot", "candidate", Map.of("records", 2));

    final JsonNode snapshot = stopAndReadEvents().getFirst();
    assertThat(snapshot.get("kind").asText()).isEqualTo("proposal_snapshot");
    assertThat(snapshot.get("outcome").asText()).isEqualTo("candidate");
    assertThat(snapshot.get("records").asInt()).isEqualTo(2);
  }

  @Test
  void proofIdIsTheProofsSha256() {
    assertThat(PqTiming.proofId(Bytes.EMPTY)).isEmpty();
    assertThat(PqTiming.proofId(Bytes.of(9))).isEqualTo(Hash.sha256(Bytes.of(9)).toHexString());
  }

  private long start(final boolean observer) throws IOException {
    return PqTiming.start(
        new PqTiming.Run(
            root,
            observer,
            "pq-devnet-0",
            "teku/test",
            "leanvm",
            Optional.of(16),
            UInt64.valueOf(GENESIS_TIME),
            Bytes32.ZERO,
            Duration.ofSeconds(12),
            Duration.ofSeconds(3),
            Duration.ofSeconds(6),
            Duration.ofSeconds(6),
            Duration.ofSeconds(12),
            slot -> (GENESIS_TIME + slot.longValue() * 12) * SECOND),
        wallNanos::get,
        monoNanos::get);
  }

  private Path runDirectory() throws IOException {
    try (Stream<Path> runs = Files.list(root)) {
      return runs.findFirst().orElseThrow();
    }
  }

  private JsonNode stopAndReadManifest() throws IOException {
    PqTiming.stop();
    return new ObjectMapper().readTree(runDirectory().resolve("run.json").toFile());
  }

  private List<JsonNode> stopAndReadEvents() throws IOException {
    final JsonNode manifest = stopAndReadManifest();
    final List<JsonNode> events = new ArrayList<>();
    for (final JsonNode chunk : manifest.get("chunks")) {
      events.addAll(PqTimingEvents.readChunk(runDirectory().resolve(chunk.get("name").asText())));
    }
    return events;
  }
}
