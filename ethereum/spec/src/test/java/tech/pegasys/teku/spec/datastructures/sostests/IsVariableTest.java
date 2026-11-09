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

package tech.pegasys.teku.spec.datastructures.sostests;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class IsVariableTest {
  private static final Spec SPEC = TestSpecFactory.createMinimalPhase0();
  private static final SchemaDefinitions SCHEMA_DEFINITIONS = SPEC.getGenesisSchemaDefinitions();

  static Stream<Arguments> variableSizeTypes() {
    return Stream.of(
        Arguments.of(SCHEMA_DEFINITIONS.getBeaconBlockBodySchema()),
        Arguments.of(SCHEMA_DEFINITIONS.getBeaconBlockSchema()),
        Arguments.of(SCHEMA_DEFINITIONS.getAttestationSchema()),
        Arguments.of(SCHEMA_DEFINITIONS.getAttesterSlashingSchema()),
        Arguments.of(SCHEMA_DEFINITIONS.getIndexedAttestationSchema()),
        Arguments.of(SCHEMA_DEFINITIONS.getBeaconStateSchema()),
        Arguments.of(
            BeaconStateSchemaPhase0.required(SCHEMA_DEFINITIONS.getBeaconStateSchema())
                .getPendingAttestationSchema()),
        Arguments.of(SCHEMA_DEFINITIONS.getAggregateAndProofSchema()));
  }

  static Stream<Arguments> fixedSizeTypes() {
    return Stream.of(
        Arguments.of(BeaconBlockHeader.SSZ_SCHEMA),
        Arguments.of(Eth1Data.SSZ_SCHEMA),
        Arguments.of(AttestationData.SSZ_SCHEMA),
        Arguments.of(DepositData.SSZ_SCHEMA),
        Arguments.of(Deposit.SSZ_SCHEMA),
        Arguments.of(ProposerSlashing.SSZ_SCHEMA),
        Arguments.of(VoluntaryExit.SSZ_SCHEMA),
        Arguments.of(Checkpoint.SSZ_SCHEMA),
        Arguments.of(Fork.SSZ_SCHEMA),
        Arguments.of(SCHEMA_DEFINITIONS.getHistoricalBatchSchema()),
        Arguments.of(Validator.SSZ_SCHEMA));
  }

  @ParameterizedTest
  @MethodSource("variableSizeTypes")
  void testTheTypeIsVariableSize(final SszSchema<?> type) {
    assertFalse(type.isFixedSize());
  }

  @ParameterizedTest
  @MethodSource("fixedSizeTypes")
  void testTheTypeIsFixedSize(final SszSchema<?> type) {
    assertTrue(type.isFixedSize());
  }
}
