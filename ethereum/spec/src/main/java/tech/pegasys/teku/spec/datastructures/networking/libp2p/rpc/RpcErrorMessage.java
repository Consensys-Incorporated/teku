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

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.impl.SszListImpl;
import tech.pegasys.teku.infrastructure.ssz.impl.SszUtils;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class RpcErrorMessage extends SszListImpl<SszByte> implements SszList<SszByte> {

  public static final int MAX_ERROR_MESSAGE_LENGTH = 256;
  private static final Charset ERROR_MESSAGE_CHARSET = StandardCharsets.UTF_8;

  public static class RpcErrorMessageSchema
      extends AbstractSszListSchema<SszByte, RpcErrorMessage> {
    private RpcErrorMessageSchema() {
      super(SszPrimitiveSchemas.BYTE_SCHEMA, MAX_ERROR_MESSAGE_LENGTH);
    }

    @Override
    public RpcErrorMessage createFromBackingNode(final TreeNode node) {
      return new RpcErrorMessage(node);
    }
  }

  public static final RpcErrorMessageSchema SSZ_SCHEMA = new RpcErrorMessageSchema();

  public RpcErrorMessage(final Bytes bytes) {
    super(SSZ_SCHEMA, SszUtils.toSszByteList(SSZ_SCHEMA, bytes).getBackingNode());
  }

  private RpcErrorMessage(final TreeNode node) {
    super(SSZ_SCHEMA, node);
  }

  public Bytes getData() {
    return SszUtils.getAllBytes(this);
  }

  @Override
  public String toString() {
    return new String(getData().toArrayUnsafe(), ERROR_MESSAGE_CHARSET);
  }
}
