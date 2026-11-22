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

package tech.pegasys.teku.networks;

import java.nio.file.Path;
import java.util.Optional;

/**
 * The PQ timing export, in the format of the other PQ devnet clients' {@code --pq-timing-output}.
 *
 * @param output the directory runs are written to; when absent, pq-timing in the beacon node's data
 *     directory
 * @param measurement observer mode: join every attestation subnet and run proving and merging
 *     experiments from live gossip, as {@code --pq-measurement} does
 */
public record PqTimingConfig(Optional<Path> output, boolean measurement) {

  /** The timing config of the given flags, or empty when neither is set. */
  public static Optional<PqTimingConfig> of(
      final Optional<Path> output, final boolean measurement) {
    if (output.isEmpty() && !measurement) {
      return Optional.empty();
    }
    return Optional.of(new PqTimingConfig(output, measurement));
  }
}
