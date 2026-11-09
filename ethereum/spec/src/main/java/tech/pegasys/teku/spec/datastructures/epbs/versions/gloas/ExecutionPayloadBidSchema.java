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

import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.BUILDER_INDEX_SELF_BUILD;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOB_KZG_COMMITMENTS;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.BUILDER_INDEX;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXECUTION_PAYMENT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.EXECUTION_REQUESTS_ROOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.FEE_RECIPIENT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.GAS_LIMIT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.PARENT_BLOCK_HASH;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.PARENT_BLOCK_ROOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.PREV_RANDAO;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.SLOT;
import static tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields.VALUE;
import static tech.pegasys.teku.spec.schemas.registry.SchemaTypes.BLOB_KZG_COMMITMENTS_SCHEMA;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema12;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.ProgressiveSchemaUtils;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistry;

public class ExecutionPayloadBidSchema
    extends ContainerSchema12<
        ExecutionPayloadBid,
        SszBytes32,
        SszBytes32,
        SszBytes32,
        SszBytes32,
        SszByteVector,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszUInt64,
        SszList<SszKZGCommitment>,
        SszBytes32> {

  private static final boolean[] ACTIVE_FIELDS = ProgressiveSchemaUtils.allActive(12);

  public ExecutionPayloadBidSchema(final SchemaRegistry schemaRegistry) {
    super(
        "ExecutionPayloadBid",
        ACTIVE_FIELDS,
        namedSchema(PARENT_BLOCK_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(PARENT_BLOCK_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(BLOCK_HASH, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(PREV_RANDAO, SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema(FEE_RECIPIENT, SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema(GAS_LIMIT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(BUILDER_INDEX, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(SLOT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(VALUE, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(EXECUTION_PAYMENT, SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema(BLOB_KZG_COMMITMENTS, schemaRegistry.get(BLOB_KZG_COMMITMENTS_SCHEMA)),
        namedSchema(EXECUTION_REQUESTS_ROOT, SszPrimitiveSchemas.BYTES32_SCHEMA));
  }

  public ExecutionPayloadBid create(
      final Bytes32 parentBlockHash,
      final Bytes32 parentBlockRoot,
      final Bytes32 blockHash,
      final Bytes32 prevRandao,
      final Bytes20 feeRecipient,
      final UInt64 gasLimit,
      final UInt64 builderIndex,
      final UInt64 slot,
      final UInt64 value,
      final UInt64 executionPayment,
      final SszList<SszKZGCommitment> blobKzgCommitments,
      final Bytes32 executionRequestsRoot) {
    return new ExecutionPayloadBid(
        this,
        parentBlockHash,
        parentBlockRoot,
        blockHash,
        prevRandao,
        feeRecipient,
        gasLimit,
        builderIndex,
        slot,
        value,
        executionPayment,
        blobKzgCommitments,
        executionRequestsRoot);
  }

  @Override
  public ExecutionPayloadBid createFromBackingNode(final TreeNode node) {
    return new ExecutionPayloadBid(this, node);
  }

  public ExecutionPayloadBid createLocalSelfBuiltBid(
      final Bytes32 parentBlockRoot,
      final UInt64 slot,
      final ExecutionPayload executionPayload,
      final SszList<SszKZGCommitment> blobKzgCommitments,
      final Bytes32 executionRequestsRoot) {
    return new ExecutionPayloadBid(
        this,
        executionPayload.getParentHash(),
        parentBlockRoot,
        executionPayload.getBlockHash(),
        executionPayload.getPrevRandao(),
        executionPayload.getFeeRecipient(),
        executionPayload.getGasLimit(),
        // For self-builds, use `BUILDER_INDEX_SELF_BUILD`
        BUILDER_INDEX_SELF_BUILD,
        slot,
        // amount and execution_payment must be zero for self-build blocks
        ZERO,
        ZERO,
        blobKzgCommitments,
        executionRequestsRoot);
  }

  @SuppressWarnings("unchecked")
  public SszListSchema<SszKZGCommitment, ?> getBlobKzgCommitmentsSchema() {
    return (SszListSchema<SszKZGCommitment, ?>) getChildSchema(getFieldIndex(BLOB_KZG_COMMITMENTS));
  }
}
