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

package tech.pegasys.teku.spec.logic.versions.gloas.execution;

import static tech.pegasys.teku.spec.config.SpecConfigGloas.PAYLOAD_BUILDER_VERSION;
import static tech.pegasys.teku.spec.logic.common.helpers.Predicates.getExecutionAddressUnchecked;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderExitRequest;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.versions.fulu.execution.ExecutionRequestsProcessorFulu;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ValidatorsUtilGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

public class ExecutionRequestsProcessorGloas extends ExecutionRequestsProcessorFulu {

  private final SpecConfigGloas specConfigGloas;
  private final PredicatesGloas predicatesGloas;
  private final BeaconStateMutatorsGloas beaconStateMutatorsGloas;
  private final BeaconStateAccessorsGloas beaconStateAccessorsGloas;

  public ExecutionRequestsProcessorGloas(
      final SchemaDefinitionsGloas schemaDefinitions,
      final MiscHelpersGloas miscHelpers,
      final SpecConfigGloas specConfig,
      final PredicatesGloas predicates,
      final ValidatorsUtilGloas validatorsUtil,
      final BeaconStateMutatorsGloas beaconStateMutators,
      final BeaconStateAccessorsGloas beaconStateAccessors) {
    super(
        schemaDefinitions,
        miscHelpers,
        specConfig,
        predicates,
        validatorsUtil,
        beaconStateMutators,
        beaconStateAccessors);
    this.specConfigGloas = specConfig;
    this.predicatesGloas = predicates;
    this.beaconStateMutatorsGloas = beaconStateMutators;
    this.beaconStateAccessorsGloas = beaconStateAccessors;
  }

  @Override
  public void processBuilderDepositRequests(
      final MutableBeaconState state, final List<BuilderDepositRequest> builderDepositRequests) {
    builderDepositRequests.forEach(
        request -> {
          // process_builder_deposit_request
          if (!predicatesGloas.isBuilderWithdrawalCredential(request.getWithdrawalCredentials())) {
            return;
          }
          final BLSPublicKey pubkey = request.getPubkey();
          beaconStateAccessorsGloas
              .getBuilderIndex(state, pubkey)
              .ifPresentOrElse(
                  builderIndex -> {
                    final SszMutableList<Builder> builders =
                        MutableBeaconStateGloas.required(state).getBuilders();
                    final Builder builder = builders.get(builderIndex);
                    Builder modifiedBuilder = builder;
                    // If exited and swept, reset the withdrawable epoch
                    if (!builder.getWithdrawableEpoch().equals(SpecConfig.FAR_FUTURE_EPOCH)
                        && builder.getBalance().isZero()) {
                      final UInt64 epoch = beaconStateAccessorsGloas.getCurrentEpoch(state);
                      modifiedBuilder =
                          modifiedBuilder.copyWithNewWithdrawableEpoch(
                              epoch.plus(specConfigGloas.getMinBuilderWithdrawabilityDelay()));
                    }
                    // Increase balance by deposit amount
                    modifiedBuilder =
                        modifiedBuilder.copyWithNewBalance(
                            modifiedBuilder.getBalance().plus(request.getAmount()));
                    builders.set(builderIndex, modifiedBuilder);
                  },
                  () -> {
                    if (isValidBuilderDepositSignature(request)) {
                      beaconStateMutatorsGloas.addBuilderToRegistry(
                          state,
                          pubkey,
                          PAYLOAD_BUILDER_VERSION,
                          getExecutionAddressUnchecked(request.getWithdrawalCredentials()),
                          request.getAmount(),
                          state.getSlot());
                    }
                  });
        });
  }

  // is_valid_builder_deposit_signature
  private boolean isValidBuilderDepositSignature(final BuilderDepositRequest request) {
    final DepositMessage depositMessage =
        new DepositMessage(
            request.getPubkey(), request.getWithdrawalCredentials(), request.getAmount());
    final Bytes32 domain = miscHelpers.computeDomain(Domain.BUILDER_DEPOSIT);
    final Bytes signingRoot = miscHelpers.computeSigningRoot(depositMessage, domain);
    return specConfig
        .getBLSSignatureVerifier()
        .verify(request.getPubkey(), signingRoot, request.getSignature());
  }

  @Override
  public void processBuilderExitRequests(
      final MutableBeaconState state, final List<BuilderExitRequest> builderExitRequests) {
    builderExitRequests.forEach(
        request -> {
          // process_builder_exit_request
          final BLSPublicKey pubkey = request.getPubkey();
          beaconStateAccessorsGloas
              .getBuilderIndex(state, pubkey)
              .map(UInt64::valueOf)
              .ifPresent(
                  builderIndex -> {
                    final SszMutableList<Builder> builders =
                        MutableBeaconStateGloas.required(state).getBuilders();
                    final Builder builder = builders.get(builderIndex.intValue());
                    if (!predicatesGloas.isActiveBuilder(state, builderIndex)) {
                      return;
                    }
                    if (!builder
                        .getExecutionAddress()
                        .getWrappedBytes()
                        .equals(request.getSourceAddress().getWrappedBytes())) {
                      return;
                    }
                    if (!beaconStateAccessorsGloas
                        .getPendingBalanceToWithdrawForBuilder(state, builderIndex)
                        .isZero()) {
                      return;
                    }
                    beaconStateMutatorsGloas.initiateBuilderExit(state, builderIndex);
                  });
        });
  }
}
