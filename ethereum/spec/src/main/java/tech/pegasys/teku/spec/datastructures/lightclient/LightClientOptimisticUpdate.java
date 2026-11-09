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

package tech.pegasys.teku.spec.datastructures.lightclient;

import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;

public class LightClientOptimisticUpdate
    extends Container3<LightClientOptimisticUpdate, LightClientHeader, SyncAggregate, SszUInt64> {
  public LightClientOptimisticUpdate(
      final LightClientOptimisticUpdateSchema schema,
      final LightClientHeader attestedHeader,
      final SyncAggregate syncAggregate,
      final SszUInt64 signatureSlot) {
    super(schema, attestedHeader, syncAggregate, signatureSlot);
  }

  protected LightClientOptimisticUpdate(
      final LightClientOptimisticUpdateSchema schema, final TreeNode node) {
    super(schema, node);
  }

  public LightClientHeader getAttestedHeader() {
    return getField0();
  }

  public SyncAggregate getSyncAggregate() {
    return getField1();
  }

  public SszUInt64 getSignatureSlot() {
    return getField2();
  }
}
