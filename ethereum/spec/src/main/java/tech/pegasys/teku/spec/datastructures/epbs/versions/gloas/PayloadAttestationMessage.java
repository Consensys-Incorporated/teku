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

package tech.pegasys.teku.spec.datastructures.epbs.versions.gloas;

import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;

public class PayloadAttestationMessage
    extends Container3<PayloadAttestationMessage, SszUInt64, PayloadAttestationData, SszSignature> {

  PayloadAttestationMessage(
      final PayloadAttestationMessageSchema schema,
      final UInt64 validatorIndex,
      final PayloadAttestationData data,
      final BLSSignature signature) {
    super(schema, SszUInt64.of(validatorIndex), data, new SszSignature(signature));
  }

  PayloadAttestationMessage(
      final PayloadAttestationMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public UInt64 getValidatorIndex() {
    return getField0().get();
  }

  public PayloadAttestationData getData() {
    return getField1();
  }

  public BLSSignature getSignature() {
    return getField2().getSignature();
  }

  @Override
  public PayloadAttestationMessageSchema getSchema() {
    return (PayloadAttestationMessageSchema) super.getSchema();
  }
}
