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

package tech.pegasys.teku.spec.datastructures.hashtree.traversal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;

class PreOrderTraversalTreeIterator implements Iterator<Bytes32> {
  private final Deque<Bytes32> remainingNodes = new ArrayDeque<>();
  private final ChildLookup childLookup;

  private PreOrderTraversalTreeIterator(final Bytes32 rootHash, final ChildLookup childLookup) {
    this.childLookup = childLookup;
    remainingNodes.push(rootHash);
  }

  public static Iterator<Bytes32> create(final Bytes32 rootNode, final ChildLookup childLookup) {
    return new PreOrderTraversalTreeIterator(rootNode, childLookup);
  }

  @Override
  public boolean hasNext() {
    return !remainingNodes.isEmpty();
  }

  @Override
  public Bytes32 next() {
    final Bytes32 next = remainingNodes.pop();
    Optional.ofNullable(childLookup.getChildren(next))
        .ifPresent(children -> children.forEach(remainingNodes::push));
    return next;
  }
}
