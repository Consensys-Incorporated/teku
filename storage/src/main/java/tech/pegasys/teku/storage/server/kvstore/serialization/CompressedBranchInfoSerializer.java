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

package tech.pegasys.teku.storage.server.kvstore.serialization;

import static com.google.common.base.Preconditions.checkArgument;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNodeSource.CompressedBranchInfo;

public class CompressedBranchInfoSerializer implements KvStoreSerializer<CompressedBranchInfo> {

  @Override
  public CompressedBranchInfo deserialize(final byte[] rawData) {
    final Bytes data = Bytes.wrap(rawData);
    checkArgument(
        (data.size() - Integer.BYTES) % Bytes32.SIZE == 0,
        "Branch data was an invalid length %s",
        data);
    // Take int from front (depth)
    final int depth = data.getInt(0);
    // Then split remaining into 32 byte chunks (children)
    final Bytes childHashes = data.slice(Integer.BYTES);
    final Bytes32[] children = new Bytes32[childHashes.size() / Bytes32.SIZE];
    for (int i = 0; i < children.length; i++) {
      final Bytes32 child = Bytes32.wrap(childHashes.slice(i * Bytes32.SIZE, Bytes32.SIZE));
      children[i] = child;
    }
    return new CompressedBranchInfo(depth, children);
  }

  @Override
  public byte[] serialize(final CompressedBranchInfo value) {
    final byte[] data = new byte[value.getChildren().length * Bytes32.SIZE + Integer.BYTES];
    System.arraycopy(
        Bytes.ofUnsignedInt(value.getDepth()).toArrayUnsafe(), 0, data, 0, Integer.BYTES);
    Bytes32[] children = value.getChildren();
    for (int i = 0, childrenLength = children.length; i < childrenLength; i++) {
      final Bytes32 child = children[i];
      System.arraycopy(
          child.toArrayUnsafe(), 0, data, i * Bytes32.SIZE + Integer.BYTES, Bytes32.SIZE);
    }
    return data;
  }
}
