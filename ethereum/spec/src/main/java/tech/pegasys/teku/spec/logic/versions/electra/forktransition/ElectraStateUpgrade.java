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

package tech.pegasys.teku.spec.logic.versions.electra.forktransition;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.Comparator;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateMutatorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.MiscHelpersElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;

public class ElectraStateUpgrade implements StateUpgrade<BeaconStateDeneb> {

  private final SpecConfigElectra specConfig;
  private final SchemaDefinitionsElectra schemaDefinitions;
  private final BeaconStateAccessorsElectra beaconStateAccessors;
  private final BeaconStateMutatorsElectra beaconStateMutators;

  public ElectraStateUpgrade(
      final SpecConfigElectra specConfig,
      final SchemaDefinitionsElectra schemaDefinitions,
      final BeaconStateAccessorsElectra beaconStateAccessors,
      final BeaconStateMutatorsElectra beaconStateMutators) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.beaconStateMutators = beaconStateMutators;
  }

  @Override
  public BeaconStateElectra upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateDeneb preStateDeneb = BeaconStateDeneb.required(preState);
    final PredicatesElectra predicatesElectra = new PredicatesElectra(specConfig);
    final MiscHelpersElectra miscHelpersElectra =
        new MiscHelpersElectra(specConfig, predicatesElectra, schemaDefinitions);
    final UInt64 activationExitEpoch = miscHelpersElectra.computeActivationExitEpoch(epoch);
    return BeaconStateElectra.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedElectra(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateDeneb.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateDeneb.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateDeneb.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateDeneb.getNextSyncCommittee());
              state.setInactivityScores(preStateDeneb.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getElectraForkVersion(),
                      epoch));

              state.setLatestExecutionPayloadHeader(
                  preStateDeneb.getLatestExecutionPayloadHeaderRequired());
              state.setNextWithdrawalValidatorIndex(
                  preStateDeneb.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateDeneb.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateDeneb.getHistoricalSummaries());
              state.setDepositRequestsStartIndex(
                  SpecConfigElectra.UNSET_DEPOSIT_REQUESTS_START_INDEX);
              state.setDepositBalanceToConsume(UInt64.ZERO);
              state.setExitBalanceToConsume(
                  beaconStateAccessors.getActivationExitChurnLimit(state));
              state.setEarliestExitEpoch(findEarliestExitEpoch(state, activationExitEpoch));
              state.setConsolidationBalanceToConsume(
                  beaconStateAccessors.getConsolidationChurnLimit(state));
              state.setEarliestConsolidationEpoch(activationExitEpoch);

              final SszMutableList<Validator> validators = state.getValidators();

              // Add validators that are not yet active to pending balance deposits
              IntStream.range(0, validators.size())
                  .filter(
                      index -> validators.get(index).getActivationEpoch().equals(FAR_FUTURE_EPOCH))
                  .boxed()
                  .sorted(
                      Comparator.comparing(
                              (Integer index) ->
                                  validators.get(index).getActivationEligibilityEpoch())
                          .thenComparing(index -> index))
                  .forEach(
                      index ->
                          beaconStateMutators.queueEntireBalanceAndResetValidator(state, index));

              // Ensure early adopters of compounding credentials go through the activation churn
              IntStream.range(0, validators.size())
                  .forEach(
                      index -> {
                        if (predicatesElectra.hasCompoundingWithdrawalCredential(
                            validators.get(index))) {
                          beaconStateMutators.queueExcessActiveBalance(state, index);
                        }
                      });
            });
  }

  private UInt64 findEarliestExitEpoch(final BeaconState state, final UInt64 activationExitEpoch) {
    final UInt64 maxExitEpochFromValidatorSet =
        state.getValidators().stream()
            .map(Validator::getExitEpoch)
            .filter(exitEpoch -> !exitEpoch.equals(FAR_FUTURE_EPOCH))
            .max(UInt64::compareTo)
            .orElse(UInt64.ZERO);
    return maxExitEpochFromValidatorSet.max(activationExitEpoch).increment();
  }
}
