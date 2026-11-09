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

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.apache.tuweni.bytes.Bytes32;

public class OrderedTreeStream {
  public static Stream<Bytes32> createPreOrderTraversalStream(
      final Bytes32 rootHash, final ChildLookup childLookup) {
    Iterator<Bytes32> iterator = PreOrderTraversalTreeIterator.create(rootHash, childLookup);
    return iteratorToStream(iterator);
  }

  public static Stream<Bytes32> createBreadthFirstStream(
      final Bytes32 rootHash, final ChildLookup childLookup) {
    Iterator<Bytes32> iterator = BreadthFirstTraversalTreeIterator.create(rootHash, childLookup);
    return iteratorToStream(iterator);
  }

  private static Stream<Bytes32> iteratorToStream(final Iterator<Bytes32> iterator) {
    final Spliterator<Bytes32> split =
        Spliterators.spliteratorUnknownSize(
            iterator,
            Spliterator.IMMUTABLE
                | Spliterator.DISTINCT
                | Spliterator.NONNULL
                | Spliterator.ORDERED
                | Spliterator.SORTED);

    return StreamSupport.stream(split, false);
  }
}
