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

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil.NodeRelation;

public class GIndexUtilTest {

  static final long INVALID_G_INDEX = 0L;

  static Stream<Arguments> compareCases() {
    return Stream.of(
        Arguments.of(GIndexUtil.SELF_G_INDEX, GIndexUtil.SELF_G_INDEX, NodeRelation.SAME),
        Arguments.of(Long.MAX_VALUE, Long.MAX_VALUE, NodeRelation.SAME),
        Arguments.of(Long.MIN_VALUE, Long.MIN_VALUE, NodeRelation.SAME),
        Arguments.of(GIndexUtil.LEFTMOST_G_INDEX, GIndexUtil.LEFTMOST_G_INDEX, NodeRelation.SAME),
        Arguments.of(GIndexUtil.RIGHTMOST_G_INDEX, GIndexUtil.RIGHTMOST_G_INDEX, NodeRelation.SAME),
        Arguments.of(0b1L, 0b11L, NodeRelation.PREDECESSOR),
        Arguments.of(0b1L, 0b10L, NodeRelation.PREDECESSOR),
        Arguments.of(0b10L, 0b11L, NodeRelation.LEFT),
        Arguments.of(0b1011L, 0b110L, NodeRelation.LEFT),
        Arguments.of(0b1010L, 0b110L, NodeRelation.LEFT),
        Arguments.of(0b1010L, 0b111L, NodeRelation.LEFT),
        Arguments.of(0b1011L, 0b1100L, NodeRelation.LEFT),
        Arguments.of(0b1011L, 0b101L, NodeRelation.SUCCESSOR),
        Arguments.of(0b10L, GIndexUtil.RIGHTMOST_G_INDEX, NodeRelation.LEFT),
        Arguments.of(0b11L, GIndexUtil.RIGHTMOST_G_INDEX, NodeRelation.PREDECESSOR),
        Arguments.of(0b10L, GIndexUtil.LEFTMOST_G_INDEX, NodeRelation.PREDECESSOR),
        Arguments.of(0b11L, GIndexUtil.LEFTMOST_G_INDEX, NodeRelation.RIGHT),
        Arguments.of(0b1L, Long.MAX_VALUE, NodeRelation.PREDECESSOR),
        Arguments.of(0b10L, Long.MAX_VALUE, NodeRelation.LEFT),
        Arguments.of(0b11L, Long.MAX_VALUE, NodeRelation.PREDECESSOR),
        Arguments.of(GIndexUtil.LEFTMOST_G_INDEX, GIndexUtil.SELF_G_INDEX, NodeRelation.SUCCESSOR),
        Arguments.of(
            GIndexUtil.LEFTMOST_G_INDEX, GIndexUtil.LEFTMOST_G_INDEX + 1, NodeRelation.LEFT),
        Arguments.of(INVALID_G_INDEX, 0b1L, null),
        Arguments.of(0b1L, INVALID_G_INDEX, null));
  }

  @ParameterizedTest
  @MethodSource("compareCases")
  void testCompare(final long idx1, final long idx2, final NodeRelation expected) {
    if (expected != null) {
      assertThat(GIndexUtil.gIdxCompare(idx1, idx2)).isEqualTo(expected);
      assertThat(GIndexUtil.gIdxCompare(idx2, idx1)).isEqualTo(expected.inverse());
    } else {
      assertThatThrownBy(() -> GIndexUtil.gIdxCompare(idx1, idx2));
    }
  }

  @Test
  void testIsSelf() {
    assertThat(GIndexUtil.gIdxIsSelf(GIndexUtil.SELF_G_INDEX)).isTrue();
    assertThat(GIndexUtil.gIdxIsSelf(0b10)).isFalse();
    assertThat(GIndexUtil.gIdxIsSelf(GIndexUtil.LEFTMOST_G_INDEX)).isFalse();
    assertThat(GIndexUtil.gIdxIsSelf(GIndexUtil.RIGHTMOST_G_INDEX)).isFalse();
  }

  @Test
  void testGetDepth() {
    assertThat(GIndexUtil.gIdxGetDepth(GIndexUtil.SELF_G_INDEX)).isEqualTo(0);
    assertThat(GIndexUtil.gIdxGetDepth(0b10)).isEqualTo(1);
    assertThat(GIndexUtil.gIdxGetDepth(0b11)).isEqualTo(1);
    assertThat(GIndexUtil.gIdxGetDepth(0b100)).isEqualTo(2);
    assertThat(GIndexUtil.gIdxGetDepth(0b101)).isEqualTo(2);
    assertThat(GIndexUtil.gIdxGetDepth(0b111)).isEqualTo(2);
    assertThat(GIndexUtil.gIdxGetDepth(GIndexUtil.LEFTMOST_G_INDEX)).isEqualTo(63);
    assertThat(GIndexUtil.gIdxGetDepth(GIndexUtil.RIGHTMOST_G_INDEX)).isEqualTo(63);
    assertThat(GIndexUtil.gIdxGetDepth(GIndexUtil.gIdxLeftmostFrom(0b10010))).isEqualTo(63);
    assertThatThrownBy(() -> GIndexUtil.gIdxGetDepth(INVALID_G_INDEX));
  }

  @Test
  void testConstants() {
    assertThat(GIndexUtil.SELF_G_INDEX).isEqualTo(1L);
    assertThat(GIndexUtil.LEFTMOST_G_INDEX).isEqualTo(Long.MIN_VALUE);
    assertThat(GIndexUtil.RIGHTMOST_G_INDEX).isEqualTo(-1L);
  }

  @Test
  void testLeftRightGIndex() {
    assertThat(GIndexUtil.gIdxLeftGIndex(GIndexUtil.SELF_G_INDEX)).isEqualTo(0b10L);
    assertThat(GIndexUtil.gIdxRightGIndex(GIndexUtil.SELF_G_INDEX)).isEqualTo(0b11L);
    assertThat(GIndexUtil.gIdxLeftGIndex(GIndexUtil.gIdxGetParent(GIndexUtil.LEFTMOST_G_INDEX)))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxRightGIndex(GIndexUtil.gIdxGetParent(GIndexUtil.RIGHTMOST_G_INDEX)))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxLeftGIndex(0b10L)).isEqualTo(0b100L);
    assertThat(GIndexUtil.gIdxRightGIndex(0b10L)).isEqualTo(0b101L);
    assertThat(GIndexUtil.gIdxLeftGIndex(0b11L)).isEqualTo(0b110L);
    assertThat(GIndexUtil.gIdxRightGIndex(0b11L)).isEqualTo(0b111L);
    assertThatThrownBy(() -> GIndexUtil.gIdxLeftGIndex(INVALID_G_INDEX));
    assertThatThrownBy(() -> GIndexUtil.gIdxLeftGIndex(GIndexUtil.LEFTMOST_G_INDEX));
    assertThatThrownBy(() -> GIndexUtil.gIdxLeftGIndex(GIndexUtil.RIGHTMOST_G_INDEX));
  }

  @Test
  void testChildGIndex() {
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 0, 0))
        .isEqualTo(GIndexUtil.SELF_G_INDEX);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.LEFTMOST_G_INDEX, 0, 0))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.RIGHTMOST_G_INDEX, 0, 0))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 0, 1)).isEqualTo(0b10L);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 1, 1)).isEqualTo(0b11L);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 0, 2)).isEqualTo(0b100L);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 1, 2)).isEqualTo(0b101L);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 2, 2)).isEqualTo(0b110L);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 3, 2)).isEqualTo(0b111L);
    assertThat(GIndexUtil.gIdxChildGIndex(0b100L, 0, 2)).isEqualTo(0b10000L);
    assertThat(GIndexUtil.gIdxChildGIndex(0b100L, 1, 2)).isEqualTo(0b10001L);
    assertThat(GIndexUtil.gIdxChildGIndex(0b100L, 2, 2)).isEqualTo(0b10010L);
    assertThat(GIndexUtil.gIdxChildGIndex(0b100L, 3, 2)).isEqualTo(0b10011L);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 0, 31))
        .isEqualTo(0b10000000_00000000_00000000_00000000L);
    assertThat(GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 1, 31))
        .isEqualTo(0b10000000_00000000_00000000_00000001L);

    assertThatThrownBy(() -> GIndexUtil.gIdxChildGIndex(INVALID_G_INDEX, 0, 2));
    assertThatThrownBy(() -> GIndexUtil.gIdxChildGIndex(GIndexUtil.SELF_G_INDEX, 4, 2));
    assertThatThrownBy(() -> GIndexUtil.gIdxChildGIndex(GIndexUtil.LEFTMOST_G_INDEX, 0, 1));
    assertThatThrownBy(() -> GIndexUtil.gIdxChildGIndex(GIndexUtil.RIGHTMOST_G_INDEX, 0, 1));
    assertThatThrownBy(
        () ->
            GIndexUtil.gIdxChildGIndex(
                GIndexUtil.gIdxGetParent(GIndexUtil.LEFTMOST_G_INDEX), 0, 2));
  }

  @Test
  void testCombine() {
    assertThat(GIndexUtil.gIdxCompose(GIndexUtil.SELF_G_INDEX, GIndexUtil.SELF_G_INDEX))
        .isEqualTo(GIndexUtil.SELF_G_INDEX);
    assertThat(GIndexUtil.gIdxCompose(GIndexUtil.SELF_G_INDEX, 0b10101)).isEqualTo(0b10101);
    assertThat(GIndexUtil.gIdxCompose(0b10101, GIndexUtil.SELF_G_INDEX)).isEqualTo(0b10101);
    assertThat(GIndexUtil.gIdxCompose(0b10, 0b11)).isEqualTo(0b101);
    assertThat(GIndexUtil.gIdxCompose(0b11, 0b11)).isEqualTo(0b111);
    assertThat(GIndexUtil.gIdxCompose(0b10, 0b10)).isEqualTo(0b100);

    assertThatThrownBy(() -> GIndexUtil.gIdxCompose(GIndexUtil.LEFTMOST_G_INDEX, 0b11));
    assertThatThrownBy(() -> GIndexUtil.gIdxCompose(GIndexUtil.RIGHTMOST_G_INDEX, 0b11));
    assertThatThrownBy(() -> GIndexUtil.gIdxCompose(0xFFFFFFFF, 0x01FFFFFFFFL));
  }

  @Test
  void testLeftRightmost() {
    assertThat(GIndexUtil.gIdxLeftmostFrom(GIndexUtil.SELF_G_INDEX))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxRightmostFrom(GIndexUtil.SELF_G_INDEX))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxLeftmostFrom(0b10)).isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxRightmostFrom(0b11)).isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxLeftmostFrom(0b11)).isEqualTo(0b11L << 62);
    assertThat(GIndexUtil.gIdxRightmostFrom(0b10))
        .isEqualTo(0b1011111111111111_1111111111111111_1111111111111111_1111111111111111L);

    assertThat(GIndexUtil.gIdxLeftmostFrom(GIndexUtil.LEFTMOST_G_INDEX))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxRightmostFrom(GIndexUtil.LEFTMOST_G_INDEX))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxLeftmostFrom(GIndexUtil.RIGHTMOST_G_INDEX))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxRightmostFrom(GIndexUtil.RIGHTMOST_G_INDEX))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);

    assertThatThrownBy(() -> GIndexUtil.gIdxLeftmostFrom(INVALID_G_INDEX));
    assertThatThrownBy(() -> GIndexUtil.gIdxRightmostFrom(INVALID_G_INDEX));
  }

  @Test
  void testGetChildIndex() {
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.SELF_G_INDEX, 0)).isEqualTo(0);
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.LEFTMOST_G_INDEX, 0)).isEqualTo(0);
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 0)).isEqualTo(0);
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.LEFTMOST_G_INDEX, 22)).isEqualTo(0);
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.LEFTMOST_G_INDEX, GIndexUtil.MAX_DEPTH))
        .isEqualTo(0);
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 1)).isEqualTo(1);
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 2)).isEqualTo(3);
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 3)).isEqualTo(7);
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 22))
        .isEqualTo((1 << 22) - 1);
    assertThat(GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, GIndexUtil.MAX_DEPTH))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxGetChildIndex(0b1100, 0)).isEqualTo(0);
    assertThat(GIndexUtil.gIdxGetChildIndex(0b1100, 1)).isEqualTo(1);
    assertThat(GIndexUtil.gIdxGetChildIndex(0b1100, 2)).isEqualTo(2);
    assertThat(GIndexUtil.gIdxGetChildIndex(0b1100, 3)).isEqualTo(4);
    assertThat(GIndexUtil.gIdxGetChildIndex(0b1000, 1)).isEqualTo(0);
    assertThat(GIndexUtil.gIdxGetChildIndex(0b1000, 2)).isEqualTo(0);
    assertThat(GIndexUtil.gIdxGetChildIndex(0b1000, 3)).isEqualTo(0);

    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(INVALID_G_INDEX, 0));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(INVALID_G_INDEX, 1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(GIndexUtil.SELF_G_INDEX, 1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(GIndexUtil.SELF_G_INDEX, 2));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(GIndexUtil.SELF_G_INDEX, -1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetChildIndex(GIndexUtil.RIGHTMOST_G_INDEX, 64));
  }

  @Test
  void testRelativeGIndex() {
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.SELF_G_INDEX, 0))
        .isEqualTo(GIndexUtil.SELF_G_INDEX);
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.LEFTMOST_G_INDEX, 0))
        .isEqualTo(GIndexUtil.LEFTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.RIGHTMOST_G_INDEX, 0))
        .isEqualTo(GIndexUtil.RIGHTMOST_G_INDEX);
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.RIGHTMOST_G_INDEX, 62)).isEqualTo(0b11L);
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b100100111, 3)).isEqualTo(0b100111L);
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b10, 1)).isEqualTo(GIndexUtil.SELF_G_INDEX);
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b1011, 0)).isEqualTo(0b1011);
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b1011, 1)).isEqualTo(0b111);
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b1011, 2)).isEqualTo(0b11);
    assertThat(GIndexUtil.gIdxGetRelativeGIndex(0b1011, 3)).isEqualTo(GIndexUtil.SELF_G_INDEX);

    assertThatThrownBy(() -> GIndexUtil.gIdxGetRelativeGIndex(GIndexUtil.SELF_G_INDEX, 1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetRelativeGIndex(0b10, 2));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetRelativeGIndex(0b10, -1));
    assertThatThrownBy(() -> GIndexUtil.gIdxGetRelativeGIndex(0b10, 64));
  }

  @ParameterizedTest
  @ValueSource(
      ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 50, 51, 52, 53, 54, 55, 100, 101, 102, 103, 104})
  void shouldReverseChildIndex(final long rootGIndex) {
    final int treeDepth = 4;
    final int childIndex = 9;
    final long childGIndex = GIndexUtil.gIdxChildGIndex(rootGIndex, childIndex, treeDepth);

    final int reversedChildIndex = GIndexUtil.gIdxChildIndexFromGIndex(childGIndex, treeDepth);
    assertThat(reversedChildIndex).isEqualTo(childIndex);
  }
}
