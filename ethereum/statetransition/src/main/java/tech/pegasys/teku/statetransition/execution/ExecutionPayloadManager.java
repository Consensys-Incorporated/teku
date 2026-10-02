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

package tech.pegasys.teku.statetransition.execution;

import java.util.Optional;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public interface ExecutionPayloadManager {

  ExecutionPayloadManager NOOP =
      new ExecutionPayloadManager() {
        @Override
        public boolean isExecutionPayloadRecentlySeen(final Bytes32 beaconBlockRoot) {
          return false;
        }

        @Override
        public boolean isExecutionPayloadSeenBeforeDeadline(final Bytes32 beaconBlockRoot) {
          return false;
        }

        @Override
        public SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
            final SignedExecutionPayloadEnvelope signedExecutionPayload,
            final Optional<UInt64> maybeArrivalTimestamp,
            final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
          return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
        }

        @Override
        public SafeFuture<ValidateAndImportResult> validateAndImportExecutionPayloadForBroadcast(
            final SignedExecutionPayloadEnvelope signedExecutionPayload,
            final Optional<BroadcastValidationLevel> broadcastValidationLevel) {
          return SafeFuture.completedFuture(
              new ValidateAndImportResult(InternalValidationResult.ACCEPT, Optional.empty()));
        }

        @Override
        public SafeFuture<ExecutionPayloadImportResult> importExecutionPayload(
            final SignedExecutionPayloadEnvelope signedExecutionPayload,
            final boolean payloadCommitmentVerified) {
          return SafeFuture.completedFuture(
              ExecutionPayloadImportResult.successful(signedExecutionPayload));
        }

        @Override
        public SafeFuture<ExecutionRequests> getParentExecutionRequestsForBlock(
            final UInt64 slot,
            final Bytes32 parentRoot,
            final ForkChoicePayloadStatus parentPayloadStatus) {
          return SafeFuture.completedFuture(null);
        }

        @Override
        public void subscribeFailedPayloadExecution(
            final FailedPayloadExecutionSubscriber subscriber) {}

        @Override
        public void subscribeRequiredExecutionPayload(final Consumer<Bytes32> subscriber) {}
      };

  /**
   * {@link SignedExecutionPayloadEnvelope} has been recently seen referencing the block. This is
   * used for RPC fetch de-duplication.
   */
  boolean isExecutionPayloadRecentlySeen(Bytes32 beaconBlockRoot);

  /**
   * {@link SignedExecutionPayloadEnvelope} was seen before {@code get_payload_due_ms()}. This
   * method is used for the `payload_present` vote.
   */
  boolean isExecutionPayloadSeenBeforeDeadline(Bytes32 beaconBlockRoot);

  /**
   * Performs gossip validation on the {@code signedExecutionPayload} and imports it async if
   * accepted
   *
   * @return the gossip validation result
   */
  SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      SignedExecutionPayloadEnvelope signedExecutionPayload,
      Optional<UInt64> maybeArrivalTimestamp,
      Optional<BroadcastValidationLevel> broadcastValidationLevel);

  SafeFuture<ValidateAndImportResult> validateAndImportExecutionPayloadForBroadcast(
      SignedExecutionPayloadEnvelope signedExecutionPayload,
      Optional<BroadcastValidationLevel> broadcastValidationLevel);

  /**
   * Imports execution payload via fork choice `on_execution_payload`
   *
   * @param payloadCommitmentVerified whether the envelope has been proven to be the block's
   *     committed payload, i.e. its bid consistency (builder index, payload block hash, execution
   *     requests root) and builder signature were verified (as done by gossip validation). Only
   *     when {@code true} may an invalid import result be cached against the beacon block root, so
   *     that full-payload attestation gossip validation can reject votes for it. Callers that have
   *     not verified the commitment (e.g. sync or RPC-by-root imports) MUST pass {@code false}, as
   *     a forged/mismatched envelope for an honest block would otherwise poison the invalid-payload
   *     cache and cause legitimate attestations to be rejected.
   * @return the execution payload import result
   */
  SafeFuture<ExecutionPayloadImportResult> importExecutionPayload(
      SignedExecutionPayloadEnvelope signedExecutionPayload, boolean payloadCommitmentVerified);

  /** Retrieves parent execution requests (used in block production) */
  SafeFuture<ExecutionRequests> getParentExecutionRequestsForBlock(
      UInt64 slot, Bytes32 parentRoot, ForkChoicePayloadStatus parentPayloadStatus);

  default SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    return validateAndImportExecutionPayload(
        signedExecutionPayload, Optional.empty(), Optional.empty());
  }

  default SafeFuture<InternalValidationResult> validateAndImportExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload,
      final Optional<UInt64> arrivalTimestamp) {
    return validateAndImportExecutionPayload(
        signedExecutionPayload, arrivalTimestamp, Optional.empty());
  }

  void subscribeFailedPayloadExecution(final FailedPayloadExecutionSubscriber subscriber);

  /**
   * Subscribes to block roots whose pending payload was evicted before its block was imported, so
   * the payload has to be fetched by root.
   */
  void subscribeRequiredExecutionPayload(final Consumer<Bytes32> subscriber);

  interface FailedPayloadExecutionSubscriber {
    void onPayloadExecutionFailed(SignedExecutionPayloadEnvelope executionPayload);
  }

  record ValidateAndImportResult(
      InternalValidationResult validationResult,
      Optional<SafeFuture<ExecutionPayloadImportResult>> importResult) {}
}
