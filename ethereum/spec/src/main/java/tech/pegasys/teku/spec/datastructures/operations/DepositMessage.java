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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class DepositMessage
    extends Container3<DepositMessage, SszPublicKey, SszBytes32, SszUInt64> {

  public static class DepositMessageSchema
      extends ContainerSchema3<DepositMessage, SszPublicKey, SszBytes32, SszUInt64> {

    public DepositMessageSchema() {
      super(
          "DepositMessage",
          namedSchema("pubkey", SszPublicKeySchema.INSTANCE),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public DepositMessage createFromBackingNode(final TreeNode node) {
      return new DepositMessage(this, node);
    }
  }

  public static final DepositMessageSchema SSZ_SCHEMA = new DepositMessageSchema();

  private DepositMessage(final DepositMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public DepositMessage(
      final BLSPublicKey pubkey, final Bytes32 withdrawalCredentials, final UInt64 amount) {
    super(
        SSZ_SCHEMA,
        new SszPublicKey(pubkey),
        SszBytes32.of(withdrawalCredentials),
        SszUInt64.of(amount));
  }

  public BLSPublicKey getPubkey() {
    return getField0().getBLSPublicKey();
  }

  public Bytes32 getWithdrawalCredentials() {
    return getField1().get();
  }

  public UInt64 getAmount() {
    return getField2().get();
  }

  @Override
  public DepositMessageSchema getSchema() {
    return (DepositMessageSchema) super.getSchema();
  }
}
