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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProposerPreferencesSchema
    extends ContainerSchema5<
        ProposerPreferences, SszBytes32, SszUInt64, SszUInt64, SszByteVector, SszUInt64> {

  public ProposerPreferencesSchema() {
    super(
        "ProposerPreferences",
        namedSchema("dependent_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("proposal_slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("fee_recipient", SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema("target_gas_limit", SszPrimitiveSchemas.UINT64_SCHEMA));
  }

  public ProposerPreferences create(
      final Bytes32 dependentRoot,
      final UInt64 proposalSlot,
      final UInt64 validatorIndex,
      final Eth1Address feeRecipient,
      final UInt64 targetGasLimit) {
    return new ProposerPreferences(
        this, dependentRoot, proposalSlot, validatorIndex, feeRecipient, targetGasLimit);
  }

  @Override
  public ProposerPreferences createFromBackingNode(final TreeNode node) {
    return new ProposerPreferences(this, node);
  }
}
