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

package tech.pegasys.teku.spec.logic.versions.fulu.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

public class FuluStateUpgrade implements StateUpgrade<BeaconStateElectra> {

  private final SpecConfigFulu specConfig;
  private final BeaconStateAccessorsFulu beaconStateAccessors;
  private final SchemaDefinitionsFulu schemaDefinitions;
  private final MiscHelpersFulu miscHelpers;

  public FuluStateUpgrade(
      final SpecConfigFulu specConfig,
      final SchemaDefinitionsFulu schemaDefinitions,
      final BeaconStateAccessorsFulu beaconStateAccessors,
      final MiscHelpersFulu miscHelpers) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
  }

  @Override
  public BeaconStateFulu upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateElectra preStateElectra = BeaconStateElectra.required(preState);

    return BeaconStateFulu.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedFulu(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateElectra.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateElectra.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateElectra.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateElectra.getNextSyncCommittee());
              state.setInactivityScores(preStateElectra.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getFuluForkVersion(),
                      epoch));

              state.setLatestExecutionPayloadHeader(
                  preStateElectra.getLatestExecutionPayloadHeaderRequired());
              state.setNextWithdrawalValidatorIndex(
                  preStateElectra.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateElectra.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateElectra.getHistoricalSummaries());
              state.setDepositRequestsStartIndex(preStateElectra.getDepositRequestsStartIndex());
              state.setDepositBalanceToConsume(preStateElectra.getDepositBalanceToConsume());
              state.setExitBalanceToConsume(preStateElectra.getExitBalanceToConsume());
              state.setEarliestExitEpoch(preStateElectra.getEarliestExitEpoch());
              state.setConsolidationBalanceToConsume(
                  preStateElectra.getConsolidationBalanceToConsume());
              state.setEarliestConsolidationEpoch(preStateElectra.getEarliestConsolidationEpoch());
              state.setPendingDeposits(preStateElectra.getPendingDeposits());
              state.setPendingPartialWithdrawals(preStateElectra.getPendingPartialWithdrawals());
              state.setPendingConsolidations(preStateElectra.getPendingConsolidations());

              state.setProposerLookahead(
                  schemaDefinitions
                      .getProposerLookaheadSchema()
                      .of(
                          miscHelpers.initializeProposerLookahead(
                              preStateElectra, beaconStateAccessors)));
            });
  }
}
