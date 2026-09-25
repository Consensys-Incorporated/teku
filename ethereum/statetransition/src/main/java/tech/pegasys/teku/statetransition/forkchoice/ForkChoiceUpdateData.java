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

package tech.pegasys.teku.statetransition.forkchoice;

import com.google.common.base.MoreObjects;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes8;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadContext;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceState;
import tech.pegasys.teku.spec.executionlayer.ForkChoiceUpdatedResult;
import tech.pegasys.teku.spec.executionlayer.PayloadBuildingAttributes;

public class ForkChoiceUpdateData {
  static final UInt64 RESEND_AFTER_MILLIS = UInt64.valueOf(30_000);
  private static final Logger LOG = LogManager.getLogger();
  private static final ForkChoiceState DEFAULT_FORK_CHOICE_STATE =
      new ForkChoiceState(
          new ForkChoiceNode(Bytes32.ZERO, ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING),
          UInt64.ZERO,
          UInt64.ZERO,
          Bytes32.ZERO,
          Bytes32.ZERO,
          Bytes32.ZERO,
          false);

  private final ForkChoiceState forkChoiceState;
  private final Optional<PayloadBuildingAttributes> payloadBuildingAttributes;
  private final Optional<Bytes32> terminalBlockHash;
  private final SafeFuture<Optional<ExecutionPayloadContext>> executionPayloadContext =
      new SafeFuture<>();
  private UInt64 toBeSentAtTime = UInt64.ZERO;

  public ForkChoiceUpdateData() {
    this.forkChoiceState = DEFAULT_FORK_CHOICE_STATE;
    this.payloadBuildingAttributes = Optional.empty();
    this.terminalBlockHash = Optional.empty();
  }

  public ForkChoiceUpdateData(
      final ForkChoiceState forkChoiceState,
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes,
      final Optional<Bytes32> terminalBlockHash) {
    if (terminalBlockHash.isPresent() && forkChoiceState.headExecutionBlockHash().isZero()) {
      this.forkChoiceState =
          new ForkChoiceState(
              forkChoiceState.headBlock(),
              forkChoiceState.headBlockSlot(),
              // We don't have data for terminal block number
              UInt64.ZERO,
              terminalBlockHash.get(),
              Bytes32.ZERO,
              Bytes32.ZERO,
              false);
    } else {
      this.forkChoiceState = forkChoiceState;
    }
    this.payloadBuildingAttributes = payloadBuildingAttributes;
    this.terminalBlockHash = terminalBlockHash;
  }

  public ForkChoiceUpdateData withForkChoiceState(final ForkChoiceState forkChoiceState) {
    if (this.forkChoiceState.equals(forkChoiceState)) {
      return this;
    }
    return new ForkChoiceUpdateData(forkChoiceState, Optional.empty(), terminalBlockHash);
  }

  public ForkChoiceUpdateData withFreshForkChoiceState(final ForkChoiceState forkChoiceState) {
    return new ForkChoiceUpdateData(forkChoiceState, Optional.empty(), terminalBlockHash);
  }

  /**
   * Same fork choice state and payload attributes, with a new execution payload context future and
   * the resend guard cleared, so {@link #send} issues a fresh forkChoiceUpdated.
   */
  public ForkChoiceUpdateData forResend() {
    return new ForkChoiceUpdateData(forkChoiceState, payloadBuildingAttributes, terminalBlockHash);
  }

  public ForkChoiceUpdateData withPayloadBuildingAttributes(
      final Optional<PayloadBuildingAttributes> payloadBuildingAttributes) {
    if (this.payloadBuildingAttributes.equals(payloadBuildingAttributes)) {
      return this;
    }
    return new ForkChoiceUpdateData(forkChoiceState, payloadBuildingAttributes, terminalBlockHash);
  }

  public ForkChoiceUpdateData withTerminalBlockHash(final Bytes32 terminalBlockHash) {
    if (this.terminalBlockHash.isPresent()
        && this.terminalBlockHash.get().equals(terminalBlockHash)) {
      return this;
    }
    return new ForkChoiceUpdateData(
        forkChoiceState, payloadBuildingAttributes, Optional.of(terminalBlockHash));
  }

  public Optional<PayloadBuildingAttributes> getPayloadBuildingAttributes() {
    return payloadBuildingAttributes;
  }

  public SafeFuture<Optional<ExecutionPayloadContext>> getExecutionPayloadContext() {
    return executionPayloadContext;
  }

  public Optional<SafeFuture<ForkChoiceUpdatedResult>> send(
      final ExecutionLayerChannel executionLayer, final UInt64 currentTimestamp) {
    if (!shouldBeSent(currentTimestamp)) {
      return Optional.empty();
    }
    toBeSentAtTime = currentTimestamp.plus(RESEND_AFTER_MILLIS);

    if (forkChoiceState.headExecutionBlockHash().isZero()) {
      LOG.debug("send - getHeadBlockHash is zero - returning empty");
      executionPayloadContext.complete(Optional.empty());
      return Optional.empty();
    }

    logSendingForkChoiceUpdated();
    final SafeFuture<ForkChoiceUpdatedResult> forkChoiceUpdatedResult =
        executionLayer.engineForkChoiceUpdated(forkChoiceState, payloadBuildingAttributes);

    forkChoiceUpdatedResult
        .thenPeek(this::warnIfPayloadIdIsMissing)
        .thenApply(ForkChoiceUpdatedResult::getPayloadId)
        .thenPeek(this::logSendForkChoiceUpdatedComplete)
        .thenApply(
            maybePayloadId ->
                maybePayloadId.map(
                    payloadId ->
                        new ExecutionPayloadContext(
                            payloadId,
                            forkChoiceState,

                            // it is safe to use orElseThrow() since it should be impossible to
                            // receive a payloadId without having previously sent payloadAttributes
                            payloadBuildingAttributes.orElseThrow())))
        .propagateTo(executionPayloadContext);

    return Optional.of(forkChoiceUpdatedResult);
  }

  private void warnIfPayloadIdIsMissing(final ForkChoiceUpdatedResult forkChoiceUpdatedResult) {
    if (payloadBuildingAttributes.isEmpty() || forkChoiceUpdatedResult.getPayloadId().isPresent()) {
      return;
    }
    LOG.warn(
        "Execution layer returned no payloadId for block production at slot {} (status: {}). Block production for this slot will fail unless a new payloadId is obtained.",
        payloadBuildingAttributes.get().proposalSlot(),
        forkChoiceUpdatedResult.getPayloadStatus().getStatus().map(Enum::name).orElse("UNKNOWN"));
  }

  private void logSendForkChoiceUpdatedComplete(final Optional<Bytes8> payloadId) {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "send - forkChoiceUpdated returned payload id {} for {}, {}",
          payloadId,
          forkChoiceState,
          payloadBuildingAttributes);
    }
  }

  private void logSendingForkChoiceUpdated() {
    if (LOG.isDebugEnabled()) {
      LOG.debug(
          "send - calling forkChoiceUpdated({}, {})", forkChoiceState, payloadBuildingAttributes);
    } else {
      payloadBuildingAttributes.ifPresent(
          buildingAttributes ->
              LOG.info(
                  "Calling local execution layer to start block production (block slot: {})",
                  buildingAttributes.proposalSlot()));
    }
  }

  public boolean hasHeadBlockHash() {
    return !forkChoiceState.headExecutionBlockHash().isZero();
  }

  public boolean hasTerminalBlockHash() {
    return terminalBlockHash.isPresent();
  }

  public boolean isTerminalBlockHash(final Bytes32 executionBlockHash) {
    return terminalBlockHash.map(executionBlockHash::equals).orElse(false);
  }

  public ForkChoiceState getForkChoiceState() {
    return forkChoiceState;
  }

  private boolean shouldBeSent(final UInt64 currentTimestamp) {
    if (toBeSentAtTime.isZero()) {
      return true;
    }
    if (toBeSentAtTime.isLessThanOrEqualTo(currentTimestamp)) {
      LOG.debug("send - already sent but resending");
      return true;
    }
    LOG.debug("send - already sent");
    return false;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("forkChoiceState", forkChoiceState)
        .add("payloadBuildingAttributes", payloadBuildingAttributes)
        .add("terminalBlockHash", terminalBlockHash)
        .add("executionPayloadContext", executionPayloadContext)
        .toString();
  }
}
