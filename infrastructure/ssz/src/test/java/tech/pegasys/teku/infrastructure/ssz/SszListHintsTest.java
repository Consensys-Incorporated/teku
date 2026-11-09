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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestByteVectorContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestDoubleSuperContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestSmallContainer;
import tech.pegasys.teku.infrastructure.ssz.TestContainers.TestSubContainer;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.sos.SszReader;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class SszListHintsTest {

  <TElement extends SszData> List<SszList<TElement>> createListVariants(
      final SszListSchema<TElement, ?> type, final SszList<TElement> list0) {
    List<SszList<TElement>> ret = new ArrayList<>();
    ret.add(list0);
    if (!(list0 instanceof SszMutableData)) {
      ret.add(type.createFromBackingNode(list0.getBackingNode()));
      ret.add(type.sszDeserialize(SszReader.fromBytes(list0.sszSerialize())));
    }
    return ret;
  }

  <TElement extends SszData> void assertEmptyListVariants(
      final SszListSchema<TElement, ?> type, final SszList<TElement> list0) {
    createListVariants(type, list0).forEach(l -> assertEmptyList(type, l));
  }

  <TElement extends SszData> void assertEmptyList(
      final SszListSchema<TElement, ?> type, final SszList<TElement> list) {

    if (!(list instanceof SszMutableData)) {
      assertThat(list.hashTreeRoot()).isEqualTo(type.getDefaultTree().hashTreeRoot());
    }

    assertThat(list.isEmpty()).isTrue();
    assertThat(list.size()).isEqualTo(0);
    assertThatThrownBy(() -> list.get(0)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> list.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> list.get((int) type.getMaxLength()))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  <TElement extends SszData> void assertListElementsVariants(
      final SszListSchema<TElement, ?> type,
      final SszList<TElement> list0,
      final List<TElement> expectedElements) {
    createListVariants(type, list0).forEach(l -> assertListElements(type, l, expectedElements));
  }

  <TElement extends SszData> void assertListElements(
      final SszListSchema<TElement, ?> type,
      final SszList<TElement> list,
      final List<TElement> expectedElements) {

    assertThat(list.isEmpty()).isFalse();
    assertThat(list.size()).isEqualTo(expectedElements.size());
    assertThatThrownBy(() -> list.get(list.size())).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> list.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> list.get((int) type.getMaxLength()))
        .isInstanceOf(IndexOutOfBoundsException.class);
    for (int i = 0; i < expectedElements.size(); i++) {
      assertThat(list.get(i)).isEqualTo(expectedElements.get(i));
    }
  }

  <TElement extends SszData> void assertListEqualsVariants(
      final SszListSchema<TElement, ?> type,
      final SszList<TElement> list1,
      final SszList<TElement> list2) {
    List<SszList<TElement>> listVariants1 = createListVariants(type, list1);
    List<SszList<TElement>> listVariants2 = createListVariants(type, list2);

    listVariants1.forEach(
        listVariant1 ->
            listVariants2.forEach(listVariant2 -> assertListEquals(listVariant1, listVariant2)));
  }

  <TElement extends SszData> void assertListEquals(
      final SszList<TElement> list1, final SszList<TElement> list2) {

    assertThat(list1.size()).isEqualTo(list2.size());
    assertThat(list1).isEqualTo(list2);

    for (int i = 0; i < list1.size(); i++) {
      TElement el1 = list1.get(i);
      TElement el2 = list2.get(i);
      assertThat(el1).isEqualTo(el2);
      if (!(el1 instanceof SszMutableData) && (el2 instanceof SszMutableData)) {
        assertThat(el1.hashTreeRoot()).isEqualTo(el2.hashTreeRoot());
      }
    }

    if (!(list1 instanceof SszMutableData) && (list2 instanceof SszMutableData)) {
      assertThat(list1.hashTreeRoot()).isEqualTo(list2.hashTreeRoot());
    }
  }

  static Stream<Arguments> listTypesTestParameters() {
    Random random = new Random(1);
    SszSchema<?> listElementType1 = TestContainer.SSZ_SCHEMA;
    Supplier<TestContainer> elementSupplier1 =
        () -> {
          Bytes32 bytes32 = Bytes32.random();
          TestSubContainer subContainer =
              new TestSubContainer(UInt64.fromLongBits(random.nextLong()), bytes32);
          return new TestContainer(subContainer, UInt64.fromLongBits(random.nextLong()));
        };

    SszSchema<?> listElementType2 = TestSmallContainer.SSZ_SCHEMA;
    Supplier<TestSmallContainer> elementSupplier2 =
        () -> new TestSmallContainer(random.nextBoolean());

    SszSchema<?> listElementType3 = TestByteVectorContainer.SSZ_SCHEMA;
    Supplier<TestByteVectorContainer> elementSupplier3 =
        () -> TestByteVectorContainer.random(random);

    SszSchema<?> listElementType4 = TestDoubleSuperContainer.SSZ_SCHEMA;
    Supplier<TestDoubleSuperContainer> elementSupplier4 =
        () ->
            new TestDoubleSuperContainer(
                random.nextLong(),
                TestByteVectorContainer.random(random),
                random.nextLong(),
                TestByteVectorContainer.random(random),
                random.nextLong());

    return Stream.of(
        Arguments.of(listElementType1, 3, elementSupplier1),
        Arguments.of(listElementType1, 4, elementSupplier1),
        Arguments.of(listElementType1, 5, elementSupplier1),
        Arguments.of(listElementType1, 15, elementSupplier1),
        Arguments.of(listElementType1, 16, elementSupplier1),
        Arguments.of(listElementType1, 17, elementSupplier1),
        Arguments.of(listElementType1, 100, elementSupplier1),
        Arguments.of(listElementType1, 127, elementSupplier1),
        Arguments.of(listElementType1, 128, elementSupplier1),
        Arguments.of(listElementType1, 129, elementSupplier1),
        Arguments.of(listElementType1, 255, elementSupplier1),
        Arguments.of(listElementType1, 256, elementSupplier1),
        Arguments.of(listElementType1, 257, elementSupplier1),
        Arguments.of(listElementType1, Integer.MAX_VALUE, elementSupplier1),
        Arguments.of(listElementType1, 16L * Integer.MAX_VALUE, elementSupplier1),
        Arguments.of(listElementType2, 15, elementSupplier2),
        Arguments.of(listElementType2, 16, elementSupplier2),
        Arguments.of(listElementType2, 17, elementSupplier2),
        Arguments.of(listElementType3, 15, elementSupplier3),
        Arguments.of(listElementType3, 16, elementSupplier3),
        Arguments.of(listElementType3, 17, elementSupplier3),
        Arguments.of(listElementType4, 15, elementSupplier4),
        Arguments.of(listElementType4, 16, elementSupplier4),
        Arguments.of(listElementType4, 17, elementSupplier4));
  }

  static <TElement extends SszData> List<SszListSchema<TElement, ?>> generateTypesWithHints(
      final SszListSchema<TElement, ?> originalType) {
    return Stream.concat(
            Stream.of(originalType),
            IntStream.of(0, 1, 2, 4, 8, 10)
                .filter(i -> (1 << i) < originalType.getMaxLength())
                .mapToObj(SszSchemaHints::sszSuperNode)
                .map(
                    typeHints ->
                        (SszListSchema<TElement, ?>)
                            SszListSchema.create(
                                originalType.getElementSchema(),
                                originalType.getMaxLength(),
                                typeHints)))
        .collect(Collectors.toList());
  }

  @ParameterizedTest
  @MethodSource("listTypesTestParameters")
  <TElement extends SszData> void testIdenticalTypes(
      final SszSchema<TElement> listElementType,
      final long maxListSize,
      final Supplier<TElement> listElementsFactory) {

    List<SszListSchema<TElement, ?>> types =
        generateTypesWithHints(SszListSchema.create(listElementType, maxListSize));

    RewindingSupplier<TElement> rewindingSupplier = new RewindingSupplier<>(listElementsFactory);
    ArrayList<SszList<TElement>> resultsToCompare = new ArrayList<>();
    testList(types.get(0), rewindingSupplier, resultsToCompare::add);

    for (int i = 1; i < types.size(); i++) {
      SszListSchema<TElement, ?> type = types.get(i);
      rewindingSupplier.rewind();
      ArrayDeque<SszList<TElement>> resQueue = new ArrayDeque<>(resultsToCompare);
      testList(
          type,
          rewindingSupplier,
          r -> {
            SszList<TElement> compareToList = resQueue.removeFirst();
            assertListEqualsVariants(type, r, compareToList);
            assertThat(r.sszSerialize()).isEqualTo(compareToList.sszSerialize());
          });
    }
  }

  @SuppressWarnings("JavaCase")
  <TElement extends SszData> void testList(
      final SszListSchema<TElement, ?> type,
      final Supplier<TElement> listElementsFactory,
      final Consumer<SszList<TElement>> results) {

    SszList<TElement> def = type.getDefault();
    assertEmptyListVariants(type, def);
    results.accept(def);

    SszMutableList<TElement> w0 = def.createWritableCopy();
    assertEmptyListVariants(type, w0);

    SszList<TElement> r0 = w0.commitChanges();
    assertEmptyListVariants(type, r0);
    results.accept(r0);

    SszMutableList<TElement> w1 = r0.createWritableCopy();
    assertEmptyListVariants(type, w1);

    TElement elem1 = listElementsFactory.get();
    w1.append(elem1);
    assertListElementsVariants(type, w1, List.of(elem1));

    SszList<TElement> r1 = w1.commitChanges();
    assertListElementsVariants(type, r1, List.of(elem1));
    results.accept(r1);

    SszMutableList<TElement> w2 = r1.createWritableCopy();
    assertListElementsVariants(type, r1, List.of(elem1));

    TElement elem2 = listElementsFactory.get();
    w2.append(elem2);
    assertListElementsVariants(type, w2, List.of(elem1, elem2));

    SszList<TElement> r2 = w2.commitChanges();
    assertListElementsVariants(type, r2, List.of(elem1, elem2));
    results.accept(r2);

    SszMutableList<TElement> w3 = r2.createWritableCopy();
    assertListElementsVariants(type, w3, List.of(elem1, elem2));

    TElement elem3 = listElementsFactory.get();
    w3.set(0, elem3);
    assertListElementsVariants(type, w3, List.of(elem3, elem2));

    SszList<TElement> r3 = w3.commitChanges();
    assertListElementsVariants(type, r3, List.of(elem3, elem2));
    results.accept(r3);

    SszMutableList<TElement> w4 = r2.createWritableCopy();
    w3.set(0, elem1);
    SszList<TElement> r4 = w4.commitChanges();
    assertListElementsVariants(type, r4, List.of(elem1, elem2));
    assertListEqualsVariants(type, r4, r2);
    results.accept(r4);

    IntStream sizes = IntStream.range(2, 18);
    sizes =
        IntStream.concat(
            sizes,
            IntStream.of(63, 64, 65, 127, 128, 129, 255, 256, 511, 512, 513, 1023, 1024, 1025));
    sizes =
        IntStream.concat(
            sizes,
            type.getMaxLength() > (1 << 16)
                ? IntStream.empty()
                : IntStream.of((int) type.getMaxLength() - 1, (int) type.getMaxLength()));

    sizes
        .filter(s -> s <= type.getMaxLength())
        .forEach(
            size -> {
              SszMutableList<TElement> w1_0 = def.createWritableCopy();
              List<TElement> elements = new ArrayList<>();
              for (int i = 0; i < size; i++) {
                TElement el = listElementsFactory.get();
                elements.add(el);
                w1_0.append(el);
              }
              SszList<TElement> r1_0 = w1_0.commitChanges();
              results.accept(r1_0);
              assertListElementsVariants(type, r1_0, elements);

              IntStream changeIndices =
                  IntStream.of(0, 1, 2, 3, 4, 7, 8, size - 1, size - 2).filter(i -> i < size);
              SszMutableList<TElement> w1_1 = r1_0.createWritableCopy();
              changeIndices.forEach(
                  chIdx -> {
                    TElement newElem = listElementsFactory.get();
                    elements.set(chIdx, newElem);
                    w1_1.set(chIdx, newElem);
                  });
              SszList<TElement> r1_1 = w1_1.commitChanges();
              assertListElementsVariants(type, r1_1, elements);
              results.accept(r1_0);
            });

    if (type.getMaxLength() <= (1 << 10)) {
      // check max capacity if the max len is not too huge
      SszMutableList<TElement> w5 = def.createWritableCopy();
      for (long i = 0; i < type.getMaxLength(); i++) {
        w5.append(listElementsFactory.get());
      }
      assertThatThrownBy(() -> w5.append(listElementsFactory.get()))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }
  }

  private static class RewindingSupplier<T> implements Supplier<T> {
    private final Supplier<T> origin;
    private final List<T> memory = new ArrayList<>();
    private int memoryPos = 0;

    public RewindingSupplier(final Supplier<T> origin) {
      this.origin = origin;
    }

    public void rewind() {
      memoryPos = 0;
    }

    @Override
    public T get() {
      T ret;
      if (memoryPos == memory.size()) {
        ret = origin.get();
        memory.add(ret);
        memoryPos++;
      } else {
        ret = memory.get(memoryPos++);
      }
      return ret;
    }
  }
}
