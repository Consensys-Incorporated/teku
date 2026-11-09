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

package tech.pegasys.teku.spec.datastructures.state.versions.gloas;

import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BuilderPendingPayment
    extends Container3<BuilderPendingPayment, SszUInt64, BuilderPendingWithdrawal, SszUInt64> {

  protected BuilderPendingPayment(
      final BuilderPendingPaymentSchema schema,
      final UInt64 weight,
      final BuilderPendingWithdrawal withdrawal,
      final UInt64 proposerIndex) {
    super(schema, SszUInt64.of(weight), withdrawal, SszUInt64.of(proposerIndex));
  }

  protected BuilderPendingPayment(
      final BuilderPendingPaymentSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public UInt64 getWeight() {
    return getField0().get();
  }

  public BuilderPendingWithdrawal getWithdrawal() {
    return getField1();
  }

  public UInt64 getProposerIndex() {
    return getField2().get();
  }

  public BuilderPendingPayment copyWithNewWeight(final UInt64 weight) {
    return new BuilderPendingPayment(getSchema(), weight, getWithdrawal(), getProposerIndex());
  }

  @Override
  public BuilderPendingPaymentSchema getSchema() {
    return (BuilderPendingPaymentSchema) super.getSchema();
  }
}
