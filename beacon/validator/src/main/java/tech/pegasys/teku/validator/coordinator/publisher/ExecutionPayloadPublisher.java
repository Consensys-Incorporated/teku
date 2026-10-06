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

package tech.pegasys.teku.validator.coordinator.publisher;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeContents;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.validator.api.PublishSignedExecutionPayloadResult;

/** Used to publish execution payload and data column sidecars */
public interface ExecutionPayloadPublisher {

  ExecutionPayloadPublisher NOOP =
      new ExecutionPayloadPublisher() {
        @Override
        public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
            final SignedExecutionPayloadEnvelope signedExecutionPayload,
            final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
          return SafeFuture.completedFuture(
              PublishSignedExecutionPayloadResult.success(
                  signedExecutionPayload.getBeaconBlockRoot()));
        }

        @Override
        public SafeFuture<PublishSignedExecutionPayloadResult> publishSignedExecutionPayload(
            final SignedExecutionPayloadEnvelopeContents signedExecutionPayloadEnvelopeContents,
            final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
          return SafeFuture.completedFuture(
              PublishSignedExecutionPayloadResult.success(
                  signedExecutionPayloadEnvelopeContents.getBeaconBlockRoot()));
        }
      };

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
