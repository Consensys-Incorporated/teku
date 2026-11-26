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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.pq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.common.io.Resources;
import java.io.IOException;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCollectionSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsPq;

class BeaconStateSchemaPqTest {

  private static final Bytes32 FIXTURE_GENESIS_VALIDATORS_ROOT =
      Bytes32.fromHexString("0x1532f07efb06c2d3db78b66ebfebbc2ec80e54163c9eb62428df0bf3d7ad874a");

  private final Spec spec = TestSpecFactory.createMinimalPq();
  private final BeaconStateSchemaPq schema =
      SchemaDefinitionsPq.required(spec.forMilestone(SpecMilestone.PQ).getSchemaDefinitions())
          .getPqBeaconStateSchema();

  @Test
  void dropsSyncCommitteesAndRegistrationsAndAddsPqFields() {
    assertThat(schema.getFieldIndex(BeaconStateFields.CURRENT_SYNC_COMMITTEE)).isEqualTo(-1);
    assertThat(schema.getFieldIndex(BeaconStateFields.NEXT_SYNC_COMMITTEE)).isEqualTo(-1);
    assertThat(schema.getFieldIndex(BeaconStateFields.VALIDATOR_PQ_REGISTRATIONS)).isEqualTo(-1);
    assertThat(schema.getFieldIndex(BeaconStateFields.BUILDER_PQ_REGISTRATIONS)).isEqualTo(-1);
    assertThat(schema.getFieldIndex(BeaconStateFields.VALIDATOR_PREREGISTRATIONS)).isNotNegative();
    assertThat(schema.getFieldIndex(BeaconStateFields.BUILDER_PREREGISTRATIONS)).isNotNegative();
    assertThat(schema.getFieldIndex(BeaconStateFields.AGGREGATION_COMMITTEE_WINDOW))
        .isNotNegative();
    // 51 fields wide, 4 of them gaps
    assertThat(schema.getFieldsCount()).isEqualTo(47);
  }

  @Test
  void packsValidatorsInSuperNodesAsEarlierForksDo() {
    final SszCollectionSchema<?, ?> validatorsSchema =
        (SszCollectionSchema<?, ?>)
            schema.getChildSchema(schema.getFieldIndex(BeaconStateFields.VALIDATORS));
    assertThat(validatorsSchema.getElementSchema()).isEqualTo(ValidatorPq.SSZ_SCHEMA);
    assertThat(validatorsSchema.getSuperNodeLayout()).isPresent();
  }

  @Test
  void superNodesKeepTheValidatorsRoot() {
    // Enough validators to fill several supernodes of 256
    final BeaconStatePq state =
        BeaconStatePq.required(schema.createEmpty())
            .updatedPq(
                mutable -> {
                  for (int i = 0; i < 1000; i++) {
                    mutable.getValidators().append(validator(i));
                  }
                  mutable.getValidators().set(700, validator(5));
                });
    final Bytes validatorsSsz = state.getValidators().sszSerialize();
    final SszList<?> plain =
        SszProgressiveListSchema.create(ValidatorPq.SSZ_SCHEMA).sszDeserialize(validatorsSsz);
    final BeaconStatePq deserialized =
        BeaconStatePq.required(schema.sszDeserialize(state.sszSerialize()));

    assertThat(state.getValidators().hashTreeRoot()).isEqualTo(plain.hashTreeRoot());
    assertThat(deserialized.getValidators().hashTreeRoot()).isEqualTo(plain.hashTreeRoot());
    assertThat(deserialized.getValidators().get(700)).isEqualTo(validator(5));
    assertThat(deserialized.getValidators().get(999)).isEqualTo(validator(999));
  }

  @Test
  void aggregationCommitteeWindowCoversThePreviousCurrentAndLookaheadEpochs() {
    final int slotsPerEpoch = spec.getGenesisSpecConfig().getSlotsPerEpoch();
    final int minSeedLookahead = spec.getGenesisSpecConfig().getMinSeedLookahead();
    assertThat(schema.getAggregationCommitteeWindowSchema().getLength())
        .isEqualTo((long) (minSeedLookahead + 2) * slotsPerEpoch);
  }

  @Test
  void loadsThePqGenesisOfAnIndependentImplementation() throws IOException {
    final Bytes ssz =
        Bytes.wrap(
            Resources.toByteArray(
                Resources.getResource(BeaconStateSchemaPqTest.class, "pq_genesis_minimal.ssz")));

    final BeaconStatePq state = BeaconStatePq.required(schema.sszDeserialize(ssz));

    assertThat(state.sszSerialize()).isEqualTo(ssz);
    assertThat(state.getGenesisValidatorsRoot()).isEqualTo(FIXTURE_GENESIS_VALIDATORS_ROOT);
    assertThat(state.getValidators().hashTreeRoot()).isEqualTo(FIXTURE_GENESIS_VALIDATORS_ROOT);
    assertThat(state.getFork().getCurrentVersion().toHexString()).isEqualTo("0xe0000038");

    assertThat(state.getValidators().size()).isEqualTo(64);
    final ValidatorPq validator = (ValidatorPq) state.getValidators().get(0);
    assertThat(validator.getXmssPublicKey())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xd7792e263b94cf0b7a9f70d8374c6d34a600b4247057d4079f810caff711c632"));
    assertThat(validator.getVrfRoot())
        .isEqualTo(
            Bytes32.fromHexString(
                "0xb1e3a6168d285a532e0dd3c6e1b5588b3bc68c27b45af71ee60ad5243600d281"));
    assertThat(validator.getWithdrawalCredentials())
        .isEqualTo(
            Bytes32.fromHexString(
                "0x0200000000000000000000008943545177806ed17b9f23f0a21ee5948ecaa776"));
    assertThat(validator.getEffectiveBalance()).isEqualTo(UInt64.valueOf(32_000_000_000L));

    assertThat(state.getBuilders()).isEmpty();
    assertThat(state.getValidatorPreregistrations()).isEmpty();
    assertThat(state.getBuilderPreregistrations()).isEmpty();
    // The previous epoch has no aggregators at genesis; slot 8 is the first of the current epoch
    assertThat(aggregators(state, 0)).isEmpty();
    assertThat(aggregators(state, 8))
        .containsExactly(
            List.of(UInt64.valueOf(19), UInt64.valueOf(60), UInt64.valueOf(2), UInt64.valueOf(37)),
            List.of(
                UInt64.valueOf(59), UInt64.valueOf(58), UInt64.valueOf(29), UInt64.valueOf(42)));
  }

  @Test
  void hasNoSyncCommittees() {
    final BeaconStatePq state = BeaconStatePq.required(schema.createEmpty());
    assertThatThrownBy(state::getCurrentSyncCommittee)
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(state::getNextSyncCommittee)
        .isInstanceOf(UnsupportedOperationException.class);
    final MutableBeaconStatePq mutable = state.createWritableCopy();
    assertThatThrownBy(() -> mutable.setCurrentSyncCommittee(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> mutable.setNextSyncCommittee(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void roundTripsAStateWithPqFieldsSet() {
    final BeaconStatePq state =
        BeaconStatePq.required(schema.createEmpty())
            .updatedPq(
                mutable -> {
                  mutable.getValidators().append(validator(1));
                  mutable.getValidatorPreregistrations().append(validatorPreregistration(2));
                  mutable.getBuilderPreregistrations().append(builderPreregistration(3));
                  final SszList<SszList<SszUInt64>> committees =
                      schema
                          .getAggregationCommitteeWindowSchema()
                          .getElementSchema()
                          .sszDeserialize(
                              schema
                                  .getAggregationCommitteeWindowSchema()
                                  .getElementSchema()
                                  .getDefault()
                                  .sszSerialize());
                  mutable.getAggregationCommitteeWindow().set(5, committees);
                });

    final BeaconStatePq roundTripped =
        BeaconStatePq.required(schema.sszDeserialize(state.sszSerialize()));

    assertThat(roundTripped).isEqualTo(state);
    assertThat(roundTripped.hashTreeRoot()).isEqualTo(state.hashTreeRoot());
    assertThat(roundTripped.getValidators().get(0)).isEqualTo(validator(1));
    assertThat(roundTripped.getValidatorPreregistrations().get(0))
        .isEqualTo(validatorPreregistration(2));
    assertThat(roundTripped.getBuilderPreregistrations().get(0))
        .isEqualTo(builderPreregistration(3));
  }

  private static List<List<UInt64>> aggregators(final BeaconStatePq state, final int slotIndex) {
    return state.getAggregationCommitteeWindow().get(slotIndex).stream()
        .map(committee -> committee.stream().map(SszUInt64::get).toList())
        .toList();
  }

  private static ValidatorPq validator(final int seed) {
    return new ValidatorPq(
        Bytes32.repeat((byte) seed),
        Bytes32.repeat((byte) (seed + 1)),
        Bytes32.repeat((byte) (seed + 2)),
        UInt64.valueOf(32_000_000_000L),
        false,
        UInt64.ZERO,
        UInt64.ZERO,
        UInt64.MAX_VALUE,
        UInt64.MAX_VALUE);
  }

  private static ValidatorPreregistration validatorPreregistration(final int seed) {
    return new ValidatorPreregistration(
        Bytes32.repeat((byte) seed),
        Bytes32.repeat((byte) (seed + 1)),
        Bytes32.repeat((byte) (seed + 2)),
        UInt64.valueOf(seed));
  }

  private static BuilderPreregistration builderPreregistration(final int seed) {
    return new BuilderPreregistration(
        Bytes32.repeat((byte) seed), Bytes32.repeat((byte) (seed + 1)), UInt64.valueOf(seed));
  }
}
