/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.reference.altair.fork;

import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateSchemaBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateSchemaCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateSchemaDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateSchemaElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateSchemaFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;

public class ForkUpgradeTestExecutor implements TestExecutor {

  public static final ImmutableMap<String, TestExecutor> FORK_UPGRADE_TEST_TYPES =
      ImmutableMap.of("fork/fork", new ForkUpgradeTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final MetaData metadata = TestDataUtils.loadYaml(testDefinition, "meta.yaml", MetaData.class);
    final SpecMilestone specMilestone = SpecMilestone.forName(metadata.fork);
    final boolean blsEnabled = metadata.getBlsSetting() != BlsSetting.IGNORED;
    processUpgrade(testDefinition, specMilestone, blsEnabled);
  }

  private void processUpgrade(
      final TestDefinition testDefinition,
      final SpecMilestone milestone,
      final boolean blsEnabled) {
    final SpecMilestone previousMilestone = milestone.getPreviousMilestone();
    final SpecVersion previousMilestoneSpecVersion =
        testDefinition.getSpec(blsEnabled).forMilestone(previousMilestone);
    final BeaconStateSchema<?, ?> fromMilestoneSchema =
        switch (milestone) {
          case ALTAIR -> BeaconStateSchemaPhase0.create(previousMilestoneSpecVersion.getConfig());
          case BELLATRIX ->
              BeaconStateSchemaAltair.create(previousMilestoneSpecVersion.getConfig());
          case CAPELLA ->
              BeaconStateSchemaBellatrix.create(
                  previousMilestoneSpecVersion.getConfig(),
                  previousMilestoneSpecVersion.getSchemaDefinitions().getSchemaRegistry());
          case DENEB ->
              BeaconStateSchemaCapella.create(
                  previousMilestoneSpecVersion.getConfig(),
                  previousMilestoneSpecVersion.getSchemaDefinitions().getSchemaRegistry());
          case ELECTRA ->
              BeaconStateSchemaDeneb.create(
                  previousMilestoneSpecVersion.getConfig(),
                  previousMilestoneSpecVersion.getSchemaDefinitions().getSchemaRegistry());
          case FULU ->
              BeaconStateSchemaElectra.create(
                  previousMilestoneSpecVersion.getConfig(),
                  previousMilestoneSpecVersion.getSchemaDefinitions().getSchemaRegistry());
          case GLOAS ->
              BeaconStateSchemaFulu.create(
                  previousMilestoneSpecVersion.getConfig(),
                  previousMilestoneSpecVersion.getSchemaDefinitions().getSchemaRegistry());
          case HEZE ->
              BeaconStateSchemaGloas.create(
                  previousMilestoneSpecVersion.getConfig(),
                  previousMilestoneSpecVersion.getSchemaDefinitions().getSchemaRegistry());
          default ->
              throw new IllegalStateException(
                  "Unhandled fork upgrade for test "
                      + testDefinition.getDisplayName()
                      + ": "
                      + milestone);
        };
    final BeaconState preState =
        TestDataUtils.loadSsz(testDefinition, "pre.ssz_snappy", fromMilestoneSchema);
    final BeaconState postState = TestDataUtils.loadStateFromSsz(testDefinition, "post.ssz_snappy");

    final StateUpgrade<?> stateUpgrade =
        testDefinition.getSpec(blsEnabled).getGenesisSpec().getStateUpgrade().orElseThrow();

    final BeaconState updated = stateUpgrade.upgrade(preState);

    assertThatSszData(updated).isEqualByGettersTo(postState);
  }

  private static class MetaData {
    @JsonProperty(value = "fork", required = true)
    private String fork;

    @JsonProperty(value = "bls_setting", required = false, defaultValue = "1")
    private int blsSetting;

    public BlsSetting getBlsSetting() {
      return BlsSetting.forCode(blsSetting);
    }
  }
}
