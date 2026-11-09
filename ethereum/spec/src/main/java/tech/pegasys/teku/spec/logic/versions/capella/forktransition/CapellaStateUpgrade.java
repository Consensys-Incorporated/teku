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

package tech.pegasys.teku.spec.logic.versions.capella.forktransition;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class CapellaStateUpgrade implements StateUpgrade<BeaconStateCapella> {

  private final SpecConfigCapella specConfig;
  private final SchemaDefinitionsCapella schemaDefinitions;
  private final BeaconStateAccessorsAltair beaconStateAccessors;

  public CapellaStateUpgrade(
      final SpecConfigCapella specConfig,
      final SchemaDefinitionsCapella schemaDefinitions,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateCapella upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    BeaconStateBellatrix preStateBellatrix = BeaconStateBellatrix.required(preState);
    return BeaconStateCapella.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedCapella(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateBellatrix.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(
                  preStateBellatrix.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateBellatrix.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateBellatrix.getNextSyncCommittee());
              state.setInactivityScores(preStateBellatrix.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getCapellaForkVersion(),
                      epoch));

              final ExecutionPayloadHeader bellatrixHeader =
                  preStateBellatrix.getLatestExecutionPayloadHeaderRequired();
              final ExecutionPayloadHeader upgradedExecutionPayloadHeader =
                  schemaDefinitions
                      .getExecutionPayloadHeaderSchema()
                      .createExecutionPayloadHeader(
                          builder ->
                              builder
                                  .parentHash(bellatrixHeader.getParentHash())
                                  .feeRecipient(bellatrixHeader.getFeeRecipient())
                                  .stateRoot(bellatrixHeader.getStateRoot())
                                  .receiptsRoot(bellatrixHeader.getReceiptsRoot())
                                  .logsBloom(bellatrixHeader.getLogsBloom())
                                  .prevRandao(bellatrixHeader.getPrevRandao())
                                  .blockNumber(bellatrixHeader.getBlockNumber())
                                  .gasLimit(bellatrixHeader.getGasLimit())
                                  .gasUsed(bellatrixHeader.getGasUsed())
                                  .timestamp(bellatrixHeader.getTimestamp())
                                  .extraData(bellatrixHeader.getExtraData())
                                  .baseFeePerGas(bellatrixHeader.getBaseFeePerGas())
                                  .blockHash(bellatrixHeader.getBlockHash())
                                  .transactionsRoot(bellatrixHeader.getTransactionsRoot())
                                  .withdrawalsRoot(() -> Bytes32.ZERO));

              state.setLatestExecutionPayloadHeader(upgradedExecutionPayloadHeader);

              state.setNextWithdrawalValidatorIndex(UInt64.ZERO);
              state.setNextWithdrawalIndex(UInt64.ZERO);
              state.setHistoricalSummaries(
                  BeaconStateSchemaCapella.required(state.getBeaconStateSchema())
                      .getHistoricalSummariesSchema()
                      .createFromElements(List.of()));
            });
  }
}
