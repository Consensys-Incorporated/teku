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

import com.google.common.base.MoreObjects;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.AbstractBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.SlotCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.TransitionCaches;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.ValidatorStatsAltair;

public class BeaconStatePqImpl extends AbstractBeaconState<MutableBeaconStatePq>
    implements BeaconStatePq, BeaconStateCache, ValidatorStatsAltair {

  BeaconStatePqImpl(final BeaconStateSchemaPq schema) {
    super(schema);
  }

  BeaconStatePqImpl(
      final BeaconStateSchemaPq schema,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    super(schema, schema.getDefaultTree(), null, transitionCaches, slotCaches);
  }

  BeaconStatePqImpl(
      final SszCompositeSchema<?> type,
      final TreeNode backingNode,
      final IntCache<SszData> cache,
      final TransitionCaches transitionCaches,
      final SlotCaches slotCaches) {
    super(type, backingNode, cache, transitionCaches, slotCaches);
  }

  BeaconStatePqImpl(
      final AbstractSszContainerSchema<? extends SszContainer> type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  @Override
  public BeaconStateSchemaPq getBeaconStateSchema() {
    return (BeaconStateSchemaPq) getSchema();
  }

  @Override
  public MutableBeaconStatePq createWritableCopy() {
    return new MutableBeaconStatePqImpl(this);
  }

  @Override
  protected void describeCustomFields(final MoreObjects.ToStringHelper stringBuilder) {
    BeaconStatePq.describeCustomPqFields(stringBuilder, this);
  }
}
