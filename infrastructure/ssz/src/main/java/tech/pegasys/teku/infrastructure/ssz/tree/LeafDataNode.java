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

import org.apache.tuweni.bytes.Bytes;

/** Represents a tree node which can supply its leaves data */
public interface LeafDataNode extends TreeNode {

  /**
   * Returns the merged leaf data of this node's leaf descendants (see {@link SszSuperNode} for
   * example) or just a leaf data if this node represents a single leaf (see {@link
   * LeafNode#getData()}
   */
  Bytes getData();
}
