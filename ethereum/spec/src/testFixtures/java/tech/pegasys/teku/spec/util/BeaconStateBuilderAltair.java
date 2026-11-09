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

import static com.google.common.base.Preconditions.checkNotNull;

import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateSchemaAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;

public class BeaconStateBuilderAltair
    extends AbstractBeaconStateBuilder<
        BeaconStateAltair, MutableBeaconStateAltair, BeaconStateBuilderAltair> {

  private SszList<SszByte> previousEpochParticipation;
  private SszList<SszByte> currentEpochParticipation;
  private SszUInt64List inactivityScores;
  private SyncCommittee currentSyncCommittee;
  private SyncCommittee nextSyncCommittee;

  private BeaconStateBuilderAltair(
      final SpecVersion spec,
      final DataStructureUtil dataStructureUtil,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    super(spec, dataStructureUtil, defaultValidatorCount, defaultItemsInSSZLists);
  }

  @Override
  protected BeaconStateAltair getEmptyState() {
    return BeaconStateSchemaAltair.create(spec.getConfig()).createEmpty();
  }

  @Override
  protected void setUniqueFields(final MutableBeaconStateAltair state) {
    state.setPreviousEpochParticipation(previousEpochParticipation);
    state.setCurrentEpochParticipation(currentEpochParticipation);
    state.setInactivityScores(inactivityScores);
    state.setCurrentSyncCommittee(currentSyncCommittee);
    state.setNextSyncCommittee(nextSyncCommittee);
  }

  public static BeaconStateBuilderAltair create(
      final DataStructureUtil dataStructureUtil,
      final Spec spec,
      final int defaultValidatorCount,
      final int defaultItemsInSSZLists) {
    return new BeaconStateBuilderAltair(
        spec.forMilestone(SpecMilestone.ALTAIR),
        dataStructureUtil,
        defaultValidatorCount,
        defaultItemsInSSZLists);
  }

  private BeaconStateSchemaAltair getBeaconStateSchema() {
    return (BeaconStateSchemaAltair) spec.getSchemaDefinitions().getBeaconStateSchema();
  }

  @Override
  protected void initDefaults() {
    super.initDefaults();

    final BeaconStateSchemaAltair schema = getBeaconStateSchema();

    previousEpochParticipation =
        dataStructureUtil.randomSszList(
            schema.getPreviousEpochParticipationSchema(),
            defaultValidatorCount,
            dataStructureUtil::randomSszByte);
    currentEpochParticipation =
        dataStructureUtil.randomSszList(
            schema.getCurrentEpochParticipationSchema(),
            defaultValidatorCount,
            dataStructureUtil::randomSszByte);
    inactivityScores =
        dataStructureUtil.randomSszUInt64List(
            schema.getInactivityScoresSchema(), defaultItemsInSSZLists);
    currentSyncCommittee = dataStructureUtil.randomSyncCommittee();
    nextSyncCommittee = dataStructureUtil.randomSyncCommittee();
  }

  public BeaconStateBuilderAltair previousEpochAttestations(final SszList<SszByte> value) {
    checkNotNull(value);
    this.previousEpochParticipation = value;
    return this;
  }

  public BeaconStateBuilderAltair currentEpochAttestations(final SszList<SszByte> value) {
    checkNotNull(value);
    this.currentEpochParticipation = value;
    return this;
  }

  public BeaconStateBuilderAltair inactivityScores(final SszUInt64List inactivityScores) {
    checkNotNull(inactivityScores);
    this.inactivityScores = inactivityScores;
    return this;
  }

  public BeaconStateBuilderAltair currentSyncCommittee(final SyncCommittee currentSyncCommittee) {
    checkNotNull(currentSyncCommittee);
    this.currentSyncCommittee = currentSyncCommittee;
    return this;
  }

  public BeaconStateBuilderAltair nextSyncCommittee(final SyncCommittee nextSyncCommittee) {
    checkNotNull(nextSyncCommittee);
    this.nextSyncCommittee = nextSyncCommittee;
    return this;
  }
}
