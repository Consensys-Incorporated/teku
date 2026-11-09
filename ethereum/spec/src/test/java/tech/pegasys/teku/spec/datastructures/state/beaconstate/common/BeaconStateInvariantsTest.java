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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.common;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_TIME_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_TIME_SCHEMA;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_SCHEMA;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.SLOT_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.SLOT_SCHEMA;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateInvariantsTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  public void extractSlot_randomValues() {
    for (int i = 0; i < 50; i++) {
      final BeaconState state = dataStructureUtil.randomBeaconState();
      final Bytes stateBytes = state.sszSerialize();

      final UInt64 expectedSlot = state.getSlot();
      final UInt64 result = BeaconStateInvariants.extractSlot(stateBytes);
      assertThat(result).isEqualTo(expectedSlot);
    }
  }

  @Test
  public void extractSlot_zero() {
    final BeaconState state = dataStructureUtil.stateBuilderPhase0().slot(UInt64.ZERO).build();
    final Bytes stateBytes = state.sszSerialize();

    final UInt64 result = BeaconStateInvariants.extractSlot(stateBytes);
    assertThat(result).isEqualTo(UInt64.ZERO);
  }

  @Test
  public void extractSlot_maxValue() {
    final BeaconState state = dataStructureUtil.stateBuilderPhase0().slot(UInt64.MAX_VALUE).build();
    final Bytes stateBytes = state.sszSerialize();

    final UInt64 result = BeaconStateInvariants.extractSlot(stateBytes);
    assertThat(result).isEqualTo(UInt64.MAX_VALUE);
  }

  @Test
  public void genesisTimeSchemaShouldMatchField() {
    assertThat(GENESIS_TIME_SCHEMA).isEqualTo(GENESIS_TIME_FIELD.getSchema().get());
  }

  @Test
  public void genesisValidatorsRootSchemaShouldMatchField() {
    assertThat(GENESIS_VALIDATORS_ROOT_SCHEMA)
        .isEqualTo(GENESIS_VALIDATORS_ROOT_FIELD.getSchema().get());
  }

  @Test
  public void slotSchemaShouldMatchField() {
    assertThat(SLOT_SCHEMA).isEqualTo(SLOT_FIELD.getSchema().get());
  }
}
