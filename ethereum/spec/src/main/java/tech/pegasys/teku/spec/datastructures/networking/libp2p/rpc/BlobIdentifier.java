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
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BlobIdentifier extends Container2<BlobIdentifier, SszBytes32, SszUInt64> {

  public static class BlobIdentifierSchema
      extends ContainerSchema2<BlobIdentifier, SszBytes32, SszUInt64> {

    private BlobIdentifierSchema() {
      super(
          "BlobIdentifier",
          namedSchema("block_root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          namedSchema("index", SszPrimitiveSchemas.UINT64_SCHEMA));
    }

    @Override
    public BlobIdentifier createFromBackingNode(final TreeNode node) {
      return new BlobIdentifier(node);
    }
  }

  public static final BlobIdentifierSchema SSZ_SCHEMA = new BlobIdentifierSchema();

  private BlobIdentifier(final TreeNode node) {
    super(SSZ_SCHEMA, node);
  }

  public BlobIdentifier(final Bytes32 root, final UInt64 index) {
    super(SSZ_SCHEMA, SszBytes32.of(root), SszUInt64.of(index));
  }

  public Bytes32 getBlockRoot() {
    return getField0().get();
  }

  public UInt64 getIndex() {
    return getField1().get();
  }
}
