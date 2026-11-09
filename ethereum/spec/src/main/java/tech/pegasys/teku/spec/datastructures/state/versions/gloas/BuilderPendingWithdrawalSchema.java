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

package tech.pegasys.teku.spec.datastructures.state.versions.gloas;

import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BuilderPendingWithdrawalSchema
    extends ContainerSchema3<BuilderPendingWithdrawal, SszByteVector, SszUInt64, SszUInt64> {

  public BuilderPendingWithdrawalSchema() {
    super(
        "BuilderPendingWithdrawal",
        namedSchema("fee_recipient", SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("builder_index", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public BuilderPendingWithdrawal create(
      final Eth1Address feeRecipient, final UInt64 amount, final UInt64 builderIndex) {
    return new BuilderPendingWithdrawal(this, feeRecipient, amount, builderIndex);
  }

  @Override
  public BuilderPendingWithdrawal createFromBackingNode(final TreeNode node) {
    return new BuilderPendingWithdrawal(this, node);
  }
}
