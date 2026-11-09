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

package tech.pegasys.teku.spec.datastructures.state.versions.electra;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class PendingDeposit
    extends Container5<
        PendingDeposit, SszPublicKey, SszBytes32, SszUInt64, SszSignature, SszUInt64> {

  public static class PendingDepositSchema
      extends ContainerSchema5<
          PendingDeposit, SszPublicKey, SszBytes32, SszUInt64, SszSignature, SszUInt64> {

    public PendingDepositSchema() {
      super(
          "PendingDeposit",
          namedSchema("pubkey", SszPublicKeySchema.INSTANCE),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("signature", SszSignatureSchema.INSTANCE),
          namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public PendingDeposit createFromBackingNode(final TreeNode node) {
      return new PendingDeposit(this, node);
    }

    public PendingDeposit create(
        final SszPublicKey publicKey,
        final SszBytes32 withdrawalCredentials,
        final SszUInt64 amount,
        final SszSignature signature,
        final SszUInt64 slot) {
      return new PendingDeposit(this, publicKey, withdrawalCredentials, amount, signature, slot);
    }
  }

  private PendingDeposit(final PendingDepositSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  private PendingDeposit(
      final PendingDepositSchema type,
      final SszPublicKey publicKey,
      final SszBytes32 withdrawalCredentials,
      final SszUInt64 amount,
      final SszSignature signature,
      final SszUInt64 slot) {
    super(type, publicKey, withdrawalCredentials, amount, signature, slot);
  }

  public BLSPublicKey getPublicKey() {
    return ((SszPublicKey) get(0)).getBLSPublicKey();
  }

  public Bytes32 getWithdrawalCredentials() {
    return ((SszBytes32) get(1)).get();
  }

  public UInt64 getAmount() {
    return ((SszUInt64) get(2)).get();
  }

  public BLSSignature getSignature() {
    return ((SszSignature) get(3)).getSignature();
  }

  public UInt64 getSlot() {
    return ((SszUInt64) get(4)).get();
  }
}
