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

package tech.pegasys.teku.spec.datastructures.execution.versions.electra;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

// https://eips.ethereum.org/EIPS/eip-7251
public class ConsolidationRequest
    extends Container3<ConsolidationRequest, SszByteVector, SszPublicKey, SszPublicKey> {

  public static final byte REQUEST_TYPE = 0x2;
  public static final Bytes REQUEST_TYPE_PREFIX = Bytes.of(REQUEST_TYPE);

  protected ConsolidationRequest(
      final ConsolidationRequestSchema schema,
      final Bytes20 sourceAddress,
      final BLSPublicKey sourcePubkey,
      final BLSPublicKey targetPubkey) {
    super(
        schema,
        SszByteVector.fromBytes(sourceAddress.getWrappedBytes()),
        new SszPublicKey(sourcePubkey),
        new SszPublicKey(targetPubkey));
  }

  ConsolidationRequest(final ConsolidationRequestSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Bytes20 getSourceAddress() {
    return new Bytes20(getField0().getBytes());
  }

  public BLSPublicKey getSourcePubkey() {
    return getField1().getBLSPublicKey();
  }

  public BLSPublicKey getTargetPubkey() {
    return getField2().getBLSPublicKey();
  }

  @Override
  public ConsolidationRequestSchema getSchema() {
    return (ConsolidationRequestSchema) super.getSchema();
  }
}
