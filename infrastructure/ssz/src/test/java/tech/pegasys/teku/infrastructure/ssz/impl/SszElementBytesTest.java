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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszBooleanField;
import tech.pegasys.teku.infrastructure.ssz.schema.SszByteVectorField;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchemaHints;
import tech.pegasys.teku.infrastructure.ssz.schema.SszUInt64Field;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.impl.AbstractSszContainerSchema.NamedSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SszElementBytesTest {

  private static final List<NamedSchema<?>> FIELDS =
      List.of(
          NamedSchema.of("first", SszPrimitiveSchemas.UINT64_SCHEMA),
          NamedSchema.of("flag", SszPrimitiveSchemas.BIT_SCHEMA),
          NamedSchema.of("root", SszPrimitiveSchemas.BYTES32_SCHEMA),
          NamedSchema.of("second", SszPrimitiveSchemas.UINT64_SCHEMA));

  private static final SszContainerSchema<SszContainer> SCHEMA =
      SszContainerSchema.create("Sample", FIELDS, SszContainerImpl::new);
  // The same layout and view class, but another schema instance
  private static final SszContainerSchema<SszContainer> OTHER_SCHEMA =
      SszContainerSchema.create("Sample", FIELDS, SszContainerImpl::new);

  private static final SszUInt64Field<SszContainer> FIRST = SCHEMA.uint64Field("first");
  private static final SszBooleanField<SszContainer> FLAG = SCHEMA.booleanField("flag");
  private static final SszByteVectorField<SszContainer> ROOT =
      SCHEMA.byteVectorField("root", Bytes32.SIZE);
  private static final SszUInt64Field<SszContainer> SECOND = SCHEMA.uint64Field("second");

  private static final SszListSchema<SszContainer, ?> LIST_SCHEMA =
      SszListSchema.create(SCHEMA, 1024, SszSchemaHints.sszSuperNode(2));

  private record Fields(long first, boolean flag, Bytes32 root, long second) {}

  private static Fields fields(final int i) {
    // Covers zero, the maximum uint64 (raw bits -1) and both flag values
    final long first = i == 1 ? -1L : i * 1_000_000_007L;
    return new Fields(first, i % 2 == 1, Hash.sha256(Bytes.ofUnsignedInt(i)), Long.MAX_VALUE - i);
  }

  private static SszContainer element(final Fields fields) {
    return SCHEMA.createFromFieldValues(
        List.of(
            SszUInt64.of(UInt64.fromLongBits(fields.first())),
            SszBit.of(fields.flag()),
            SszBytes32.of(fields.root()),
            SszUInt64.of(UInt64.fromLongBits(fields.second()))));
  }

  private static List<Fields> expectedFields(final int size) {
    final List<Fields> result = new ArrayList<>();
    for (int i = 0; i < size; i++) {
      result.add(fields(i));
    }
    return result;
  }

  private static SszList<SszContainer> fromViews(final int size) {
    return LIST_SCHEMA.createFromElements(
        expectedFields(size).stream().map(SszElementBytesTest::element).toList());
  }

  static Stream<Arguments> lists() {
    final SszList<SszContainer> fromViews = fromViews(11);
    final SszList<SszContainer> packed = LIST_SCHEMA.sszDeserialize(fromViews.sszSerialize());
    return Stream.of(
        Arguments.of("from views", fromViews),
        Arguments.of("packed", packed),
        Arguments.of("mutable packed", packed.createWritableCopy()));
  }

  private static List<Fields> readAll(final SszList<SszContainer> list) {
    final List<Fields> result = new ArrayList<>();
    list.forEachElementSsz(
        0,
        (index, element) -> {
          final byte[] root = element.copy(ROOT);
          assertThat(root).hasSize(Bytes32.SIZE);
          assertThat(element.getByte(ROOT, 0)).isEqualTo(root[0]);
          assertThat(element.getByte(ROOT, Bytes32.SIZE - 1)).isEqualTo(root[Bytes32.SIZE - 1]);
          result.add(
              new Fields(
                  element.get(FIRST), element.get(FLAG), Bytes32.wrap(root), element.get(SECOND)));
          return true;
        });
    return result;
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("lists")
  void readsEveryFieldKind(final String name, final SszList<SszContainer> list) {
    assertThat(readAll(list)).isEqualTo(expectedFields(list.size()));
  }

  @Test
  void readsTheChangesOfAMutableList() {
    final SszList<SszContainer> packed = LIST_SCHEMA.sszDeserialize(fromViews(11).sszSerialize());
    final SszMutableList<SszContainer> mutable = packed.createWritableCopy();
    final Fields changed = new Fields(42, true, Bytes32.ZERO, 7);
    mutable.set(5, element(changed));
    mutable.append(element(fields(11)));

    final List<Fields> expected = new ArrayList<>(expectedFields(12));
    expected.set(5, changed);
    assertThat(readAll(mutable)).isEqualTo(expected);
  }

  @Test
  void computesTheOffsetsFromTheSchema() {
    assertThat(List.of(FIRST.offset(), FLAG.offset(), ROOT.offset(), SECOND.offset()))
        .containsExactly(0, 8, 9, 41);
    assertThat(List.of(FIRST.length(), FLAG.length(), ROOT.length(), SECOND.length()))
        .containsExactly(8, 1, 32, 8);
    assertThat(ROOT.schema()).isSameAs(SCHEMA);
    assertThat(ROOT.name()).isEqualTo("root");
  }

  @Test
  void rejectsAnUnknownField() {
    assertThatThrownBy(() -> SCHEMA.uint64Field("third"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Container Sample has no field third");
  }

  @Test
  void rejectsAFieldOfAnotherKind() {
    assertThatThrownBy(() -> SCHEMA.uint64Field("flag"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Field Sample.flag is Bit, expected UInt64");
    assertThatThrownBy(() -> SCHEMA.booleanField("first"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Field Sample.first is UInt64, expected Bit");
    assertThatThrownBy(() -> SCHEMA.byteVectorField("root", 48))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Field Sample.root is Bytes32, expected a byte vector of length 48");
    assertThatThrownBy(() -> SCHEMA.byteVectorField("second", 8))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Field Sample.second is UInt64, expected a byte vector of length 8");
  }

  @Test
  void acceptsAByteVectorSchemaOfTheLength() {
    final SszContainerSchema<SszContainer> schema =
        SszContainerSchema.create(
            "WithVector",
            List.of(
                NamedSchema.of("flag", SszPrimitiveSchemas.BIT_SCHEMA),
                NamedSchema.of("key", SszByteVectorSchema.create(48))),
            SszContainerImpl::new);
    assertThat(schema.byteVectorField("key", 48).offset()).isEqualTo(1);
    assertThatThrownBy(() -> schema.byteVectorField("key", 32))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Field WithVector.key is ");
  }

  @Test
  void rejectsAHandleBuiltWithAWrongOffset() {
    assertThatThrownBy(() -> new SszUInt64Field<SszContainer>(SCHEMA, "second", 40))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Field Sample.second is at offset 41, not 40");
    assertThat(new SszUInt64Field<SszContainer>(SCHEMA, "second", 41)).isEqualTo(SECOND);
  }

  @Test
  void rejectsAVariableSizeContainer() {
    final SszContainerSchema<SszContainer> schema =
        SszContainerSchema.create(
            "Variable",
            List.of(
                NamedSchema.of("first", SszPrimitiveSchemas.UINT64_SCHEMA),
                NamedSchema.of("list", SszListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA, 8))),
            SszContainerImpl::new);
    assertThatThrownBy(() -> schema.uint64Field("first"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Container Variable is not fixed-size");
  }

  @Test
  void rejectsAHandleOfAnotherSchemaInstance() {
    final SszUInt64Field<SszContainer> otherFirst = OTHER_SCHEMA.uint64Field("first");
    assertThatThrownBy(
            () ->
                fromViews(3)
                    .forEachElementSsz(
                        0,
                        (index, element) -> {
                          element.get(otherFirst);
                          return true;
                        }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Field Sample.first was created from another instance of the schema Sample than the"
                + " list's elements");
  }

  static Stream<Arguments> immutableLists() {
    final SszList<SszContainer> fromViews = fromViews(11);
    return Stream.of(
        Arguments.of("from views", fromViews),
        Arguments.of("packed", LIST_SCHEMA.sszDeserialize(fromViews.sszSerialize())));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("immutableLists")
  void readsHighBitUInt64ValuesAsRawBits(final String name, final SszList<SszContainer> list) {
    // FAR_FUTURE_EPOCH (all ones), 2^63 and 2^63 + 5 are negative longs: compared unsigned they
    // order above every value below 2^63
    final List<UInt64> values =
        List.of(
            UInt64.MAX_VALUE,
            UInt64.fromLongBits(Long.MIN_VALUE),
            UInt64.fromLongBits(Long.MIN_VALUE + 5));
    final SszMutableList<SszContainer> mutable = list.createWritableCopy();
    for (int i = 0; i < values.size(); i++) {
      mutable.set(i, element(new Fields(values.get(i).longValue(), false, Bytes32.ZERO, i)));
    }
    for (final SszList<SszContainer> updated : List.of(mutable, mutable.commitChanges())) {
      final List<UInt64> read = new ArrayList<>();
      updated.forEachElementSsz(
          0,
          values.size(),
          (index, element) -> {
            final long first = element.get(FIRST);
            assertThat(first).isNegative();
            assertThat(Long.compareUnsigned(first, Long.MAX_VALUE)).isPositive();
            read.add(UInt64.fromLongBits(first));
            return true;
          });
      assertThat(read).isEqualTo(values);
      assertThat(read.getFirst().longValue()).isEqualTo(-1L);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("lists")
  void invalidatesTheCursorWhenTheWalkEnds(final String name, final SszList<SszContainer> list) {
    final List<SszElementBytes<SszContainer>> retained = new ArrayList<>();
    list.forEachElementSsz(
        0,
        (index, element) -> {
          retained.add(element);
          return true;
        });
    list.forEachElementSsz(
        0,
        (index, element) -> {
          retained.add(element);
          return index < 2;
        });
    assertThat(retained).hasSize(list.size() + 3);
    for (final SszElementBytes<SszContainer> element : retained) {
      assertThatThrownBy(() -> element.get(FIRST)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> element.copy(ROOT)).isInstanceOf(IllegalStateException.class);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("lists")
  void invalidatesTheCursorWhenTheVisitorThrows(
      final String name, final SszList<SszContainer> list) {
    final List<SszElementBytes<SszContainer>> retained = new ArrayList<>();
    assertThatThrownBy(
            () ->
                list.forEachElementSsz(
                    0,
                    (index, element) -> {
                      retained.add(element);
                      if (index == 1) {
                        throw new IllegalArgumentException("visitor failure");
                      }
                      return true;
                    }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("visitor failure");
    assertThat(retained).hasSize(2);
    for (final SszElementBytes<SszContainer> element : retained) {
      assertThatThrownBy(() -> element.get(FIRST)).isInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> element.copy(ROOT)).isInstanceOf(IllegalStateException.class);
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("lists")
  void handsOutIndependentCopiesOfByteVectorFields(
      final String name, final SszList<SszContainer> list) {
    final Bytes serializedBefore = list.sszSerialize();
    final List<byte[]> saved = new ArrayList<>();
    list.forEachElementSsz(
        0,
        (index, element) -> {
          final byte[] copy = element.copy(ROOT);
          saved.add(copy);
          // Writing into the copy must not reach the element nor later reads of it
          copy[0] ^= (byte) 0xFF;
          copy[Bytes32.SIZE - 1] ^= (byte) 0xFF;
          assertThat(element.copy(ROOT)).isEqualTo(fields(index).root().toArrayUnsafe());
          return true;
        });
    assertThat(list.sszSerialize()).isEqualTo(serializedBefore);
    assertThat(readAll(list)).isEqualTo(expectedFields(list.size()));
    // Each saved copy is still the (mutated) array of its own element, untouched by later visits
    for (int i = 0; i < saved.size(); i++) {
      final byte[] expected = fields(i).root().toArray();
      expected[0] ^= (byte) 0xFF;
      expected[Bytes32.SIZE - 1] ^= (byte) 0xFF;
      assertThat(saved.get(i)).isEqualTo(expected);
    }
  }

  @Test
  void rejectsAByteIndexOutsideTheField() {
    assertThatThrownBy(
            () ->
                fromViews(3)
                    .forEachElementSsz(
                        0,
                        (index, element) -> {
                          element.getByte(ROOT, Bytes32.SIZE);
                          return true;
                        }))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }
}
