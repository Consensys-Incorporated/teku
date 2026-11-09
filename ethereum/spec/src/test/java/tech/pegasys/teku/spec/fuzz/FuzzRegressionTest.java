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

package tech.pegasys.teku.spec.fuzz;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import java.net.URL;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.util.BeaconBlockBodyLists;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.BlockProcessingException;

public class FuzzRegressionTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();

  @Test
  void shouldRejectAttesterSlashingWithInvalidValidatorIndex() throws Exception {
    final BeaconState state =
        load("issue2345/state.ssz", spec.getGenesisSchemaDefinitions().getBeaconStateSchema());
    final AttesterSlashing slashing =
        load(
            "issue2345/attester_slashing.ssz",
            spec.getGenesisSchemaDefinitions().getAttesterSlashingSchema());
    SszList<AttesterSlashing> slashings =
        BeaconBlockBodyLists.ofSpec(spec).createAttesterSlashings(slashing);

    assertThatThrownBy(
            () ->
                state.updated(
                    mutableState ->
                        spec.getBlockProcessor(mutableState.getSlot())
                            .processAttesterSlashings(mutableState, slashings)))
        .isInstanceOf(BlockProcessingException.class);
  }

  private <T extends SszData> T load(final String resource, final SszSchema<T> type)
      throws Exception {
    final URL resourceUrl = FuzzRegressionTest.class.getResource(resource);
    final Bytes data = Bytes.wrap(Resources.toByteArray(resourceUrl));
    return type.sszDeserialize(data);
  }
}
