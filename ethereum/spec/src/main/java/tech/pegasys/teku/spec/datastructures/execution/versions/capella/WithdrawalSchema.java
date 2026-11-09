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

package tech.pegasys.teku.spec.datastructures.execution.versions.capella;

import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class WithdrawalSchema
    extends ContainerSchema4<Withdrawal, SszUInt64, SszUInt64, SszByteVector, SszUInt64> {

  public WithdrawalSchema() {
    super(
        "Withdrawal",
        namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("address", SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public Withdrawal create(
      final UInt64 index, final UInt64 validatorIndex, final Bytes20 address, final UInt64 amount) {
    return new Withdrawal(this, index, validatorIndex, address, amount);
  }

  @Override
  public Withdrawal createFromBackingNode(final TreeNode node) {
    return new Withdrawal(this, node);
  }
}
