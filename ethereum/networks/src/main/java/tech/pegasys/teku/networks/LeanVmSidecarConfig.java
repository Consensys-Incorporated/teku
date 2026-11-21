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
 * The leanVM sidecar ({@code leanvm-sidecar}) a beacon node proves and verifies PQ attestation
 * proofs with.
 *
 * @param binary when present, the node launches this executable itself and stops it on shutdown
 * @param socket the Unix socket the sidecar listens on; when absent the node picks one in its data
 *     directory, which needs {@code binary}
 */
public record LeanVmSidecarConfig(Optional<Path> binary, Optional<Path> socket) {

  /** The sidecar config of the given flags, or empty when neither is set. */
  public static Optional<LeanVmSidecarConfig> of(
      final Optional<Path> binary, final Optional<Path> socket) {
    if (binary.isEmpty() && socket.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new LeanVmSidecarConfig(binary, socket));
  }
}
