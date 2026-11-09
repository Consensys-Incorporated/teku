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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

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

public class WithdrawalRequestSchema
    extends ContainerSchema3<WithdrawalRequest, SszByteVector, SszPublicKey, SszUInt64> {

  public WithdrawalRequestSchema() {
    super(
        "WithdrawalRequest",
        namedSchema("source_address", SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema("validator_pubkey", SszPublicKeySchema.INSTANCE),
        namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public WithdrawalRequest create(
      final Bytes20 sourceAddress, final BLSPublicKey validatorPubkey, final UInt64 amount) {
    return new WithdrawalRequest(this, sourceAddress, validatorPubkey, amount);
  }

  @Override
  public WithdrawalRequest createFromBackingNode(final TreeNode node) {
    return new WithdrawalRequest(this, node);
  }
}
