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

import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason.FAILED_BROADCAST_VALIDATION;

import com.google.common.base.Suppliers;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FailureReason;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult;
import tech.pegasys.teku.validator.api.SendSignedBlockResult;
import tech.pegasys.teku.validator.coordinator.BlockFactory;
import tech.pegasys.teku.validator.coordinator.DutyMetrics;

public abstract class AbstractBlockPublisher implements BlockPublisher {
  private static final Logger LOG = LogManager.getLogger();

  protected final BlockFactory blockFactory;
  protected final DutyMetrics dutyMetrics;

  public AbstractBlockPublisher(final BlockFactory blockFactory, final DutyMetrics dutyMetrics) {
    this.blockFactory = blockFactory;
    this.dutyMetrics = dutyMetrics;
  }

  @Override
  public SafeFuture<SendSignedBlockResult> sendSignedBlock(
      final SignedBlockContainer blockContainer,
      final BroadcastValidationLevel broadcastValidationLevel,
      final BlockPublishingPerformance blockPublishingPerformance) {
    return blockFactory
        .unblindSignedBlockIfBlinded(blockContainer.getSignedBlock(), blockPublishingPerformance)
        // creating sidecars after unblinding the block to ensure in the blinded flow we will have
        // the cached builder payload
        .thenCompose(
            maybeSignedBlock -> {
              if (maybeSignedBlock.isEmpty()) {
                return handleMissingBlockAfterUnblinding();
              }
              return gossipAndImportUnblindedSignedBlockAndSidecars(
                  maybeSignedBlock.get(),
                  Suppliers.memoize(() -> blockFactory.createBlobSidecars(blockContainer)),
                  Suppliers.memoize(() -> blockFactory.createDataColumnSidecars(blockContainer)),
                  broadcastValidationLevel,
                  blockPublishingPerformance);
            })
        .thenCompose(result -> calculateResult(blockContainer, result, blockPublishingPerformance));
  }

  private SafeFuture<BlockImportAndBroadcastValidationResults>
      gossipAndImportUnblindedSignedBlockAndSidecars(
          final SignedBeaconBlock block,
          final Supplier<List<BlobSidecar>> blobSidecars,
          final Supplier<List<DataColumnSidecar>> dataColumnSidecars,
          final BroadcastValidationLevel broadcastValidationLevel,
          final BlockPublishingPerformance blockPublishingPerformance) {
    if (broadcastValidationLevel == BroadcastValidationLevel.NOT_REQUIRED) {
      // when broadcast validation is disabled, we can publish the block (and sidecars) immediately
      // and then import
      publishBlockAndSidecars(block, blobSidecars, dataColumnSidecars, blockPublishingPerformance);
      importBlobSidecars(blobSidecars, blockPublishingPerformance);
      return importBlock(block, broadcastValidationLevel);
    }

    // when broadcast validation is enabled, we need to wait for the validation to complete before
    // publishing the block (and sidecars)

    final SafeFuture<BlockImportAndBroadcastValidationResults>
        blockImportAndBroadcastValidationResults = importBlock(block, broadcastValidationLevel);

    final UInt64 slot = block.getSlot();

    // prepare and import blob sidecars in parallel with block import
    importBlobSidecarsInParallel(blobSidecars, blockPublishingPerformance, slot);

    blockImportAndBroadcastValidationResults
        .thenCompose(BlockImportAndBroadcastValidationResults::broadcastValidationResult)
        .thenAccept(
            broadcastValidationResult -> {
              if (broadcastValidationResult == BroadcastValidationResult.SUCCESS) {
                publishBlockAndSidecars(
                    block, blobSidecars, dataColumnSidecars, blockPublishingPerformance);
                LOG.debug("{} publishing initiated", getPublishingType());
              } else {
                LOG.warn(
                    "{} publishing skipped due to broadcast validation result {} for slot {}",
                    getPublishingType(),
                    broadcastValidationResult,
                    slot);
              }
            })
        .finish(
            err -> LOG.error("{} publishing failed for slot {}", getPublishingType(), slot, err));

    return blockImportAndBroadcastValidationResults;
  }

  abstract SafeFuture<BlockImportAndBroadcastValidationResults> handleMissingBlockAfterUnblinding();

  abstract SafeFuture<BlockImportAndBroadcastValidationResults> importBlock(
      SignedBeaconBlock block, BroadcastValidationLevel broadcastValidationLevel);

  abstract void importBlobSidecars(
      Supplier<List<BlobSidecar>> blobSidecars,
      BlockPublishingPerformance blockPublishingPerformance);

  abstract void importBlobSidecarsInParallel(
      Supplier<List<BlobSidecar>> blobSidecars,
      BlockPublishingPerformance blockPublishingPerformance,
      UInt64 slot);

  abstract void publishBlockAndSidecars(
      SignedBeaconBlock block,
      Supplier<List<BlobSidecar>> blobSidecars,
      Supplier<List<DataColumnSidecar>> dataColumnSidecars,
      BlockPublishingPerformance blockPublishingPerformance);

  // Used exclusively for logging
  abstract String getPublishingType();

  private SafeFuture<SendSignedBlockResult> calculateResult(
      final SignedBlockContainer blockContainer,
      final BlockImportAndBroadcastValidationResults blockImportAndBroadcastValidationResults,
      final BlockPublishingPerformance blockPublishingPerformance) {

    // broadcast validation can fail earlier than block import.
    // The assumption is that in that block import will fail but not as fast
    // (there might be the state transition in progress)
    // Thus, to let the API return as soon as possible, let's check broadcast validation first.
    return blockImportAndBroadcastValidationResults
        .broadcastValidationResult()
        .thenCompose(
            broadcastValidationResult -> {
              if (broadcastValidationResult.isFailure()) {
                return SafeFuture.completedFuture(
                    SendSignedBlockResult.rejected(
                        FAILED_BROADCAST_VALIDATION.name()
                            + ": "
                            + broadcastValidationResult.name()));
              }

              return blockImportAndBroadcastValidationResults
                  .blockImportResult()
                  .thenApply(
                      importResult -> {
                        blockPublishingPerformance.blockImportCompleted();
                        if (importResult.isSuccessful()) {
                          LOG.trace(
                              "Successfully imported proposed block: {}",
                              blockContainer.getSignedBlock().toLogString());
                          dutyMetrics.onBlockPublished(blockContainer.getSlot());
                          return SendSignedBlockResult.success(blockContainer.getRoot());
                        }
                        if (importResult.getFailureReason() == FailureReason.BLOCK_IS_FROM_FUTURE) {
                          LOG.debug(
                              "Delayed processing proposed block {} because it is from the future",
                              blockContainer.getSignedBlock().toLogString());
                          dutyMetrics.onBlockPublished(blockContainer.getSlot());
                          return SendSignedBlockResult.notImported(
                              importResult.getFailureReason().name());
                        }
                        if (importResult.getFailureReason() == FailureReason.BUILDER_WITHHOLD) {
                          LOG.debug(
                              "Block was not imported because builder didn't reveal full block {}",
                              blockContainer.getSignedBlock().toLogString());
                          dutyMetrics.onBlockPublished(blockContainer.getSlot());
                          return SendSignedBlockResult.notImported(
                              importResult.getFailureReason().name());
                        }

                        VALIDATOR_LOGGER.proposedBlockImportFailed(
                            importResult.getFailureReason().toString(),
                            blockContainer.getSlot(),
                            blockContainer.getRoot(),
                            importResult.getFailureCause());

                        return SendSignedBlockResult.notImported(
                            importResult.getFailureReason().name());
                      });
            });
  }
}
