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

package tech.pegasys.teku.spec.logic.versions.eip8483.forktransition;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.BeaconStateEip8483;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class Eip8483StateUpgradeTest {

  private final UInt64 eip8483ForkEpoch = UInt64.valueOf(2);
  private final Spec spec = TestSpecFactory.createMinimalWithEip8483ForkEpoch(eip8483ForkEpoch);
  private final DataStructureUtil gloasDataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalGloas());

  @Test
  void upgradeCopiesGloasFieldsAndAddsEmptyRegistrations() {
    final UInt64 forkSlot = spec.computeStartSlotAtEpoch(eip8483ForkEpoch);
    final BeaconStateGloas preState =
        BeaconStateGloas.required(
            gloasDataStructureUtil.stateBuilderGloas(16, 3, 4).slot(forkSlot).build());

    final BeaconState upgraded =
        spec.forMilestone(SpecMilestone.EIP8483).getStateUpgrade().orElseThrow().upgrade(preState);
    final BeaconStateEip8483 postState = BeaconStateEip8483.required(upgraded);

    assertThat(postState.getFork().getPreviousVersion())
        .isEqualTo(preState.getFork().getCurrentVersion());
    assertThat(postState.getFork().getCurrentVersion())
        .isEqualTo(spec.getGenesisSpecConfig().getEip8483ForkVersion());
    assertThat(postState.getFork().getEpoch()).isEqualTo(eip8483ForkEpoch);

    assertThat(postState.getValidatorPqRegistrations()).isEmpty();
    assertThat(postState.getBuilderPqRegistrations()).isEmpty();

    // Every Gloas field is carried over unchanged
    assertThat(postState.getValidators()).isEqualTo(preState.getValidators());
    assertThat(postState.getBalances()).isEqualTo(preState.getBalances());
    assertThat(postState.getBuilders()).isEqualTo(preState.getBuilders());
    assertThat(postState.getBuilderPendingPayments())
        .isEqualTo(preState.getBuilderPendingPayments());
    assertThat(postState.getBuilderPendingWithdrawals())
        .isEqualTo(preState.getBuilderPendingWithdrawals());
    assertThat(postState.getLatestExecutionPayloadBid())
        .isEqualTo(preState.getLatestExecutionPayloadBid());
    assertThat(postState.getPayloadExpectedWithdrawals())
        .isEqualTo(preState.getPayloadExpectedWithdrawals());
    assertThat(postState.getPtcWindow()).isEqualTo(preState.getPtcWindow());
    assertThat(postState.getLatestBlockHash()).isEqualTo(preState.getLatestBlockHash());
    assertThat(postState.getProposerLookahead()).isEqualTo(preState.getProposerLookahead());
    assertThat(postState.getPendingDeposits()).isEqualTo(preState.getPendingDeposits());

    // and the post-state round-trips through the EIP-8483 schema
    final BeaconState roundTripped =
        spec.forMilestone(SpecMilestone.EIP8483)
            .getSchemaDefinitions()
            .getBeaconStateSchema()
            .sszDeserialize(postState.sszSerialize());
    assertThat(roundTripped.hashTreeRoot()).isEqualTo(postState.hashTreeRoot());
  }
}
