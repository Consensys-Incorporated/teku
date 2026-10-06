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
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.ProposerSlashingValidator.ProposerSlashingInvalidReason;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ProposerSlashingValidator implements OperationValidator<ProposerSlashing> {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final Set<UInt64> receivedValidSlashingForProposerSet =
      LimitedSet.createSynchronizedLRU(VALID_VALIDATOR_INDEX_SET_SIZE);

  public ProposerSlashingValidator(final Spec spec, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
  }

  @Override
  public SafeFuture<InternalValidationResult> validateForGossip(final ProposerSlashing slashing) {
    if (!isFirstValidSlashingForValidator(slashing)) {
      LOG.trace(
          "ProposerSlashingValidator: Slashing is not the first one for the given validator.");
      return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
    }

    return passesProcessProposerSlashingConditions(slashing)
        .thenApply(
            invalidReason -> {
              if (invalidReason.isPresent()) {
                return InternalValidationResult.create(
                    ValidationResultCode.REJECT, invalidReason.get().describe());
              }

              if (receivedValidSlashingForProposerSet.add(
                  slashing.getHeader1().getMessage().getProposerIndex())) {
                return InternalValidationResult.ACCEPT;
              } else {
                LOG.trace(
                    "ProposerSlashingValidator: Slashing is not the first one for the given validator.");
                return InternalValidationResult.IGNORE;
              }
            });
  }

  @Override
  public Optional<OperationInvalidReason> validateForBlockInclusion(
      final BeaconState stateAtBlockSlot, final ProposerSlashing slashing) {
    final Optional<OperationInvalidReason> invalidReason =
        spec.validateProposerSlashing(stateAtBlockSlot, slashing);
    if (invalidReason.isPresent()) {
      return invalidReason;
    }

    return verifySignature(stateAtBlockSlot, slashing);
  }

  private SafeFuture<Optional<OperationInvalidReason>> passesProcessProposerSlashingConditions(
      final ProposerSlashing slashing) {
    return getState()
        .thenApply(
            state -> {
              final Optional<OperationInvalidReason> invalidReason =
                  spec.validateProposerSlashing(state, slashing);
              if (invalidReason.isPresent()) {
                return invalidReason;
              }

              return verifySignature(state, slashing);
            });
  }

  private Optional<OperationInvalidReason> verifySignature(
      final BeaconState state, final ProposerSlashing slashing) {
    if (!spec.verifyProposerSlashingSignature(state, slashing, BLSSignatureVerifier.SIMPLE)) {
      return Optional.of(ProposerSlashingInvalidReason.INVALID_SIGNATURE);
    }
    return Optional.empty();
  }

  private boolean isFirstValidSlashingForValidator(final ProposerSlashing slashing) {
    return !receivedValidSlashingForProposerSet.contains(
        slashing.getHeader1().getMessage().getProposerIndex());
  }

  private SafeFuture<BeaconState> getState() {
    return recentChainData
        .getBestState()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to get best state for proposer slashing processing."));
  }
}
