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

package tech.pegasys.teku.spec.datastructures.execution.versions.eip8483;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

// https://eips.ethereum.org/EIPS/eip-8483 (builder variant: 32-byte XMSS public key)
public class BuilderPqRegistrationRequest
    extends Container3<BuilderPqRegistrationRequest, SszByteVector, SszPublicKey, SszBytes32> {

  public static final byte REQUEST_TYPE = 0x6;
  public static final Bytes REQUEST_TYPE_PREFIX = Bytes.of(REQUEST_TYPE);

  BuilderPqRegistrationRequest(
      final BuilderPqRegistrationRequestSchema schema,
      final Bytes20 sourceAddress,
      final BLSPublicKey builderPubkey,
      final Bytes32 pqData) {
    super(
        schema,
        SszByteVector.fromBytes(sourceAddress.getWrappedBytes()),
        new SszPublicKey(builderPubkey),
        SszBytes32.of(pqData));
  }

  BuilderPqRegistrationRequest(
      final BuilderPqRegistrationRequestSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Bytes20 getSourceAddress() {
    return new Bytes20(getField0().getBytes());
  }

  public BLSPublicKey getBuilderPubkey() {
    return getField1().getBLSPublicKey();
  }

  /** The builder's 32-byte XMSS public key. */
  public Bytes32 getPqData() {
    return getField2().get();
  }

  @Override
  public BuilderPqRegistrationRequestSchema getSchema() {
    return (BuilderPqRegistrationRequestSchema) super.getSchema();
  }
}
