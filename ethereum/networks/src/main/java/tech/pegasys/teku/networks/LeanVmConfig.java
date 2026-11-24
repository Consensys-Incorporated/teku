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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;

/**
 * leanVM, which a beacon node proves and verifies PQ attestation proofs with, inside its own
 * process.
 *
 * @param threads the prover's threads; 0 is one per core
 * @param enabledBySidecarFlag whether a deprecated leanVM sidecar flag asked for leanVM, which the
 *     node warns about at startup
 */
public record LeanVmConfig(int threads, boolean enabledBySidecarFlag) {

  public static final int DEFAULT_THREADS = 0;

  /** The leanVM config of the given flags, or empty when leanVM isn't enabled. */
  public static Optional<LeanVmConfig> of(
      final boolean enabled, final int threads, final boolean enabledBySidecarFlag) {
    checkArgument(threads >= 0, "leanVM prover threads must not be negative");
    return enabled
        ? Optional.of(new LeanVmConfig(threads, enabledBySidecarFlag))
        : Optional.empty();
  }

  /** The prover's thread count on this machine. */
  public int resolvedThreads() {
    return threads == 0 ? Runtime.getRuntime().availableProcessors() : threads;
  }
}
