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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container1;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public final class LightClientBootstrapRequestMessage
    extends Container1<LightClientBootstrapRequestMessage, SszBytes32> implements RpcRequest {

  public static class LightClientBootstrapRequestMessageSchema
      extends ContainerSchema1<LightClientBootstrapRequestMessage, SszBytes32> {

    public LightClientBootstrapRequestMessageSchema() {
      super(
          "LightClientBootstrapRequestMessage",
          namedSchema("block_root", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public LightClientBootstrapRequestMessage createFromBackingNode(final TreeNode node) {
      return new LightClientBootstrapRequestMessage(this, node);
    }
  }

  public static final LightClientBootstrapRequestMessageSchema SSZ_SCHEMA =
      new LightClientBootstrapRequestMessageSchema();

  private LightClientBootstrapRequestMessage(
      final LightClientBootstrapRequestMessageSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public LightClientBootstrapRequestMessage(final Bytes32 blockRoot) {
    super(SSZ_SCHEMA, SszBytes32.of(blockRoot));
  }

  public Bytes32 getBlockRoot() {
    return getField0().get();
  }

  @Override
  public int getMaximumResponseChunks() {
    return 1;
  }
}
