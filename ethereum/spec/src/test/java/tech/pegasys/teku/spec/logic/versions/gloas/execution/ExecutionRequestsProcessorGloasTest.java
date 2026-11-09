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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.BuilderDepositRequest;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.logic.common.ProcessorTestHelper;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;

class ExecutionRequestsProcessorGloasTest extends ProcessorTestHelper {

  private final SpecConfigGloas configGloas = SpecConfigGloas.required(spec.getGenesisSpecConfig());

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalGloas();
  }

  @Test
  public void processBuilderDepositRequests_shouldResetWithdrawableEpochWhenExitedBuilderIsSwept() {
    final UInt64 currentEpoch = UInt64.valueOf(100);
    final UInt64 slot = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 depositAmount = UInt64.valueOf(1_000);
    final UInt64 originalWithdrawableEpoch = currentEpoch.minus(1);
    final UInt64 expectedWithdrawableEpoch =
        currentEpoch.plus(configGloas.getMinBuilderWithdrawabilityDelay());
    final Builder builder =
        dataStructureUtil
            .builderBuilder()
            .balance(UInt64.ZERO)
            .withdrawableEpoch(originalWithdrawableEpoch)
            .build();
    final BeaconStateGloas postState = processBuilderDepositRequest(slot, builder, depositAmount);

    final Builder updatedBuilder = postState.getBuilders().get(0);
    assertThat(updatedBuilder.getBalance()).isEqualTo(depositAmount);
    assertThat(updatedBuilder.getWithdrawableEpoch()).isEqualTo(expectedWithdrawableEpoch);
  }

  @Test
  public void
      processBuilderDepositRequests_shouldNotResetWithdrawableEpochWhenExitedBuilderIsNotSwept() {
    final UInt64 currentEpoch = UInt64.valueOf(100);
    final UInt64 slot = spec.computeStartSlotAtEpoch(currentEpoch);
    final UInt64 existingBalance = UInt64.valueOf(1);
    final UInt64 depositAmount = UInt64.valueOf(1_000);
    final UInt64 originalWithdrawableEpoch = currentEpoch.minus(1);
    final Builder builder =
        dataStructureUtil
            .builderBuilder()
            .balance(existingBalance)
            .withdrawableEpoch(originalWithdrawableEpoch)
            .build();
    final BeaconStateGloas postState = processBuilderDepositRequest(slot, builder, depositAmount);

    final Builder updatedBuilder = postState.getBuilders().get(0);
    assertThat(updatedBuilder.getBalance()).isEqualTo(existingBalance.plus(depositAmount));
    assertThat(updatedBuilder.getWithdrawableEpoch()).isEqualTo(originalWithdrawableEpoch);
  }

  private BeaconStateGloas processBuilderDepositRequest(
      final UInt64 slot, final Builder builder, final UInt64 depositAmount) {
    final BeaconState preState =
        dataStructureUtil
            .stateBuilderGloas(0, 0, 0)
            .slot(slot)
            .build()
            .updated(
                mutableState -> {
                  final MutableBeaconStateGloas mutableStateGloas =
                      MutableBeaconStateGloas.required(mutableState);
                  final SszMutableList<Builder> builders = mutableStateGloas.getBuilders();
                  builders.append(builder);
                  mutableStateGloas.setBuilders(builders);
                });
    final BuilderDepositRequest request = builderDepositRequest(slot, builder, depositAmount);

    return BeaconStateGloas.required(
        preState.updated(
            mutableState ->
                getExecutionRequestsProcessor(preState)
                    .processBuilderDepositRequests(mutableState, List.of(request))));
  }

  private BuilderDepositRequest builderDepositRequest(
      final UInt64 slot, final Builder builder, final UInt64 depositAmount) {
    return getGloasSchemaDefinitions(slot)
        .getBuilderDepositRequestSchema()
        .create(
            builder.getPublicKey(),
            builderWithdrawalCredentials(builder),
            depositAmount,
            dataStructureUtil.randomSignature());
  }

  private SchemaDefinitionsGloas getGloasSchemaDefinitions(final UInt64 slot) {
    return SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
  }

  private Bytes32 builderWithdrawalCredentials(final Builder builder) {
    return Bytes32.wrap(
        Bytes.concatenate(
            Bytes.fromHexString("0xB00000000000000000000000"),
            builder.getExecutionAddress().getWrappedBytes()));
  }

  private ExecutionRequestsProcessorGloas getExecutionRequestsProcessor(final BeaconState state) {
    return (ExecutionRequestsProcessorGloas) spec.getExecutionRequestsProcessor(state.getSlot());
  }
}
