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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix;

import com.google.common.base.MoreObjects.ToStringHelper;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractMutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;

class MutableBeaconStateBellatrixImpl extends AbstractMutableBeaconState<BeaconStateBellatrixImpl>
    implements MutableBeaconStateBellatrix, BeaconStateCache, ValidatorStatsAltair {

  MutableBeaconStateBellatrixImpl(final BeaconStateBellatrixImpl backingImmutableView) {
    super(backingImmutableView);
  }

  MutableBeaconStateBellatrixImpl(
      final BeaconStateBellatrixImpl backingImmutableView, final boolean builder) {
    super(backingImmutableView, builder);
  }

  @Override
  public BeaconStateSchemaBellatrix getBeaconStateSchema() {
    return (BeaconStateSchemaBellatrix) getSchema();
  }

  @Override
  protected BeaconStateBellatrixImpl createImmutableBeaconState(
      final TreeNode backingNode,
      final IntCache<SszData> viewCache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    return new BeaconStateBellatrixImpl(
        getSchema(), backingNode, viewCache, transitionCaches, slotCaches);
  }

  @Override
  public BeaconStateBellatrix commitChanges() {
    return (BeaconStateBellatrix) super.commitChanges();
  }

  @Override
  public <E1 extends Exception, E2 extends Exception, E3 extends Exception> BeaconState updated(
      final Mutator<MutableBeaconState, E1, E2, E3> mutator) {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void addCustomFields(final ToStringHelper stringBuilder) {
    BeaconStateBellatrix.describeCustomBellatrixFields(stringBuilder, this);
  }

  @Override
  public MutableBeaconStateBellatrix createWritableCopy() {
    return (MutableBeaconStateBellatrix) super.createWritableCopy();
  }
}
