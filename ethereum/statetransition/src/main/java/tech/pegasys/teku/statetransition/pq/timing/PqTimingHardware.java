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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The hardware section of the run manifest, read from procfs and cgroupfs where available. */
final class PqTimingHardware {

  private static final Splitter WORDS =
      Splitter.on(CharMatcher.whitespace()).trimResults().omitEmptyStrings();
  private static final Path CGROUP_ROOT = Path.of("/sys/fs/cgroup");

  private PqTimingHardware() {}

  static Map<String, Object> describe() {
    final Map<String, Object> hardware = new LinkedHashMap<>();
    hardware.put("architecture", System.getProperty("os.arch"));
    hardware.put("os", System.getProperty("os.name"));
    hardware.put(
        "kernel", read(Path.of("/proc/sys/kernel/osrelease")).map(String::trim).orElse(null));
    hardware.put(
        "cpu_model",
        procField(Path.of("/proc/cpuinfo"), "model name")
            .or(() -> procField(Path.of("/proc/cpuinfo"), "Hardware"))
            .orElse(null));
    hardware.put("available_parallelism", Runtime.getRuntime().availableProcessors());
    hardware.put(
        "cpu_affinity", procField(Path.of("/proc/self/status"), "Cpus_allowed_list").orElse(null));
    final Optional<Path> cgroup = ownCgroup();
    hardware.put("cgroup_cpu_equivalents", cgroup.flatMap(PqTimingHardware::cpuQuota).orElse(null));
    hardware.put(
        "visible_memory_bytes",
        procField(Path.of("/proc/meminfo"), "MemTotal")
            .map(total -> Long.parseLong(WORDS.split(total).iterator().next()) * 1024)
            .orElse(null));
    hardware.put("cgroup_memory_bytes", cgroup.flatMap(PqTimingHardware::memoryLimit).orElse(null));
    return hardware;
  }

  static Optional<String> procField(final Path file, final String key) {
    return read(file)
        .flatMap(
            content ->
                content
                    .lines()
                    .map(line -> line.split(":", 2))
                    .filter(parts -> parts.length == 2 && parts[0].trim().equals(key))
                    .map(parts -> parts[1].trim())
                    .findFirst());
  }

  private static Optional<Path> ownCgroup() {
    return read(Path.of("/proc/self/cgroup"))
        .flatMap(
            content ->
                content
                    .lines()
                    .filter(line -> line.startsWith("0::/"))
                    .map(line -> CGROUP_ROOT.resolve(line.substring("0::/".length())))
                    .filter(Files::isDirectory)
                    .findFirst())
        .or(() -> Optional.of(CGROUP_ROOT).filter(Files::isDirectory));
  }

  /** The smallest cpu.max quota from the process's cgroup up to the root, in CPUs. */
  private static Optional<Double> cpuQuota(final Path cgroup) {
    Optional<Double> quota = Optional.empty();
    for (Path dir = cgroup; dir != null && dir.startsWith(CGROUP_ROOT); dir = dir.getParent()) {
      final Optional<Double> limit =
          read(dir.resolve("cpu.max"))
              .map(WORDS::splitToList)
              .filter(parts -> parts.size() == 2 && !parts.get(0).equals("max"))
              .map(parts -> Double.parseDouble(parts.get(0)) / Double.parseDouble(parts.get(1)));
      if (limit.isPresent() && (quota.isEmpty() || limit.get() < quota.get())) {
        quota = limit;
      }
    }
    return quota;
  }

  private static Optional<Long> memoryLimit(final Path cgroup) {
    Optional<Long> memory = Optional.empty();
    for (Path dir = cgroup; dir != null && dir.startsWith(CGROUP_ROOT); dir = dir.getParent()) {
      final Optional<Long> limit =
          read(dir.resolve("memory.max"))
              .map(String::trim)
              .filter(content -> content.matches("\\d+"))
              .map(Long::parseLong);
      if (limit.isPresent() && (memory.isEmpty() || limit.get() < memory.get())) {
        memory = limit;
      }
    }
    return memory;
  }

  private static Optional<String> read(final Path file) {
    try {
      return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
    } catch (final IOException | SecurityException e) {
      return Optional.empty();
    }
  }
}
