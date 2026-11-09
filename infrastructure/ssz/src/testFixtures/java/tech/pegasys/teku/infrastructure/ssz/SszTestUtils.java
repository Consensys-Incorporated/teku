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

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.BranchNode;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszTestUtils {

  public static IntList getVectorLengths(final SszContainerSchema<?> sszContainerSchema) {
    IntList vectorLengths = new IntArrayList();
    sszContainerSchema.getFieldSchemas().stream()
        .filter(t -> t instanceof SszVectorSchema)
        .map(t -> (SszVectorSchema<?, ?>) t)
        .forEach(t -> vectorLengths.add(t.getLength()));
    return vectorLengths;
  }

  public static SszBitlist not(final SszBitlist bitlist) {
    List<Boolean> notList = bitlist.stream().map(b -> !b.get()).toList();
    int[] notBitIndices = IntStream.range(0, notList.size()).filter(notList::get).toArray();
    return bitlist.getSchema().ofBits(bitlist.size(), notBitIndices);
  }

  /** Dumps the tree to stdout */
  public static String dumpBinaryTree(final TreeNode node) {
    StringBuilder ret = new StringBuilder();
    dumpBinaryTreeRec(node, "", false, s -> ret.append(s).append('\n'));
    return ret.toString();
  }

  private static void dumpBinaryTreeRec(
      final TreeNode node,
      final String prefix,
      final boolean printCommit,
      final Consumer<String> linesConsumer) {
    if (node instanceof LeafNode leafNode) {
      linesConsumer.accept(prefix + leafNode);
    } else {
      BranchNode branchNode = (BranchNode) node;
      String s = "├─┐";
      if (printCommit) {
        s += " " + branchNode;
      }
      if (branchNode.left() instanceof LeafNode) {
        linesConsumer.accept(prefix + "├─" + branchNode.left());
      } else {
        linesConsumer.accept(prefix + s);
        dumpBinaryTreeRec(branchNode.left(), prefix + "│ ", printCommit, linesConsumer);
      }
      if (branchNode.right() instanceof LeafNode) {
        linesConsumer.accept(prefix + "└─" + branchNode.right());
      } else {
        linesConsumer.accept(prefix + "└─┐");
        dumpBinaryTreeRec(branchNode.right(), prefix + "  ", printCommit, linesConsumer);
      }
    }
  }
}
