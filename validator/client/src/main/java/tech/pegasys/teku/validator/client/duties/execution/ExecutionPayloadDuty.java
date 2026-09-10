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

package tech.pegasys.teku.validator.client.duties.execution;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.Duty;

/**
 * This duty doesn't implement the {@link Duty} interface, because it is run adhoc and triggered by
 * bid events coming from {@link ExecutionPayloadBidEventsChannel}
 */
public class ExecutionPayloadDuty implements ExecutionPayloadBidEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final AsyncRunner asyncRunner;
  private final ValidatorApiChannel validatorApiChannel;
  private final ValidatorLogger validatorLogger;

  public ExecutionPayloadDuty(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final ValidatorLogger validatorLogger) {
    this.spec = spec;
    this.asyncRunner = asyncRunner;
    this.validatorApiChannel = validatorApiChannel;
    this.validatorLogger = validatorLogger;
  }

  @Override
  public void onSelfBuiltBidIncludedInBlock(
      final Validator validator,
      final ForkInfo forkInfo,
      final ExecutionPayloadBid bid,
      final Bytes32 beaconBlockRoot) {
    // execution payload is produced and broadcast
    asyncRunner
        .runAsync(
            () ->
                performExecutionPayloadDuty(
                    validator, forkInfo, bid.getSlot(), bid.getBuilderIndex(), beaconBlockRoot))
        .finishStackTrace();
  }

  private void performExecutionPayloadDuty(
      final Validator validator,
      final ForkInfo forkInfo,
      final UInt64 slot,
      final UInt64 builderIndex,
      final Bytes32 beaconBlockRoot) {
    validatorApiChannel
        .createUnsignedExecutionPayload(slot, beaconBlockRoot)
        .thenApply(
            executionPayload ->
                executionPayload.orElseThrow(
                    () ->
                        new IllegalStateException(
                            "Node was not syncing but could not create execution payload")))
        .thenCompose(
            executionPayload -> signExecutionPayload(validator, executionPayload, forkInfo))
        .thenCompose(this::publishSignedExecutionPayload)
        .finish(error -> validatorLogger.executionPayloadDutyFailed(slot, builderIndex, error));
  }

  private SafeFuture<SignedExecutionPayloadEnvelope> signExecutionPayload(
      final Validator validator,
      final ExecutionPayloadEnvelope executionPayload,
      final ForkInfo forkInfo) {
    return validator
        .getSigner()
        .signExecutionPayloadEnvelope(executionPayload, forkInfo)
        .thenApply(
            signature ->
                SchemaDefinitionsGloas.required(
                        spec.atSlot(executionPayload.getSlot()).getSchemaDefinitions())
                    .getSignedExecutionPayloadEnvelopeSchema()
                    .create(executionPayload, signature));
  }

  private SafeFuture<Void> publishSignedExecutionPayload(
      final SignedExecutionPayloadEnvelope signedExecutionPayload) {
    return validatorApiChannel
        .publishSignedExecutionPayload(signedExecutionPayload)
        .thenAccept(
            result -> {
              final ExecutionPayloadEnvelope executionPayload = signedExecutionPayload.getMessage();
              if (result.isPublished()) {
                validatorLogger.logExecutionPayloadDuty(
                    executionPayload.getSlot(),
                    executionPayload.getBuilderIndex(),
                    executionPayload.getBeaconBlockRoot(),
                    getExecutionSummary(executionPayload));
              } else {
                final Throwable error =
                    new IllegalArgumentException(
                        "Execution payload was rejected by the beacon node: "
                            + result.getRejectionReason().orElse("<reason unknown>"));
                validatorLogger.executionPayloadDutyFailed(
                    executionPayload.getSlot(), executionPayload.getBuilderIndex(), error);
              }
            });
  }

  private String getExecutionSummary(final ExecutionPayloadEnvelope executionPayload) {
    final ExecutionPayload payload = executionPayload.getPayload();
    return String.format(
        "%s (%s%%) gas, EL block: %s (%s)",
        payload.getGasUsed(),
        payload.computeGasPercentage(LOG),
        payload.getBlockHash().toUnprefixedHexString(),
        payload.getBlockNumber());
  }
}
