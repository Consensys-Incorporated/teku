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

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Arrays;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Sha256;

class SimpleLeafNode implements LeafNode, TreeNode {

  private final Bytes data;
  private volatile Bytes32 cachedHash;

  public SimpleLeafNode(final Bytes data) {
    checkArgument(data.size() <= MAX_BYTE_SIZE);
    if (data.size() == MAX_BYTE_SIZE) {
      // if data is Bytes32, it will pass throw with no object creation
      // otherwise translate types to Bytes32
      this.cachedHash = Bytes32.wrap(data.copy());
      this.data = cachedHash;
      return;
    }
    // If we store data as is, some Bytes instances (ie ConcatenatedBytes) will
    // perform worse during serialization. So we want to translate them to a
    // single array-backed Bytes
    //
    // in this case, the following seems to perform better than Bytes.wrap(data.copy())
    this.data = Bytes.wrap(data.toArrayUnsafe());
  }

  @Override
  public Bytes getData() {
    return data;
  }

  @Override
  public Bytes32 hashTreeRoot() {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      cachedHash = Bytes32.wrap(Arrays.copyOf(data.toArrayUnsafe(), MAX_BYTE_SIZE));
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  @Override
  public Bytes32 hashTreeRoot(final Sha256 sha256) {
    Bytes32 cachedHash = this.cachedHash;
    if (cachedHash == null) {
      cachedHash = Bytes32.wrap(Arrays.copyOf(data.toArrayUnsafe(), MAX_BYTE_SIZE));
      this.cachedHash = cachedHash;
    }
    return cachedHash;
  }

  @Override
  public TreeNode updated(final TreeUpdates newNodes) {
    if (newNodes.isEmpty()) {
      return this;
    } else {
      newNodes.checkLeaf();
      return newNodes.getNode(0);
    }
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o instanceof final LeafNode otherLeaf) {
      return Objects.equals(getData(), otherLeaf.getData());
    } else {
      return false;
    }
  }

  @Override
  public int hashCode() {
    return getData().hashCode();
  }

  @Override
  public String toString() {
    return "[" + getData() + "]";
  }
}
