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

package tech.pegasys.teku.spec.datastructures.state.versions.gloas;

import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.impl.SszVectorImpl;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigGloas;

public class PayloadTimelinessCommitteeWindowSchema
    extends AbstractSszVectorSchema<SszUInt64Vector, SszVector<SszUInt64Vector>> {

  public PayloadTimelinessCommitteeWindowSchema(final SpecConfigGloas config) {
    super(
        SszUInt64VectorSchema.create(config.getPayloadTimelinessCommitteeSize()),
        (long) (2 + config.getMinSeedLookahead()) * config.getSlotsPerEpoch());
  }

  public SszUInt64VectorSchema<?> getPayloadTimelinessCommitteeSchema() {
    return (SszUInt64VectorSchema<?>) getElementSchema();
  }

  @Override
  public SszVector<SszUInt64Vector> createFromBackingNode(final TreeNode node) {
    return new SszVectorImpl<>(this, node);
  }
}
