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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_TIME_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.GENESIS_VALIDATORS_ROOT_FIELD;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateInvariants.SLOT_FIELD;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszTestUtils;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszField;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public abstract class AbstractBeaconStateSchemaTest<
    T extends BeaconState, TMutable extends MutableBeaconState> {

  private final Spec spec = createSpec();

  protected abstract Spec createSpec();

  protected final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SpecConfig genesisConfig = spec.getGenesisSpecConfig();
  private final BeaconStateSchema<T, TMutable> schema = getSchema(genesisConfig);

  protected abstract BeaconStateSchema<T, TMutable> getSchema(final SpecConfig specConfig);

  protected abstract T randomState();

  @Test
  void vectorLengthsTest() {
    IntList vectorLengths =
        IntList.of(
            genesisConfig.getSlotsPerHistoricalRoot(),
            genesisConfig.getSlotsPerHistoricalRoot(),
            genesisConfig.getEpochsPerHistoricalVector(),
            genesisConfig.getEpochsPerSlashingsVector(),
            genesisConfig.getJustificationBitsLength());
    assertEquals(vectorLengths, SszTestUtils.getVectorLengths(schema));
  }

  @Test
  public void changeSpecConfigTest() {
    final Spec standardSpec = TestSpecFactory.createMinimal(genesisConfig.getMilestone());
    final SpecConfig modifiedConfig =
        SpecConfigLoader.loadConfig(
                "minimal",
                b ->
                    b.slotsPerHistoricalRoot(123)
                        .historicalRootsLimit(123)
                        .epochsPerEth1VotingPeriod(123)
                        .validatorRegistryLimit(123L)
                        .epochsPerHistoricalVector(123)
                        .epochsPerSlashingsVector(123)
                        .maxAttestations(123))
            .specConfig();

    BeaconState s1 = getSchema(modifiedConfig).createEmpty();
    BeaconState s2 = getSchema(standardSpec.getGenesisSpecConfig()).createEmpty();

    assertThat(s1.getBlockRoots().getSchema()).isNotEqualTo(s2.getBlockRoots().getSchema());
    assertThat(s1.getStateRoots().getSchema()).isNotEqualTo(s2.getStateRoots().getSchema());
    assertThat(s1.getHistoricalRoots().getSchema())
        .isNotEqualTo(s2.getHistoricalRoots().getSchema());
    assertThat(s1.getEth1DataVotes().getSchema()).isNotEqualTo(s2.getEth1DataVotes().getSchema());
    assertThat(s1.getValidators().getSchema()).isNotEqualTo(s2.getValidators().getSchema());
    assertThat(s1.getBalances().getSchema()).isNotEqualTo(s2.getBalances().getSchema());
    assertThat(s1.getRandaoMixes().getSchema()).isNotEqualTo(s2.getRandaoMixes().getSchema());
    assertThat(s1.getSlashings().getSchema()).isNotEqualTo(s2.getSlashings().getSchema());
  }

  @Test
  void roundTripViaSsz() {
    BeaconState beaconState = randomState();
    Bytes bytes = beaconState.sszSerialize();
    BeaconState state = schema.sszDeserialize(bytes);
    assertEquals(beaconState, state);
  }

  @Test
  public void create_compareDifferentSpecs() {
    final BeaconStateSchema<T, TMutable> minimalState =
        getSchema(
            TestSpecFactory.createMinimal(genesisConfig.getMilestone()).getGenesisSpecConfig());
    final BeaconStateSchema<T, TMutable> mainnetState =
        getSchema(
            TestSpecFactory.createMainnet(genesisConfig.getMilestone()).getGenesisSpecConfig());

    assertThat(minimalState).isNotEqualTo(mainnetState);
  }

  @Test
  public void shouldValidateFieldsAreOrdered() {
    assertThatThrownBy(
            () ->
                createSchema(
                    List.of(GENESIS_TIME_FIELD, SLOT_FIELD, GENESIS_VALIDATORS_ROOT_FIELD)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "fields must be ordered and contiguous.  Encountered unexpected index 2 at fields element 1");
  }

  @Test
  public void shouldValidateFieldCount() {
    assertThatThrownBy(
            () -> createSchema(List.of(GENESIS_TIME_FIELD, GENESIS_VALIDATORS_ROOT_FIELD)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Must provide at least 3 fields");
  }

  @Test
  public void shouldValidateInvariantFields() {
    final SszField randomField =
        new SszField(
            2, "random", () -> SszVectorSchema.create(SszPrimitiveSchemas.BYTES32_SCHEMA, 10));
    assertThatThrownBy(
            () ->
                createSchema(
                    List.of(GENESIS_TIME_FIELD, GENESIS_VALIDATORS_ROOT_FIELD, randomField)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected invariant field 'slot' at index 2, but got 'random'");
  }

  private BeaconStateSchema<BeaconState, MutableBeaconState> createSchema(
      final List<SszField> fields) {
    return new TestBeaconStateSchema(fields);
  }

  private static class TestBeaconStateSchema
      extends AbstractBeaconStateSchema<BeaconState, MutableBeaconState> {

    TestBeaconStateSchema(final List<SszField> allFields) {
      super("TestSchema", allFields);
    }

    @Override
    public MutableBeaconState createBuilder() {
      return null;
    }

    @Override
    public BeaconState createEmpty() {
      return null;
    }

    @Override
    public BeaconState createFromBackingNode(final TreeNode node) {
      return null;
    }
  }
}
