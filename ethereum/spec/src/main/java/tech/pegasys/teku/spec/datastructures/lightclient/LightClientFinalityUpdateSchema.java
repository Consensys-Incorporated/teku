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

package tech.pegasys.teku.spec.datastructures.lightclient;

import static tech.pegasys.teku.spec.constants.LightClientConstants.FINALIZED_ROOT_GINDEX;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.LIGHT_CLIENT_HEADER_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBytes32VectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.logic.common.helpers.MathHelpers;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class LightClientFinalityUpdateSchema
    extends ContainerSchema5<
        LightClientFinalityUpdate,
        LightClientHeader,
        LightClientHeader,
        SszBytes32Vector,
        SyncAggregate,
        SszUInt64> {
  protected LightClientFinalityUpdateSchema(
      final SpecConfigAltair specConfigAltair,
      final int finalizedBranchGIndex,
      final SchemaRegistry registry,
      final String schemaName) {
    super(
        schemaName,
        namedSchema(
            "attested_header",
            SszSchema.as(LightClientHeader.class, registry.get(LIGHT_CLIENT_HEADER_SCHEMA))),
        namedSchema(
            "finalized_header",
            SszSchema.as(LightClientHeader.class, registry.get(LIGHT_CLIENT_HEADER_SCHEMA))),
        namedSchema(
            "finality_branch",
            SszBytes32VectorSchema.create(MathHelpers.floorLog2(finalizedBranchGIndex))),
        namedSchema(
            "sync_aggregate", SyncAggregateSchema.create(specConfigAltair.getSyncCommitteeSize())),
        namedSchema("signature_slot", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public LightClientFinalityUpdateSchema(
      final SpecConfigAltair specConfigAltair,
      final SchemaRegistry registry,
      final String schemaName) {
    this(specConfigAltair, FINALIZED_ROOT_GINDEX, registry, schemaName);
  }

  public LightClientFinalityUpdate create(
      final LightClientHeader attestedHeader,
      final LightClientHeader finalizedHeader,
      final SszBytes32Vector finalityBranch,
      final SyncAggregate aggregate,
      final SszUInt64 signatureSlot) {
    return new LightClientFinalityUpdate(
        this, attestedHeader, finalizedHeader, finalityBranch, aggregate, signatureSlot);
  }

  @Override
  public LightClientFinalityUpdate createFromBackingNode(final TreeNode node) {
    return new LightClientFinalityUpdate(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszBytes32VectorSchema<SszBytes32Vector> getFinalizedBranchSchema() {
    return (SszBytes32VectorSchema<SszBytes32Vector>) getChildSchema(2);
  }
}
