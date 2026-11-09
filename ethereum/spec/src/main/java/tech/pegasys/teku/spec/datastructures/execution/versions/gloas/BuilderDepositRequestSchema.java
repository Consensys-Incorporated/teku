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

package tech.pegasys.teku.spec.datastructures.execution.versions.gloas;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKeySchema;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class BuilderDepositRequestSchema
    extends ContainerSchema4<
        BuilderDepositRequest, SszPublicKey, SszBytes32, SszUInt64, SszSignature> {

  public BuilderDepositRequestSchema() {
    super(
        "BuilderDepositRequest",
        namedSchema("pubkey", SszPublicKeySchema.INSTANCE),
        namedSchema("withdrawal_credentials", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("amount", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  public BuilderDepositRequest create(
      final BLSPublicKey pubkey,
      final Bytes32 withdrawalCredentials,
      final UInt64 amount,
      final BLSSignature signature) {
    return new BuilderDepositRequest(this, pubkey, withdrawalCredentials, amount, signature);
  }

  @Override
  public BuilderDepositRequest createFromBackingNode(final TreeNode node) {
    return new BuilderDepositRequest(this, node);
  }
}
