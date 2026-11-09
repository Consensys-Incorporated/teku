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
import tech.pegasys.teku.spec.config.SpecConfigGloas;

public class ExecutionPayloadEnvelopesByRootRequestMessage extends SszListImpl<SszBytes32>
    implements SszList<SszBytes32>, RpcRequest {

  public static class ExecutionPayloadEnvelopesByRootRequestMessageSchema
      extends AbstractSszListSchema<SszBytes32, ExecutionPayloadEnvelopesByRootRequestMessage> {

    public ExecutionPayloadEnvelopesByRootRequestMessageSchema(final SpecConfigGloas specConfig) {
      super(SszPrimitiveSchemas.BYTES32_SCHEMA, specConfig.getMaxRequestPayloads());
    }

    @Override
    public ExecutionPayloadEnvelopesByRootRequestMessage createFromBackingNode(
        final TreeNode node) {
      return new ExecutionPayloadEnvelopesByRootRequestMessage(this, node);
    }
  }

  public ExecutionPayloadEnvelopesByRootRequestMessage(
      final ExecutionPayloadEnvelopesByRootRequestMessageSchema schema,
      final List<Bytes32> beaconBlockRoots) {
    super(
        schema,
        schema.createTreeFromElements(beaconBlockRoots.stream().map(SszBytes32::of).toList()));
  }

  private ExecutionPayloadEnvelopesByRootRequestMessage(
      final ExecutionPayloadEnvelopesByRootRequestMessageSchema schema, final TreeNode node) {
    super(schema, node);
  }

  @Override
  public int getMaximumResponseChunks() {
    return size();
  }

  @Override
  public String toString() {
    return "ExecutionPayloadEnvelopesByRootRequestMessage{" + super.toString() + "}";
  }
}
