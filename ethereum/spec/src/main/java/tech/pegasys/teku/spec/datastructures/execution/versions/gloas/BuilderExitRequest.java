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

package tech.pegasys.teku.spec.datastructures.execution.versions.gloas;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

// https://eips.ethereum.org/EIPS/eip-8282
public class BuilderExitRequest
    extends Container2<BuilderExitRequest, SszByteVector, SszPublicKey> {

  public static final byte REQUEST_TYPE = 0x4;
  public static final Bytes REQUEST_TYPE_PREFIX = Bytes.of(REQUEST_TYPE);

  BuilderExitRequest(
      final BuilderExitRequestSchema schema,
      final Bytes20 sourceAddress,
      final BLSPublicKey pubkey) {
    super(
        schema, SszByteVector.fromBytes(sourceAddress.getWrappedBytes()), new SszPublicKey(pubkey));
  }

  BuilderExitRequest(final BuilderExitRequestSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Bytes20 getSourceAddress() {
    return new Bytes20(getField0().getBytes());
  }

  public BLSPublicKey getPubkey() {
    return getField1().getBLSPublicKey();
  }

  @Override
  public BuilderExitRequestSchema getSchema() {
    return (BuilderExitRequestSchema) super.getSchema();
  }
}
