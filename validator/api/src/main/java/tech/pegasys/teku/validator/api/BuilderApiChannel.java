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

package tech.pegasys.teku.validator.api;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/builder.md#builder-activities">Builder
 * activities</a>
 */
public interface BuilderApiChannel {

  SafeFuture<Optional<ExecutionPayloadBid>> createUnsignedExecutionPayloadBid(
      UInt64 slot, UInt64 builderIndex);

  SafeFuture<Void> publishSignedExecutionPayloadBid(
      SignedExecutionPayloadBid signedExecutionPayloadBid);

  SafeFuture<Optional<ExecutionPayloadEnvelope>> createUnsignedExecutionPayload(
      UInt64 slot, Bytes32 beaconBlockRoot);

  default SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    return publishSignedExecutionPayload(signedExecutionPayload, Optional.empty());
  }

  SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      SignedExecutionPayloadEnvelope signedExecutionPayload,
      Optional<BroadcastValidationLevel> broadcastValidationLevel);

  SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
      SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
      Optional<BroadcastValidationLevel> broadcastValidationLevel);
}
