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

package tech.pegasys.teku.spec.datastructures.execution;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteList;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ExecutionProofSchema
    extends ContainerSchema5<
        ExecutionProof, SszBytes32, SszBytes32, SszUInt64, SszUInt64, SszByteList> {

  // as per suggestion in
  // https://github.com/Consensys-Incorporated/teku/pull/9853#discussion_r2329217191
  // this may change in when we get smaller proofs
  static final long MAX_PROOF_DATA_SIZE = 1024 * 1024;

  public ExecutionProofSchema() {
    super(
        "ExecutionProof",
        namedSchema("block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("block_hash", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("subnet_id", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("version", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("proof_data", SszByteListSchema.create(MAX_PROOF_DATA_SIZE)));
  }

  public ExecutionProof create(
      final SszBytes32 blockRoot,
      final SszBytes32 blockHash,
      final SszUInt64 subnetId,
      final SszUInt64 version,
      final SszByteList proofData) {
    return new ExecutionProof(this, blockRoot, blockHash, subnetId, version, proofData);
  }

  public ExecutionProof create(
      final Bytes32 blockRoot,
      final Bytes32 blockHash,
      final UInt64 subnetId,
      final UInt64 version,
      final Bytes proofData) {
    return create(
        SszBytes32.of(blockRoot),
        SszBytes32.of(blockHash),
        SszUInt64.of(subnetId),
        SszUInt64.of(version),
        getProofDataSchema().fromBytes(proofData));
  }

  @Override
  public ExecutionProof createFromBackingNode(final TreeNode node) {
    return new ExecutionProof(this, node);
  }

  @SuppressWarnings("unchecked")
  public SszByteListSchema<?> getProofDataSchema() {
    return (SszByteListSchema<?>) getFieldSchema4();
  }
}
