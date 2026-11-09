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

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public final class ExecutionPayloadEnvelopesByRangeRequestMessage
    extends Container2<ExecutionPayloadEnvelopesByRangeRequestMessage, SszUInt64, SszUInt64>
    implements RpcRequest {

  public static class ExecutionPayloadEnvelopesByRangeRequestMessageSchema
      extends ContainerSchema2<
          ExecutionPayloadEnvelopesByRangeRequestMessage, SszUInt64, SszUInt64> {

    public ExecutionPayloadEnvelopesByRangeRequestMessageSchema() {
      super(
          "ExecutionPayloadEnvelopesByRangeRequestMessage",
          namedSchema("start_slot", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("count", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public ExecutionPayloadEnvelopesByRangeRequestMessage createFromBackingNode(
        final TreeNode node) {
      return new ExecutionPayloadEnvelopesByRangeRequestMessage(this, node);
    }
  }

  public static final ExecutionPayloadEnvelopesByRangeRequestMessageSchema SSZ_SCHEMA =
      new ExecutionPayloadEnvelopesByRangeRequestMessageSchema();

  private ExecutionPayloadEnvelopesByRangeRequestMessage(
      final ExecutionPayloadEnvelopesByRangeRequestMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public ExecutionPayloadEnvelopesByRangeRequestMessage(
      final UInt64 startSlot, final UInt64 count) {
    super(SSZ_SCHEMA, SszUInt64.of(startSlot), SszUInt64.of(count));
  }

  public UInt64 getStartSlot() {
    return getField0().get();
  }

  public UInt64 getCount() {
    return getField1().get();
  }

  public UInt64 getMaxSlot() {
    return getStartSlot().plus(getCount().minusMinZero(1));
  }

  @Override
  public int getMaximumResponseChunks() {
    return getCount().intValue();
  }
}
