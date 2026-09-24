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

package tech.pegasys.teku.statetransition.validation;

import static tech.pegasys.teku.spec.config.Constants.VALID_VALIDATOR_INDEX_SET_SIZE;

import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.storage.client.RecentChainData;

public class AttesterSlashingValidator implements OperationValidator<AttesterSlashing> {

  private static final Logger LOG = LogManager.getLogger();

  private final RecentChainData recentChainData;
  private final Set<UInt64> seenIndices =
      LimitedSet.createSynchronizedLRU(VALID_VALIDATOR_INDEX_SET_SIZE);
  private final Spec spec;

  public AttesterSlashingValidator(final RecentChainData recentChainData, final Spec spec) {
    this.recentChainData = recentChainData;
    this.spec = spec;
  }

  @Override
  public SafeFuture<InternalValidationResult> validateForGossip(final AttesterSlashing slashing) {
    if (!includesUnseenIndexToSlash(slashing.getIntersectingValidatorIndices())) {
      LOG.trace("AttesterSlashingValidator: Slashing is not the first one for any validator.");
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    return getState()
        .thenApply(
            state -> {
              final Optional<OperationInvalidReason> invalidReason =
                  spec.validateAttesterSlashing(state, slashing);
              if (invalidReason.isPresent()) {
                return InternalValidationResult.create(
                    ValidationResultCode.REJECT, invalidReason.get().describe());
              }

              if (seenIndices.addAll(slashing.getIntersectingValidatorIndices())) {
                return InternalValidationResult.ACCEPT;
              } else {
                LOG.trace(
                    "AttesterSlashingValidator: Slashing is not the first one for any validator.");
                return InternalValidationResult.IGNORE;
              }
            });
  }

  @Override
  public Optional<OperationInvalidReason> validateForBlockInclusion(
      final BeaconState stateAtBlockSlot, final AttesterSlashing slashing) {
    // The signature *is* verified during the state checks as part of isValidIndexedAttestation
    return spec.validateAttesterSlashing(stateAtBlockSlot, slashing);
  }

  private boolean includesUnseenIndexToSlash(final Set<UInt64> intersectingIndices) {
    return !seenIndices.containsAll(intersectingIndices);
  }

  private SafeFuture<BeaconState> getState() {
    return recentChainData
        .getBestState()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to get best state for attester slashing processing."));
  }
}
