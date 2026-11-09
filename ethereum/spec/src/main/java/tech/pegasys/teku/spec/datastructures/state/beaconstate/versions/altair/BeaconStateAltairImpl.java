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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair;

import com.google.common.base.MoreObjects.ToStringHelper;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;

class BeaconStateAltairImpl extends AbstractBeaconState<MutableBeaconStateAltair>
    implements BeaconStateAltair, BeaconStateCache, ValidatorStatsAltair {

  BeaconStateAltairImpl(
      final BeaconStateSchema<BeaconStateAltair, MutableBeaconStateAltair> schema) {
    super(schema);
  }

  BeaconStateAltairImpl(
      final SszCompositeSchema<?> type,
      final TreeNode backingNode,
      final IntCache<SszData> cache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    super(type, backingNode, cache, transitionCaches, slotCaches);
  }

  BeaconStateAltairImpl(
      final AbstractSszContainerSchema<? extends SszContainer> type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public BeaconStateSchemaAltair getBeaconStateSchema() {
    return (BeaconStateSchemaAltair) getSchema();
  }

  @Override
  public MutableBeaconStateAltair createWritableCopy() {
    return new MutableBeaconStateAltairImpl(this);
  }

  @Override
  protected void describeCustomFields(final ToStringHelper stringBuilder) {
    describeCustomFields(stringBuilder, this);
  }

  static void describeCustomFields(
      final ToStringHelper stringBuilder, final BeaconStateAltair state) {
    stringBuilder
        .add("previous_epoch_participation", state.getPreviousEpochParticipation())
        .add("current_epoch_participation", state.getCurrentEpochParticipation())
        .add("inactivity_scores", state.getInactivityScores())
        .add("current_sync_committee", state.getCurrentSyncCommittee())
        .add("next_sync_committee", state.getNextSyncCommittee());
  }
}
