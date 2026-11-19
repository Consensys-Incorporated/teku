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

package tech.pegasys.teku.spec.datastructures.execution.versions.pq;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * A builder preregistration request (EIP-8205) emitted by the EL. The system contract checks that
 * the binding is signed by {@code pubkey} at a reserved leaf; a builder has no VRF root.
 */
public class BuilderPreregistrationRequest
    extends Container2<BuilderPreregistrationRequest, SszBytes32, SszBytes32> {

  public static final byte REQUEST_TYPE = 0x8;
  public static final Bytes REQUEST_TYPE_PREFIX = Bytes.of(REQUEST_TYPE);

  public static class BuilderPreregistrationRequestSchema
      extends ContainerSchema2<BuilderPreregistrationRequest, SszBytes32, SszBytes32> {

    private BuilderPreregistrationRequestSchema() {
      super(
          "BuilderPreregistrationRequest",
          namedSchema("pubkey", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public BuilderPreregistrationRequest createFromBackingNode(final TreeNode node) {
      return new BuilderPreregistrationRequest(this, node);
    }
  }

  public static final BuilderPreregistrationRequestSchema SSZ_SCHEMA =
      new BuilderPreregistrationRequestSchema();

  private BuilderPreregistrationRequest(
      final BuilderPreregistrationRequestSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BuilderPreregistrationRequest(
      final Bytes32 xmssPublicKey, final Bytes32 withdrawalCredentials) {
    super(SSZ_SCHEMA, SszBytes32.of(xmssPublicKey), SszBytes32.of(withdrawalCredentials));
  }

  public Bytes32 getXmssPublicKey() {
    return getField0().get();
  }

  public Bytes32 getWithdrawalCredentials() {
    return getField1().get();
  }

  @Override
  public BuilderPreregistrationRequestSchema getSchema() {
    return (BuilderPreregistrationRequestSchema) super.getSchema();
  }
}
