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

package tech.pegasys.teku.spec.datastructures.execution.versions.capella;

import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class Withdrawal
    extends Container4<Withdrawal, SszUInt64, SszUInt64, SszByteVector, SszUInt64> {

  Withdrawal(
      final WithdrawalSchema schema,
      final UInt64 index,
      final UInt64 validatorIndex,
      final Bytes20 address,
      final UInt64 amount) {
    super(
        schema,
        SszUInt64.of(index),
        SszUInt64.of(validatorIndex),
        SszByteVector.fromBytes(address.getWrappedBytes()),
        SszUInt64.of(amount));
  }

  public static final WithdrawalSchema SSZ_SCHEMA = new WithdrawalSchema();

  Withdrawal(final WithdrawalSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public UInt64 getValidatorIndex() {
    return getField1().get();
  }

  public Bytes20 getAddress() {
    return new Bytes20(getField2().getBytes());
  }

  public UInt64 getAmount() {
    return getField3().get();
  }

  @Override
  public WithdrawalSchema getSchema() {
    return (WithdrawalSchema) super.getSchema();
  }
}
