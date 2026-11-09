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

import tech.pegasys.teku.infrastructure.ssz.containers.Container1;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * <a
 * href="https://github.com/ethereum/consensus-specs/blob/v0.11.1/specs/phase0/p2p-interface.md#metadata">MetaData</a>
 */
public class PingMessage extends Container1<PingMessage, SszUInt64> implements RpcRequest {

  static class PingMessageSchema extends ContainerSchema1<PingMessage, SszUInt64> {

    public PingMessageSchema() {
      super("PingMessage", namedSchema("seq_number", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public PingMessage createFromBackingNode(final TreeNode node) {
      return new PingMessage(this, node);
    }
  }

  public static final PingMessageSchema SSZ_SCHEMA = new PingMessageSchema();

  public PingMessage(final PingMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public PingMessage(final UInt64 seqNumber) {
    super(SSZ_SCHEMA, SszUInt64.of(seqNumber));
  }

  public UInt64 getSeqNumber() {
    return getField0().get();
  }

  @Override
  public int getMaximumResponseChunks() {
    return 1;
  }
}
