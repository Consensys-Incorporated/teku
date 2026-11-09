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

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.LIGHT_CLIENT_HEADER_SCHEMA;

import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateSchema;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class LightClientOptimisticUpdateSchema
    extends ContainerSchema3<
        LightClientOptimisticUpdate, LightClientHeader, SyncAggregate, SszUInt64> {
  public LightClientOptimisticUpdateSchema(
      final SpecConfigAltair specConfigAltair,
      final SchemaRegistry registry,
      final String schemaName) {
    super(
        schemaName,
        namedSchema(
            "attested_header",
            SszSchema.as(LightClientHeader.class, registry.get(LIGHT_CLIENT_HEADER_SCHEMA))),
        namedSchema(
            "sync_aggregate", SyncAggregateSchema.create(specConfigAltair.getSyncCommitteeSize())),
        namedSchema("signature_slot", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public LightClientOptimisticUpdate create(
      final LightClientHeader attestedHeader,
      final SyncAggregate syncAggregate,
      final SszUInt64 signatureSlot) {
    return new LightClientOptimisticUpdate(this, attestedHeader, syncAggregate, signatureSlot);
  }

  @Override
  public LightClientOptimisticUpdate createFromBackingNode(final TreeNode node) {
    return new LightClientOptimisticUpdate(this, node);
  }
}
