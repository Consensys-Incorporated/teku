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

package tech.pegasys.teku.spec.schemas.api;

import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StatePtcDataSchema extends ContainerSchema2<StatePtcData, SszUInt64, SszUInt64Vector> {

  @SuppressWarnings("unchecked")
  public StatePtcDataSchema(final SszUInt64VectorSchema<?> payloadTimelinessCommitteeSchema) {
    super(
        "StatePtc",
        namedSchema("slot", UINT64_SCHEMA),
        namedSchema("validators", (SszSchema<SszUInt64Vector>) payloadTimelinessCommitteeSchema));
  }

  public StatePtcData create(final UInt64 slot, final List<UInt64> validators) {
    return new StatePtcData(this, SszUInt64.of(slot), getValidatorsSchema().of(validators));
  }

  public SszUInt64VectorSchema<?> getValidatorsSchema() {
    return (SszUInt64VectorSchema<?>) getChildSchema(1);
  }

  @Override
  public StatePtcData createFromBackingNode(final TreeNode node) {
    return new StatePtcData(this, node);
  }
}
