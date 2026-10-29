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

package tech.pegasys.teku.spec.logic.common.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators.ValidatorExitContext;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BeaconStateMutatorsTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final SpecVersion genesisSpec = spec.getGenesisSpec();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconStateMutators mutators = genesisSpec.beaconStateMutators();

  @Test
  void exitContextCountsTheValidatorsExitingAtTheLatestExitEpoch() {
    // Minimal spec: 64 validators, so the registry spans several supernodes
    final BeaconState base = dataStructureUtil.randomBeaconState(64);
    final UInt64 currentEpoch = spec.getCurrentEpoch(base);
    final UInt64 activationExitEpoch =
        genesisSpec.miscHelpers().computeActivationExitEpoch(currentEpoch);
    final UInt64 latest = activationExitEpoch.plus(5);
    final BeaconState state =
        withExitEpochs(
            base,
            List.of(
                exit(1, latest),
                exit(2, latest.minus(1)),
                exit(3, latest),
                exit(40, latest),
                exit(63, activationExitEpoch)));

    final ValidatorExitContext context = mutators.createValidatorExitContextSupplier(state).get();

    assertThat(context.getExitQueueEpoch()).isEqualTo(latest);
    assertThat(context.getExitQueueChurn()).isEqualTo(UInt64.valueOf(3));
    assertThat(context.getChurnLimit())
        .isEqualTo(genesisSpec.beaconStateAccessors().getValidatorChurnLimit(state));
  }

  @Test
  void exitContextStartsAtTheActivationExitEpochWhenNoValidatorExitsLater() {
    final BeaconState base = dataStructureUtil.randomBeaconState(64);
    final UInt64 currentEpoch = spec.getCurrentEpoch(base);
    final UInt64 activationExitEpoch =
        genesisSpec.miscHelpers().computeActivationExitEpoch(currentEpoch);
    final BeaconState state =
        withExitEpochs(base, List.of(exit(5, activationExitEpoch.minus(1)), exit(6, UInt64.ONE)));

    final ValidatorExitContext context = mutators.createValidatorExitContextSupplier(state).get();

    assertThat(context.getExitQueueEpoch()).isEqualTo(activationExitEpoch);
    assertThat(context.getExitQueueChurn()).isEqualTo(UInt64.ZERO);
  }

  @Test
  void exitContextMatchesTheReferenceLoopOverValidatorViews() {
    final BeaconState base = dataStructureUtil.randomBeaconState(64);
    final UInt64 currentEpoch = spec.getCurrentEpoch(base);
    final UInt64 activationExitEpoch =
        genesisSpec.miscHelpers().computeActivationExitEpoch(currentEpoch);
    final BeaconState state =
        withExitEpochs(
            base,
            List.of(
                exit(0, activationExitEpoch.plus(2)),
                exit(7, activationExitEpoch.plus(2)),
                exit(8, activationExitEpoch.plus(1)),
                exit(31, activationExitEpoch.plus(2)),
                exit(32, activationExitEpoch)));

    final ValidatorExitContext context = mutators.createValidatorExitContextSupplier(state).get();

    // The reference computation, reading every validator through its view
    UInt64 exitQueueEpoch = UInt64.ZERO;
    int churn = 0;
    for (final Validator validator : state.getValidators()) {
      final UInt64 exitEpoch = validator.getExitEpoch();
      if (exitEpoch.equals(FAR_FUTURE_EPOCH)) {
        continue;
      }
      if (exitEpoch.equals(exitQueueEpoch)) {
        churn++;
      } else if (exitEpoch.isGreaterThan(exitQueueEpoch)) {
        exitQueueEpoch = exitEpoch;
        churn = 1;
      }
    }
    if (activationExitEpoch.isGreaterThan(exitQueueEpoch)) {
      exitQueueEpoch = activationExitEpoch;
      churn = 0;
    }
    assertThat(context.getExitQueueEpoch()).isEqualTo(exitQueueEpoch);
    assertThat(context.getExitQueueChurn()).isEqualTo(UInt64.valueOf(churn));
  }

  private record Exit(int index, UInt64 epoch) {}

  private static Exit exit(final int index, final UInt64 epoch) {
    return new Exit(index, epoch);
  }

  private static BeaconState withExitEpochs(final BeaconState state, final List<Exit> exits) {
    return state.updated(
        mutable -> {
          // Every validator starts with no exit set, so only the listed ones count
          for (int i = 0; i < mutable.getValidators().size(); i++) {
            mutable.getValidators().update(i, v -> v.withExitEpoch(FAR_FUTURE_EPOCH));
          }
          for (final Exit exit : exits) {
            mutable.getValidators().update(exit.index(), v -> v.withExitEpoch(exit.epoch()));
          }
        });
  }
}
