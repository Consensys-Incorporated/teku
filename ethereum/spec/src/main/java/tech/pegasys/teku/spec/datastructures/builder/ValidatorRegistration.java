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

package tech.pegasys.teku.spec.datastructures.builder;

import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class ValidatorRegistration
    extends Container4<ValidatorRegistration, SszByteVector, SszUInt64, SszUInt64, SszPublicKey> {

  protected ValidatorRegistration(
      final ValidatorRegistrationSchema schema, final TreeNode backingNode) {
    super(schema, backingNode);
  }

  protected ValidatorRegistration(
      final ValidatorRegistrationSchema schema,
      final SszByteVector feeRecipient,
      final SszUInt64 gasLimit,
      final SszUInt64 timestamp,
      final SszPublicKey publicKey) {
    super(schema, feeRecipient, gasLimit, timestamp, publicKey);
  }

  public Eth1Address getFeeRecipient() {
    return Eth1Address.fromBytes(getField0().getBytes());
  }

  public UInt64 getGasLimit() {
    return getField1().get();
  }

  public UInt64 getTimestamp() {
    return getField2().get();
  }

  public BLSPublicKey getPublicKey() {
    return getField3().getBLSPublicKey();
  }

  @Override
  public ValidatorRegistrationSchema getSchema() {
    return (ValidatorRegistrationSchema) super.getSchema();
  }
}
