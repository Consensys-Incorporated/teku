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
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * A validator preregistration request (EIP-8205) emitted by the EL. The system contract checks that
 * the binding is signed by {@code pubkey} at a reserved leaf, so the request has no signature.
 */
public class ValidatorPreregistrationRequest
    extends Container3<ValidatorPreregistrationRequest, SszBytes32, SszBytes32, SszBytes32> {

  public static final byte REQUEST_TYPE = 0x7;
  public static final Bytes REQUEST_TYPE_PREFIX = Bytes.of(REQUEST_TYPE);

  public static class ValidatorPreregistrationRequestSchema
      extends ContainerSchema3<
          ValidatorPreregistrationRequest, SszBytes32, SszBytes32, SszBytes32> {

    private ValidatorPreregistrationRequestSchema() {
      super(
          "ValidatorPreregistrationRequest",
          namedSchema("pubkey", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("vrf_root", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public ValidatorPreregistrationRequest createFromBackingNode(final TreeNode node) {
      return new ValidatorPreregistrationRequest(this, node);
    }
  }

  public static final ValidatorPreregistrationRequestSchema SSZ_SCHEMA =
      new ValidatorPreregistrationRequestSchema();

  private ValidatorPreregistrationRequest(
      final ValidatorPreregistrationRequestSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public ValidatorPreregistrationRequest(
      final Bytes32 xmssPublicKey, final Bytes32 withdrawalCredentials, final Bytes32 vrfRoot) {
    super(
        SSZ_SCHEMA,
        SszBytes32.of(xmssPublicKey),
        SszBytes32.of(withdrawalCredentials),
        SszBytes32.of(vrfRoot));
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

  @Override
  public ValidatorPreregistrationRequestSchema getSchema() {
    return (ValidatorPreregistrationRequestSchema) super.getSchema();
  }
}
