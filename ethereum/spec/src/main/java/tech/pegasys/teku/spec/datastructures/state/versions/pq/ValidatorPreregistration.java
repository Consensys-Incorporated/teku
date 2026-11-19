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

package tech.pegasys.teku.spec.datastructures.state.versions.pq;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * A validator preregistration (EIP-8205) stored in the PQ state until it is used by a deposit or
 * expires at {@code expiry_slot}.
 */
public class ValidatorPreregistration
    extends Container4<ValidatorPreregistration, SszBytes32, SszBytes32, SszBytes32, SszUInt64> {

  public static class ValidatorPreregistrationSchema
      extends ContainerSchema4<
          ValidatorPreregistration, SszBytes32, SszBytes32, SszBytes32, SszUInt64> {

    private ValidatorPreregistrationSchema() {
      super(
          "ValidatorPreregistration",
          namedSchema("pubkey", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("vrf_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("expiry_slot", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public ValidatorPreregistration createFromBackingNode(final TreeNode node) {
      return new ValidatorPreregistration(this, node);
    }
  }

  public static final ValidatorPreregistrationSchema SSZ_SCHEMA =
      new ValidatorPreregistrationSchema();

  private ValidatorPreregistration(
      final ValidatorPreregistrationSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public ValidatorPreregistration(
      final Bytes32 xmssPublicKey,
      final Bytes32 withdrawalCredentials,
      final Bytes32 vrfRoot,
      final UInt64 expirySlot) {
    super(
        SSZ_SCHEMA,
        SszBytes32.of(xmssPublicKey),
        SszBytes32.of(withdrawalCredentials),
        SszBytes32.of(vrfRoot),
        SszUInt64.of(expirySlot));
  }

  public Bytes32 getXmssPublicKey() {
    return getField0().get();
  }

  public Bytes32 getWithdrawalCredentials() {
    return getField1().get();
  }

  public Bytes32 getVrfRoot() {
    return getField2().get();
  }

  public UInt64 getExpirySlot() {
    return getField3().get();
  }

  @Override
  public ValidatorPreregistrationSchema getSchema() {
    return (ValidatorPreregistrationSchema) super.getSchema();
  }
}
