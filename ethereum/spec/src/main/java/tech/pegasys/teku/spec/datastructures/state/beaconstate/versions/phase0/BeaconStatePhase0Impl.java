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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0;

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

class BeaconStatePhase0Impl extends AbstractBeaconState<MutableBeaconStatePhase0>
    implements BeaconStatePhase0, BeaconStateCache, ValidatorStatsPhase0 {

  BeaconStatePhase0Impl(
      final BeaconStateSchema<BeaconStatePhase0, MutableBeaconStatePhase0> schema) {
    super(schema);
  }

  BeaconStatePhase0Impl(
      final SszCompositeSchema<?> type,
      final TreeNode backingNode,
      final IntCache<SszData> cache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    super(type, backingNode, cache, transitionCaches, slotCaches);
  }

  BeaconStatePhase0Impl(
      final AbstractSszContainerSchema<? extends SszContainer> type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public BeaconStateSchemaPhase0 getBeaconStateSchema() {
    return (BeaconStateSchemaPhase0) getSchema();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStatePhase0 updatedPhase0(final Mutator<MutableBeaconStatePhase0, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    MutableBeaconStatePhase0 writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  public MutableBeaconStatePhase0 createWritableCopy() {
    return new MutableBeaconStatePhase0Impl(this);
  }

  @Override
  protected void describeCustomFields(final ToStringHelper stringBuilder) {
    describeCustomFields(stringBuilder, this);
  }

  static void describeCustomFields(
      final ToStringHelper stringBuilder, final BeaconStatePhase0 state) {
    stringBuilder
        .add("previous_epoch_attestations", state.getPreviousEpochAttestations())
        .add("current_epoch_attestations", state.getCurrentEpochAttestations());
  }
}
