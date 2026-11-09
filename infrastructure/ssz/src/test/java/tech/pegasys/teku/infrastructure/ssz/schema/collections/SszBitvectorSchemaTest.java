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

package tech.pegasys.teku.infrastructure.ssz.schema.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.collections.PrimitiveCollectionAssert.assertThatIntCollection;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.SszVector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszVectorSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;

public class SszBitvectorSchemaTest extends SszVectorSchemaTestBase {

  @Override
  public Stream<? extends SszSchema<?>> testSchemas() {
    return Stream.of(
        SszBitvectorSchema.create(1),
        SszBitvectorSchema.create(2),
        SszBitvectorSchema.create(3),
        SszBitvectorSchema.create(7),
        SszBitvectorSchema.create(8),
        SszBitvectorSchema.create(9),
        SszBitvectorSchema.create(254),
        SszBitvectorSchema.create(255),
        SszBitvectorSchema.create(256),
        SszBitvectorSchema.create(511),
        SszBitvectorSchema.create(512),
        SszBitvectorSchema.create(513));
  }

  @Test
  void ofBits_shouldThrowIfLengthNegative() {
    assertThatThrownBy(() -> SszBitvectorSchema.create(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void ofBits_shouldThrowIfBitIndexIsNegative() {
    SszBitvectorSchema<SszBitvector> schema = SszBitvectorSchema.create(100);
    assertThatThrownBy(() -> schema.ofBits(10, -1)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void ofBits_shouldThrowIfBitIndexGreaterThenLength() {
    SszBitvectorSchema<SszBitvector> schema = SszBitvectorSchema.create(100);
    assertThatThrownBy(() -> schema.ofBits(1, 2, 3, 101))
        .isInstanceOf(IndexOutOfBoundsException.class);
  }

  @Test
  void ofBits_test() {
    SszBitvectorSchema<SszBitvector> schema = SszBitvectorSchema.create(100);
    assertThat(schema.ofBits().sszSerialize()).isEqualTo(rightPad(Bytes.EMPTY, 13));
    assertThat(schema.ofBits(0).sszSerialize()).isEqualTo(rightPad(Bytes.of(0b1), 13));
    assertThat(schema.ofBits(1).sszSerialize()).isEqualTo(rightPad(Bytes.of(0b10), 13));
    assertThat(schema.ofBits(0, 1).sszSerialize()).isEqualTo(rightPad(Bytes.of(0b11), 13));
    assertThat(schema.ofBits().size()).isEqualTo(100);
    assertThatIntCollection(schema.ofBits().getAllSetBits()).isEmpty();
    assertThatIntCollection(schema.ofBits(99).getAllSetBits()).containsExactly(99);
    assertThat(schema.ofBits(IntStream.range(0, 100).toArray()).streamAllSetBits().distinct())
        .isSorted()
        .hasSize(100);
    assertThat(schema.ofBits(0, 2)).isEqualTo(schema.ofBits(2, 0));
  }

  private static Bytes rightPad(final Bytes bb, final int targetLen) {
    return Bytes.wrap(bb, Bytes.wrap(new byte[targetLen - bb.size()]));
  }

  @Test
  void sszDeserialize_shouldThrowIfAligningBitsAreNotZero() {
    SszBitvectorSchema<SszBitvector> schema = SszBitvectorSchema.create(10);
    assertThatThrownBy(() -> schema.sszDeserialize(Bytes.of(0xFF, 0b111)))
        .isInstanceOf(SszDeserializeException.class);
    assertThatThrownBy(() -> schema.sszDeserialize(Bytes.of(0xFF, 0b10000011)))
        .isInstanceOf(SszDeserializeException.class);
  }

  @Test
  void sszDeserialize_shouldThrowIfSszLengthDoesntMatch() {
    SszBitvectorSchema<SszBitvector> schema = SszBitvectorSchema.create(10);
    assertThatThrownBy(() -> schema.sszDeserialize(Bytes.EMPTY))
        .isInstanceOf(SszDeserializeException.class);
    assertThatThrownBy(() -> schema.sszDeserialize(Bytes.of(0xFF)))
        .isInstanceOf(SszDeserializeException.class);
    assertThatThrownBy(() -> schema.sszDeserialize(Bytes.of(0xFF, 0b00000011, 0)))
        .isInstanceOf(SszDeserializeException.class);
  }

  @Test
  void create_throwIfZeroLength() {
    assertThatThrownBy(() -> SszBitvectorSchema.create(0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void createFromElements_shouldReturnSszBitvector() {
    SszBitvectorSchema<SszBitvector> schema = SszBitvectorSchema.create(10);
    SszBitvector bitvector = schema.createFromElements(List.of(SszBit.of(false), SszBit.of(true)));
    assertThat(bitvector).isInstanceOf(SszBitvector.class);
  }

  @Test
  void testThatVectorSchemaCreatesBitvectorSchemaImplementation() {
    SszVectorSchema<SszBit, ?> schema = SszVectorSchema.create(SszPrimitiveSchemas.BIT_SCHEMA, 10);
    assertThat(schema).isInstanceOf(SszBitvectorSchema.class);
    assertThat(schema.getMaxLength()).isEqualTo(10);
    SszVector<SszBit> sszList =
        schema.createFromElements(List.of(SszBit.of(false), SszBit.of(true)));
    assertThat(sszList).isInstanceOf(SszBitvector.class);
    SszBitvector sszBitvector = (SszBitvector) sszList;
    assertThat(sszBitvector.getSchema()).isEqualTo(schema);
    assertThat(sszBitvector.size()).isEqualTo(10);
    assertThat(sszBitvector.streamAllSetBits()).containsExactlyElementsOf(IntList.of(1));
  }
}
