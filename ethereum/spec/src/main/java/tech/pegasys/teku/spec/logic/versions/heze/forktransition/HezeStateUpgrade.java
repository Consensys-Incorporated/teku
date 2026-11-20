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
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq.BeaconStatePq;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.versions.pq.forktransition.PqStateUpgrade;
import tech.pegasys.teku.spec.logic.versions.pq.helpers.BeaconStateAccessorsPq;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;

/**
 * On this branch Heze builds on PQ and keeps its state layout, so the upgrade only changes the
 * fork. When PQ is scheduled at the same epoch as Heze (and so overshadowed by it), the pre-state
 * is an EIP-8483 or Gloas state, which goes through {@code upgrade_to_pq} first.
 */
public class HezeStateUpgrade implements StateUpgrade<BeaconStatePq> {

  private final SpecConfigHeze specConfig;
  private final SchemaDefinitionsHeze schemaDefinitions;
  private final BeaconStateAccessorsPq beaconStateAccessors;
  private final PqStateUpgrade pqStateUpgrade;

  public HezeStateUpgrade(
      final SpecConfigHeze specConfig,
      final SchemaDefinitionsHeze schemaDefinitions,
      final BeaconStateAccessorsPq beaconStateAccessors,
      final PqStateUpgrade pqStateUpgrade) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.pqStateUpgrade = pqStateUpgrade;
  }

  @Override
  public BeaconStatePq upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final BeaconStatePq pqState =
        preState
            .toVersionPq()
            .map(
                state ->
                    BeaconStatePq.required(
                        schemaDefinitions
                            .getPqBeaconStateSchema()
                            .createFromBackingNode(state.getBackingNode())))
            .orElseGet(() -> pqStateUpgrade.upgrade(preState));
    return pqState.updatedPq(
        state ->
            state.setFork(
                new Fork(
                    preState.getFork().getCurrentVersion(),
                    specConfig.getHezeForkVersion(),
                    epoch)));
  }
}
