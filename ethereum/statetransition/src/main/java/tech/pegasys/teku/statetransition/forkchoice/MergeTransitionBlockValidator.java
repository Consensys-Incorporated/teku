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

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.bellatrix.BeaconBlockBodyBellatrix;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.versions.bellatrix.helpers.BellatrixTransitionHelpers;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

public class MergeTransitionBlockValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;

  public MergeTransitionBlockValidator(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<PayloadValidationResult> verifyTransitionBlock(
      final ExecutionPayloadHeader latestExecutionPayloadHeader, final SignedBeaconBlock block) {
    if (latestExecutionPayloadHeader.isDefault()) {
      // This is the first filled payload, so verify it meets transition conditions
      return verifyTransitionPayload(
              block.getSlot(),
              BeaconBlockBodyBellatrix.required(block.getMessage().getBody()).getExecutionPayload())
          .thenApply(status -> new PayloadValidationResult(block.getRoot(), status));
    }

    return verifyAncestorTransitionBlock(block.getParentRoot());
  }

  public SafeFuture<PayloadValidationResult> verifyAncestorTransitionBlock(
      final Bytes32 chainHeadRoot) {
    if (transitionBlockNotFinalized()) {
      // The transition block hasn't been finalized yet
      final Optional<Bytes32> maybeTransitionBlockRoot =
          recentChainData
              .getForkChoiceStrategy()
              .orElseThrow()
              // Use the parent root because the block itself won't yet be imported
              .getOptimisticallySyncedTransitionBlockRoot(chainHeadRoot);
      if (maybeTransitionBlockRoot.isPresent()) {
        return retrieveAndVerifyNonFinalizedTransitionBlock(maybeTransitionBlockRoot.get());
      }
    }

    // Possibly while we were looking for a non-finalized transition, it got finalized
    // So verify if there's something not-yet verified
    return verifyFinalizedTransitionBlock();
  }

  private SafeFuture<PayloadValidationResult> verifyFinalizedTransitionBlock() {
    final Optional<SlotAndExecutionPayloadSummary> finalizedOptimisticTransitionPayload =
        recentChainData.getStore().getFinalizedOptimisticTransitionPayload();

    if (finalizedOptimisticTransitionPayload.isPresent()) {
      return verifyTransitionPayload(
              finalizedOptimisticTransitionPayload.get().getSlot(),
              finalizedOptimisticTransitionPayload.get().getExecutionPayloadSummary())
          .thenCompose(
              status -> {
                if (status.hasInvalidStatus()) {
                  return SafeFuture.failedFuture(
                      new FatalServiceFailureException(
                          "fork choice",
                          "Optimistic sync finalized an invalid transition block. Unable to recover."));
                }
                if (status.hasValidStatus()) {
                  final StoreTransaction transaction = recentChainData.startStoreTransaction();
                  transaction.removeFinalizedOptimisticTransitionPayload();
                  return transaction.commit().thenApply(__ -> new PayloadValidationResult(status));
                }
                return SafeFuture.completedFuture(new PayloadValidationResult(status));
              });
    }

    return SafeFuture.completedFuture(PayloadValidationResult.VALID);
  }

  private boolean transitionBlockNotFinalized() {
    final BeaconState state = recentChainData.getStore().getLatestFinalized().getState();
    return !spec.atSlot(state.getSlot()).miscHelpers().isMergeTransitionComplete(state);
  }

  private SafeFuture<PayloadValidationResult> retrieveAndVerifyNonFinalizedTransitionBlock(
      final Bytes32 transitionBlockRoot) {
    return recentChainData
        .retrieveBlockByRoot(transitionBlockRoot)
        .thenCompose(
            block -> {
              if (block.isEmpty()) {
                // We might have finalized the transition block
                return verifyFinalizedTransitionBlock();
              }
              final BeaconBlock transitionBlock = block.get();
              final ExecutionPayload transitionExecutionPayload =
                  BeaconBlockBodyBellatrix.required(transitionBlock.getBody())
                      .getExecutionPayload();
              return verifyTransitionPayload(transitionBlock.getSlot(), transitionExecutionPayload)
                  .thenApply(status -> new PayloadValidationResult(transitionBlockRoot, status));
            });
  }

  private SafeFuture<PayloadStatus> verifyTransitionPayload(
      final UInt64 slot, final ExecutionPayloadSummary executionPayload) {
    if (spec.atSlot(slot).getMilestone().isGreaterThanOrEqualTo(SpecMilestone.CAPELLA)) {
      return SafeFuture.completedFuture(PayloadStatus.VALID);
    }
    final BellatrixTransitionHelpers bellatrixTransitionHelpers =
        spec.atSlot(slot)
            .getBellatrixTransitionHelpers()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Attempting to validate a bellatrix block when spec does not have bellatrix transition helpers"));
    return bellatrixTransitionHelpers
        .validateMergeBlock(executionPayload, slot)
        .exceptionally(
            error -> {
              LOG.error("Error while validating merge block", error);
              return PayloadStatus.failedExecution(error);
            });
  }
}
