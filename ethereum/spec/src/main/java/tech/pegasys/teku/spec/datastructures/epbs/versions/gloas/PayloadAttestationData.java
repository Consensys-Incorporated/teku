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
import tech.pegasys.teku.infrastructure.ssz.containers.Container4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PayloadAttestationData
    extends Container4<PayloadAttestationData, SszBytes32, SszUInt64, SszBoolean, SszBoolean> {

  PayloadAttestationData(
      final PayloadAttestationDataSchema schema,
      final Bytes32 beaconBlockRoot,
      final UInt64 slot,
      final boolean payloadPresent,
      final boolean blobDataAvailable) {
    super(
        schema,
        SszBytes32.of(beaconBlockRoot),
        SszUInt64.of(slot),
        SszBoolean.of(payloadPresent),
        SszBoolean.of(blobDataAvailable));
  }

  PayloadAttestationData(final PayloadAttestationDataSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Bytes32 getBeaconBlockRoot() {
    return getField0().get();
  }

  public UInt64 getSlot() {
    return getField1().get();
  }

  public boolean isPayloadPresent() {
    return getField2().get();
  }

  public boolean isBlobDataAvailable() {
    return getField3().get();
  }

  @Override
  public PayloadAttestationDataSchema getSchema() {
    return (PayloadAttestationDataSchema) super.getSchema();
  }
}
