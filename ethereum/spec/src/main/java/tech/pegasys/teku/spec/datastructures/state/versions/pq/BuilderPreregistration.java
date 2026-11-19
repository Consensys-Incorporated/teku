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
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * A builder preregistration (EIP-8205) stored in the PQ state until it is used by a builder deposit
 * or expires at {@code expiry_slot}. A builder has no VRF root.
 */
public class BuilderPreregistration
    extends Container3<BuilderPreregistration, SszBytes32, SszBytes32, SszUInt64> {

  public static class BuilderPreregistrationSchema
      extends ContainerSchema3<BuilderPreregistration, SszBytes32, SszBytes32, SszUInt64> {

    private BuilderPreregistrationSchema() {
      super(
          "BuilderPreregistration",
          namedSchema("pubkey", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("expiry_slot", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public BuilderPreregistration createFromBackingNode(final TreeNode node) {
      return new BuilderPreregistration(this, node);
    }
  }

  public static final BuilderPreregistrationSchema SSZ_SCHEMA = new BuilderPreregistrationSchema();

  private BuilderPreregistration(
      final BuilderPreregistrationSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BuilderPreregistration(
      final Bytes32 xmssPublicKey, final Bytes32 withdrawalCredentials, final UInt64 expirySlot) {
    super(
        SSZ_SCHEMA,
        SszBytes32.of(xmssPublicKey),
        SszBytes32.of(withdrawalCredentials),
        SszUInt64.of(expirySlot));
  }

  public Bytes32 getXmssPublicKey() {
    return getField0().get();
  }

  public Bytes32 getWithdrawalCredentials() {
    return getField1().get();
  }

  public UInt64 getExpirySlot() {
    return getField2().get();
  }

  @Override
  public BuilderPreregistrationSchema getSchema() {
    return (BuilderPreregistrationSchema) super.getSchema();
  }
}
