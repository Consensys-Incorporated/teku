/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.reference.phase0.gossip;

import java.util.Optional;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;

/**
 * Maps the {@code payload_status} value from a Gloas (ePBS) gossip reference-test fixture to the
 * {@link PayloadStatus} the stubbed execution layer should return for the corresponding execution
 * payload. Shared by the block, attestation and aggregate-and-proof gossip test executors, which
 * all consume the same {@code payload}/{@code payload_status} fixture keys.
 */
enum FixturePayloadStatus {
  VALID,
  NOT_VALIDATED,
  INVALIDATED;

  public PayloadStatus toPayloadStatus() {
    return switch (this) {
      case VALID -> PayloadStatus.VALID;
      case NOT_VALIDATED -> PayloadStatus.ACCEPTED;
      case INVALIDATED ->
          PayloadStatus.invalid(Optional.empty(), Optional.of("Fixture execution invalidated"));
    };
  }
}
