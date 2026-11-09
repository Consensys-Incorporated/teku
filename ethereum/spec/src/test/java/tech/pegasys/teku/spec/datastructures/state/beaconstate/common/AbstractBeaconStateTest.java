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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class AbstractBeaconStateTest<
    T extends BeaconState, TMutable extends MutableBeaconState> {

  private final Spec spec = createSpec();

  protected abstract Spec createSpec();

  private final SpecConfig genesisConfig = spec.getGenesisSpecConfig();
  private final BeaconStateSchema<T, TMutable> schema =
      getSchema(genesisConfig, spec.getGenesisSchemaDefinitions().getSchemaRegistry());
  protected DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  protected abstract BeaconStateSchema<T, TMutable> getSchema(
      final SpecConfig specConfig, final SchemaRegistry schemaRegistry);

  protected abstract T randomState();

  @Test
  public void simpleMutableBeaconStateTest() {
    UInt64 val1 = UInt64.valueOf(0x3333);
    BeaconState stateR1 =
        schema
            .createEmpty()
            .updated(
                state -> {
                  state.getBalances().appendElement(val1);
                });
    UInt64 v1 = stateR1.getBalances().getElement(0);

    assertThat(stateR1.getBalances().size()).isEqualTo(1);
    assertThat(stateR1.getBalances().getElement(0)).isEqualTo(UInt64.valueOf(0x3333));

    BeaconState stateR2 =
        stateR1.updated(
            state -> {
              state.getBalances().appendElement(UInt64.valueOf(0x4444));
            });
    UInt64 v2 = stateR2.getBalances().getElement(0);

    // check that view caching is effectively works and the value
    // is not recreated from tree node without need
    assertThat(v1).isSameAs(val1);
    assertThat(v2).isSameAs(val1);
  }

  @SuppressWarnings("unchecked")
  @Test
  public void equals_shouldReturnTrue() {
    final T state = randomState();
    final Object stateCopy = state.getSchema().createFromBackingNode(state.getBackingNode());

    assertThat(state).isNotSameAs(stateCopy);
    assertThat(state).isEqualTo(stateCopy);
    assertThat(state.hashCode()).isEqualTo(stateCopy.hashCode());
  }

  @SuppressWarnings("unchecked")
  @Test
  public void equals_shouldReturnFalse() {
    final T state = randomState();
    final T stateCopy = (T) state.updated(s -> s.setSlot(s.getSlot().plus(1)));

    assertThat(state).isNotSameAs(stateCopy);
    assertThat(state).isNotEqualTo(stateCopy);
    assertThat(state.hashCode()).isNotEqualTo(stateCopy.hashCode());
  }
}
