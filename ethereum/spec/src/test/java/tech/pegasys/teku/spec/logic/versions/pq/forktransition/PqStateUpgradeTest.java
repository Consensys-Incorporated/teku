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

package tech.pegasys.teku.spec.logic.versions.pq.forktransition;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.eip8483.ValidatorPqRegistrationRequestSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.BeaconStateEip8483;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.BeaconStateSchemaEip8483;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.ValidatorPqRegistration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsEip8483;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class PqStateUpgradeTest {

  private final UInt64 pqForkEpoch = UInt64.valueOf(2);
  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(pqForkEpoch);
  private final DataStructureUtil gloasDataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalGloas());

  @Test
  void upgradeFromEip8483CarriesRegistrations() {
    final UInt64 forkSlot = spec.computeStartSlotAtEpoch(pqForkEpoch);
    final BeaconStateEip8483 eip8483State = eip8483StateWithRegistration(forkSlot);

    final BeaconStateEip8483 postState = upgradeToPq(eip8483State);

    assertThat(postState.getFork().getPreviousVersion())
        .isEqualTo(spec.getGenesisSpecConfig().getEip8483ForkVersion());
    assertThat(postState.getFork().getCurrentVersion())
        .isEqualTo(spec.getGenesisSpecConfig().getPqForkVersion());
    assertThat(postState.getFork().getEpoch()).isEqualTo(pqForkEpoch);

    assertThat(postState.getValidatorPqRegistrations())
        .isEqualTo(eip8483State.getValidatorPqRegistrations())
        .hasSize(1);
    assertThat(postState.getBuilderPqRegistrations())
        .isEqualTo(eip8483State.getBuilderPqRegistrations());
    assertThat(postState.getValidators()).isEqualTo(eip8483State.getValidators());
    assertThat(postState.getBuilders()).isEqualTo(eip8483State.getBuilders());
    assertThat(postState.getPtcWindow()).isEqualTo(eip8483State.getPtcWindow());

    // and the post-state round-trips through the PQ schema
    final BeaconState roundTripped =
        spec.forMilestone(SpecMilestone.PQ)
            .getSchemaDefinitions()
            .getBeaconStateSchema()
            .sszDeserialize(postState.sszSerialize());
    assertThat(roundTripped.hashTreeRoot()).isEqualTo(postState.hashTreeRoot());
  }

  @Test
  void upgradeFromGloasAddsEmptyRegistrations() {
    // EIP-8483 scheduled at the same epoch as PQ is overshadowed, so PQ upgrades a Gloas state
    final UInt64 forkSlot = spec.computeStartSlotAtEpoch(pqForkEpoch);
    final BeaconStateGloas preState =
        BeaconStateGloas.required(
            gloasDataStructureUtil.stateBuilderGloas(16, 3, 4).slot(forkSlot).build());

    final BeaconStateEip8483 postState = upgradeToPq(preState);

    assertThat(postState.getFork().getPreviousVersion())
        .isEqualTo(preState.getFork().getCurrentVersion());
    assertThat(postState.getFork().getCurrentVersion())
        .isEqualTo(spec.getGenesisSpecConfig().getPqForkVersion());
    assertThat(postState.getValidatorPqRegistrations()).isEmpty();
    assertThat(postState.getBuilderPqRegistrations()).isEmpty();
    assertThat(postState.getValidators()).isEqualTo(preState.getValidators());
    assertThat(postState.getLatestExecutionPayloadBid())
        .isEqualTo(preState.getLatestExecutionPayloadBid());
  }

  private BeaconStateEip8483 upgradeToPq(final BeaconState preState) {
    return BeaconStateEip8483.required(
        spec.forMilestone(SpecMilestone.PQ).getStateUpgrade().orElseThrow().upgrade(preState));
  }

  private BeaconStateEip8483 eip8483StateWithRegistration(final UInt64 slot) {
    final BeaconStateGloas gloasState =
        BeaconStateGloas.required(
            gloasDataStructureUtil.stateBuilderGloas(16, 3, 4).slot(slot).build());
    final BeaconStateEip8483 eip8483State =
        BeaconStateEip8483.required(
            spec.forMilestone(SpecMilestone.EIP8483)
                .getStateUpgrade()
                .orElseThrow()
                .upgrade(gloasState));
    final SchemaDefinitionsEip8483 schemaDefinitions =
        SchemaDefinitionsEip8483.required(
            spec.forMilestone(SpecMilestone.EIP8483).getSchemaDefinitions());
    final ValidatorPqRegistration registration =
        schemaDefinitions
            .getValidatorPqRegistrationSchema()
            .create(
                UInt64.valueOf(3),
                Bytes.repeat((byte) 7, ValidatorPqRegistrationRequestSchema.PQ_DATA_SIZE));
    final BeaconStateSchemaEip8483 stateSchema =
        BeaconStateSchemaEip8483.required(schemaDefinitions.getBeaconStateSchema());
    return BeaconStateEip8483.required(
        eip8483State.updatedEip8483(
            state ->
                state.setValidatorPqRegistrations(
                    stateSchema.getValidatorPqRegistrationsSchema().of(registration))));
  }
}
