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

package tech.pegasys.teku.reference.phase0.rewards;

import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64List;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenalty.RewardComponent;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.RewardAndPenaltyDeltas;

public class ExpectedDeltas extends Container2<ExpectedDeltas, SszUInt64List, SszUInt64List> {
  public static final DeltasSchema SSZ_SCHEMA = new DeltasSchema();

  protected ExpectedDeltas(
      final ContainerSchema2<ExpectedDeltas, SszUInt64List, SszUInt64List> schema,
      final TreeNode backingNode) {
    super(schema, backingNode);
  }

  public RewardAndPenaltyDeltas getDeltas() {
    final SszUInt64List rewards = getField0();
    final SszUInt64List penalties = getField1();
    final RewardAndPenaltyDeltas deltas = RewardAndPenaltyDeltas.aggregated(rewards.size());
    for (int i = 0; i < rewards.size(); i++) {
      // We are using the aggregated deltas, so it does not matter what component we use here
      deltas.getDelta(i).reward(RewardComponent.HEAD, rewards.get(i).get());
      deltas.getDelta(i).penalize(RewardComponent.HEAD, penalties.get(i).get());
    }
    return deltas;
  }

  public static class DeltasSchema
      extends ContainerSchema2<ExpectedDeltas, SszUInt64List, SszUInt64List> {

    private DeltasSchema() {
      super(
          "Deltas",
          NamedSchema.of("rewards", SszUInt64ListSchema.create(Integer.MAX_VALUE)),
          NamedSchema.of("penalties", SszUInt64ListSchema.create(Integer.MAX_VALUE)));
    }

    @Override
    public ExpectedDeltas createFromBackingNode(final TreeNode node) {
      return new ExpectedDeltas(this, node);
    }
  }
}
