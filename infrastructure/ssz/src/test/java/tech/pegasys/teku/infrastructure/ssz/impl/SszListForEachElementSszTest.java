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

package tech.pegasys.teku.infrastructure.ssz.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableRefList;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.InMemoryStoringTreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.schema.SszByteVectorField;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUInt64Field;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SszListForEachElementSszTest {

  private static final SszContainerSchema<SszContainer> ELEMENT_SCHEMA =
      SszContainerSchema.create(
          "FixedElem",
          List.of(
              NamedSchema.of("a", SszPrimitiveSchemas.UINT64_SCHEMA),
              NamedSchema.of("b", SszPrimitiveSchemas.BYTES32_SCHEMA)),
          SszContainerImpl::new);
  private static final SszUInt64Field<SszContainer> A = ELEMENT_SCHEMA.uint64Field("a");
  private static final SszByteVectorField<SszContainer> B =
      ELEMENT_SCHEMA.byteVectorField("b", Bytes32.SIZE);

  private static final SszListSchema<SszContainer, ?> BOUNDED_PLAIN =
      SszListSchema.create(ELEMENT_SCHEMA, 1024);
  private static final SszListSchema<SszContainer, ?> BOUNDED_PACKED =
      SszListSchema.create(ELEMENT_SCHEMA, 1024, SszSchemaHints.sszSuperNode(3));
  // The validator registry's limit: the walk must not visit the zero subtrees past the end
  private static final SszListSchema<SszContainer, ?> BOUNDED_WIDE_PACKED =
      SszListSchema.create(ELEMENT_SCHEMA, 1L << 40, SszSchemaHints.sszSuperNode(3));
  private static final SszListSchema<SszContainer, ?> PROGRESSIVE_PLAIN =
      SszListSchema.createProgressive(ELEMENT_SCHEMA);
  private static final SszListSchema<SszContainer, ?> PROGRESSIVE_PACKED =
      SszListSchema.createProgressive(ELEMENT_SCHEMA, SszSchemaHints.sszSuperNode(4));

  private static SszContainer element(final int i) {
    return element(i, i);
  }

  private static SszContainer element(final long a, final int seed) {
    return ELEMENT_SCHEMA.createFromBackingNode(
        ELEMENT_SCHEMA.createTreeFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(a)),
                SszBytes32.of(Hash.sha256(Bytes.ofUnsignedInt(seed))))));
  }

  private static List<SszContainer> elements(final int from, final int count) {
    final List<SszContainer> result = new ArrayList<>(count);
    for (int i = from; i < from + count; i++) {
      result.add(element(i));
    }
    return result;
  }

  private static SszList<SszContainer> plain(
      final SszListSchema<SszContainer, ?> schema, final int size) {
    return schema.createFromElements(elements(0, size));
  }

  // SSZ deserialization is the path that packs supernodes
  private static SszList<SszContainer> packed(
      final SszListSchema<SszContainer, ?> schema, final int size) {
    return schema.sszDeserialize(plain(schema, size).sszSerialize());
  }

  // Sizes around the depth-3 supernode boundaries and the progressive level boundaries
  static Stream<Arguments> lists() {
    final List<Arguments> arguments = new ArrayList<>();
    for (final int size : List.of(0, 1, 7, 8, 9, 21, 341)) {
      for (final SszListSchema<SszContainer, ?> schema :
          List.of(BOUNDED_PLAIN, PROGRESSIVE_PLAIN)) {
        arguments.add(Arguments.of("plain " + schema + " " + size, plain(schema, size)));
      }
      for (final SszListSchema<SszContainer, ?> schema :
          List.of(BOUNDED_PACKED, BOUNDED_WIDE_PACKED, PROGRESSIVE_PACKED)) {
        arguments.add(Arguments.of("packed " + schema + " " + size, packed(schema, size)));
      }
    }
    return arguments.stream();
  }

  static Stream<Arguments> listsToStop() {
    return Stream.of(
        Arguments.of("plain", plain(BOUNDED_PLAIN, 21)),
        Arguments.of("packed", packed(PROGRESSIVE_PACKED, 21)));
  }

  /** The element's serialized form, rebuilt from its fields read through the handles. */
  private static Bytes read(final SszElementBytes<SszContainer> element) {
    return Bytes.concatenate(
        Bytes.ofUnsignedLong(element.get(A), ByteOrder.LITTLE_ENDIAN), Bytes.wrap(element.copy(B)));
  }

  private static List<Bytes> collect(final SszList<SszContainer> list, final int fromIndex) {
    final List<Bytes> result = new ArrayList<>();
    final boolean completed =
        list.forEachElementSsz(
            fromIndex,
            (index, bytes) -> {
              assertThat(index).isEqualTo(fromIndex + result.size());
              result.add(read(bytes));
              return true;
            });
    assertThat(completed).isTrue();
    return result;
  }

  private static List<Bytes> collect(
      final SszList<SszContainer> list, final int fromIndex, final int toIndex) {
    final List<Bytes> result = new ArrayList<>();
    final boolean completed =
        list.forEachElementSsz(
            fromIndex,
            toIndex,
            (index, bytes) -> {
              assertThat(index).isEqualTo(fromIndex + result.size());
              result.add(read(bytes));
              return true;
            });
    assertThat(completed).isTrue();
    return result;
  }

  private static List<Bytes> serialized(final List<SszContainer> elements) {
    return elements.stream().map(SszContainer::sszSerialize).toList();
  }

  private static List<Bytes> expected(final SszList<SszContainer> list, final int fromIndex) {
    return serialized(list.asList().subList(Math.min(fromIndex, list.size()), list.size()));
  }

  private static List<Bytes> expected(
      final SszList<SszContainer> list, final int fromIndex, final int toIndex) {
    final int end = Math.min(toIndex, list.size());
    return fromIndex < end ? serialized(list.asList().subList(fromIndex, end)) : List.of();
  }

  // {from, to} ranges around the depth-3 supernode boundaries: inside one supernode, up to and
  // across a boundary, empty, past the end of the list and with to <= from
  private static List<int[]> ranges(final int size) {
    final List<int[]> ranges = new ArrayList<>();
    for (final int[] range :
        List.of(
            new int[] {0, 0},
            new int[] {0, 1},
            new int[] {1, 3},
            new int[] {2, 8},
            new int[] {8, 16},
            new int[] {7, 9},
            new int[] {5, 13},
            new int[] {3, 2},
            new int[] {5, 5},
            new int[] {0, -1},
            new int[] {0, size},
            new int[] {size - 2, size + 5},
            new int[] {size, size + 3},
            new int[] {size + 1, size + 2},
            new int[] {0, Integer.MAX_VALUE})) {
      if (range[0] >= 0) {
        ranges.add(range);
      }
    }
    return ranges;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("lists")
  void readsTheBoundedRange(final String name, final SszList<SszContainer> list) {
    for (final int[] range : ranges(list.size())) {
      assertThat(collect(list, range[0], range[1]))
          .describedAs("range [%s, %s)", range[0], range[1])
          .isEqualTo(expected(list, range[0], range[1]));
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("lists")
  void readsTheBoundedRangeOfAMutableList(final String name, final SszList<SszContainer> list) {
    final SszMutableList<SszContainer> mutable = list.createWritableCopy();
    if (list.size() > 3) {
      mutable.set(3, element(1003));
    }
    if (list.size() > 8) {
      mutable.set(8, element(1008));
    }
    if (list.size() > 0) {
      mutable.set(list.size() - 1, element(1004));
    }
    mutable.append(element(1005));
    mutable.append(element(1006));

    final SszList<SszContainer> committed = mutable.commitChanges();
    for (final int[] range : ranges(mutable.size())) {
      assertThat(collect(mutable, range[0], range[1]))
          .describedAs("range [%s, %s)", range[0], range[1])
          .isEqualTo(expected(committed, range[0], range[1]));
    }
  }

  static Stream<Arguments> packedLists() {
    final List<Arguments> arguments = new ArrayList<>();
    for (final int size : List.of(7, 8, 21)) {
      for (final SszListSchema<SszContainer, ?> schema :
          List.of(BOUNDED_PACKED, BOUNDED_WIDE_PACKED, PROGRESSIVE_PACKED)) {
        arguments.add(Arguments.of(schema + " " + size, packed(schema, size)));
      }
    }
    return arguments.stream();
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("packedLists")
  void readsTheCommittedChangesOfAPackedList(final String name, final SszList<SszContainer> list) {
    final int size = list.size();
    final SszMutableList<SszContainer> mutable = list.createWritableCopy();
    mutable.set(3, element(1003));
    mutable.set(size - 1, element(1004));
    mutable.append(element(1005));
    mutable.append(element(1006));
    final SszList<SszContainer> committed = mutable.commitChanges();

    final List<SszContainer> expectedElements = new ArrayList<>(elements(0, size));
    expectedElements.set(3, element(1003));
    expectedElements.set(size - 1, element(1004));
    expectedElements.add(element(1005));
    expectedElements.add(element(1006));
    final List<Bytes> expectedBytes = serialized(expectedElements);

    assertThat(collect(committed, 0)).isEqualTo(expectedBytes);
    assertThat(collect(committed.createWritableCopy(), 0)).isEqualTo(expectedBytes);
    for (final int[] range : ranges(committed.size())) {
      final int end = Math.min(range[1], expectedBytes.size());
      assertThat(collect(committed, range[0], range[1]))
          .describedAs("range [%s, %s)", range[0], range[1])
          .isEqualTo(range[0] < end ? expectedBytes.subList(range[0], end) : List.of());
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("listsToStop")
  void letsTheVisitorUpdateTheElementItVisitsByReferenceAndStop(
      final String name, final SszList<SszContainer> list) {
    @SuppressWarnings("unchecked")
    final SszMutableRefList<SszContainer, SszMutableContainer> mutable =
        (SszMutableRefList<SszContainer, SszMutableContainer>) list.createWritableCopy();

    final List<Bytes> visited = new ArrayList<>();
    final boolean completed =
        mutable.forEachElementSsz(
            0,
            (index, bytes) -> {
              visited.add(read(bytes));
              mutable.getByRef(index).set(0, SszUInt64.of(UInt64.valueOf(2000 + index)));
              return index < 4;
            });

    assertThat(completed).isFalse();
    assertThat(visited).isEqualTo(serialized(elements(0, 5)));
    final List<SszContainer> expectedElements = new ArrayList<>(elements(0, list.size()));
    for (int i = 0; i < 5; i++) {
      expectedElements.set(i, element(2000 + i, i));
    }
    assertThat(collect(mutable, 0)).isEqualTo(serialized(expectedElements));
    assertThat(collect(mutable.commitChanges(), 0)).isEqualTo(serialized(expectedElements));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("lists")
  void readsEveryElementInOrder(final String name, final SszList<SszContainer> list) {
    for (final int from : List.of(0, 1, 5, list.size() / 2, list.size())) {
      if (from <= list.size()) {
        assertThat(collect(list, from)).isEqualTo(expected(list, from));
      }
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("lists")
  void readsMutableListThroughItsChanges(final String name, final SszList<SszContainer> list) {
    final SszMutableList<SszContainer> mutable = list.createWritableCopy();
    if (list.size() > 3) {
      mutable.set(3, element(1003));
    }
    if (list.size() > 0) {
      mutable.set(list.size() - 1, element(1004));
    }
    mutable.append(element(1005));
    mutable.append(element(1006));

    final SszList<SszContainer> committed = mutable.commitChanges();
    for (final int from : List.of(0, 3, list.size(), list.size() + 1)) {
      assertThat(collect(mutable, from)).isEqualTo(expected(committed, from));
    }
  }

  @Test
  void letsTheVisitorSetTheElementItVisits() {
    final SszMutableList<SszContainer> mutable = packed(BOUNDED_PACKED, 21).createWritableCopy();
    mutable.set(4, element(1004));
    final List<Bytes> expectedVisited = new ArrayList<>(serialized(elements(0, 21)));
    expectedVisited.set(4, element(1004).sszSerialize());

    final List<Bytes> visited = new ArrayList<>();
    mutable.forEachElementSsz(
        0,
        (index, bytes) -> {
          visited.add(read(bytes));
          mutable.set(index, element(2000 + index));
          return true;
        });

    assertThat(visited).isEqualTo(expectedVisited);
    assertThat(expected(mutable.commitChanges(), 0)).isEqualTo(serialized(elements(2000, 21)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("listsToStop")
  void stopsWhenTheVisitorSaysSo(final String name, final SszList<SszContainer> list) {
    final List<Integer> visited = new ArrayList<>();
    final boolean completed =
        list.forEachElementSsz(
            0,
            (index, bytes) -> {
              visited.add(index);
              return index < 2;
            });
    assertThat(completed).isFalse();
    assertThat(visited).containsExactly(0, 1, 2);
  }

  @Test
  void readsAnAllZeroChunkRestoredFromStorage() {
    // Elements 0-7 fill one depth-3 supernode with zeros: loading restores it as an empty one
    final List<SszContainer> elements = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      elements.add(i < 8 ? ELEMENT_SCHEMA.getDefault() : element(i));
    }
    final SszList<SszContainer> list =
        BOUNDED_PACKED.sszDeserialize(BOUNDED_PACKED.createFromElements(elements).sszSerialize());
    final InMemoryStoringTreeNodeStore nodeStore = new InMemoryStoringTreeNodeStore();
    BOUNDED_PACKED.storeBackingNodes(nodeStore, 5, 1, list.getBackingNode());
    final TreeNode loaded = BOUNDED_PACKED.loadBackingNodes(nodeStore, list.hashTreeRoot(), 1);

    assertThat(collect(BOUNDED_PACKED.createFromBackingNode(loaded), 0))
        .isEqualTo(serialized(elements));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("lists")
  void doesNotCreateElementViews(final String name, final SszList<SszContainer> list) {
    final SszList<SszContainer> fresh =
        list.getSchema().createFromBackingNode(list.getBackingNode());
    collect(fresh, 0);
    // No element view was created, so none was cached
    final IntCache<?> viewCache = ((AbstractSszComposite<?>) fresh).transferCache();
    for (int i = 0; i < fresh.size(); i++) {
      assertThat(viewCache.getCachedInt(i)).isNull();
    }
  }
}
