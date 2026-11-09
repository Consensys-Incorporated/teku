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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PayloadAttestationDataSchema
    extends ContainerSchema4<
        PayloadAttestationData, SszBytes32, SszUInt64, SszBoolean, SszBoolean> {

  public PayloadAttestationDataSchema() {
    super(
        "PayloadAttestationData",
        namedSchema("beacon_block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
        namedSchema("slot", SszPrimitiveSchemas.UINT64_SCHEMA),
        namedSchema("payload_present", SszPrimitiveSchemas.BOOLEAN_SCHEMA),
        namedSchema("blob_data_available", SszPrimitiveSchemas.BOOLEAN_SCHEMA));
  }

  public PayloadAttestationData create(
      final Bytes32 beaconBlockRoot,
      final UInt64 slot,
      final boolean payloadPresent,
      final boolean blobDataAvailable) {
    return new PayloadAttestationData(
        this, beaconBlockRoot, slot, payloadPresent, blobDataAvailable);
  }

  @Override
  public PayloadAttestationData createFromBackingNode(final TreeNode node) {
    return new PayloadAttestationData(this, node);
  }
}
