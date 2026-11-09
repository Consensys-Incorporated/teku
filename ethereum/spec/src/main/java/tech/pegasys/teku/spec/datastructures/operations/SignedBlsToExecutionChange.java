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

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class SignedBlsToExecutionChange
    extends Container2<SignedBlsToExecutionChange, BlsToExecutionChange, SszSignature>
    implements MessageWithValidatorId {

  SignedBlsToExecutionChange(
      final SignedBlsToExecutionChangeSchema schema,
      final BlsToExecutionChange message,
      final BLSSignature signature) {
    super(schema, message, new SszSignature(signature));
  }

  SignedBlsToExecutionChange(
      final ContainerSchema2<SignedBlsToExecutionChange, BlsToExecutionChange, SszSignature> type,
      final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BlsToExecutionChange getMessage() {
    return getField0();
  }

  public BLSSignature getSignature() {
    return getField1().getSignature();
  }

  @Override
  public SignedBlsToExecutionChangeSchema getSchema() {
    return (SignedBlsToExecutionChangeSchema) super.getSchema();
  }

  @Override
  public UInt64 getValidatorId() {
    return getMessage().getValidatorIndex();
  }
}
