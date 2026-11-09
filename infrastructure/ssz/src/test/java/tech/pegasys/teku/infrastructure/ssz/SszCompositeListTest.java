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

package tech.pegasys.teku.infrastructure.ssz;

import static org.apache.tuweni.bytes.Bytes.concatenate;
import static org.apache.tuweni.bytes.Bytes32.ZERO;
import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.crypto.Hash.sha256;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszLengthBounds;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.ssz.sos.SszWriter;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeStore;

public class SszCompositeListTest {

  static final SszSchema<TestView> TEST_TYPE =
      new SszSchema<>() {

        @Override
        public TreeNode getDefaultTree() {
          throw new UnsupportedOperationException();
        }

        @Override
        public TestView getDefault() {
          return new TestView(0);
        }

        @Override
        public TestView createFromBackingNode(final TreeNode node) {
          return new TestView(node);
        }

        @Override
        public boolean isPrimitive() {
          return false;
        }

        @Override
        public DeserializableTypeDefinition<TestView> getJsonTypeDefinition() {
          return DeserializableTypeDefinition.string(TestView.class)
              .parser(value -> null)
              .formatter(value -> "")
              .build();
        }

        @Override
        public void storeBackingNodes(
            final TreeNodeStore nodeStore,
            final int maxBranchLevelsSkipped,
            final long rootGIndex,
            final TreeNode node) {}

        @Override
        public TreeNode loadBackingNodes(
            final TreeNodeSource nodeSource, final Bytes32 rootHash, final long rootGIndex) {
          return null;
        }

        @Override
        public boolean isFixedSize() {
          return true;
        }

        @Override
        public int getSszFixedPartSize() {
          return 0;
        }

        @Override
        public int getSszVariablePartSize(final TreeNode node) {
          return 0;
        }

        @Override
        public int sszSerializeTree(final TreeNode node, final SszWriter writer) {
          return 0;
        }

        @Override
        public TreeNode sszDeserializeTree(final SszReader reader) {
          return null;
        }

        @Override
        public SszLengthBounds getSszLengthBounds() {
          return SszLengthBounds.ZERO;
        }
      };

  static class TestView implements SszData {

    TreeNode node;
    public final int v;

    public TestView(final int v) {
      this.v = v;
    }

    public TestView(final TreeNode node) {
      this.node = node;
      this.v = node.hashTreeRoot().trimLeadingZeros().toInt();
    }

    @Override
    public SszSchema<?> getSchema() {
      return TEST_TYPE;
    }

    @Override
    public TreeNode getBackingNode() {
      if (node == null) {
        node = LeafNode.create(Bytes32.leftPad(Bytes.ofUnsignedInt(v)));
      }
      return node;
    }

    @Override
    public SszMutableData createWritableCopy() {
      throw new UnsupportedOperationException();
    }
  }

  @Test
  public void simpleTest1() {
    SszListSchema<TestView, ?> listType = SszListSchema.create(TEST_TYPE, 3);
    SszMutableList<TestView> list = listType.getDefault().createWritableCopy();
    TreeNode n0 = list.commitChanges().getBackingNode();
    list.set(0, new TestView(0x111));
    TreeNode n1 = list.commitChanges().getBackingNode();
    list.set(1, new TestView(0x222));
    TreeNode n2 = list.commitChanges().getBackingNode();
    list.set(2, new TestView(0x333));
    TreeNode n3 = list.commitChanges().getBackingNode();
    list.set(0, new TestView(0x444));
    TreeNode n4 = list.commitChanges().getBackingNode();

    Assertions.assertThat(listType.createFromBackingNode(n0).size()).isEqualTo(0);
    Assertions.assertThat(listType.createFromBackingNode(n1).size()).isEqualTo(1);
    Assertions.assertThat(listType.createFromBackingNode(n1).get(0).v).isEqualTo(0x111);
    Assertions.assertThat(listType.createFromBackingNode(n2).size()).isEqualTo(2);
    Assertions.assertThat(listType.createFromBackingNode(n2).get(0).v).isEqualTo(0x111);
    Assertions.assertThat(listType.createFromBackingNode(n2).get(1).v).isEqualTo(0x222);
    Assertions.assertThat(listType.createFromBackingNode(n3).size()).isEqualTo(3);
    Assertions.assertThat(listType.createFromBackingNode(n3).get(0).v).isEqualTo(0x111);
    Assertions.assertThat(listType.createFromBackingNode(n3).get(1).v).isEqualTo(0x222);
    Assertions.assertThat(listType.createFromBackingNode(n3).get(2).v).isEqualTo(0x333);
    Assertions.assertThat(listType.createFromBackingNode(n4).size()).isEqualTo(3);
    Assertions.assertThat(listType.createFromBackingNode(n4).get(0).v).isEqualTo(0x444);
    Assertions.assertThat(listType.createFromBackingNode(n4).get(1).v).isEqualTo(0x222);
    Assertions.assertThat(listType.createFromBackingNode(n4).get(2).v).isEqualTo(0x333);

    assertThat(n0.hashTreeRoot())
        .isEqualTo(
            sha256(
                concatenate(
                    sha256(
                        concatenate(
                            sha256(concatenate(ZERO, ZERO)), sha256(concatenate(ZERO, ZERO)))),
                    ZERO)));

    assertThat(n1.hashTreeRoot())
        .isEqualTo(
            sha256(
                concatenate(
                    sha256(
                        concatenate(
                            sha256(
                                concatenate(
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000111"),
                                    ZERO)),
                            sha256(concatenate(ZERO, ZERO)))),
                    Bytes32.fromHexString(
                        "0x0100000000000000000000000000000000000000000000000000000000000000"))));

    assertThat(n2.hashTreeRoot())
        .isEqualTo(
            sha256(
                concatenate(
                    sha256(
                        concatenate(
                            sha256(
                                concatenate(
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000111"),
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000222"))),
                            sha256(concatenate(ZERO, ZERO)))),
                    Bytes32.fromHexString(
                        "0x0200000000000000000000000000000000000000000000000000000000000000"))));

    assertThat(n3.hashTreeRoot())
        .isEqualTo(
            sha256(
                concatenate(
                    sha256(
                        concatenate(
                            sha256(
                                concatenate(
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000111"),
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000222"))),
                            sha256(
                                concatenate(
                                    Bytes32.fromHexString(
                                        "0x0000000000000000000000000000000000000000000000000000000000000333"),
                                    ZERO)))),
                    Bytes32.fromHexString(
                        "0x0300000000000000000000000000000000000000000000000000000000000000"))));
  }
}
