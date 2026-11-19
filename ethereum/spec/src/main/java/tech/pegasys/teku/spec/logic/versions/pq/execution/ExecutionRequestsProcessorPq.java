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

package tech.pegasys.teku.spec.logic.versions.pq.execution;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.PAYLOAD_BUILDER_VERSION;
import static tech.pegasys.teku.spec.constants.PqConstants.PREREGISTRATIONS_LIMIT;
import static tech.pegasys.teku.spec.constants.PqConstants.PREREGISTRATION_EXPIRY_SLOTS;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_DEPOSIT_PREFIX;
import static tech.pegasys.teku.spec.logic.common.helpers.Predicates.getExecutionAddressUnchecked;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.DepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.BuilderPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.ValidatorPreregistrationRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.pq.VoluntaryExitRequest;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.MutableBeaconStatePq;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.logic.versions.gloas.execution.ExecutionRequestsProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateMutatorsPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.MiscHelpersPq;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.PredicatesPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

/**
 * PQ execution-layer requests: keys are XMSS public keys, deposits must match an active
 * preregistration, and preregistrations and voluntary exits arrive as requests.
 */
public class ExecutionRequestsProcessorPq extends ExecutionRequestsProcessorGloas {

  private final SpecConfigPq specConfigPq;
  private final PredicatesPq predicatesPq;
  private final MiscHelpersPq miscHelpersPq;
  private final BeaconStateAccessorsPq beaconStateAccessorsPq;
  private final BeaconStateMutatorsPq beaconStateMutatorsPq;

  public ExecutionRequestsProcessorPq(
      final SchemaDefinitionsPq schemaDefinitions,
      final MiscHelpersPq miscHelpers,
      final SpecConfigPq specConfig,
      final PredicatesPq predicates,
      final ValidatorsUtilGloas validatorsUtil,
      final BeaconStateMutatorsPq beaconStateMutators,
      final BeaconStateAccessorsPq beaconStateAccessors) {
    super(
        schemaDefinitions,
        miscHelpers,
        specConfig,
        predicates,
        validatorsUtil,
        beaconStateMutators,
        beaconStateAccessors);
    this.specConfigPq = specConfig;
    this.predicatesPq = predicates;
    this.miscHelpersPq = miscHelpers;
    this.beaconStateAccessorsPq = beaconStateAccessors;
    this.beaconStateMutatorsPq = beaconStateMutators;
  }

  /**
   * Withdrawal and consolidation requests name {@code 0^16 ‖ xmss_pubkey}; a request naming a BLS
   * key is ignored.
   */
  @Override
  protected Optional<Integer> getRequestValidatorIndex(
      final BeaconState state, final BLSPublicKey pubkey) {
    final Bytes48 pubkeyBytes = pubkey.toBytesCompressed();
    if (predicatesPq.isLegacyPubkey(pubkeyBytes)) {
      return Optional.empty();
    }
    return beaconStateAccessorsPq.getValidatorIndexByXmssKey(state, xmssKeyOf(pubkeyBytes));
  }

  /**
   * process_deposit_request, modified in PQ: a deposit for a preregistered key must match the
   * preregistration's withdrawal credentials and VRF root (the first 32 bytes of its signature),
   * which it consumes. A deposit that does not match is dropped.
   */
  @Override
  public void processDepositRequests(
      final MutableBeaconState state, final List<DepositRequest> depositRequests) {
    final MutableBeaconStatePq statePq = MutableBeaconStatePq.required(state);
    final PendingDeposit.PendingDepositSchema pendingDepositSchema =
        schemaDefinitions.getPendingDepositSchema();
    for (final DepositRequest depositRequest : depositRequests) {
      final Bytes48 requestPubkey = depositRequest.getPubkey().toBytesCompressed();
      final Bytes32 pubkey = miscHelpersPq.computeDepositPubkey(requestPubkey);
      final Optional<ValidatorPreregistration> preregistration =
          beaconStateAccessorsPq.getActiveValidatorPreregistration(statePq, pubkey);
      if (preregistration.isPresent()) {
        if (!depositRequest
            .getWithdrawalCredentials()
            .equals(preregistration.get().getWithdrawalCredentials())) {
          continue;
        }
        final Bytes32 vrfRoot =
            Bytes32.wrap(depositRequest.getSignature().toBytesCompressed().slice(0, Bytes32.SIZE));
        if (!vrfRoot.equals(preregistration.get().getVrfRoot())) {
          continue;
        }
        beaconStateMutatorsPq.removeValidatorPreregistration(statePq, pubkey);
      }
      statePq
          .getPendingDeposits()
          .append(
              pendingDepositSchema.create(
                  new SszPublicKey(requestPubkey),
                  SszBytes32.of(depositRequest.getWithdrawalCredentials()),
                  SszUInt64.of(depositRequest.getAmount()),
                  new SszSignature(depositRequest.getSignature().toBytesCompressed()),
                  SszUInt64.of(state.getSlot())));
    }
  }

  /**
   * process_builder_deposit_request, modified in PQ: deposits carry no signature, must match an
   * active preregistration if there is one, and a legacy deposit's new builder exits at once.
   */
  @Override
  public void processBuilderDepositRequests(
      final MutableBeaconState state, final List<BuilderDepositRequest> builderDepositRequests) {
    final MutableBeaconStatePq statePq = MutableBeaconStatePq.required(state);
    for (final BuilderDepositRequest request : builderDepositRequests) {
      if (!predicatesPq.isBuilderWithdrawalCredential(request.getWithdrawalCredentials())) {
        continue;
      }
      final Bytes48 requestPubkey = request.getPubkey().toBytesCompressed();
      final Bytes32 pubkey = miscHelpersPq.computeDepositPubkey(requestPubkey);
      final Optional<BuilderPreregistration> preregistration =
          beaconStateAccessorsPq.getActiveBuilderPreregistration(statePq, pubkey);
      if (preregistration.isPresent()) {
        if (!request
            .getWithdrawalCredentials()
            .equals(preregistration.get().getWithdrawalCredentials())) {
          continue;
        }
        beaconStateMutatorsPq.removeBuilderPreregistration(statePq, pubkey);
      }
      final Optional<Integer> existingIndex =
          beaconStateAccessorsPq.getBuilderIndexByXmssKey(statePq, pubkey);
      if (existingIndex.isEmpty()) {
        final UInt64 builderIndex = beaconStateAccessorsPq.getIndexForNewBuilder(statePq);
        beaconStateMutatorsPq.addBuilderToRegistry(
            statePq,
            pubkey,
            PAYLOAD_BUILDER_VERSION,
            getExecutionAddressUnchecked(request.getWithdrawalCredentials()),
            request.getAmount(),
            state.getSlot());
        if (predicatesPq.isLegacyPubkey(requestPubkey)) {
          beaconStateMutatorsPq.initiateBuilderExit(statePq, builderIndex);
        }
      } else {
        final SszMutableList<Builder> builders = statePq.getBuilders();
        final Builder builder = builders.get(existingIndex.get());
        Builder updated = builder;
        // If exited and swept, reset the withdrawable epoch
        if (!builder.getWithdrawableEpoch().equals(FAR_FUTURE_EPOCH)
            && builder.getBalance().isZero()) {
          updated =
              updated.copyWithNewWithdrawableEpoch(
                  beaconStateAccessorsPq
                      .getCurrentEpoch(state)
                      .plus(specConfigPq.getMinBuilderWithdrawabilityDelay()));
        }
        builders.set(
            existingIndex.get(),
            updated.copyWithNewBalance(updated.getBalance().plus(request.getAmount())));
      }
    }
  }

  /** process_builder_exit_request, modified in PQ: the builder is looked up by its XMSS key. */
  @Override
  public void processBuilderExitRequests(
      final MutableBeaconState state, final List<BuilderExitRequest> builderExitRequests) {
    for (final BuilderExitRequest request : builderExitRequests) {
      final Bytes32 pubkey = xmssKeyOf(request.getPubkey().toBytesCompressed());
      final Optional<Integer> maybeBuilderIndex =
          beaconStateAccessorsPq.getBuilderIndexByXmssKey(state, pubkey);
      if (maybeBuilderIndex.isEmpty()) {
        continue;
      }
      final UInt64 builderIndex = UInt64.valueOf(maybeBuilderIndex.get());
      final Builder builder =
          MutableBeaconStateGloas.required(state).getBuilders().get(maybeBuilderIndex.get());
      if (!predicatesPq.isActiveBuilder(state, builderIndex)) {
        continue;
      }
      if (!builder
          .getExecutionAddress()
          .getWrappedBytes()
          .equals(request.getSourceAddress().getWrappedBytes())) {
        continue;
      }
      if (!beaconStateAccessorsPq
          .getPendingBalanceToWithdrawForBuilder(state, builderIndex)
          .isZero()) {
        continue;
      }
      beaconStateMutatorsPq.initiateBuilderExit(state, builderIndex);
    }
  }

  /** process_validator_preregistration_request */
  @Override
  public void processValidatorPreregistrationRequests(
      final MutableBeaconState state, final List<ValidatorPreregistrationRequest> requests) {
    final MutableBeaconStatePq statePq = MutableBeaconStatePq.required(state);
    for (final ValidatorPreregistrationRequest request : requests) {
      final Bytes32 pubkey = request.getXmssPublicKey();
      // An active binding for this pubkey makes the new request a no-op
      if (beaconStateAccessorsPq.getActiveValidatorPreregistration(statePq, pubkey).isPresent()) {
        continue;
      }
      // A pubkey with an existing validator or a pending deposit cannot be preregistered
      if (beaconStateAccessorsPq.getValidatorIndexByXmssKey(statePq, pubkey).isPresent()) {
        continue;
      }
      if (miscHelpersPq.isPendingValidator(statePq.getPendingDeposits().asList(), pubkey)) {
        continue;
      }
      // The capacity check counts only active records
      final long activePreregistrations =
          statePq.getValidatorPreregistrations().stream()
              .filter(record -> predicatesPq.isActiveValidatorPreregistration(statePq, record))
              .count();
      if (activePreregistrations >= PREREGISTRATIONS_LIMIT) {
        continue;
      }
      final ValidatorPreregistration preregistration =
          new ValidatorPreregistration(
              pubkey,
              request.getWithdrawalCredentials(),
              request.getVrfRoot(),
              state.getSlot().plus(PREREGISTRATION_EXPIRY_SLOTS));
      beaconStateAccessorsPq
          .getValidatorPreregistrationIndex(statePq, pubkey)
          .ifPresentOrElse(
              // Replace the expired record in place
              index -> statePq.getValidatorPreregistrations().set(index, preregistration),
              () -> statePq.getValidatorPreregistrations().append(preregistration));
    }
  }

  /** process_builder_preregistration_request */
  @Override
  public void processBuilderPreregistrationRequests(
      final MutableBeaconState state, final List<BuilderPreregistrationRequest> requests) {
    final MutableBeaconStatePq statePq = MutableBeaconStatePq.required(state);
    for (final BuilderPreregistrationRequest request : requests) {
      final Bytes32 pubkey = request.getXmssPublicKey();
      if (beaconStateAccessorsPq.getActiveBuilderPreregistration(statePq, pubkey).isPresent()) {
        continue;
      }
      if (beaconStateAccessorsPq.getBuilderIndexByXmssKey(statePq, pubkey).isPresent()) {
        continue;
      }
      final long activePreregistrations =
          statePq.getBuilderPreregistrations().stream()
              .filter(record -> predicatesPq.isActiveBuilderPreregistration(statePq, record))
              .count();
      if (activePreregistrations >= PREREGISTRATIONS_LIMIT) {
        continue;
      }
      final BuilderPreregistration preregistration =
          new BuilderPreregistration(
              pubkey,
              request.getWithdrawalCredentials(),
              state.getSlot().plus(PREREGISTRATION_EXPIRY_SLOTS));
      beaconStateAccessorsPq
          .getBuilderPreregistrationIndex(statePq, pubkey)
          .ifPresentOrElse(
              index -> statePq.getBuilderPreregistrations().set(index, preregistration),
              () -> statePq.getBuilderPreregistrations().append(preregistration));
    }
  }

  /**
   * process_voluntary_exit_request: the system contract verifies the exit's XMSS signature, so the
   * beacon chain does not.
   */
  @Override
  public void processVoluntaryExitRequests(
      final MutableBeaconState state, final List<VoluntaryExitRequest> requests) {
    final Supplier<ValidatorExitContext> validatorExitContextSupplier =
        beaconStateMutatorsPq.createValidatorExitContextSupplier(state);
    final UInt64 currentEpoch = beaconStateAccessorsPq.getCurrentEpoch(state);
    for (final VoluntaryExitRequest request : requests) {
      final Optional<Integer> maybeIndex =
          beaconStateAccessorsPq.getValidatorIndexByXmssKey(state, request.getXmssPublicKey());
      if (maybeIndex.isEmpty()) {
        continue;
      }
      final int index = maybeIndex.get();
      final Validator validator = state.getValidators().get(index);
      if (!predicatesPq.isActiveValidator(validator, currentEpoch)) {
        continue;
      }
      if (!validator.getExitEpoch().equals(FAR_FUTURE_EPOCH)) {
        continue;
      }
      if (currentEpoch.isLessThan(
          validator.getActivationEpoch().plus(specConfigPq.getShardCommitteePeriod()))) {
        continue;
      }
      if (!validatorsUtil.getPendingBalanceToWithdraw(state, index).isZero()) {
        continue;
      }
      beaconStateMutatorsPq.initiateValidatorExit(state, index, validatorExitContextSupplier);
    }
  }

  private static Bytes32 xmssKeyOf(final Bytes48 requestPubkey) {
    return Bytes32.wrap(requestPubkey.slice(XMSS_DEPOSIT_PREFIX.size()));
  }
}
