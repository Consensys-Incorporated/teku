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

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class BlsToExecutionChange
    extends Container3<BlsToExecutionChange, SszUInt64, SszPublicKey, SszByteVector> {

  BlsToExecutionChange(
      final BlsToExecutionChangeSchema schema,
      final UInt64 validatorIndex,
      final BLSPublicKey fromBlsPubkey,
      final Bytes20 toExecutionAddress) {
    super(
        schema,
        SszUInt64.of(validatorIndex),
        new SszPublicKey(fromBlsPubkey),
        SszByteVector.fromBytes(toExecutionAddress.getWrappedBytes()));
  }

  BlsToExecutionChange(final BlsToExecutionChangeSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public UInt64 getValidatorIndex() {
    return getField0().get();
  }

  public BLSPublicKey getFromBlsPubkey() {
    return getField1().getBLSPublicKey();
  }

  public Bytes20 getToExecutionAddress() {
    return new Bytes20(getField2().getBytes());
  }

  @Override
  public BlsToExecutionChangeSchema getSchema() {
    return (BlsToExecutionChangeSchema) super.getSchema();
  }
}
