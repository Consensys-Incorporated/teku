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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.airlift.compress.v3.zstd.ZstdInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** Starts a PQ timing session for a test and reads back what it recorded. */
public final class PqTimingEvents {

  private PqTimingEvents() {}

  /** Starts recording to {@code root} with 12 second slots from genesis time 0. */
  public static void start(final Path root, final boolean observer) throws IOException {
    PqTiming.start(
        new PqTiming.Run(
            root,
            observer,
            "test",
            "teku/test",
            "leanvm",
            Optional.empty(),
            UInt64.ZERO,
            Bytes32.ZERO,
            Duration.ofSeconds(12),
            Duration.ofSeconds(3),
            Duration.ofSeconds(6),
            Duration.ofSeconds(6),
            Duration.ofSeconds(12),
            slot -> slot.longValue() * 12_000_000_000L));
  }

  /** Stops recording and reads the run's manifest. */
  public static JsonNode stopAndReadManifest(final Path root) throws IOException {
    PqTiming.stop();
    return new ObjectMapper().readTree(runDirectory(root).resolve("run.json").toFile());
  }

  /** Stops recording and reads every event of the run, in the order they were written. */
  public static List<JsonNode> stopAndRead(final Path root) throws IOException {
    final JsonNode manifest = stopAndReadManifest(root);
    final List<JsonNode> events = new ArrayList<>();
    for (final JsonNode chunk : manifest.get("chunks")) {
      events.addAll(readChunk(runDirectory(root).resolve(chunk.get("name").asText())));
    }
    return events;
  }

  /** Reads the events of one zstd-compressed JSON lines chunk. */
  public static List<JsonNode> readChunk(final Path chunk) throws IOException {
    final ObjectMapper json = new ObjectMapper();
    final List<JsonNode> events = new ArrayList<>();
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(
                new ZstdInputStream(Files.newInputStream(chunk)), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        events.add(json.readTree(line));
      }
    }
    return events;
  }

  private static Path runDirectory(final Path root) throws IOException {
    try (Stream<Path> runs = Files.list(root)) {
      return runs.findFirst().orElseThrow();
    }
  }
}
