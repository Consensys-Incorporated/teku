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

package tech.pegasys.teku.statetransition.datacolumns.util;

import static java.lang.Integer.max;
import static java.lang.Integer.min;

import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;

public class StringifyUtil {

  public static String columnIndicesToString(
      final Collection<Integer> indices, final int maxColumns) {
    final String lenStr = "(len: " + indices.size() + ") ";
    if (indices.isEmpty()) {
      return lenStr + "[]";
    } else if (indices.size() == maxColumns) {
      return lenStr + "[all]";
    } else if (maxColumns - indices.size() <= 16) {
      final Set<Integer> exceptIndices =
          IntStream.range(0, maxColumns).boxed().collect(Collectors.toSet());
      exceptIndices.removeAll(indices);
      return lenStr + "[all except " + sortAndJoin(exceptIndices) + "]";
    } else {
      final List<IntRange> ranges = reduceToIntRanges(indices);
      if (ranges.size() <= 16) {
        return lenStr
            + "["
            + ranges.stream().map(Objects::toString).collect(Collectors.joining(","))
            + "]";
      } else {
        final BitSet bitSet = new BitSet(maxColumns);
        indices.forEach(bitSet::set);
        return lenStr + "[bitmap: " + Bytes.of(bitSet.toByteArray()) + "]";
      }
    }
  }

  public static String toIntRangeStringWithSize(final Collection<Integer> ints) {
    return "(size: " + ints.size() + ") " + toIntRangeString(ints);
  }

  public static String toIntRangeString(final Collection<Integer> ints) {
    final List<IntRange> ranges = reduceToIntRanges(ints);
    return "[" + ranges.stream().map(Objects::toString).collect(Collectors.joining(",")) + "]";
  }

  private record IntRange(int first, int last) {

    static IntRange of(final int i) {
      return new IntRange(i, i);
    }

    static List<IntRange> union(final List<IntRange> left, final List<IntRange> right) {
      if (left.isEmpty()) {
        return right;
      } else if (right.isEmpty()) {
        return right;
      } else {
        return Stream.of(
                left.stream().limit(left.size() - 1),
                left.getLast().union(right.getFirst()).stream(),
                right.stream().skip(1))
            .flatMap(s -> s)
            .toList();
      }
    }

    boolean isEmpty() {
      return first > last;
    }

    @SuppressWarnings("UnusedMethod")
    boolean isSingle() {
      return first == last;
    }

    int size() {
      return Integer.max(0, last() - first() + 1);
    }

    List<IntRange> union(final IntRange other) {
      if (this.isEmpty()) {
        return List.of(other);
      } else if (other.isEmpty()) {
        return List.of(this);
      } else if (other.first() > this.last() + 1) {
        return List.of(this, other);
      } else if (this.first() > other.last() + 1) {
        return List.of(other, this);
      } else {
        return List.of(
            new IntRange(min(this.first(), other.first()), max(this.last(), other.last())));
      }
    }

    @Override
    public String toString() {
      return switch (size()) {
        case 0 -> "<empty>";
        case 1 -> Integer.toString(first());
        case 2 -> first() + "," + last();
        default -> first() + ".." + last();
      };
    }
  }

  private static List<IntRange> reduceToIntRanges(final Collection<Integer> nums) {
    return nums.stream()
        .sorted()
        .map(i -> List.of(IntRange.of(i)))
        .reduce(IntRange::union)
        .orElse(Collections.emptyList());
  }

  private static String sortAndJoin(final Collection<Integer> nums) {
    return nums.stream().sorted().map(Objects::toString).collect(Collectors.joining(","));
  }
}
