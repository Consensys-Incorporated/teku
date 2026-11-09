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

package tech.pegasys.teku.weaksubjectivity;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.WeakSubjectivityLogger;
import tech.pegasys.teku.infrastructure.time.Throttler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.weaksubjectivity.config.WeakSubjectivityConfig;
import tech.pegasys.teku.weaksubjectivity.policies.WeakSubjectivityViolationPolicy;

public class WeakSubjectivityValidator {
  private static final Logger LOG = LogManager.getLogger();
  // About a week with mainnet config
  static final long MAX_SUPPRESSED_EPOCHS = 1575;

  private final WeakSubjectivityCalculator calculator;
  private final WeakSubjectivityViolationPolicy violationPolicy;

  private final WeakSubjectivityConfig config;
  private final Spec spec;
  private volatile Optional<UInt64> suppressWSPeriodErrorsUntilEpoch = Optional.empty();
  private final Throttler<WeakSubjectivityLogger> wsChecksSuppressedLogger;
  private final Throttler<WeakSubjectivityLogger> deferValidationLogger;

  WeakSubjectivityValidator(
      final WeakSubjectivityConfig config,
      final WeakSubjectivityCalculator calculator,
      final WeakSubjectivityViolationPolicy violationPolicy) {
    this.spec = config.getSpec();
    this.calculator = calculator;
    this.violationPolicy = violationPolicy;
    this.config = config;

    final int throttlingPeriodEpochs = 1;
    final WeakSubjectivityLogger wsLogger = WeakSubjectivityLogger.createFileLogger();
    this.wsChecksSuppressedLogger =
        new Throttler<>(wsLogger, UInt64.valueOf(throttlingPeriodEpochs));
    this.deferValidationLogger = new Throttler<>(wsLogger, UInt64.valueOf(throttlingPeriodEpochs));
  }

  public static WeakSubjectivityValidator moderate(final WeakSubjectivityConfig config) {
    return new WeakSubjectivityValidator(
        config,
        WeakSubjectivityCalculator.create(config.getSpec()),
        WeakSubjectivityViolationPolicy.moderate());
  }

  public static WeakSubjectivityValidator lenient(final WeakSubjectivityConfig config) {
    return new WeakSubjectivityValidator(
        config,
        WeakSubjectivityCalculator.create(config.getSpec()),
        WeakSubjectivityViolationPolicy.lenient());
  }

  public Optional<Checkpoint> getWSCheckpoint() {
    return config.getWeakSubjectivityCheckpoint();
  }

  /** Check whether the chain matches any configured weak subjectivity checkpoint or state */
  public SafeFuture<Void> validateChainIsConsistentWithWSCheckpoint(
      final CombinedChainDataClient chainData) {
    if (config.getWeakSubjectivityCheckpoint().isEmpty()) {
      // Nothing to validate against
      return SafeFuture.COMPLETE;
    }
    final Checkpoint wsCheckpoint = config.getWeakSubjectivityCheckpoint().get();
    if (!chainData.isFinalizedEpoch(wsCheckpoint.getEpoch())) {
      // Checkpoint is in the future - nothing to validate yet
      return SafeFuture.COMPLETE;
    }

    return chainData
        .getFinalizedBlockInEffectAtSlot(wsCheckpoint.getEpochStartSlot(spec))
        .thenAccept(
            maybeBlock -> {
              SignedBeaconBlock blockAtCheckpointSlot =
                  maybeBlock.orElseThrow(
                      () ->
                          new IllegalStateException(
                              "Unable to verify weak subjectivity checkpoint at epoch "
                                  + wsCheckpoint.getEpoch()
                                  + ": finalized block is unavailable"));
              if (!blockAtCheckpointSlot.getRoot().equals(wsCheckpoint.getRoot())) {
                handleInconsistentWsCheckpoint(blockAtCheckpointSlot);
              }
            });
  }

  public Optional<UInt64> getWSPeriod(final CheckpointState latestFinalizedCheckpoint) {
    if (isPriorToWSCheckpoint(latestFinalizedCheckpoint)) {
      return Optional.empty();
    }

    return Optional.of(calculator.computeWeakSubjectivityPeriod(latestFinalizedCheckpoint));
  }

  /**
   * Validates that the latest finalized checkpoint is within the weak subjectivity period, given
   * the current slot based on clock time. If validation fails, configured policies are run to
   * handle this failure.
   *
   * @param latestFinalizedCheckpoint The latest finalized checkpoint
   * @param currentSlot The current slot based on clock time
   */
  public void validateLatestFinalizedCheckpoint(
      final CheckpointState latestFinalizedCheckpoint, final UInt64 currentSlot) {
    if (isPriorToWSCheckpoint(latestFinalizedCheckpoint)) {
      // Defer validation until we reach the weakSubjectivity checkpoint
      final UInt64 finalizedEpoch = latestFinalizedCheckpoint.getEpoch();
      final UInt64 wsEpoch = config.getWeakSubjectivityCheckpoint().orElseThrow().getEpoch();
      deferValidationLogger.invoke(
          currentSlot,
          l ->
              l.warnWeakSubjectivityFinalizedCheckpointValidationDeferred(finalizedEpoch, wsEpoch));
      return;
    }

    // Validate against ws checkpoint
    if (isAtWSCheckpoint(latestFinalizedCheckpoint)
        && !isWSCheckpointRoot(latestFinalizedCheckpoint.getRoot())) {
      // Finalized root is inconsistent with ws checkpoint
      handleInconsistentWsCheckpoint(
          latestFinalizedCheckpoint.getRoot(), latestFinalizedCheckpoint.getBlockSlot());
    }

    // Determine whether we should suppress ws period errors
    UInt64 currentEpoch = spec.computeEpochAtSlot(currentSlot);
    final Optional<UInt64> suppressionEpoch = getSuppressWSPeriodChecksUntilEpoch(currentSlot);
    final boolean shouldSuppressErrors =
        suppressionEpoch.map(e -> e.isGreaterThan(currentEpoch)).orElse(false);

    // Validate against ws period
    final boolean withinWSPeriod = isWithinWSPeriod(latestFinalizedCheckpoint, currentSlot);
    if (!withinWSPeriod && !shouldSuppressErrors) {
      handleFinalizedCheckpointOutsideWSPeriod(latestFinalizedCheckpoint, currentSlot);
    } else if (!withinWSPeriod) {
      wsChecksSuppressedLogger.invoke(
          currentSlot, l -> l.warnWeakSubjectivityChecksSuppressed(suppressionEpoch.orElseThrow()));
    }
  }

  public boolean isBlockValid(
      final SignedBeaconBlock block, final ReadOnlyForkChoiceStrategy forkChoiceStrategy) {
    if (config.getWeakSubjectivityCheckpoint().isEmpty()) {
      return true;
    }
    final Checkpoint wsCheckpoint = config.getWeakSubjectivityCheckpoint().get();

    UInt64 blockEpoch = spec.computeEpochAtSlot(block.getSlot());
    boolean blockAtEpochBoundary = spec.computeStartSlotAtEpoch(blockEpoch).equals(block.getSlot());
    if (isWSCheckpointEpoch(blockEpoch) && blockAtEpochBoundary) {
      // Block is at ws checkpoint slot - so it must match the ws checkpoint block
      return isWSCheckpointBlock(block);
    } else if (isWSCheckpointBlock(block)) {
      // The block is the checkpoint
      return true;
    } else if (blockEpoch.isGreaterThanOrEqualTo(wsCheckpoint.getEpoch())) {
      // If the block is at or past the checkpoint, the wsCheckpoint must be an ancestor
      final Optional<Bytes32> ancestor =
          spec.getAncestor(
              forkChoiceStrategy, block.getParentRoot(), wsCheckpoint.getEpochStartSlot(spec));
      // If ancestor is not present, the chain must have moved passed the wsCheckpoint
      return ancestor.map(a -> a.equals(wsCheckpoint.getRoot())).orElse(true);
    }

    // If block is prior to the checkpoint, we can't yet validate
    return true;
  }

  /**
   * A catch-all handler for managing problems encountered while executing other validations
   *
   * @param message An error message
   * @param error The error encountered
   */
  public void handleValidationFailure(final String message, final Throwable error) {
    violationPolicy.onFailedToPerformValidation(message, error);
  }

  private void handleFinalizedCheckpointOutsideWSPeriod(
      final CheckpointState latestFinalizedCheckpoint, final UInt64 currentSlot) {
    final UInt64 wsPeriod = calculator.computeWeakSubjectivityPeriod(latestFinalizedCheckpoint);
    violationPolicy.onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
        spec.computeEpochAtSlot(currentSlot), latestFinalizedCheckpoint, wsPeriod);
  }

  private void handleInconsistentWsCheckpoint(final SignedBeaconBlock inconsistentBlock) {
    handleInconsistentWsCheckpoint(inconsistentBlock.getRoot(), inconsistentBlock.getSlot());
  }

  private void handleInconsistentWsCheckpoint(
      final Bytes32 inconsistentBlockRoot, final UInt64 inconsistentBlockSlot) {
    final Checkpoint wsCheckpoint = config.getWeakSubjectivityCheckpoint().orElseThrow();
    violationPolicy.onChainInconsistentWithWeakSubjectivityCheckpoint(
        wsCheckpoint, inconsistentBlockRoot, inconsistentBlockSlot);
  }

  @VisibleForTesting
  Optional<UInt64> getSuppressWSPeriodChecksUntilEpoch(final UInt64 currentSlot) {
    if (suppressWSPeriodErrorsUntilEpoch.isEmpty()
        && config.getSuppressWSPeriodChecksUntilEpoch().isPresent()) {
      // Initialize the suppression logic
      final UInt64 configuredSuppressionEpoch = config.getSuppressWSPeriodChecksUntilEpoch().get();
      final UInt64 startupEpoch = spec.computeEpochAtSlot(currentSlot);
      final UInt64 maxSuppressedEpoch = startupEpoch.plus(MAX_SUPPRESSED_EPOCHS);
      final UInt64 suppressionEpoch = configuredSuppressionEpoch.min(maxSuppressedEpoch);
      if (suppressionEpoch.isLessThan(configuredSuppressionEpoch)) {
        LOG.info(
            "Configured weak subjectivity error suppression epoch ({}) is too large, suppression epoch set to {} ({} epochs ahead of current epoch {}).",
            configuredSuppressionEpoch,
            suppressionEpoch,
            MAX_SUPPRESSED_EPOCHS,
            startupEpoch);
      }
      LOG.warn(
          "Configured to suppress weak subjectivity period checks until epoch {}",
          suppressionEpoch);

      suppressWSPeriodErrorsUntilEpoch = Optional.of(suppressionEpoch);
    }

    return suppressWSPeriodErrorsUntilEpoch;
  }

  public boolean isWithinWSPeriod(final CheckpointState checkpointState, final UInt64 currentSlot) {
    return calculator.isWithinWeakSubjectivityPeriod(checkpointState, currentSlot);
  }

  private boolean isWSCheckpointEpoch(final UInt64 epoch) {
    return config
        .getWeakSubjectivityCheckpoint()
        .map(c -> c.getEpoch().equals(epoch))
        .orElse(false);
  }

  private boolean isWSCheckpointBlock(final SignedBeaconBlock block) {
    return isWSCheckpointRoot(block.getRoot());
  }

  private boolean isWSCheckpointRoot(final Bytes32 blockRoot) {
    return config
        .getWeakSubjectivityCheckpoint()
        .map(c -> c.getRoot().equals(blockRoot))
        .orElse(false);
  }

  private boolean isPriorToWSCheckpoint(final CheckpointState checkpoint) {
    return config
        .getWeakSubjectivityCheckpoint()
        .map(c -> checkpoint.getEpoch().isLessThan(c.getEpoch()))
        .orElse(false);
  }

  private boolean isAtWSCheckpoint(final CheckpointState checkpoint) {
    return config
        .getWeakSubjectivityCheckpoint()
        .map(c -> checkpoint.getEpoch().equals(c.getEpoch()))
        .orElse(false);
  }
}
