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

import static tech.pegasys.teku.spec.constants.NetworkConstants.MAX_REQUEST_LIGHT_CLIENT_UPDATES;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public final class LightClientUpdatesByRangeRequestMessage
    extends Container2<LightClientUpdatesByRangeRequestMessage, SszUInt64, SszUInt64>
    implements RpcRequest {

  public static class LightClientUpdatesByRangeRequestMessageSchema
      extends ContainerSchema2<LightClientUpdatesByRangeRequestMessage, SszUInt64, SszUInt64> {

    public LightClientUpdatesByRangeRequestMessageSchema() {
      super(
          "LightClientUpdatesByRangeRequestMessage",
          namedSchema("start_period", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("count", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public LightClientUpdatesByRangeRequestMessage createFromBackingNode(final TreeNode node) {
      return new LightClientUpdatesByRangeRequestMessage(this, node);
    }
  }

  public static final LightClientUpdatesByRangeRequestMessageSchema SSZ_SCHEMA =
      new LightClientUpdatesByRangeRequestMessageSchema();

  private LightClientUpdatesByRangeRequestMessage(
      final LightClientUpdatesByRangeRequestMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public LightClientUpdatesByRangeRequestMessage(final UInt64 startPeriod, final UInt64 count) {
    super(SSZ_SCHEMA, SszUInt64.of(startPeriod), SszUInt64.of(count));
  }

  public UInt64 getStartPeriod() {
    return getField0().get();
  }

  public UInt64 getCount() {
    return getField1().get();
  }

  @Override
  public int getMaximumResponseChunks() {
    return getCount().min(MAX_REQUEST_LIGHT_CLIENT_UPDATES).intValue();
  }
}
