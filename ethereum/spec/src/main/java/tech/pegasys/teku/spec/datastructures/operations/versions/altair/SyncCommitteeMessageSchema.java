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

package tech.pegasys.teku.spec.datastructures.operations.versions.altair;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.type.SszSignature;
import tech.pegasys.teku.spec.datastructures.type.SszSignatureSchema;

public class SyncCommitteeMessageSchema
    extends ContainerSchema4<SyncCommitteeMessage, SszUInt64, SszBytes32, SszUInt64, SszSignature> {

  public static final SyncCommitteeMessageSchema INSTANCE = new SyncCommitteeMessageSchema();

  private SyncCommitteeMessageSchema() {
    super(
        "SyncCommitteeMessage",
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("validator_index", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("signature", SszSignatureSchema.INSTANCE));
  }

  @Override
  public SyncCommitteeMessage createFromBackingNode(final TreeNode node) {
    return new SyncCommitteeMessage(this, node);
  }

  public SyncCommitteeMessage create(
      final UInt64 slot,
      final Bytes32 beaconBlockRoot,
      final UInt64 validatorIndex,
      final BLSSignature signature) {
    return new SyncCommitteeMessage(
        this,
        SszUInt64.of(slot),
        SszBytes32.of(beaconBlockRoot),
        SszUInt64.of(validatorIndex),
        new SszSignature(signature));
  }
}
