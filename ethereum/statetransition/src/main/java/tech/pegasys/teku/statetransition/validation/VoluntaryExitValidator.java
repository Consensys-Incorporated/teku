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

import static tech.pegasys.teku.spec.config.Constants.VALID_VALIDATOR_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.ValidationResultCode.IGNORE;

import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.operations.validation.OperationInvalidReason;
import tech.pegasys.teku.spec.logic.versions.phase0.operations.validation.VoluntaryExitValidator.ExitInvalidReason;
import tech.pegasys.teku.storage.client.RecentChainData;

public class VoluntaryExitValidator implements OperationValidator<SignedVoluntaryExit> {

  // 117 minutes as millis (MappedOperationPool retries at 2 hrs)
  static final int CACHE_DURATAION_MILLIS = 117 * 60_000;
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final GossipValidationHelper gossipValidationHelper;
  private final Map<UInt64, UInt64> receivedValidators =
      LimitedMap.createSynchronizedNatural(VALID_VALIDATOR_SET_SIZE);
  private final TimeProvider timeProvider;

  public VoluntaryExitValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final TimeProvider timeProvider,
      final GossipValidationHelper gossipValidationHelper) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.timeProvider = timeProvider;
    this.gossipValidationHelper = gossipValidationHelper;
  }

  @Override
  public SafeFuture<InternalValidationResult> validateForGossip(final SignedVoluntaryExit exit) {
    if (!canAcceptExitForValidator(exit)) {
      LOG.trace(
          "VoluntaryExitValidator: Exit is not the first one for validator {}.",
          exit.getMessage().getValidatorIndex());
      return SafeFuture.completedFuture(
          InternalValidationResult.create(
              IGNORE,
              String.format(
                  "Exit is not the first one for validator %s.",
                  exit.getMessage().getValidatorIndex())));
    }

    // [IGNORE] The voluntary exit epoch is not in the future
    if (gossipValidationHelper.isEpochFromFuture(exit.getMessage().getEpoch())) {
      return SafeFuture.completedFuture(
          InternalValidationResult.create(
              IGNORE,
              String.format(
                  "Voluntary exit for validator %s is from a future epoch.",
                  exit.getMessage().getValidatorIndex())));
    }

    return getFailureReason(exit)
        .thenApply(
            failureReason -> {
              if (failureReason.isPresent()) {
                return InternalValidationResult.reject(
                    "Exit for validator %s is invalid: %s",
                    exit.getMessage().getValidatorIndex(), failureReason.get().describe());
              }
              if (!cacheValidatorSeenEntry(exit)) {
                LOG.trace(
                    "VoluntaryExitValidator: Exit is not the first one for validator {}.",
                    exit.getMessage().getValidatorIndex());
                return InternalValidationResult.create(
                    IGNORE,
                    String.format(
                        "Exit is not the first one for validator %s.",
                        exit.getMessage().getValidatorIndex()));
              }
              return InternalValidationResult.ACCEPT;
            });
  }

  private boolean cacheValidatorSeenEntry(final SignedVoluntaryExit exit) {
    final UInt64 validatorIndex = exit.getMessage().getValidatorIndex();
    final UInt64 currentTimeMillis = timeProvider.getTimeInMillis();
    final UInt64 previousTime = receivedValidators.putIfAbsent(validatorIndex, currentTimeMillis);
    if (previousTime == null) {
      return true;
    } else if (previousTime.plus(CACHE_DURATAION_MILLIS).isLessThan(currentTimeMillis)) {
      // the previous entry was seen too long
      // we also don't care if this is not concurrently perfect so just update it.
      receivedValidators.put(validatorIndex, currentTimeMillis);
      return true;
    }
    return false;
  }

  @Override
  public Optional<OperationInvalidReason> validateForBlockInclusion(
      final BeaconState stateAtBlockSlot, final SignedVoluntaryExit exit) {
    return getFailureReason(stateAtBlockSlot, exit);
  }

  private SafeFuture<Optional<OperationInvalidReason>> getFailureReason(
      final SignedVoluntaryExit exit) {
    return getState().thenApply(state -> getFailureReason(state, exit));
  }

  private Optional<OperationInvalidReason> getFailureReason(
      final BeaconState state, final SignedVoluntaryExit exit) {
    Optional<OperationInvalidReason> invalidReason = spec.validateVoluntaryExit(state, exit);
    if (invalidReason.isPresent()) {
      return invalidReason;
    }

    if (!spec.verifyVoluntaryExitSignature(state, exit, BLSSignatureVerifier.SIMPLE)) {
      return Optional.of(ExitInvalidReason.invalidSignature());
    }
    return Optional.empty();
  }

  private boolean canAcceptExitForValidator(final SignedVoluntaryExit exit) {
    final UInt64 currentTimeMillis = timeProvider.getTimeInMillis();
    final UInt64 previousTime = receivedValidators.get(exit.getMessage().getValidatorIndex());
    // if we haven't seen the index or its outside our cache period, then we can just allow here.
    return previousTime == null
        || previousTime.plus(CACHE_DURATAION_MILLIS).isLessThan(currentTimeMillis);
  }

  private SafeFuture<BeaconState> getState() {
    return recentChainData
        .getBestState()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Unable to get best state for voluntary exit processing."));
  }
}
