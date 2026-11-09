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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_PAYLOAD_SCHEMA;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.EXECUTION_REQUESTS_SCHEMA;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.ProgressiveSchemaUtils;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class ExecutionPayloadEnvelopeSchema
    extends ContainerSchema5<
        ExecutionPayloadEnvelope,
        ExecutionPayload,
        ExecutionRequests,
        SszUInt64,
        SszBytes32,
        SszBytes32> {

  private static final boolean[] ACTIVE_FIELDS = ProgressiveSchemaUtils.allActive(5);

  public ExecutionPayloadEnvelopeSchema(final SchemaRegistry schemaRegistry) {
    super(
        "ExecutionPayloadEnvelope",
        ACTIVE_FIELDS,
        namedSchema(
            "payload",
            SszSchema.as(ExecutionPayload.class, schemaRegistry.get(EXECUTION_PAYLOAD_SCHEMA))),
        namedSchema(
            "execution_requests",
            SszSchema.as(ExecutionRequests.class, schemaRegistry.get(EXECUTION_REQUESTS_SCHEMA))),
        namedSchema("builder_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("parent_beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA));
  }

  public ExecutionPayloadEnvelope create(
      final ExecutionPayload payload,
      final ExecutionRequests executionRequests,
      final UInt64 builderIndex,
      final Bytes32 beaconBlockRoot,
      final Bytes32 parentBeaconBlockRoot) {
    return new ExecutionPayloadEnvelope(
        this, payload, executionRequests, builderIndex, beaconBlockRoot, parentBeaconBlockRoot);
  }

  @Override
  public ExecutionPayloadEnvelope createFromBackingNode(final TreeNode node) {
    return new ExecutionPayloadEnvelope(this, node);
  }
}
