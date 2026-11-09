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

package tech.pegasys.teku.spec.datastructures.operations;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class BlsToExecutionChangeSchema
    extends ContainerSchema3<BlsToExecutionChange, SszUInt64, SszPublicKey, SszByteVector> {

  public BlsToExecutionChangeSchema() {
    super(
        "BLSToExecutionChange",
        namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("from_bls_pubkey", SszPublicKeySchema.INSTANCE),
        namedSchema("to_execution_address", SszByteVectorSchema.create(Bytes20.SIZE)));
  }

  public BlsToExecutionChange create(
      final UInt64 validatorIndex,
      final BLSPublicKey fromBlsPubkey,
      final Bytes20 toExecutionAddress) {
    return new BlsToExecutionChange(this, validatorIndex, fromBlsPubkey, toExecutionAddress);
  }

  @Override
  public BlsToExecutionChange createFromBackingNode(final TreeNode node) {
    return new BlsToExecutionChange(this, node);
  }
}
