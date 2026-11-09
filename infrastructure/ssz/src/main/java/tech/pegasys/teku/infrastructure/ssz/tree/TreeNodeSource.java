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

import com.google.common.base.MoreObjects;
import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

public interface TreeNodeSource {
  CompressedBranchInfo loadBranchNode(Bytes32 rootHash, long gIndex);

  Bytes loadLeafNode(Bytes32 rootHash, long gIndex);

  class CompressedBranchInfo {
    private final int depth;
    private final Bytes32[] children;

    public CompressedBranchInfo(final int depth, final Bytes32[] children) {
      this.depth = depth;
      this.children = children;
    }

    public int getDepth() {
      return depth;
    }

    public Bytes32[] getChildren() {
      return children;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final CompressedBranchInfo that = (CompressedBranchInfo) o;
      return depth == that.depth && Arrays.equals(children, that.children);
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(depth);
      result = 31 * result + Arrays.hashCode(children);
      return result;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("depth", depth)
          .add("children", children)
          .toString();
    }
  }
}
