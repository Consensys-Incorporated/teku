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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public final class BeaconBlocksByRangeRequestMessage
    extends Container3<BeaconBlocksByRangeRequestMessage, SszUInt64, SszUInt64, SszUInt64>
    implements RpcRequest {

  public static class BeaconBlocksByRangeRequestMessageSchema
      extends ContainerSchema3<BeaconBlocksByRangeRequestMessage, SszUInt64, SszUInt64, SszUInt64> {

    public BeaconBlocksByRangeRequestMessageSchema() {
      super(
          "BeaconBlocksByRangeRequestMessage",
          namedSchema("start_slot", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("count", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("step", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public BeaconBlocksByRangeRequestMessage createFromBackingNode(final TreeNode node) {
      return new BeaconBlocksByRangeRequestMessage(this, node);
    }
  }

  public static final BeaconBlocksByRangeRequestMessageSchema SSZ_SCHEMA =
      new BeaconBlocksByRangeRequestMessageSchema();

  private BeaconBlocksByRangeRequestMessage(
      final BeaconBlocksByRangeRequestMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public BeaconBlocksByRangeRequestMessage(
      final UInt64 startSlot, final UInt64 count, final UInt64 step) {
    super(SSZ_SCHEMA, SszUInt64.of(startSlot), SszUInt64.of(count), SszUInt64.of(step));
  }

  public UInt64 getStartSlot() {
    return getField0().get();
  }

  public UInt64 getCount() {
    return getField1().get();
  }

  public UInt64 getStep() {
    return getField2().get();
  }

  public UInt64 getMaxSlot() {
    return getStartSlot().plus(getCount().minusMinZero(1).times(getStep()));
  }

  @Override
  public int getMaximumResponseChunks() {
    return getCount().intValue();
  }
}
