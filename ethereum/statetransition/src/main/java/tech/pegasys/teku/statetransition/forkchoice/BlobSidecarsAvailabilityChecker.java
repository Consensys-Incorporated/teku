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

package tech.pegasys.teku.statetransition.forkchoice;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.DataAndValidationResult;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTracker;
import tech.pegasys.teku.storage.client.RecentChainData;

/**
 * Performs complete data availability check <a
 * href="https://github.com/ethereum/consensus-specs/blob/master/specs/deneb/fork-choice.md#is_data_available">is_data_available</a>
 */
public class BlobSidecarsAvailabilityChecker implements AvailabilityChecker<BlobSidecar> {
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final BlockBlobSidecarsTracker blockBlobSidecarsTracker;

  private final SafeFuture<DataAndValidationResult<BlobSidecar>> validationResult =
      new SafeFuture<>();

  private final Duration waitForTrackerCompletionTimeout;

  public BlobSidecarsAvailabilityChecker(
      final Spec spec,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.waitForTrackerCompletionTimeout =
        calculateCompletionTimeout(spec, blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot());
  }

  @VisibleForTesting
  BlobSidecarsAvailabilityChecker(
      final Spec spec,
      final RecentChainData recentChainData,
      final BlockBlobSidecarsTracker blockBlobSidecarsTracker,
      final Duration waitForTrackerCompletionTimeout) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.blockBlobSidecarsTracker = blockBlobSidecarsTracker;
    this.waitForTrackerCompletionTimeout = waitForTrackerCompletionTimeout;
  }

  @Override
  public boolean initiateDataAvailabilityCheck() {
    final SafeFuture<Void> blobSidecarCompletion = blockBlobSidecarsTracker.getCompletionFuture();

    if (!blobSidecarCompletion.isDone() && isBlockOutsideDataAvailabilityWindow()) {
      // avoid waiting for blob sidecars if the block is outside the data availability window
      validationResult.complete(DataAndValidationResult.notRequired());
      return true;
    }

    blobSidecarCompletion
        .orTimeout(waitForTrackerCompletionTimeout)
        .thenApply(__ -> validateCompletedBlobSidecars())
        .exceptionallyCompose(
            error ->
                ExceptionUtil.getCause(error, TimeoutException.class)
                    .<SafeFuture<DataAndValidationResult<BlobSidecar>>>map(
                        timeoutException -> {
                          if (isBlockOutsideDataAvailabilityWindow()) {
                            return SafeFuture.completedFuture(
                                DataAndValidationResult.notRequired());
                          }
                          return SafeFuture.completedFuture(
                              DataAndValidationResult.notAvailable(timeoutException));
                        })
                    .orElseGet(() -> SafeFuture.failedFuture(error)))
        .propagateTo(validationResult);
    return true;
  }

  private DataAndValidationResult<BlobSidecar> validateCompletedBlobSidecars() {
    final MiscHelpers miscHelpers =
        spec.atSlot(blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot()).miscHelpers();
    final List<BlobSidecar> blobSidecars =
        List.copyOf(blockBlobSidecarsTracker.getBlobSidecars().values());
    final SignedBeaconBlock block = blockBlobSidecarsTracker.getBlock().orElseThrow();

    try {
      if (!miscHelpers.verifyBlobKzgProofBatch(blobSidecars)) {
        return DataAndValidationResult.invalidResult(blobSidecars);
      }

      miscHelpers.verifyBlobSidecarCompleteness(blobSidecars, block);
    } catch (final Exception ex) {
      return DataAndValidationResult.invalidResult(blobSidecars, ex);
    }

    if (!miscHelpers.verifyBlobSidecarBlockHeaderSignatureViaValidatedSignedBlock(
        blobSidecars, block)) {
      return DataAndValidationResult.invalidResult(
          blobSidecars,
          new IllegalStateException("Blob sidecars block header does not match signed block"));
    }

    return DataAndValidationResult.validResult(blobSidecars);
  }

  @Override
  public SafeFuture<DataAndValidationResult<BlobSidecar>> getAvailabilityCheckResult() {
    return validationResult;
  }

  @Override
  public void logAvailabilityCheckResult(
      final Logger log, final DataAndValidationResult<BlobSidecar> result) {
    final SlotAndBlockRoot slotAndBlockRoot = blockBlobSidecarsTracker.getSlotAndBlockRoot();
    log.debug(
        "Data availability check for slot: {}, block_root: {} result: {}",
        slotAndBlockRoot.getSlot(),
        slotAndBlockRoot.getBlockRoot(),
        result.toLogString());
  }

  private boolean isBlockOutsideDataAvailabilityWindow() {
    return !spec.isAvailabilityOfBlobSidecarsRequiredAtSlot(
        recentChainData.getStore(), blockBlobSidecarsTracker.getSlotAndBlockRoot().getSlot());
  }

  static Duration calculateCompletionTimeout(final Spec spec, final UInt64 slot) {
    return Duration.ofMillis(spec.getAttestationDueMillis(slot));
  }
}
