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

package tech.pegasys.teku.spec.datastructures.state;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;

public class Checkpoint extends Container2<Checkpoint, SszUInt64, SszBytes32> {

  public static class CheckpointSchema extends ContainerSchema2<Checkpoint, SszUInt64, SszBytes32> {

    public CheckpointSchema() {
      super(
          "Checkpoint",
          namedSchema("epoch", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("root", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public Checkpoint createFromBackingNode(final TreeNode node) {
      return new Checkpoint(this, node);
    }
  }

  public static final CheckpointSchema SSZ_SCHEMA = new CheckpointSchema();

  private Checkpoint(final CheckpointSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public Checkpoint(final UInt64 epoch, final Bytes32 root) {
    super(SSZ_SCHEMA, SszUInt64.of(epoch), SszBytes32.of(root));
  }

  public UInt64 getEpoch() {
    return getField0().get();
  }

  public Bytes32 getRoot() {
    return getField1().get();
  }

  public UInt64 getEpochStartSlot(final Spec spec) {
    return spec.computeStartSlotAtEpoch(getEpoch());
  }

  public SlotAndBlockRoot toSlotAndBlockRoot(final Spec spec) {
    return new SlotAndBlockRoot(getEpochStartSlot(spec), getRoot());
  }
}
