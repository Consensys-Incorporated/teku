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

package tech.pegasys.teku.spec.datastructures.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRangeRequestMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.GoodbyeMessage;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositMessage;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.ForkData;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

/**
 * This is in the wrong module but we want to be able to access the datastructure types to check
 * they produce the right value which is a much more valuable test than using fake classes
 */
public class LengthBoundsCalculatorTest {
  final Spec spec = TestSpecFactory.createMainnetPhase0();
  final SchemaDefinitions schemaDefinitions = spec.getGenesisSchemaDefinitions();

  @ParameterizedTest(name = "{0}")
  @MethodSource("generateParameters")
  void shouldCalculateCorrectLengthBounds(
      final SchemaProvider type, final SszLengthBounds expected) {
    SszSchema<?> schema = type.get(schemaDefinitions);
    assertThat(schema.getSszLengthBounds()).isEqualTo(expected);
  }

  // Expected values taken from https://gist.github.com/protolambda/db75c7faa1e94f2464787a480e5d613e
  static Stream<Arguments> generateParameters() {
    return Stream.of(
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getAggregateAndProofSchema,
            SszLengthBounds.ofBytes(337, 593)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getAttestationSchema,
            SszLengthBounds.ofBytes(229, 485)),
        Arguments.of(getProvider(AttestationData.SSZ_SCHEMA), SszLengthBounds.ofBytes(128, 128)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getAttesterSlashingSchema,
            SszLengthBounds.ofBytes(464, 33232)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getBeaconBlockSchema,
            SszLengthBounds.ofBytes(304, 157656)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getBeaconBlockBodySchema,
            SszLengthBounds.ofBytes(220, 157572)),
        Arguments.of(getProvider(BeaconBlockHeader.SSZ_SCHEMA), SszLengthBounds.ofBytes(112, 112)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getBeaconStateSchema,
            SszLengthBounds.ofBytes(2687377, 141837543039377L)),
        Arguments.of(getProvider(Checkpoint.SSZ_SCHEMA), SszLengthBounds.ofBytes(40, 40)),
        Arguments.of(getProvider(Deposit.SSZ_SCHEMA), SszLengthBounds.ofBytes(1240, 1240)),
        Arguments.of(getProvider(DepositData.SSZ_SCHEMA), SszLengthBounds.ofBytes(184, 184)),
        Arguments.of(getProvider(DepositMessage.SSZ_SCHEMA), SszLengthBounds.ofBytes(88, 88)),
        Arguments.of(getProvider(Eth1Data.SSZ_SCHEMA), SszLengthBounds.ofBytes(72, 72)),
        Arguments.of(getProvider(Fork.SSZ_SCHEMA), SszLengthBounds.ofBytes(16, 16)),
        Arguments.of(getProvider(ForkData.SSZ_SCHEMA), SszLengthBounds.ofBytes(36, 36)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getHistoricalBatchSchema,
            SszLengthBounds.ofBytes(524288, 524288)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getIndexedAttestationSchema,
            SszLengthBounds.ofBytes(228, 16612)),
        Arguments.of(
            (SchemaProvider)
                definitions ->
                    BeaconStateSchemaPhase0.required(definitions.getBeaconStateSchema())
                        .getPendingAttestationSchema(),
            SszLengthBounds.ofBytes(149, 405)),
        Arguments.of(getProvider(ProposerSlashing.SSZ_SCHEMA), SszLengthBounds.ofBytes(416, 416)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getSignedAggregateAndProofSchema,
            SszLengthBounds.ofBytes(437, 693)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getSignedBeaconBlockSchema,
            SszLengthBounds.ofBytes(404, 157756)),
        Arguments.of(
            getProvider(SignedBeaconBlockHeader.SSZ_SCHEMA), SszLengthBounds.ofBytes(208, 208)),
        Arguments.of(
            getProvider(SignedVoluntaryExit.SSZ_SCHEMA), SszLengthBounds.ofBytes(112, 112)),
        Arguments.of(getProvider(Validator.SSZ_SCHEMA), SszLengthBounds.ofBytes(121, 121)),
        Arguments.of(getProvider(VoluntaryExit.SSZ_SCHEMA), SszLengthBounds.ofBytes(16, 16)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getMetadataMessageSchema,
            SszLengthBounds.ofBytes(16, 16)),
        Arguments.of(
            (SchemaProvider) SchemaDefinitions::getStatusMessageSchema,
            SszLengthBounds.ofBytes(84, 84)),
        Arguments.of(getProvider(GoodbyeMessage.SSZ_SCHEMA), SszLengthBounds.ofBytes(8, 8)),
        Arguments.of(
            getProvider(BeaconBlocksByRangeRequestMessage.SSZ_SCHEMA),
            SszLengthBounds.ofBytes(24, 24)));
  }

  @Deprecated
  private static SchemaProvider getProvider(final SszSchema<?> schema) {
    return __ -> schema;
  }

  private interface SchemaProvider {
    SszSchema<?> get(SchemaDefinitions schemas);
  }
}
