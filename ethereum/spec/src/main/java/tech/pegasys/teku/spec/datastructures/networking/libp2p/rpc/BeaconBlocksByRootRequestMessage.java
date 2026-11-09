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

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.config.SpecConfig;

public class BeaconBlocksByRootRequestMessage extends SszListImpl<SszBytes32>
    implements SszList<SszBytes32>, RpcRequest {

  public static class BeaconBlocksByRootRequestMessageSchema
      extends AbstractSszListSchema<SszBytes32, BeaconBlocksByRootRequestMessage> {

    public BeaconBlocksByRootRequestMessageSchema(final SpecConfig specConfig) {
      // size validation according to the spec is done in the RPC handler
      super(SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getMaxRequestBlocks());
    }

    @Override
    public BeaconBlocksByRootRequestMessage createFromBackingNode(final TreeNode node) {
      return new BeaconBlocksByRootRequestMessage(this, node);
    }
  }

  public BeaconBlocksByRootRequestMessage(
      final BeaconBlocksByRootRequestMessageSchema schema, final List<Bytes32> roots) {
    super(schema, schema.createTreeFromElements(roots.stream().map(SszBytes32::of).toList()));
  }

  private BeaconBlocksByRootRequestMessage(
      final BeaconBlocksByRootRequestMessageSchema schema, final TreeNode node) {
    super(schema, node);
  }

  @Override
  public int getMaximumResponseChunks() {
    return size();
  }

  @Override
  public String toString() {
    return "BeaconBlocksByRootRequestMessage{" + super.toString() + "}";
  }
}
