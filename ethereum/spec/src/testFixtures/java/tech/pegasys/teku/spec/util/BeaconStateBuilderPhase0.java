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

package tech.pegasys.teku.spec.util;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStateSchemaPhase0;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.MutableBeaconStatePhase0;

public class BeaconStateBuilderPhase0
    extends AbstractBeaconStateBuilder<
        BeaconStatePhase0, MutableBeaconStatePhase0, BeaconStateBuilderPhase0> {

  private SszList<PendingAttestation> previousEpochAttestations;
  private SszList<PendingAttestation> currentEpochAttestations;

  private BeaconStateBuilderPhase0(
      final SpecVersion spec,
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    super(spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  @Override
  protected BeaconStatePhase0 getEmptyState() {
    return BeaconStateSchemaPhase0.create(spec.getConfig()).createEmpty();
  }

  @Override
  protected void setUniqueFields(final MutableBeaconStatePhase0 state) {
    state.getPreviousEpochAttestations().setAll(previousEpochAttestations);
    state.getCurrentEpochAttestations().setAll(currentEpochAttestations);
  }

  public static BeaconStateBuilderPhase0 create(
      final DataStructureUtil dataStructureUtil,
      final Spec spec,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilderPhase0(
        spec.forMilestone(SpecMilestone.PHASE0),
        dataStructureUtil,
        defaultValidatorCount,
        defaultItemsInSSZLists);
  }

  private BeaconStateSchemaPhase0 getBeaconStateSchema() {
    return (BeaconStateSchemaPhase0) spec.getSchemaDefinitions().getBeaconStateSchema();
  }

  @Override
  protected void initDefaults() {
    super.initDefaults();

    final BeaconStateSchemaPhase0 schema = getBeaconStateSchema();

    previousEpochAttestations =
        dataStructureUtil.randomSszList(
            schema.getPreviousEpochAttestationsSchema(),
            defaultItemsInSSZLists,
            () -> dataStructureUtil.randomPendingAttestation(schema.getPendingAttestationSchema()));
    currentEpochAttestations =
        dataStructureUtil.randomSszList(
            schema.getCurrentEpochAttestationsSchema(),
            defaultItemsInSSZLists,
            () -> dataStructureUtil.randomPendingAttestation(schema.getPendingAttestationSchema()));
  }
}
