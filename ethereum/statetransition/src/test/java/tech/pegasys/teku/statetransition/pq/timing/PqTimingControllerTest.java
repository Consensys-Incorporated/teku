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

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProver;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPoolPq;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

class PqTimingControllerTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private final StorageSystem storageSystem =
      InMemoryStorageSystemBuilder.create()
          .specProvider(spec)
          .storageMode(StateStorageMode.ARCHIVE)
          .numberOfValidators(64)
          .build();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final AtomicBoolean syncing = new AtomicBoolean(false);

  @TempDir Path root;

  private RecentChainData recentChainData;
  private UInt64 pqSlot;

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    pqSlot = spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH);
    final SignedBlockAndState head = storageSystem.chainUpdater().advanceChain(pqSlot.plus(1));
    storageSystem.chainUpdater().updateBestBlock(head);
    recentChainData = storageSystem.recentChainData();
  }

  @AfterEach
  void tearDown() {
    PqTiming.stop();
  }

  @Test
  void startsRecordingOnTheFirstSlotWithoutExperimentsByDefault() throws IOException {
    final PqTimingController controller = controller(false);
    assertThat(PqTiming.isEnabled()).isFalse();

    for (int i = 0; i < 6; i++) {
      controller.onSlot(pqSlot.plus(i));
    }
    asyncRunner.executeUntilDone();

    assertThat(PqTiming.isEnabled()).isTrue();
    final JsonNode manifest = PqTimingEvents.stopAndReadManifest(root);
    assertThat(manifest.get("observer").asBoolean()).isFalse();
    assertThat(manifest.get("network").asText()).isEqualTo("minimal");
    assertThat(manifest.get("prover_backend").asText()).isEqualTo("concatenation");
    assertThat(manifest.get("genesis_time").asLong())
        .isEqualTo(recentChainData.getGenesisTime().longValue());
    final JsonNode timing = manifest.get("timing");
    assertThat(timing.get("slot_ns").asLong()).isEqualTo(6_000_000_000L);
    // Teku's validator client asks for aggregates when they are due
    assertThat(timing.get("committee_collection_ns").asLong())
        .isEqualTo(timing.get("aggregate_ns").asLong());
    assertThat(timing.get("initial_merge_ns").asLong()).isEqualTo(6_000_000_000L);
    assertThat(PqTimingEvents.stopAndRead(root)).isEmpty();
  }

  @Test
  void plansAnExperimentEveryThreeSlotsRotatingScenarios() throws IOException {
    final PqTimingController controller = controller(true);

    for (int i = 0; i < 9; i++) {
      controller.onSlot(pqSlot.plus(i));
    }
    asyncRunner.executeUntilDone();

    final JsonNode manifest = PqTimingEvents.stopAndReadManifest(root);
    assertThat(manifest.get("observer").asBoolean()).isTrue();
    // the observer collects committee inputs until 40% into the slot
    assertThat(manifest.get("timing").get("committee_collection_ns").asLong())
        .isEqualTo(2_400_000_000L);
    final long phase = manifest.get("sampling").get("anchor_phase").asLong();
    final List<JsonNode> events = PqTimingEvents.stopAndRead(root);
    assertThat(events.stream().filter(event -> kind(event, "network_status"))).hasSize(1);
    final List<JsonNode> planned =
        events.stream().filter(event -> kind(event, "experiment_planned")).toList();
    assertThat(planned).hasSize(3);
    assertThat(planned)
        .allSatisfy(
            event -> {
              final long anchor = event.get("experiment_id").asLong();
              assertThat(anchor % PqTiming.STRIDE).isEqualTo(phase);
              assertThat(event.get("outcome").asText()).isEqualTo("planned");
              assertThat(event.get("scenario").asText())
                  .isEqualTo(PqTimingController.SCENARIOS[(int) (anchor / 3 % 3)]);
              assertThat(event.get("committee_count").asLong()).isPositive();
              assertThat(event.get("active_validators").asLong()).isEqualTo(64);
            });
    assertThat(planned)
        .extracting(event -> event.get("scenario").asText())
        .containsExactlyInAnyOrder("committee_only", "proposer_only", "combined");

    // With nothing in the pool, the committee experiments find no inputs
    final JsonNode committeeOnly =
        planned.stream()
            .filter(event -> event.get("scenario").asText().equals("committee_only"))
            .findFirst()
            .orElseThrow();
    assertThat(committeeOnly.get("committee_slot").asLong())
        .isEqualTo(committeeOnly.get("experiment_id").asLong());
    assertThat(committeeOnly.get("target_slot").isNull()).isTrue();
    assertThat(
            events.stream()
                .filter(event -> kind(event, "job_outcome"))
                .map(event -> event.get("outcome").asText()))
        .containsExactly("no_useful_inputs", "no_useful_inputs");
    // and the proposals have nothing to include
    final List<JsonNode> snapshots =
        events.stream().filter(event -> kind(event, "proposal_snapshot")).toList();
    assertThat(snapshots).hasSize(2);
    assertThat(snapshots)
        .allSatisfy(
            snapshot -> {
              assertThat(snapshot.get("outcome").asText()).isEqualTo("no_candidate");
              assertThat(snapshot.get("origin").asText()).isEqualTo("measurement");
              assertThat(snapshot.get("slot").asLong())
                  .isEqualTo(snapshot.get("experiment_id").asLong() + 2);
            });
    assertThat(events.stream().filter(event -> kind(event, "input_cutoff"))).hasSize(4);
  }

  @Test
  void skipsExperimentsWhileSyncing() throws IOException {
    final PqTimingController controller = controller(true);
    syncing.set(true);

    for (int i = 0; i < 3; i++) {
      controller.onSlot(pqSlot.plus(i));
    }
    asyncRunner.executeUntilDone();

    final List<JsonNode> events = PqTimingEvents.stopAndRead(root);
    assertThat(events.stream().filter(event -> kind(event, "experiment_planned")))
        .singleElement()
        .satisfies(event -> assertThat(event.get("outcome").asText()).isEqualTo("syncing"));
    assertThat(events.getFirst().get("synced").asBoolean()).isFalse();
  }

  @Test
  void skipsExperimentsBeforeTheFork() throws IOException {
    final PqTimingController controller = controller(true);

    for (int i = 0; i < 3; i++) {
      controller.onSlot(UInt64.valueOf(i));
    }
    asyncRunner.executeUntilDone();

    assertThat(
            PqTimingEvents.stopAndRead(root).stream()
                .filter(event -> kind(event, "experiment_planned")))
        .singleElement()
        .satisfies(event -> assertThat(event.get("outcome").asText()).isEqualTo("pre_pq"));
  }

  private PqTimingController controller(final boolean measure) {
    return new PqTimingController(
        spec,
        recentChainData,
        new AggregatingAttestationPoolPq(spec, recentChainData, StarkProver.CONCATENATED),
        asyncRunner,
        root,
        measure,
        "minimal",
        "teku/test",
        Optional.empty(),
        syncing::get,
        () -> 3);
  }

  private static boolean kind(final JsonNode event, final String kind) {
    return event.get("kind").asText().equals(kind);
  }
}
