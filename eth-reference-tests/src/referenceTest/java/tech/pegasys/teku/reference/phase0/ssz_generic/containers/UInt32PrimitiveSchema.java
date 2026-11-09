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

package tech.pegasys.teku.reference.phase0.ssz_generic.containers;

import static com.google.common.base.Preconditions.checkArgument;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.PrimitiveTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszTypeDefinitionWrapper;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class UInt32PrimitiveSchema extends AbstractSszPrimitiveSchema<Long, SszUInt32> {

  public static final AbstractSszPrimitiveSchema<Long, SszUInt32> UINT32_SCHEMA =
      new UInt32PrimitiveSchema();

  private static final long MAX_UINT32 = 0xFFFF_FFFFL;
  private static final DeserializableTypeDefinition<Long> UINT32_TYPE =
      new PrimitiveTypeDefinition<>() {
        @Override
        protected void serializeOpenApiTypeFields(final JsonGenerator gen) throws IOException {
          gen.writeStringField("type", "number");
          gen.writeStringField("description", "unsigned 32 bit integer");
          gen.writeStringField("format", "uint32");
        }

        @Override
        public void serialize(final Long value, final JsonGenerator gen) throws IOException {
          gen.writeNumber(value);
        }

        @Override
        public Long deserialize(final JsonParser parser) throws IOException {
          return Long.parseUnsignedLong(parser.getValueAsString());
        }

        @Override
        public String serializeToString(final Long value) {
          return Objects.toString(value, null);
        }

        @Override
        public Long deserializeFromString(final String value) {
          return Long.parseUnsignedLong(value);
        }
      };

  private UInt32PrimitiveSchema() {
    super(32);
  }

  @Override
  public Long createFromLeafBackingNode(final LeafDataNode node, final int internalIndex) {
    final Bytes elementBytes = node.getData().slice(internalIndex * 4, 4);
    return elementBytes.toLong(ByteOrder.LITTLE_ENDIAN);
  }

  @Override
  public TreeNode updateBackingNode(
      final TreeNode srcNode, final int internalIndex, final SszData newValue) {
    final long value = ((SszUInt32) newValue).get();
    final Bytes bytes = Bytes.ofUnsignedInt(value, ByteOrder.LITTLE_ENDIAN);
    final Bytes currentValue = ((LeafNode) srcNode).getData();
    return LeafNode.create(updateExtending(currentValue, internalIndex * 4, bytes));
  }

  @Override
  public SszUInt32 boxed(final Long rawValue) {
    checkArgument(
        0 <= rawValue && rawValue <= MAX_UINT32, "Value %s is outside range for uint32", rawValue);
    return SszUInt32.of(rawValue);
  }

  @Override
  public TreeNode getDefaultTree() {
    return LeafNode.ZERO_LEAVES[4];
  }

  @Override
  public DeserializableTypeDefinition<SszUInt32> getJsonTypeDefinition() {
    return new SszTypeDefinitionWrapper<>(this, UINT32_TYPE);
  }

  @Override
  public String toString() {
    return "UInt32";
  }
}
