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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.statetransition.pq.timing.PqTimingRecorder.EventClass;

class PqTimingRecorderTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @TempDir Path root;

  private final AtomicLong wallNanos = new AtomicLong(1_700_000_000_000_000_000L);
  private final AtomicLong monoNanos = new AtomicLong(5_000);

  @Test
  void writesTheManifestAndEventsInChunks() throws IOException {
    final PqTimingRecorder recorder =
        open(PqTimingRecorder.QUEUE_SIZE, PqTimingRecorder.ROTATE_BYTES);
    assertThat(readManifest(recorder).get("chunks")).isEmpty();
    assertThat(readManifest(recorder).get("schema_version").asInt()).isEqualTo(1);

    monoNanos.addAndGet(250);
    recorder.emit(EventClass.STATUS, "network_status", Map.of("synced", true));
    recorder.emit(EventClass.RECEIPT, "message_received", 42, 5_100, Map.of("slot", 9));
    recorder.close();

    final JsonNode manifest = readManifest(recorder);
    assertThat(manifest.get("chunks")).hasSize(1);
    final JsonNode chunk = manifest.get("chunks").get(0);
    assertThat(chunk.get("name").asText()).isEqualTo("events-000001.jsonl.zst");
    assertThat(chunk.get("first").asLong()).isEqualTo(1);
    assertThat(chunk.get("last").asLong()).isEqualTo(2);
    assertThat(manifest.get("ended_unix_ns").asLong()).isEqualTo(wallNanos.get());
    assertThat(manifest.get("recorder_status").get("submitted_sequence").asLong()).isEqualTo(2);
    assertThat(manifest.get("recorder_status").get("disk_failed").asBoolean()).isFalse();

    final List<JsonNode> events = readEvents(recorder, chunk.get("name").asText());
    assertThat(events).hasSize(2);
    assertThat(events.get(0).get("kind").asText()).isEqualTo("network_status");
    assertThat(events.get(0).get("seq").asLong()).isEqualTo(1);
    assertThat(events.get(0).get("synced").asBoolean()).isTrue();
    assertThat(events.get(0).get("wall_ns").asLong()).isEqualTo(wallNanos.get());
    // relative to when the recorder opened
    assertThat(events.get(0).get("mono_ns").asLong()).isEqualTo(250);
    assertThat(events.get(1).get("wall_ns").asLong()).isEqualTo(42);
    assertThat(events.get(1).get("mono_ns").asLong()).isEqualTo(100);
    try (Stream<Path> files = Files.list(recorder.getDirectory())) {
      assertThat(files.map(path -> path.getFileName().toString()))
          .containsExactlyInAnyOrder("run.json", "events-000001.jsonl.zst");
    }
  }

  @Test
  void openChunkCanBeReadAfterEachStatus() throws IOException {
    final PqTimingRecorder recorder =
        open(PqTimingRecorder.QUEUE_SIZE, PqTimingRecorder.ROTATE_BYTES);
    final Path openChunk = recorder.getDirectory().resolve("events-000001.jsonl.partial");

    recorder.emit(EventClass.RECEIPT, "message_received", Map.of("slot", 9));
    // the writer has taken the event, but no status is due
    Waiter.waitFor(() -> assertThat(openChunk).exists());
    assertThat(openChunk).isEmptyFile();

    monoNanos.addAndGet(PqTimingRecorder.STATUS_NANOS);
    recorder.emit(EventClass.VALIDATION, "validated", Map.of("outcome", "accepted"));

    Waiter.waitFor(
        () ->
            assertThat(Files.readAllLines(openChunk))
                .map(line -> JSON.readTree(line).get("kind").asText())
                .containsExactly("message_received", "validated"));
    assertThat(readManifest(recorder).get("chunks")).isEmpty();

    recorder.close();
    assertThat(openChunk).doesNotExist();
    assertThat(readEvents(recorder, "events-000001.jsonl.zst")).hasSize(2);
  }

  @Test
  void rotatesChunksBySize() throws IOException {
    final PqTimingRecorder recorder = open(PqTimingRecorder.QUEUE_SIZE, 1);

    recorder.emit(EventClass.WORK, "proof_started", Map.of());
    recorder.emit(EventClass.WORK, "proof_finished", Map.of());
    recorder.close();

    final JsonNode chunks = readManifest(recorder).get("chunks");
    assertThat(chunks).hasSize(2);
    assertThat(readEvents(recorder, chunks.get(1).get("name").asText()))
        .singleElement()
        .satisfies(event -> assertThat(event.get("kind").asText()).isEqualTo("proof_finished"));
  }

  @Test
  void countsEventsItCanNotQueue() throws IOException {
    final PqTimingRecorder recorder =
        open(PqTimingRecorder.QUEUE_SIZE, PqTimingRecorder.ROTATE_BYTES);
    recorder.close();

    recorder.emit(EventClass.VALIDATION, "validated", Map.of());

    assertThat(recorder.getDropped(EventClass.VALIDATION)).isEqualTo(1);
    assertThat(recorder.getDropped(EventClass.RECEIPT)).isZero();
  }

  @Test
  void closingTwiceIsHarmless() throws IOException {
    final PqTimingRecorder recorder =
        open(PqTimingRecorder.QUEUE_SIZE, PqTimingRecorder.ROTATE_BYTES);
    recorder.close();
    recorder.close();

    assertThat(readManifest(recorder).has("ended_unix_ns")).isTrue();
  }

  private PqTimingRecorder open(final int queueSize, final long rotateBytes) throws IOException {
    return PqTimingRecorder.open(
        root,
        "run-1",
        Map.of("schema_version", 1, "run_id", "run-1"),
        wallNanos::get,
        monoNanos::get,
        queueSize,
        rotateBytes);
  }

  private JsonNode readManifest(final PqTimingRecorder recorder) throws IOException {
    return JSON.readTree(recorder.getDirectory().resolve("run.json").toFile());
  }

  private static List<JsonNode> readEvents(final PqTimingRecorder recorder, final String chunk)
      throws IOException {
    return PqTimingEvents.readChunk(recorder.getDirectory().resolve(chunk));
  }
}
