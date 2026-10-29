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

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszContainer;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.cache.IntCache;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.InMemoryStoringTreeNodeStore;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
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
  private static final int ELEMENT_SIZE = ELEMENT_SCHEMA.getSszFixedPartSize();

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
    return ELEMENT_SCHEMA.createFromBackingNode(
        ELEMENT_SCHEMA.createTreeFromFieldValues(
            List.of(
                SszUInt64.of(UInt64.valueOf(i)),
                SszBytes32.of(Hash.sha256(Bytes.ofUnsignedInt(i))))));
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

  private static List<Bytes> collect(final SszList<?> list, final int fromIndex) {
    final List<Bytes> result = new ArrayList<>();
    final boolean completed =
        list.forEachElementSsz(
            fromIndex,
            (index, data, offset) -> {
              assertThat(index).isEqualTo(fromIndex + result.size());
              result.add(data.slice(offset, ELEMENT_SIZE).copy());
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
        (index, data, offset) -> {
          visited.add(data.slice(offset, ELEMENT_SIZE).copy());
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
            (index, data, offset) -> {
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
