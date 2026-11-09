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

import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

/**
 * Base class of mutable structures over Binary Backing Tree ({@link TreeNode}) Each {@link
 * SszMutableData} subclass class normally inherits from the corresponding immutable class to have
 * both get/set methods however {@link SszMutableData} instance shouldn't be leaked as {@link
 * SszData} instance, the {@link #commitChanges()} should be used instead to get immutable structure
 */
public interface SszMutableData extends SszData {

  /** Resets this ssz structure to its default value */
  void clear();

  /** Creates the corresponding immutable structure with all the changes */
  SszData commitChanges();

  /**
   * Returns the backing tree of this modified structure.
   *
   * <p>Note that calling this method on {@link SszMutableData} could be suboptimal from performance
   * perspective as it internally needs to create an immutable {@link SszData} via {@link
   * #commitChanges()} which is then discarded. It's normally better to make all modifications on
   * {@link SszMutableData}, commit the changes and then call either {@link
   * SszData#getBackingNode()} or {@link SszData#hashTreeRoot()} on the resulting immutable instance
   */
  @Override
  default TreeNode getBackingNode() {
    return commitChanges().getBackingNode();
  }
}
