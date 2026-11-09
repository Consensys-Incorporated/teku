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

package tech.pegasys.teku.infrastructure.ssz.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.collections.PrimitiveCollectionAssert.assertThatIntCollection;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.SszVectorTestBase;
import tech.pegasys.teku.infrastructure.ssz.collections.impl.SszBitvectorImpl;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitvectorSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszBitvectorTest implements SszPrimitiveCollectionTestBase, SszVectorTestBase {

  private static final Random RANDOM = new Random(1);

  private static SszBitvector random(final SszBitvectorSchema<?> schema) {
    return schema.ofBits(
        IntStream.range(0, schema.getLength()).filter(__ -> RANDOM.nextBoolean()).toArray());
  }

  @Override
  public Stream<SszBitvector> sszData() {
    return Stream.of(
        random(SszBitvectorSchema.create(1)),
        random(SszBitvectorSchema.create(2)),
        random(SszBitvectorSchema.create(7)),
        random(SszBitvectorSchema.create(8)),
        random(SszBitvectorSchema.create(9)),
        random(SszBitvectorSchema.create(15)),
        random(SszBitvectorSchema.create(16)),
        random(SszBitvectorSchema.create(17)),
        random(SszBitvectorSchema.create(255)),
        random(SszBitvectorSchema.create(256)),
        random(SszBitvectorSchema.create(257)),
        random(SszBitvectorSchema.create(511)),
        random(SszBitvectorSchema.create(512)),
        random(SszBitvectorSchema.create(513)),
        random(SszBitvectorSchema.create(1023)),
        random(SszBitvectorSchema.create(1024)),
        random(SszBitvectorSchema.create(1025)));
  }

  public Stream<Arguments> bitvectorArgs() {
    return sszData().map(Arguments::of);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testSszRoundtrip(final SszBitvector bitvector1) {
    final Bytes ssz1 = bitvector1.sszSerialize();
    final SszBitvector bitvector2 = bitvector1.getSchema().sszDeserialize(ssz1);

    assertThatIntCollection(bitvector2.getAllSetBits()).isEqualTo(bitvector1.getAllSetBits());
    Assertions.assertThat(bitvector2.size()).isEqualTo(bitvector1.size());
    for (int i = 0; i < bitvector1.size(); i++) {
      assertThat(bitvector2.getBit(i)).isEqualTo(bitvector1.getBit(i));
      Assertions.assertThat(bitvector2.get(i)).isEqualTo(bitvector1.get(i));
    }
    SszDataAssert.assertThatSszData(bitvector2).isEqualByAllMeansTo(bitvector1);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testTreeRoundtrip(final SszBitvector bitvector1) {
    final TreeNode tree = bitvector1.getBackingNode();
    final SszBitvector bitvector2 = bitvector1.getSchema().createFromBackingNode(tree);

    assertThatIntCollection(bitvector2.getAllSetBits()).isEqualTo(bitvector1.getAllSetBits());
    Assertions.assertThat(bitvector2.size()).isEqualTo(bitvector1.size());
    for (int i = 0; i < bitvector1.size(); i++) {
      assertThat(bitvector2.getBit(i)).isEqualTo(bitvector1.getBit(i));
      Assertions.assertThat(bitvector2.get(i)).isEqualTo(bitvector1.get(i));
    }
    SszDataAssert.assertThatSszData(bitvector2).isEqualByAllMeansTo(bitvector1);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void or_testEqualList(final SszBitvector bitvector) {
    final SszBitvector res = bitvector.or(bitvector);
    assertThat(res).isEqualTo(bitvector);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void or_shouldThrowIfBitvectorSizeIsLarger(final SszBitvector bitvector) {
    final SszBitvectorSchema<SszBitvector> largerSchema =
        SszBitvectorSchema.create(bitvector.getSchema().getMaxLength() + 1);
    final SszBitvector largerBitvector =
        largerSchema.ofBits(bitvector.size() - 1, bitvector.size());
    assertThatThrownBy(() -> bitvector.or(largerBitvector))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void or_shouldThrowIfBitvectorSizeIsSmaller(final SszBitvector bitvector) {
    if (bitvector.getSchema().getMaxLength() == 1) {
      return;
    }
    final SszBitvectorSchema<SszBitvector> smallerSchema =
        SszBitvectorSchema.create(bitvector.getSchema().getMaxLength() - 1);
    final SszBitvector smallerBitvector = smallerSchema.ofBits();
    assertThatThrownBy(() -> bitvector.or(smallerBitvector))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void getBitCount_shouldReturnCorrectCount(final SszBitvector bitvector) {
    final long bitCount = bitvector.stream().filter(AbstractSszPrimitive::get).count();
    assertThat(bitvector.getBitCount()).isEqualTo(bitCount);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void createWritableCopy_shouldThrow(final SszBitvector bitvector) {
    assertThatThrownBy(bitvector::createWritableCopy)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void rightShift_shouldYieldAllZeroesWhenShiftingByVectorLength(final SszBitvector bitvector) {
    assertThat(bitvector.rightShift(bitvector.size()).getBitCount()).isZero();
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void rightShift_zeroShiftShouldYieldTheSameVector(final SszBitvector bitvector) {
    SszDataAssert.assertThatSszData(bitvector.rightShift(0)).isEqualByAllMeansTo(bitvector);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void rightShift_test(final SszBitvector vector) {
    final SszBitvectorSchema<?> schema = vector.getSchema();
    IntStream.of(
            1,
            2,
            3,
            4,
            5,
            6,
            7,
            8,
            15,
            16,
            17,
            31,
            32,
            33,
            255,
            256,
            257,
            511,
            512,
            513,
            schema.getLength() - 1,
            schema.getLength(),
            schema.getLength() + 1)
        .forEach(
            i -> {
              final SszBitvector shiftedVector = vector.rightShift(i);
              final SszBitvector vectorExpected =
                  schema.ofBits(
                      vector
                          .streamAllSetBits()
                          .map(b -> b + i)
                          .filter(b -> b < schema.getLength())
                          .toArray());
              SszDataAssert.assertThatSszData(shiftedVector).isEqualByAllMeansTo(vectorExpected);
            });
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testBitMethodsAreConsistent(final SszBitvector vector) {
    assertThat(vector.streamAllSetBits())
        .containsExactlyInAnyOrderElementsOf(vector.getAllSetBits());
    final List<Integer> bitsIndices = vector.getAllSetBits();
    for (int i = 0; i < vector.size(); i++) {
      assertThat(vector.getBit(i)).isEqualTo(bitsIndices.contains(i));
    }
    assertThat(vector.getBitCount()).isEqualTo(bitsIndices.size());
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testOr(final SszBitvector bitvector) {
    final SszBitvector orVector = random(bitvector.getSchema());
    final SszBitvector res = bitvector.or(orVector);
    assertThat(res.size()).isEqualTo(bitvector.size());
    assertThat(res.getSchema()).isEqualTo(bitvector.getSchema());
    for (int i = 0; i < bitvector.size(); i++) {
      assertThat(res.getBit(i)).isEqualTo(bitvector.getBit(i) || orVector.getBit(i));
    }
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testOrWithEmptyBitvector(final SszBitvector bitvector) {
    final SszBitvector empty = bitvector.getSchema().ofBits();
    assertThat(bitvector.or(empty)).isEqualTo(bitvector);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testGetLastSetBitIndex(final SszBitvector bitvector) {
    final int result = bitvector.getLastSetBitIndex();
    final int expected = bitvector.streamAllSetBits().reduce((first, second) -> second).orElse(-1);
    assertThat(result).isEqualTo(expected);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void get_shouldThrowIndexOutOfBounds(final SszBitvector vector) {
    assertThatThrownBy(() -> vector.get(-1)).isInstanceOf(IndexOutOfBoundsException.class);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testFromBytes(final SszBitvector bitvector) {
    final Bytes bytes = bitvector.sszSerialize();
    final long size = bitvector.getSchema().getMaxLength();
    final SszBitvector result =
        SszBitvectorImpl.fromBytes(bitvector.getSchema(), bytes, (int) size);
    SszDataAssert.assertThatSszData(result).isEqualByAllMeansTo(bitvector);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testFromHexString(final SszBitvector bitvector) {
    final String hexString = bitvector.sszSerialize().toHexString();
    final long size = bitvector.getSchema().getMaxLength();
    final SszBitvector result =
        SszBitvectorImpl.fromHexString(bitvector.getSchema(), hexString, (int) size);
    SszDataAssert.assertThatSszData(result).isEqualByAllMeansTo(bitvector);
  }

  @ParameterizedTest
  @MethodSource("bitvectorArgs")
  void testBitSetRoundtrip(final SszBitvector bitvector) {

    final SszBitvectorSchema<?> schema = bitvector.getSchema();

    final SszBitvector newVector = schema.wrapBitSet(bitvector.size(), bitvector.getAsBitSet());

    SszDataAssert.assertThatSszData(newVector).isEqualByAllMeansTo(bitvector);
  }
}
