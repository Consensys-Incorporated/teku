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

package tech.pegasys.teku.infrastructure.ssz.schema.collections.impl;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.ssz.tree.LeafNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

class SchemaUtils {

  public static TreeNode createTreeFromBytes(final Bytes bytes, final int treeDepth) {
    return TreeUtil.createTree(
        split(bytes, LeafNode.MAX_BYTE_SIZE).stream().map(LeafNode::create).toList(), treeDepth);
  }

  public static List<Bytes> split(final Bytes bytes, final int chunkSize) {
    List<Bytes> ret = new ArrayList<>();
    int off = 0;
    int size = bytes.size();
    while (off < size) {
      Bytes leafData = bytes.slice(off, Integer.min(chunkSize, size - off));
      ret.add(leafData);
      off += chunkSize;
    }
    return ret;
  }
}
