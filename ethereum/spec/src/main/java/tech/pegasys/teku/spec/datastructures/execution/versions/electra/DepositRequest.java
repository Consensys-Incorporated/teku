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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container5;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

// https://eips.ethereum.org/EIPS/eip-6110
public class DepositRequest
    extends Container5<
        DepositRequest, SszPublicKey, SszBytes32, SszUInt64, SszSignature, SszUInt64> {

  public static final byte REQUEST_TYPE = 0x0;
  public static final Bytes REQUEST_TYPE_PREFIX = Bytes.of(REQUEST_TYPE);

  DepositRequest(
      final DepositRequestSchema schema,
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature,
      final UInt64 index) {
    super(
        schema,
        new SszPublicKey(pubkey),
        SszBytes32.of(withdrawalCredentials),
        SszUInt64.of(amount),
        new SszSignature(signature),
        SszUInt64.of(index));
  }

  DepositRequest(final DepositRequestSchema type, final TreeNode backingNode) {
    super(type, backingNode);
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

  public BLSSignature getSignature() {
    return getField3().getSignature();
  }

  public UInt64 getIndex() {
    return getField4().get();
  }

  @Override
  public DepositRequestSchema getSchema() {
    return (DepositRequestSchema) super.getSchema();
  }
}
