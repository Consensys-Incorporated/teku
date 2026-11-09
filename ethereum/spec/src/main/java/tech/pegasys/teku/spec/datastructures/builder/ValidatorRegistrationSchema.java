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
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.ssz.collections.SszByteVector;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;

public class ValidatorRegistrationSchema
    extends ContainerSchema4<
        ValidatorRegistration, SszByteVector, SszUInt64, SszUInt64, SszPublicKey> {
  public ValidatorRegistrationSchema() {
    super(
        "ValidatorRegistration",
        namedSchema("fee_recipient", SszByteVectorSchema.create(Bytes20.SIZE)),
        namedSchema("gas_limit", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("timestamp", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("pubkey", SszPublicKeySchema.INSTANCE));
  }

  public ValidatorRegistration create(
      final Bytes20 feeRecipient,
      final UInt64 gasLimit,
      final UInt64 timestamp,
      final BLSPublicKey publicKey) {
    return new ValidatorRegistration(
        this,
        SszByteVector.fromBytes(feeRecipient.getWrappedBytes()),
        SszUInt64.of(gasLimit),
        SszUInt64.of(timestamp),
        new SszPublicKey(publicKey));
  }

  @Override
  public ValidatorRegistration createFromBackingNode(final TreeNode node) {
    return new ValidatorRegistration(this, node);
  }
}
