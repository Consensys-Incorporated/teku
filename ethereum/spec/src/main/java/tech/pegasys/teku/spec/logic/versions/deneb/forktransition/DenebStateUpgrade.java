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

package tech.pegasys.teku.spec.logic.versions.deneb.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadHeaderCapella;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;

public class DenebStateUpgrade implements StateUpgrade<BeaconStateDeneb> {

  private final SpecConfigDeneb specConfig;
  private final SchemaDefinitionsDeneb schemaDefinitions;
  private final BeaconStateAccessorsAltair beaconStateAccessors;

  public DenebStateUpgrade(
      final SpecConfigDeneb specConfig,
      final SchemaDefinitionsDeneb schemaDefinitions,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateDeneb upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStateCapella preStateCapella = BeaconStateCapella.required(preState);
    return BeaconStateDeneb.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedDeneb(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateCapella.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateCapella.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateCapella.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateCapella.getNextSyncCommittee());
              state.setInactivityScores(preStateCapella.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getDenebForkVersion(),
                      epoch));

              final ExecutionPayloadHeaderCapella capellaHeader =
                  preStateCapella
                      .getLatestExecutionPayloadHeaderRequired()
                      .toVersionCapella()
                      .orElseThrow();
              final ExecutionPayloadHeader upgradedExecutionPayloadHeader =
                  schemaDefinitions
                      .getExecutionPayloadHeaderSchema()
                      .createExecutionPayloadHeader(
                          builder ->
                              builder
                                  .parentHash(capellaHeader.getParentHash())
                                  .feeRecipient(capellaHeader.getFeeRecipient())
                                  .stateRoot(capellaHeader.getStateRoot())
                                  .receiptsRoot(capellaHeader.getReceiptsRoot())
                                  .logsBloom(capellaHeader.getLogsBloom())
                                  .prevRandao(capellaHeader.getPrevRandao())
                                  .blockNumber(capellaHeader.getBlockNumber())
                                  .gasLimit(capellaHeader.getGasLimit())
                                  .gasUsed(capellaHeader.getGasUsed())
                                  .timestamp(capellaHeader.getTimestamp())
                                  .extraData(capellaHeader.getExtraData())
                                  .baseFeePerGas(capellaHeader.getBaseFeePerGas())
                                  .blockHash(capellaHeader.getBlockHash())
                                  .transactionsRoot(capellaHeader.getTransactionsRoot())
                                  .withdrawalsRoot(capellaHeader::getWithdrawalsRoot)
                                  .blobGasUsed(() -> UInt64.ZERO)
                                  .excessBlobGas(() -> UInt64.ZERO));

              state.setLatestExecutionPayloadHeader(upgradedExecutionPayloadHeader);

              state.setNextWithdrawalValidatorIndex(
                  preStateCapella.getNextWithdrawalValidatorIndex());
              state.setNextWithdrawalIndex(preStateCapella.getNextWithdrawalIndex());
              state.setHistoricalSummaries(preStateCapella.getHistoricalSummaries());
            });
  }
}
