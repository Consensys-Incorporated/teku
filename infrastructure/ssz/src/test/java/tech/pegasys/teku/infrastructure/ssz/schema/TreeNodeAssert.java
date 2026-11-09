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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.assertj.core.api.AbstractAssert;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafDataNode;
import tech.pegasys.teku.infrastructure.ssz.tree.SszSuperNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.ZeroBranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil.ZeroLeafNode;

public class TreeNodeAssert extends AbstractAssert<TreeNodeAssert, TreeNode> {

  private TreeNodeAssert(final TreeNode treeNode) {
    super(treeNode, TreeNodeAssert.class);
  }

  public static TreeNodeAssert assertThatTreeNode(final TreeNode actual) {
    return new TreeNodeAssert(actual);
  }

  public TreeNodeAssert isTreeEqual(final TreeNode expected) {
    try {
      assertTreeEqual(actual, expected, GIndexUtil.SELF_G_INDEX);
    } catch (final Throwable t) {
      failWithActualExpectedAndMessage(
          printTree(actual), printTree(expected), "Trees did not match: " + t.getMessage(), t);
    }
    return this;
  }

  public static String printTree(final TreeNode node) {
    final StringWriter out = new StringWriter();
    printTree(new PrintWriter(out), node, "", GIndexUtil.SELF_G_INDEX);
    return out.toString();
  }

  private static void printTree(
      final PrintWriter out, final TreeNode node, final String indent, final long gIndex) {
    final String prefix = indent + gIndex + " ";
    if (node instanceof ZeroLeafNode) {
      out.println(
          prefix + node.hashTreeRoot() + ": " + ((LeafDataNode) node).getData() + " (zero)");
    } else if (node instanceof SszSuperNode) {
      out.println(
          prefix + node.hashTreeRoot() + ": " + ((LeafDataNode) node).getData() + " (supernode)");
    } else if (node instanceof LeafDataNode) {
      out.println(prefix + node.hashTreeRoot() + ": " + ((LeafDataNode) node).getData());
    } else if (node instanceof ZeroBranchNode) {
      out.println(prefix + node.hashTreeRoot() + " (" + node + ")");
    } else if (node instanceof BranchNode) {
      out.println(prefix + node.hashTreeRoot());
      printTree(
          out,
          node.get(GIndexUtil.LEFT_CHILD_G_INDEX),
          indent + "  ",
          GIndexUtil.gIdxCompose(gIndex, GIndexUtil.LEFT_CHILD_G_INDEX));
      printTree(
          out,
          node.get(GIndexUtil.RIGHT_CHILD_G_INDEX),
          indent + "  ",
          GIndexUtil.gIdxCompose(gIndex, GIndexUtil.RIGHT_CHILD_G_INDEX));
    } else {
      throw new IllegalArgumentException("Unknown node type: " + node.getClass());
    }
  }

  private void assertTreeEqual(final TreeNode actual, final TreeNode expected, final long gIndex) {
    assertThat(actual.hashTreeRoot())
        .describedAs("node root at gIndex %s", gIndex)
        .isEqualTo(expected.hashTreeRoot());
    if (actual instanceof ZeroBranchNode && expected instanceof ZeroBranchNode) {
      assertThat(actual.hashTreeRoot())
          .describedAs("zero branch node root at gIndex %s", gIndex)
          .isEqualTo(expected.hashTreeRoot());
    } else if (actual instanceof BranchNode actualBranch) {
      final BranchNode expectedBranch = (BranchNode) expected;
      assertTreeEqual(
          actualBranch.left(), expectedBranch.left(), GIndexUtil.gIdxLeftGIndex(gIndex));
      assertTreeEqual(
          actualBranch.right(), expectedBranch.right(), GIndexUtil.gIdxRightGIndex(gIndex));
    } else if (expected instanceof SszSuperNode) {
      assertThat(((LeafDataNode) actual).getData())
          .describedAs("leaf data at gIndex %s", gIndex)
          .isEqualTo(((LeafDataNode) expected).getData());
      assertThat(actual)
          .describedAs("node at gIndex %s should be supernode but wasn't", gIndex)
          .isInstanceOf(SszSuperNode.class);
    } else if (actual instanceof LeafDataNode) {
      assertThat(((LeafDataNode) actual).getData())
          .describedAs("leaf data at gIndex %s", gIndex)
          .isEqualTo(((LeafDataNode) expected).getData());
    } else {
      fail("Unknown type of TreeNode (%s) at gIndex %s", actual.getClass(), gIndex);
    }
  }
}
