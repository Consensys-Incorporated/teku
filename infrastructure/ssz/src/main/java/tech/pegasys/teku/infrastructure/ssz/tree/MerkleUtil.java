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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;

public class MerkleUtil {

  /**
   * Returns the merkle inclusion proof for the field at the given leaf index in the given tree. See
   * https://github.com/ethereum/consensus-specs/blob/master/ssz/merkle-proofs.md#merkle-multiproofs
   * for more info on merkle proofs.
   */
  public static List<Bytes32> constructMerkleProof(
      final TreeNode root, final long leafGeneralizedIndex) {
    if (leafGeneralizedIndex <= 1) {
      // Nothing to prove or invalid index.
      return Collections.emptyList();
    }
    // Path represented by generalized indices.
    List<Long> rootToLeafPath = getPathToNode(leafGeneralizedIndex);

    // The sibling of every node along the path is part of the proof.
    List<Bytes32> proof = new ArrayList<>();

    for (int i = 0; i < rootToLeafPath.size() - 1; i++) {
      long currentNodeIndex = rootToLeafPath.get(i);
      long nextNodeIndex = rootToLeafPath.get(i + 1);

      if (GIndexUtil.gIdxLeftGIndex(currentNodeIndex) == nextNodeIndex) {
        // Next node is left, so the right node is in the proof.
        proof.add(root.get(GIndexUtil.gIdxRightGIndex(currentNodeIndex)).hashTreeRoot());
      } else if (GIndexUtil.gIdxRightGIndex(currentNodeIndex) == nextNodeIndex) {
        // Next node is right, so the left node is in the proof.
        proof.add(root.get(GIndexUtil.gIdxLeftGIndex(currentNodeIndex)).hashTreeRoot());
      } else {
        throw new IllegalArgumentException("Invalid path from root to leaf: " + rootToLeafPath);
      }
    }

    Collections.reverse(proof);
    return Collections.unmodifiableList(proof);
  }

  /**
   * Returns the path from the root to the specified node as an array of generalized indices.
   * path[0] is the root itself (gIndex of 1) path[path.length - 1] is the node (gIndex of
   * `nodeIndex`)
   */
  @VisibleForTesting
  static List<Long> getPathToNode(final long nodeIndex) {
    if (nodeIndex < 1) {
      throw new IllegalArgumentException("Invalid node index: " + nodeIndex);
    }

    final List<Long> path = new ArrayList<>();
    final int depth = GIndexUtil.gIdxGetDepth(nodeIndex);
    long currentIndex = nodeIndex;

    for (int i = 0; i <= depth; ++i) {
      path.add(currentIndex);
      currentIndex = GIndexUtil.gIdxGetParent(currentIndex);
    }
    return Lists.reverse(path);
  }
}
