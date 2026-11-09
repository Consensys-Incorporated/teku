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

package tech.pegasys.teku.spec.logic.versions.heze.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;

public class HezeStateUpgrade implements StateUpgrade<BeaconStateGloas> {

  private final SpecConfigHeze specConfig;
  private final SchemaDefinitionsHeze schemaDefinitions;
  private final BeaconStateAccessorsGloas beaconStateAccessors;

  public HezeStateUpgrade(
      final SpecConfigHeze specConfig,
      final SchemaDefinitionsHeze schemaDefinitions,
      final BeaconStateAccessorsGloas beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateGloas upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateGloas preStateGloas = BeaconStateGloas.required(preState);

    return BeaconStateGloas.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedGloas(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getHezeForkVersion(),
                      epoch));

              state.setPreviousEpochParticipation(preStateGloas.getPreviousEpochParticipation());
              state.setCurrentEpochParticipation(preStateGloas.getCurrentEpochParticipation());
              state.setInactivityScores(preStateGloas.getInactivityScores());
              state.setCurrentSyncCommittee(preStateGloas.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateGloas.getNextSyncCommittee());

              state.setLatestBlockHash(preStateGloas.getLatestBlockHash());
              state.setNextWithdrawalIndex(preStateGloas.getNextWithdrawalIndex());
              state.setNextWithdrawalValidatorIndex(
                  preStateGloas.getNextWithdrawalValidatorIndex());
              state.setHistoricalSummaries(preStateGloas.getHistoricalSummaries());
              state.setDepositRequestsStartIndex(preStateGloas.getDepositRequestsStartIndex());
              state.setDepositBalanceToConsume(preStateGloas.getDepositBalanceToConsume());
              state.setExitBalanceToConsume(preStateGloas.getExitBalanceToConsume());
              state.setEarliestExitEpoch(preStateGloas.getEarliestExitEpoch());
              state.setConsolidationBalanceToConsume(
                  preStateGloas.getConsolidationBalanceToConsume());
              state.setEarliestConsolidationEpoch(preStateGloas.getEarliestConsolidationEpoch());
              state.setPendingDeposits(preStateGloas.getPendingDeposits());
              state.setPendingPartialWithdrawals(preStateGloas.getPendingPartialWithdrawals());
              state.setPendingConsolidations(preStateGloas.getPendingConsolidations());
              state.setProposerLookahead(preStateGloas.getProposerLookahead());
              state.setBuilders(preStateGloas.getBuilders());
              state.setNextWithdrawalBuilderIndex(preStateGloas.getNextWithdrawalBuilderIndex());
              state.setExecutionPayloadAvailability(
                  preStateGloas.getExecutionPayloadAvailability());
              state.setBuilderPendingPayments(preStateGloas.getBuilderPendingPayments());
              state.setBuilderPendingWithdrawals(preStateGloas.getBuilderPendingWithdrawals());
              state.setLatestExecutionPayloadBid(preStateGloas.getLatestExecutionPayloadBid());
              state.setPayloadExpectedWithdrawals(preStateGloas.getPayloadExpectedWithdrawals());
              state.setPtcWindow(preStateGloas.getPtcWindow());
            });
  }
}
