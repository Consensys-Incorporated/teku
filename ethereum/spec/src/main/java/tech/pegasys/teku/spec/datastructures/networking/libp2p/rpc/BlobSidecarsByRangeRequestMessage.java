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

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlobSidecarsByRangeRequestMessage
    extends Container2<BlobSidecarsByRangeRequestMessage, SszUInt64, SszUInt64>
    implements RpcRequest {

  private final Optional<Integer> maxBlobsPerBlock;

  public static class BlobSidecarsByRangeRequestMessageSchema
      extends ContainerSchema2<BlobSidecarsByRangeRequestMessage, SszUInt64, SszUInt64> {

    public BlobSidecarsByRangeRequestMessageSchema() {
      super(
          "BlobSidecarsByRangeRequestMessage",
          namedSchema("start_slot", SszPrimitiveSchemas.UINT64_SCHEMA),
          namedSchema("count", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public BlobSidecarsByRangeRequestMessage createFromBackingNode(final TreeNode node) {
      return new BlobSidecarsByRangeRequestMessage(this, node);
    }
  }

  public static final BlobSidecarsByRangeRequestMessageSchema SSZ_SCHEMA =
      new BlobSidecarsByRangeRequestMessageSchema();

  private BlobSidecarsByRangeRequestMessage(
      final BlobSidecarsByRangeRequestMessage.BlobSidecarsByRangeRequestMessageSchema type,
      final TreeNode backingNode) {
    super(type, backingNode);
    this.maxBlobsPerBlock = Optional.empty();
  }

  public BlobSidecarsByRangeRequestMessage(
      final UInt64 startSlot, final UInt64 count, final int maxBlobsPerBlock) {
    super(SSZ_SCHEMA, SszUInt64.of(startSlot), SszUInt64.of(count));
    this.maxBlobsPerBlock = Optional.of(maxBlobsPerBlock);
  }

  public UInt64 getStartSlot() {
    return getField0().get();
  }

  public UInt64 getCount() {
    return getField1().get();
  }

  public UInt64 getMaxSlot() {
    return getStartSlot().plus(getCount()).minusMinZero(1);
  }

  @Override
  public int getMaximumResponseChunks() {
    return getCount().intValue()
        * maxBlobsPerBlock.orElseThrow(() -> new IllegalStateException("Unexpected method usage"));
  }
}
