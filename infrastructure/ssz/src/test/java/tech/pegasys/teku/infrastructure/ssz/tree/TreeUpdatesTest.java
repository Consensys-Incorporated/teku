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

package tech.pegasys.teku.infrastructure.ssz.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteOrder;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUpdates.Update;

public class TreeUpdatesTest {

  public static TreeNode newTestLeaf(final long l) {
    return LeafNode.create(Bytes32.leftPad(Bytes.ofUnsignedLong(l, ByteOrder.BIG_ENDIAN)));
  }

  @Test
  public void splitTest1() {
    int treeDepth = 3;
    int treeWidth = 1 << (treeDepth - 1);
    TreeUpdates n1 =
        IntStream.range(0, 3)
            .mapToObj(i -> new Update(i + treeWidth, newTestLeaf(i)))
            .collect(TreeUpdates.collector());

    assertThatThrownBy(() -> n1.checkLeaf());
    assertThat(n1.isFinal()).isFalse();

    Pair<TreeUpdates, TreeUpdates> s1 = n1.splitAtPivot();
    TreeUpdates n10 = s1.getLeft();
    TreeUpdates n11 = s1.getRight();
    assertThat(n10.size()).isEqualTo(2);
    assertThat(n11.size()).isEqualTo(1);

    assertThatThrownBy(() -> n10.checkLeaf());
    assertThatThrownBy(() -> n11.checkLeaf());
    assertThat(n10.isFinal()).isFalse();
    assertThat(n11.isFinal()).isFalse();

    Pair<TreeUpdates, TreeUpdates> s10 = n10.splitAtPivot();
    Pair<TreeUpdates, TreeUpdates> s11 = n11.splitAtPivot();
    TreeUpdates n100 = s10.getLeft();
    TreeUpdates n101 = s10.getRight();
    TreeUpdates n110 = s11.getLeft();
    TreeUpdates n111 = s11.getRight();

    assertThat(n100.size()).isEqualTo(1);
    assertThat(n100.getGIndex(0)).isEqualTo(treeWidth + 0);
    assertThat(n100.getNode(0)).isEqualTo(newTestLeaf(0));
    assertThat(n101.size()).isEqualTo(1);
    assertThat(n101.getGIndex(0)).isEqualTo(treeWidth + 1);
    assertThat(n101.getNode(0)).isEqualTo(newTestLeaf(1));
    assertThat(n110.size()).isEqualTo(1);
    assertThat(n110.getGIndex(0)).isEqualTo(treeWidth + 2);
    assertThat(n110.getNode(0)).isEqualTo(newTestLeaf(2));
    assertThat(n111.size()).isEqualTo(0);
    n100.checkLeaf();
    n101.checkLeaf();
    n110.checkLeaf();
    assertThat(n100.isFinal()).isTrue();
    assertThat(n101.isFinal()).isTrue();
    assertThat(n110.isFinal()).isTrue();
    assertThat(n111.isFinal()).isFalse();

    assertThatThrownBy(() -> n111.checkLeaf());
    assertThatThrownBy(() -> n100.splitAtPivot());
    assertThatThrownBy(() -> n101.splitAtPivot());
    assertThatThrownBy(() -> n110.splitAtPivot());
    assertThatThrownBy(() -> n111.splitAtPivot());
  }

  @Test
  public void splitTest2() {
    int treeDepth = 3;
    int treeWidth = 1 << (treeDepth - 1);
    TreeUpdates n1 =
        IntStream.of(0, 3)
            .mapToObj(i -> new Update(i + treeWidth, newTestLeaf(i)))
            .collect(TreeUpdates.collector());

    Pair<TreeUpdates, TreeUpdates> s1 = n1.splitAtPivot();
    TreeUpdates n10 = s1.getLeft();
    TreeUpdates n11 = s1.getRight();
    assertThat(n10.size()).isEqualTo(1);
    assertThat(n11.size()).isEqualTo(1);

    assertThatThrownBy(() -> n10.checkLeaf());
    assertThatThrownBy(() -> n11.checkLeaf());
    assertThat(n10.isFinal()).isFalse();
    assertThat(n11.isFinal()).isFalse();
    assertThat(n10.getRelativeGIndex(0)).isEqualTo(0b10);
    assertThat(n11.getRelativeGIndex(0)).isEqualTo(0b11);

    Pair<TreeUpdates, TreeUpdates> s10 = n10.splitAtPivot();
    Pair<TreeUpdates, TreeUpdates> s11 = n11.splitAtPivot();
    TreeUpdates n100 = s10.getLeft();
    TreeUpdates n101 = s10.getRight();
    TreeUpdates n110 = s11.getLeft();
    TreeUpdates n111 = s11.getRight();

    assertThat(n100.size()).isEqualTo(1);
    assertThat(n100.getGIndex(0)).isEqualTo(treeWidth + 0);
    assertThat(n100.getRelativeGIndex(0)).isEqualTo(GIndexUtil.SELF_G_INDEX);
    assertThat(n100.getNode(0)).isEqualTo(newTestLeaf(0));
    assertThat(n101.size()).isEqualTo(0);
    assertThat(n110.size()).isEqualTo(0);

    assertThat(n111.size()).isEqualTo(1);
    assertThat(n111.getGIndex(0)).isEqualTo(treeWidth + 3);
    assertThat(n100.getRelativeGIndex(0)).isEqualTo(GIndexUtil.SELF_G_INDEX);
    assertThat(n111.getNode(0)).isEqualTo(newTestLeaf(3));

    n100.checkLeaf();
    n111.checkLeaf();
    assertThat(n100.isFinal()).isTrue();
    assertThat(n101.isFinal()).isFalse();
    assertThat(n110.isFinal()).isFalse();
    assertThat(n111.isFinal()).isTrue();
  }

  @Test
  public void splitTest3() {
    int treeDepth = 3;
    int treeWidth = 1 << (treeDepth - 1);
    TreeUpdates n1 =
        IntStream.of(3)
            .mapToObj(i -> new Update(i + treeWidth, newTestLeaf(i)))
            .collect(TreeUpdates.collector());

    Pair<TreeUpdates, TreeUpdates> s1 = n1.splitAtPivot();
    TreeUpdates n10 = s1.getLeft();
    TreeUpdates n11 = s1.getRight();
    assertThat(n10.size()).isEqualTo(0);
    assertThat(n11.size()).isEqualTo(1);

    assertThatThrownBy(() -> n10.checkLeaf());
    assertThatThrownBy(() -> n11.checkLeaf());
    assertThat(n10.isFinal()).isFalse();
    assertThat(n11.isFinal()).isFalse();

    Pair<TreeUpdates, TreeUpdates> s11 = n11.splitAtPivot();
    TreeUpdates n110 = s11.getLeft();
    TreeUpdates n111 = s11.getRight();

    assertThat(n110.size()).isEqualTo(0);
    assertThat(n111.size()).isEqualTo(1);
    assertThat(n111.getGIndex(0)).isEqualTo(treeWidth + 3);
    assertThat(n111.getRelativeGIndex(0)).isEqualTo(GIndexUtil.SELF_G_INDEX);
    assertThat(n111.getNode(0)).isEqualTo(newTestLeaf(3));

    n111.checkLeaf();
    assertThat(n110.isFinal()).isFalse();
    assertThat(n111.isFinal()).isTrue();
  }

  @Test
  public void illegalIndicesTest() {
    int treeDepth = 3;
    int treeWidth = 1 << (treeDepth - 1);
    assertThatThrownBy(
            () ->
                IntStream.of(0, 2, 1)
                    .mapToObj(i -> new Update(i + treeWidth, newTestLeaf(i)))
                    .collect(TreeUpdates.collector()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                IntStream.of(1, 0, 2)
                    .mapToObj(i -> new Update(i + treeWidth, newTestLeaf(i)))
                    .collect(TreeUpdates.collector()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                IntStream.of(1, 1, 2)
                    .mapToObj(i -> new Update(i + treeWidth, newTestLeaf(i)))
                    .collect(TreeUpdates.collector()))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                IntStream.of(0, 4, 5)
                    .mapToObj(i -> new Update(i, newTestLeaf(i)))
                    .collect(TreeUpdates.collector()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                IntStream.of(1, 4, 5)
                    .mapToObj(i -> new Update(i, newTestLeaf(i)))
                    .collect(TreeUpdates.collector()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                IntStream.of(3, 4, 5)
                    .mapToObj(i -> new Update(i, newTestLeaf(i)))
                    .collect(TreeUpdates.collector()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                IntStream.of(4, 5, 123)
                    .mapToObj(i -> new Update(i, newTestLeaf(i)))
                    .collect(TreeUpdates.collector()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
