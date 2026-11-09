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

package tech.pegasys.teku.infrastructure.ssz.schema;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions.SSZ_BIT_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions.SSZ_BYTES32_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions.SSZ_BYTES4_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions.SSZ_NONE_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions.SSZ_UINT256_TYPE_DEFINITION;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBoolean;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes4;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszNone;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt256;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszPrimitiveSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszUInt64Schema;
import tech.pegasys.teku.infrastructure.ssz.schema.json.SszPrimitiveTypeDefinitions;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/** The collection of commonly used basic types */
public final class SszPrimitiveSchemas {

  public static final AbstractSszPrimitiveSchema<Void, SszNone> NONE_SCHEMA =
      new AbstractSszPrimitiveSchema<>(0) {
        @Override
        public Void createFromLeafBackingNode(final LeafDataNode node, final int internalIndex) {
          return null;
        }

        @Override
        protected TreeNode updateBackingNode(
            final TreeNode srcNode, final int internalIndex, final SszData newValue) {
          return srcNode;
        }

        @Override
        public SszNone boxed(final Void rawValue) {
          return SszNone.INSTANCE;
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.EMPTY_LEAF;
        }

        @Override
        public DeserializableTypeDefinition<SszNone> getJsonTypeDefinition() {
          return SSZ_NONE_TYPE_DEFINITION;
        }

        @Override
        public String toString() {
          return "None";
        }
      };

  public static final AbstractSszPrimitiveSchema<Boolean, SszBit> BIT_SCHEMA =
      new AbstractSszPrimitiveSchema<>(1) {
        @Override
        public Boolean createFromLeafBackingNode(final LeafDataNode node, final int idx) {
          return (node.getData().get(idx / 8) & (1 << (idx % 8))) != 0;
        }

        @Override
        public TreeNode updateBackingNode(
            final TreeNode srcNode, final int idx, final SszData newValue) {
          int byteIndex = idx / 8;
          int bitIndex = idx % 8;
          Bytes originalBytes = ((LeafNode) srcNode).getData();
          byte b = byteIndex < originalBytes.size() ? originalBytes.get(byteIndex) : 0;
          boolean bit = ((SszBit) newValue).get();
          if (bit) {
            b = (byte) (b | (1 << bitIndex));
          } else {
            b = (byte) (b & ~(1 << bitIndex));
          }
          Bytes newBytes = updateExtending(originalBytes, byteIndex, Bytes.of(b));
          return LeafNode.create(newBytes);
        }

        @Override
        public SszBit boxed(final Boolean rawValue) {
          return SszBit.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[1];
        }

        @Override
        public DeserializableTypeDefinition<SszBit> getJsonTypeDefinition() {
          return SSZ_BIT_TYPE_DEFINITION;
        }

        @Override
        public LeafNode createNodeFromSszBytes(final Bytes bytes) {
          final byte data = bytes.get(0);
          if (data != 0 && data != 1) {
            throw new SszDeserializeException("Invalid bit value: " + bytes);
          }
          return super.createNodeFromSszBytes(bytes);
        }

        @Override
        public String toString() {
          return "Bit";
        }
      };

  public static final AbstractSszPrimitiveSchema<Byte, SszByte> BYTE_SCHEMA =
      new SszByteSchema() {
        @Override
        public DeserializableTypeDefinition<SszByte> getJsonTypeDefinition() {
          return SszPrimitiveTypeDefinitions.SSZ_BYTE_TYPE_DEFINITION;
        }

        @Override
        public String toString() {
          return "Bytes";
        }
      };

  public static final AbstractSszPrimitiveSchema<Boolean, SszBoolean> BOOLEAN_SCHEMA =
      new SszBooleanSchema() {
        @Override
        public DeserializableTypeDefinition<SszBoolean> getJsonTypeDefinition() {
          return SszPrimitiveTypeDefinitions.SSZ_BOOLEAN_TYPE_DEFINITION;
        }

        @Override
        public String toString() {
          return "Boolean";
        }
      };

  public static final AbstractSszPrimitiveSchema<Byte, SszByte> UINT8_SCHEMA =
      new SszByteSchema() {
        @Override
        public DeserializableTypeDefinition<SszByte> getJsonTypeDefinition() {
          return SszPrimitiveTypeDefinitions.SSZ_UINT8_TYPE_DEFINITION;
        }

        @Override
        public SszByte boxed(final Byte rawValue) {
          return SszByte.asUInt8(rawValue);
        }

        @Override
        public String toString() {
          return "UInt8";
        }
      };

  ;

  public static final AbstractSszPrimitiveSchema<UInt64, SszUInt64> UINT64_SCHEMA =
      new AbstractSszUInt64Schema<>() {
        @Override
        public SszUInt64 boxed(final UInt64 rawValue) {
          return SszUInt64.of(rawValue);
        }
      };

  public static final AbstractSszPrimitiveSchema<UInt256, SszUInt256> UINT256_SCHEMA =
      new AbstractSszPrimitiveSchema<>(256) {
        @Override
        public UInt256 createFromLeafBackingNode(final LeafDataNode node, final int internalIndex) {
          // reverse() is due to LE -> BE conversion
          return UInt256.fromBytes(node.getData().reverse());
        }

        @Override
        public TreeNode updateBackingNode(
            final TreeNode srcNode, final int internalIndex, final SszData newValue) {
          // reverse() is due to BE -> LE conversion
          return LeafNode.create(((SszUInt256) newValue).get().toBytes().reverse());
        }

        @Override
        public SszUInt256 boxed(final UInt256 rawValue) {
          return SszUInt256.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[32];
        }

        @Override
        public DeserializableTypeDefinition<SszUInt256> getJsonTypeDefinition() {
          return SSZ_UINT256_TYPE_DEFINITION;
        }

        @Override
        public String toString() {
          return "UInt256";
        }
      };

  public static final AbstractSszPrimitiveSchema<Bytes4, SszBytes4> BYTES4_SCHEMA =
      new AbstractSszPrimitiveSchema<>(32) {
        @Override
        public Bytes4 createFromLeafBackingNode(final LeafDataNode node, final int internalIndex) {
          return new Bytes4(node.getData().slice(internalIndex * 4, 4));
        }

        @Override
        public TreeNode updateBackingNode(
            final TreeNode srcNode, final int internalIndex, final SszData newValue) {
          checkArgument(
              internalIndex >= 0 && internalIndex < 8, "Invalid internal index: %s", internalIndex);
          Bytes bytes = ((SszBytes4) newValue).get().getWrappedBytes();
          Bytes curVal = ((LeafNode) srcNode).getData();
          Bytes newBytes = updateExtending(curVal, internalIndex * 4, bytes);
          return LeafNode.create(newBytes);
        }

        @Override
        public SszBytes4 boxed(final Bytes4 rawValue) {
          return SszBytes4.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[4];
        }

        @Override
        public DeserializableTypeDefinition<SszBytes4> getJsonTypeDefinition() {
          return SSZ_BYTES4_TYPE_DEFINITION;
        }

        @Override
        public String toString() {
          return "Bytes4";
        }
      };

  public static final AbstractSszPrimitiveSchema<Bytes32, SszBytes32> BYTES32_SCHEMA =
      new AbstractSszPrimitiveSchema<>(256) {
        @Override
        public Bytes32 createFromLeafBackingNode(final LeafDataNode node, final int internalIndex) {
          return node.hashTreeRoot();
        }

        @Override
        public TreeNode updateBackingNode(
            final TreeNode srcNode, final int internalIndex, final SszData newValue) {
          return LeafNode.create(((SszBytes32) newValue).get());
        }

        @Override
        public SszBytes32 boxed(final Bytes32 rawValue) {
          return SszBytes32.of(rawValue);
        }

        @Override
        public TreeNode getDefaultTree() {
          return LeafNode.ZERO_LEAVES[32];
        }

        @Override
        public DeserializableTypeDefinition<SszBytes32> getJsonTypeDefinition() {
          return SSZ_BYTES32_TYPE_DEFINITION;
        }

        @Override
        public String toString() {
          return "Bytes32";
        }
      };

  abstract static class SszByteSchema extends AbstractSszPrimitiveSchema<Byte, SszByte> {

    private SszByteSchema() {
      super(8);
    }

    @Override
    public Byte createFromLeafBackingNode(final LeafDataNode node, final int internalIndex) {
      return node.getData().get(internalIndex);
    }

    @Override
    public TreeNode updateBackingNode(
        final TreeNode srcNode, final int index, final SszData newValue) {
      byte aByte = ((SszByte) newValue).get();
      Bytes curVal = ((LeafNode) srcNode).getData();
      Bytes newBytes = updateExtending(curVal, index, Bytes.of(aByte));
      return LeafNode.create(newBytes);
    }

    @Override
    public SszByte boxed(final Byte rawValue) {
      return SszByte.of(rawValue);
    }

    @Override
    public TreeNode getDefaultTree() {
      return LeafNode.ZERO_LEAVES[1];
    }
  }

  abstract static class SszBooleanSchema extends AbstractSszPrimitiveSchema<Boolean, SszBoolean> {

    private SszBooleanSchema() {
      super(8);
    }

    @Override
    public Boolean createFromLeafBackingNode(final LeafDataNode node, final int internalIndex) {
      final byte data = node.getData().get(internalIndex);
      if (data != 0 && data != 1) {
        throw new IllegalArgumentException("Invalid leaf value: " + data);
      }
      return data == 1;
    }

    @Override
    public TreeNode updateBackingNode(
        final TreeNode srcNode, final int index, final SszData newValue) {
      boolean aNew = ((SszBoolean) newValue).get();
      Bytes curVal = ((LeafNode) srcNode).getData();
      Bytes newBytes = updateExtending(curVal, index, Bytes.of(aNew ? 1 : 0));
      return LeafNode.create(newBytes);
    }

    @Override
    public SszBoolean boxed(final Boolean rawValue) {
      return SszBoolean.of(rawValue);
    }

    @Override
    public TreeNode getDefaultTree() {
      return LeafNode.ZERO_LEAVES[1];
    }

    @Override
    public LeafNode createNodeFromSszBytes(final Bytes bytes) {
      for (byte current : bytes.toArrayUnsafe()) {
        if (current != 0 && current != 1) {
          throw new SszDeserializeException("Invalid serialized boolean value: " + current);
        }
      }
      return super.createNodeFromSszBytes(bytes);
    }
  }
}
