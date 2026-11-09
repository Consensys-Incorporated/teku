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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.EQUIVOCATION;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.GOSSIP;
import static tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel.NOT_REQUIRED;
import static tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult.CONSENSUS_FAILURE;
import static tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult.EQUIVOCATION_FAILURE;
import static tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult.GOSSIP_FAILURE;
import static tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator.BroadcastValidationResult.SUCCESS;

import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult;

class BlockBroadcastValidatorImpl implements BlockBroadcastValidator {
  private final BlockGossipValidator blockGossipValidator;
  private final BroadcastValidationLevel broadcastValidationLevel;
  private final SafeFuture<Boolean> consensusValidationSuccessResult;
  private final SafeFuture<BroadcastValidationResult> broadcastValidationResult;

  private BlockBroadcastValidatorImpl(
      final BlockGossipValidator blockGossipValidator,
      final BroadcastValidationLevel broadcastValidationLevel) {
    this.blockGossipValidator = blockGossipValidator;
    this.broadcastValidationLevel = broadcastValidationLevel;
    this.consensusValidationSuccessResult = new SafeFuture<>();
    this.broadcastValidationResult = new SafeFuture<>();
  }

  static BlockBroadcastValidatorImpl create(
      final SignedBeaconBlock block,
      final BlockGossipValidator blockGossipValidator,
      final BroadcastValidationLevel broadcastValidationLevel) {
    final BlockBroadcastValidatorImpl blockBroadcastValidator =
        new BlockBroadcastValidatorImpl(blockGossipValidator, broadcastValidationLevel);
    blockBroadcastValidator.buildValidationPipeline(block);
    return blockBroadcastValidator;
  }

  @Override
  public void onConsensusValidationSucceeded() {
    consensusValidationSuccessResult.complete(true);
  }

  @Override
  public void attachToBlockImport(final SafeFuture<BlockImportResult> blockImportResult) {
    switch (broadcastValidationLevel) {
      case NOT_REQUIRED, EQUIVOCATION, GOSSIP -> {
        // EQUIVOCATION/GOSSIP validation isn't dependent on block import result,
        // so not propagating exceptions to consensusValidationSuccessResult allow blocks\blobs
        // to be published even in case block import fails before the validation completes
      }
      case CONSENSUS, CONSENSUS_AND_EQUIVOCATION ->
          // Any successful block import will be considered as a consensus validation success, but
          // more importantly we propagate exceptions to the consensus validation, thus we capture
          // any early block import failures
          blockImportResult
              .thenApply(BlockImportResult::isSuccessful)
              .propagateTo(consensusValidationSuccessResult);
    }
  }

  @Override
  public SafeFuture<BroadcastValidationResult> getResult() {
    return broadcastValidationResult;
  }

  private void buildValidationPipeline(final SignedBeaconBlock block) {
    // NOT_REQUIRED should use the NOOP implementation but let's cover the case for safety
    if (broadcastValidationLevel == NOT_REQUIRED) {
      broadcastValidationResult.complete(SUCCESS);
      consensusValidationSuccessResult.cancel(true);
      return;
    }

    // EQUIVOCATION only validation
    if (broadcastValidationLevel == EQUIVOCATION) {
      final BroadcastValidationResult validationResult;
      // marking the block as received because gossip validation won't be done
      if (isEquivocatingBlock(block, true)) {
        validationResult = EQUIVOCATION_FAILURE;
      } else {
        validationResult = SUCCESS;
      }
      broadcastValidationResult.complete(validationResult);
      consensusValidationSuccessResult.cancel(true);
      return;
    }

    // We will skip marking the block as received when CONSENSUS_EQUIVOCATION level is chosen. This
    // is because we will perform an additional equivocation check after the block import where it
    // will be marked as received
    final boolean markAsReceived = broadcastValidationLevel != CONSENSUS_AND_EQUIVOCATION;

    // GOSSIP only validation (includes EQUIVOCATION validation)
    SafeFuture<BroadcastValidationResult> validationPipeline =
        blockGossipValidator
            .validate(block, markAsReceived)
            .thenApply(
                gossipValidationResult -> {
                  if (gossipValidationResult.isAccept()
                      || gossipValidationResult.isIgnoreAlreadySeen()) {
                    return SUCCESS;
                  }
                  return GOSSIP_FAILURE;
                });

    if (broadcastValidationLevel == GOSSIP) {
      validationPipeline.propagateTo(broadcastValidationResult);
      consensusValidationSuccessResult.cancel(true);
      return;
    }

    // GOSSIP and CONSENSUS validation
    validationPipeline =
        validationPipeline.thenCompose(
            broadcastValidationResult -> {
              if (broadcastValidationResult != SUCCESS) {
                // forward gossip validation failure
                return SafeFuture.completedFuture(broadcastValidationResult);
              }
              return consensusValidationSuccessResult.thenApply(
                  consensusValidationSuccess -> {
                    if (consensusValidationSuccess) {
                      return SUCCESS;
                    }
                    return CONSENSUS_FAILURE;
                  });
            });

    if (broadcastValidationLevel == BroadcastValidationLevel.CONSENSUS) {
      validationPipeline.propagateTo(broadcastValidationResult);
      return;
    }

    // GOSSIP, CONSENSUS and final EQUIVOCATION validation at the end
    validationPipeline
        .thenApply(
            broadcastValidationResult -> {
              if (broadcastValidationResult != SUCCESS) {
                // forward gossip or consensus validation failure
                return broadcastValidationResult;
              }
              // we didn't initially mark it as received, so doing it at this final equivocation
              // check
              if (isEquivocatingBlock(block, true)) {
                return EQUIVOCATION_FAILURE;
              }

              return SUCCESS;
            })
        .propagateTo(broadcastValidationResult);
  }

  private boolean isEquivocatingBlock(final SignedBeaconBlock block, final boolean markAsReceived) {
    return blockGossipValidator
        .performBlockEquivocationCheck(markAsReceived, block)
        .equals(EquivocationCheckResult.EQUIVOCATING_BLOCK_FOR_SLOT_PROPOSER);
  }
}
