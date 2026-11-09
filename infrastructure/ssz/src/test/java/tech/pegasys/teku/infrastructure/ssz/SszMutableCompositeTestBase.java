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
import static tech.pegasys.teku.infrastructure.ssz.SszDataTestBase.passWhenEmpty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.assertj.core.api.Assumptions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutablePrimitiveCollection;
import tech.pegasys.teku.infrastructure.ssz.schema.SszCompositeSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64ListSchema;

public interface SszMutableCompositeTestBase extends SszCompositeTestBase {

  SszSchema<?> NON_EXISTING_SCHEMA = SszUInt64ListSchema.create(9283496234L);
  SszData NON_EXISTING_SCHEMA_DATA = NON_EXISTING_SCHEMA.getDefault();
  RandomSszDataGenerator GENERATOR = new RandomSszDataGenerator();

  static SszData getSomeNewChild(final SszCompositeSchema<?> schema) {
    final SszSchema<?> childSchema;
    if (schema instanceof SszListSchema) {
      childSchema = ((SszListSchema<?, ?>) schema).getElementSchema();
    } else {
      childSchema = schema.getChildSchema(0);
    }
    return childSchema.getDefault();
  }

  @SuppressWarnings("unchecked")
  default Stream<SszMutableComposite<SszData>> sszMutableComposites() {
    return sszWritableData()
        .map(SszData::createWritableCopy)
        .map(d -> (SszMutableComposite<SszData>) d);
  }

  default Stream<Arguments> sszMutableCompositeArguments() {
    return passWhenEmpty(sszMutableComposites().map(Arguments::of));
  }

  default Stream<Arguments> sszMutableCompositeWithUpdateIndicesArguments() {
    return passWhenEmpty(
        sszMutableComposites()
            .flatMap(
                data ->
                    Stream.of(
                            IntStream.of(0),
                            IntStream.of(data.size()),
                            IntStream.of(data.size() / 2),
                            IntStream.of(data.size() / 2, data.size() - 1),
                            IntStream.of(0, data.size() - 1),
                            IntStream.of(0, data.size() / 2, data.size() - 1),
                            IntStream.concat(IntStream.range(1, 32), IntStream.of(data.size() - 1)))
                        .map(
                            indices ->
                                indices
                                    .filter(i -> i >= 0 && i < data.size())
                                    .sorted()
                                    .distinct()
                                    .boxed()
                                    .collect(Collectors.toList()))
                        .distinct()
                        .filter(l -> !l.isEmpty())
                        .map(indexList -> Arguments.of(data, indexList))));
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_throwsIndexOutOfBounds(final SszMutableComposite<SszData> data) {
    SszData someData = getSomeNewChild(data.getSchema());
    assertThatThrownBy(() -> data.set(data.size() + 1, someData))
        .isInstanceOf(IndexOutOfBoundsException.class);
    assertThatThrownBy(() -> data.set(-1, someData)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_throwsNPE(final SszMutableComposite<SszData> data) {
    if (data.getSchema().getMaxLength() == 0) {
      return;
    }
    assertThatThrownBy(() -> data.set(0, null)).isInstanceOf(NullPointerException.class);
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldThrowWhenSchemaMismatch(final SszMutableComposite<SszData> data) {
    Assumptions.assumeThat(data).isNotInstanceOf(SszMutablePrimitiveCollection.class);

    for (int i = 0; i < data.size(); i++) {
      assertThatThrownBy(() -> data.set(0, NON_EXISTING_SCHEMA_DATA))
          .isInstanceOf(InvalidValueSchemaException.class);
    }
  }

  @MethodSource("sszMutableCompositeWithUpdateIndicesArguments")
  @ParameterizedTest
  default void set_shouldMatchGet(
      final SszMutableComposite<SszData> data, final List<Integer> updateIndices) {
    SszComposite<SszData> origData = data.commitChanges();

    SszCompositeSchema<? extends SszComposite<?>> schema = data.getSchema();

    List<SszData> newChildrenValues =
        updateIndices.stream()
            .map(idx -> GENERATOR.randomData(schema.getChildSchema(idx)))
            .collect(Collectors.toList());

    for (int i = 0; i < updateIndices.size(); i++) {
      Integer updateIndex = updateIndices.get(i);
      SszData updateValue = newChildrenValues.get(i);
      data.set(updateIndex, updateValue);
    }

    for (int i = 0; i < data.size(); i++) {
      int idx = updateIndices.indexOf(i);
      if (idx < 0) {
        SszDataAssert.assertThatSszData(data.get(i)).isEqualByAllMeansTo(origData.get(i));
      } else {
        SszData updateValue = newChildrenValues.get(idx);
        SszDataAssert.assertThatSszData(data.get(i)).isEqualByAllMeansTo(updateValue);
      }
    }

    SszComposite<SszData> data1 = data.commitChanges();

    for (int i = 0; i < data.size(); i++) {
      int idx = updateIndices.indexOf(i);
      if (idx < 0) {
        SszDataAssert.assertThatSszData(data1.get(i)).isEqualByAllMeansTo(origData.get(i));
      } else {
        SszData updateValue = newChildrenValues.get(idx);
        SszDataAssert.assertThatSszData(data1.get(i)).isEqualByAllMeansTo(updateValue);
      }
    }

    SszComposite<?> data2 = schema.createFromBackingNode(data1.getBackingNode());

    for (int i = 0; i < data.size(); i++) {
      int idx = updateIndices.indexOf(i);
      if (idx < 0) {
        SszDataAssert.assertThatSszData((SszData) data2.get(i))
            .isEqualByAllMeansTo(origData.get(i));
      } else {
        SszData updateValue = newChildrenValues.get(idx);
        SszDataAssert.assertThatSszData((SszData) data2.get(i)).isEqualByAllMeansTo(updateValue);
      }
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldNotHaveSideEffects(final SszMutableComposite<SszData> data) {
    List<Integer> updatedIndices =
        IntStream.concat(IntStream.range(0, 2), IntStream.of(data.size() - 1))
            .distinct()
            .filter(i -> i >= 0 && i < data.size())
            .boxed()
            .toList();

    SszComposite<SszData> origData = data.commitChanges();

    SszCompositeSchema<?> schema = data.getSchema();
    List<SszData> newChildren =
        updatedIndices.stream()
            .map(updatedIndex -> GENERATOR.randomData(schema.getChildSchema(updatedIndex)))
            .collect(Collectors.toList());

    List<SszComposite<SszData>> updatedData = new ArrayList<>();
    for (int i = 0; i < updatedIndices.size(); i++) {
      Integer updateIndex = updatedIndices.get(i);
      SszData oldValue = data.get(updateIndex);
      data.set(updateIndex, newChildren.get(i));
      SszComposite<SszData> data1 = data.commitChanges();
      updatedData.add(data1);
      data.set(updateIndex, oldValue);
    }
    SszComposite<SszData> data1 = data.commitChanges();

    SszDataAssert.assertThatSszData((SszComposite<SszData>) data).isEqualByGettersTo(origData);
    SszDataAssert.assertThatSszData(data1).isEqualByAllMeansTo(origData);

    for (int i = 0; i < updatedIndices.size(); i++) {
      SszComposite<SszData> updated = updatedData.get(i);
      for (int i1 = 0; i1 < updated.size(); i1++) {
        if (i1 != updatedIndices.get(i)) {
          SszDataAssert.assertThatSszData(updated.get(i1)).isEqualByAllMeansTo(origData.get(i1));
        } else {
          SszDataAssert.assertThatSszData(updated.get(i1)).isEqualByAllMeansTo(newChildren.get(i));
        }
      }
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldAppendOnExtendableStructures(final SszMutableComposite<SszData> data) {
    if (data.size() < data.getSchema().getMaxLength()) {
      // the structure could be extended (just a List for now)
      int origSize = data.size();

      SszData appendChild = GENERATOR.randomData(data.getSchema().getChildSchema(origSize));
      data.set(origSize, appendChild);

      assertThat(data.size()).isEqualTo(origSize + 1);
      SszDataAssert.assertThatSszData(data.get(origSize)).isEqualByAllMeansTo(appendChild);

      SszComposite<SszData> data1 = data.commitChanges();
      assertThat(data1.size()).isEqualTo(origSize + 1);
      SszDataAssert.assertThatSszData(data1.get(origSize)).isEqualByAllMeansTo(appendChild);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldThrowWhenSetAboveSizeForExtendableStructures(
      final SszMutableComposite<SszData> data) {
    if (data.size() < data.getSchema().getMaxLength()) {
      // the structure could be extended (just a List case for now)
      assertThatThrownBy(
              () ->
                  data.set(
                      data.size() + 1,
                      data.getSchema().getChildSchema(data.size() + 1).getDefault()))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void set_shouldThrowWhenAppendingAboveMaxLen(final SszMutableComposite<SszData> data) {
    long maxLengthLong = data.getSchema().getMaxLength();
    if (maxLengthLong <= 1024) {
      final int maxLength = (int) maxLengthLong;
      // don't want to waste too much time appending
      for (int i = data.size(); i < maxLength; i++) {
        data.set(i, data.getSchema().getChildSchema(i).getDefault());
      }
      assertThatThrownBy(
              () -> data.set(maxLength, data.getSchema().getChildSchema(maxLength).getDefault()))
          .isInstanceOf(IndexOutOfBoundsException.class);
    }
  }

  @MethodSource("sszMutableCompositeArguments")
  @ParameterizedTest
  default void setInvalidator_shouldBeNotifiedOnSet(final SszMutableComposite<SszData> data) {
    if (data.size() == 0) {
      return;
    }
    AtomicInteger invalidateCount = new AtomicInteger();
    data.setInvalidator(__ -> invalidateCount.incrementAndGet());
    data.set(0, GENERATOR.randomData(data.getSchema().getChildSchema(0)));

    assertThat(invalidateCount).hasValue(1);

    data.update(0, __ -> GENERATOR.randomData(data.getSchema().getChildSchema(0)));

    assertThat(invalidateCount).hasValue(2);
  }
}
