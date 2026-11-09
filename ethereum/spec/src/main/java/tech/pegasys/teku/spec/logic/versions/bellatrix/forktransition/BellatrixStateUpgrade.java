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

package tech.pegasys.teku.spec.logic.versions.bellatrix.forktransition;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsBellatrix;

public class BellatrixStateUpgrade implements StateUpgrade<BeaconStateBellatrix> {
  final SpecConfigBellatrix specConfig;
  final SchemaDefinitionsBellatrix schemaDefinitions;
  final BeaconStateAccessorsAltair beaconStateAccessors;

  public BellatrixStateUpgrade(
      final SpecConfigBellatrix specConfig,
      final SchemaDefinitionsBellatrix schemaDefinitions,
      final BeaconStateAccessorsAltair beaconStateAccessors) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
  }

  @Override
  public BeaconStateBellatrix upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    BeaconStateAltair preStateAltair = BeaconStateAltair.required(preState);

    return BeaconStateBellatrix.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedBellatrix(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setCurrentEpochParticipation(preStateAltair.getCurrentEpochParticipation());
              state.setPreviousEpochParticipation(preStateAltair.getPreviousEpochParticipation());
              state.setCurrentSyncCommittee(preStateAltair.getCurrentSyncCommittee());
              state.setNextSyncCommittee(preStateAltair.getNextSyncCommittee());
              state.setInactivityScores(preStateAltair.getInactivityScores());

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getBellatrixForkVersion(),
                      epoch));

              state.setLatestExecutionPayloadHeader(
                  schemaDefinitions.getExecutionPayloadHeaderSchema().getDefault());
            });
  }
}
