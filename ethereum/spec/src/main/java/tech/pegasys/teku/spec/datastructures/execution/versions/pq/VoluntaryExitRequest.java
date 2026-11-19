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

package tech.pegasys.teku.spec.datastructures.execution.versions.pq;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.containers.Container1;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema1;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * A voluntary exit request emitted by the EL, replacing the BLS-signed voluntary exit in PQ. The
 * system contract checks that the exit is signed by {@code pubkey} at a reserved leaf, so the
 * request has no signature.
 */
public class VoluntaryExitRequest extends Container1<VoluntaryExitRequest, SszBytes32> {

  public static final byte REQUEST_TYPE = 0x9;
  public static final Bytes REQUEST_TYPE_PREFIX = Bytes.of(REQUEST_TYPE);

  public static class VoluntaryExitRequestSchema
      extends ContainerSchema1<VoluntaryExitRequest, SszBytes32> {

    private VoluntaryExitRequestSchema() {
      super("VoluntaryExitRequest", namedSchema("pubkey", SszPrimitiveSchemas.BYTES32_SCHEMA));
    }

    @Override
    public VoluntaryExitRequest createFromBackingNode(final TreeNode node) {
      return new VoluntaryExitRequest(this, node);
    }
  }

  public static final VoluntaryExitRequestSchema SSZ_SCHEMA = new VoluntaryExitRequestSchema();

  private VoluntaryExitRequest(final VoluntaryExitRequestSchema type, final TreeNode backingNode) {
    super(type, backingNode);
  }

  public VoluntaryExitRequest(final Bytes32 xmssPublicKey) {
    super(SSZ_SCHEMA, SszBytes32.of(xmssPublicKey));
  }

  public Bytes32 getXmssPublicKey() {
    return getField0().get();
  }

  @Override
  public VoluntaryExitRequestSchema getSchema() {
    return (VoluntaryExitRequestSchema) super.getSchema();
  }
}
