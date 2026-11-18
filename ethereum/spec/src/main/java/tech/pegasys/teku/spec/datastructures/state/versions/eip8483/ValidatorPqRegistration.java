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

package tech.pegasys.teku.spec.datastructures.state.versions.eip8483;

import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorPqRegistration
    extends Container2<ValidatorPqRegistration, SszUInt64, SszByteVector> {

  ValidatorPqRegistration(
      final ValidatorPqRegistrationSchema schema, final UInt64 validatorIndex, final Bytes pqData) {
    super(schema, SszUInt64.of(validatorIndex), SszByteVector.fromBytes(pqData));
  }

  ValidatorPqRegistration(final ValidatorPqRegistrationSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public UInt64 getValidatorIndex() {
    return getField0().get();
  }

  /** 64 bytes: a 32-byte XMSS public key followed by 32 bytes of VRF data. */
  public Bytes getPqData() {
    return getField1().getBytes();
  }

  @Override
  public ValidatorPqRegistrationSchema getSchema() {
    return (ValidatorPqRegistrationSchema) super.getSchema();
  }
}
