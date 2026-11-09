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

import java.util.BitSet;
import java.util.OptionalInt;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszTestUtils;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeUtil;

public class SszBitlistTest implements SszPrimitiveListTestBase {

  static final Random RANDOM = new Random(1);
  static final SszBitlistSchema<SszBitlist> EMPTY_SCHEMA = SszBitlistSchema.create(0);
  static final SszBitlistSchema<SszBitlist> SCHEMA = SszBitlistSchema.create(500);
  static final SszBitlistSchema<SszBitlist> HUGE_SCHEMA = SszBitlistSchema.create(1L << 62);

  static SszBitlist random(final SszBitlistSchema<?> schema, final int size) {
    return schema.ofBits(
        size, IntStream.range(0, size).filter(__ -> RANDOM.nextBoolean()).toArray());
  }

  @Override
  public Stream<SszBitlist> sszData() {
    return Stream.of(
        EMPTY_SCHEMA.empty(),
        SCHEMA.empty(),
        HUGE_SCHEMA.empty(),
        random(SCHEMA, 1),
        random(HUGE_SCHEMA, 1),
        random(SCHEMA, 2),
        random(HUGE_SCHEMA, 2),
        random(SCHEMA, 254),
        SCHEMA.ofBits(254),
        SCHEMA.ofBits(254, IntStream.range(0, 254).toArray()),
        random(HUGE_SCHEMA, 254),
        random(SCHEMA, 255),
        SCHEMA.ofBits(255),
        SCHEMA.ofBits(255, IntStream.range(0, 255).toArray()),
        random(HUGE_SCHEMA, 255),
        random(SCHEMA, 256),
        SCHEMA.ofBits(256),
        SCHEMA.ofBits(256, IntStream.range(0, 256).toArray()),
        random(HUGE_SCHEMA, 256),
        random(SCHEMA, 257),
        random(HUGE_SCHEMA, 257),
        random(SCHEMA, 499),
        random(SCHEMA, 500),
        random(HUGE_SCHEMA, 511),
        random(HUGE_SCHEMA, 512),
        random(HUGE_SCHEMA, 513),
        random(HUGE_SCHEMA, 10000));
  }

  public Stream<Arguments> bitlistArgs() {
    return sszData().map(Arguments::of);
  }

  public Stream<Arguments> nonEmptyBitlistArgs() {
    return sszData().filter(b -> !b.isEmpty()).map(Arguments::of);
  }

  public Stream<Arguments> emptyBitlistArgs() {
    return sszData().filter(SszCollection::isEmpty).map(Arguments::of);
  }

  public Stream<Arguments> fromBytesTestCases() {
    return sszData().map(bitlist -> Arguments.of(bitlist, bitlist.sszSerialize()));
  }

  public Stream<Arguments> fromHexStringTestCases() {
    return sszData().map(bitlist -> Arguments.of(bitlist, bitlist.sszSerialize().toHexString()));
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testSszRoundtrip(final SszBitlist bitlist1) {
    Bytes ssz1 = bitlist1.sszSerialize();
    SszBitlist bitlist2 = bitlist1.getSchema().sszDeserialize(ssz1);

    assertThatIntCollection(bitlist2.getAllSetBits()).isEqualTo(bitlist1.getAllSetBits());
    assertThat(bitlist2.size()).isEqualTo(bitlist1.size());
    for (int i = 0; i < bitlist1.size(); i++) {
      assertThat(bitlist2.getBit(i)).isEqualTo(bitlist1.getBit(i));
      assertThat(bitlist2.get(i)).isEqualTo(bitlist1.get(i));
    }
    assertThat(bitlist2.hashTreeRoot()).isEqualTo(bitlist1.hashTreeRoot());
    assertThat(bitlist2).isEqualTo(bitlist1);
    assertThat(bitlist2.hashCode()).isEqualTo(bitlist1.hashCode());

    Bytes ssz2 = bitlist2.sszSerialize();
    assertThat(ssz2).isEqualTo(ssz1);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testBitSetRoundtrip(final SszBitlist bitlist1) {
    final SszBitlist bitlist2 =
        bitlist1.getSchema().wrapBitSet(bitlist1.size(), bitlist1.getAsBitSet());

    for (int i = 0; i < bitlist1.size(); i++) {
      assertThat(bitlist2.getBit(i)).isEqualTo(bitlist1.getBit(i));
      assertThat(bitlist2.get(i)).isEqualTo(bitlist1.get(i));
    }

    assertThat(bitlist2).isEqualTo(bitlist1);
    assertThat(bitlist2.hashCode()).isEqualTo(bitlist1.hashCode());
    assertThat(bitlist2.hashTreeRoot()).isEqualTo(bitlist1.hashTreeRoot());
    assertThat(bitlist2.sszSerialize()).isEqualTo(bitlist1.sszSerialize());
  }

  @Test
  void getAsBitSet_withFullStartEnd() {
    final SszBitlist list = random(SCHEMA, 100);

    final BitSet fullSlice = list.getAsBitSet(0, 100);

    final SszBitlist newList = SCHEMA.wrapBitSet(100, fullSlice);

    assertThat(newList.getAsBitSet()).isEqualTo(fullSlice);
    SszDataAssert.assertThatSszData(newList).isEqualByAllMeansTo(list);
  }

  @Test
  void getAsBitSet_withSubsetStartEnd() {
    final SszBitlist list = random(SCHEMA, 100);

    final BitSet slice = list.getAsBitSet(5, 55);

    final SszBitlist newList = SCHEMA.wrapBitSet(50, slice);

    assertThat(newList.getAsBitSet()).isEqualTo(slice);
    IntStream.range(0, 50)
        .forEach(
            i ->
                assertThat(newList.getBit(i))
                    .describedAs("Bit %s should be equal", i)
                    .isEqualTo(list.getBit(i + 5)));
  }

  @Test
  void wrapBitSet_shouldDropBitsIfBitSetIsLarger() {
    final BitSet bitSet = new BitSet(100);
    bitSet.set(99);
    assertThat(bitSet.stream().count()).isEqualTo(1);

    final SszBitlist sszBitlist = SCHEMA.wrapBitSet(10, bitSet);
    final SszBitlist expectedSszBitlist = SCHEMA.ofBits(10);

    assertThat(sszBitlist).isEqualTo(expectedSszBitlist);
    assertThat(sszBitlist.hashCode()).isEqualTo(expectedSszBitlist.hashCode());
    assertThat(sszBitlist.hashTreeRoot()).isEqualTo(expectedSszBitlist.hashTreeRoot());
    assertThat(sszBitlist.sszSerialize()).isEqualTo(expectedSszBitlist.sszSerialize());
  }

  @Test
  void wrapBitSet_shouldThrowIfSizeIsLargerThanSchemaMaxLength() {
    assertThatThrownBy(
            () -> SCHEMA.wrapBitSet(Math.toIntExact(SCHEMA.getMaxLength() + 1), new BitSet()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testTreeRoundtrip(final SszBitlist bitlist1) {
    TreeNode tree = bitlist1.getBackingNode();
    SszBitlist bitlist2 = bitlist1.getSchema().createFromBackingNode(tree);

    assertThatIntCollection(bitlist2.getAllSetBits()).isEqualTo(bitlist1.getAllSetBits());
    assertThat(bitlist2.size()).isEqualTo(bitlist1.size());
    for (int i = 0; i < bitlist1.size(); i++) {
      assertThat(bitlist2.getBit(i)).isEqualTo(bitlist1.getBit(i));
      assertThat(bitlist2.get(i)).isEqualTo(bitlist1.get(i));
    }
    assertThat(bitlist2).isEqualTo(bitlist1);
    assertThat(bitlist2.hashCode()).isEqualTo(bitlist1.hashCode());
    assertThat(bitlist2.hashTreeRoot()).isEqualTo(bitlist1.hashTreeRoot());
    assertThat(bitlist2.sszSerialize()).isEqualTo(bitlist1.sszSerialize());
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void or_testEqualList(final SszBitlist bitlist) {
    SszBitlist res = bitlist.or(bitlist);
    assertThat(res).isEqualTo(bitlist);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void or_shouldThrowIfBitlistSizeIsLarger(final SszBitlist bitlist) {
    SszBitlistSchema<SszBitlist> largerSchema =
        SszBitlistSchema.create(bitlist.getSchema().getMaxLength() + 1);
    SszBitlist largerBitlist = largerSchema.ofBits(bitlist.size() + 1, bitlist.size());
    assertThatThrownBy(() -> bitlist.or(largerBitlist))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void getBitCount_shouldReturnCorrectCount(final SszBitlist bitlist) {
    long bitCount = bitlist.stream().filter(AbstractSszPrimitive::get).count();
    assertThat(bitlist.getBitCount()).isEqualTo(bitCount);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void intersects_shouldNotIntersectWithEmpty(final SszBitlist bitlist) {
    assertThat(bitlist.intersects(bitlist.getSchema().empty())).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldIntersectWithSelf(final SszBitlist bitlist) {
    if (bitlist.getBitCount() == 0) {
      return;
    }
    assertThat(bitlist.intersects(bitlist)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldNotIntersectWithZeroes(final SszBitlist bitlist) {
    assertThat(bitlist.intersects(bitlist.getSchema().ofBits(bitlist.size()))).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldNotIntersectWithNotSelf(final SszBitlist bitlist) {
    assertThat(bitlist.intersects(SszTestUtils.not(bitlist))).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldIntersectWithLargerBitlist(final SszBitlist bitlist) {
    if (bitlist.getBitCount() == 0) {
      return;
    }
    SszBitlist largerBitlist =
        SszBitlistSchema.create(bitlist.size() + 1)
            .ofBits(
                bitlist.size() + 1,
                IntStream.concat(bitlist.streamAllSetBits(), IntStream.of(bitlist.size()))
                    .toArray());
    assertThat(bitlist.intersects(largerBitlist)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldIntersectWithFirstBit(final SszBitlist bitlist) {
    OptionalInt maybeFirstBit = bitlist.streamAllSetBits().findFirst();
    if (maybeFirstBit.isEmpty()) {
      return;
    }
    int i = maybeFirstBit.orElseThrow();
    assertThat(bitlist.intersects(bitlist.getSchema().ofBits(i + 1, i))).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldIntersectWithLastBit(final SszBitlist bitlist) {
    OptionalInt maybeLastBit = bitlist.streamAllSetBits().max();
    if (maybeLastBit.isEmpty()) {
      return;
    }
    int i = maybeLastBit.orElseThrow();
    assertThat(bitlist.intersects(bitlist.getSchema().ofBits(i + 1, i))).isTrue();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void isSupersetOf_shouldReturnTrueForSelf(final SszBitlist bitlist) {
    assertThat(bitlist.isSuperSetOf(bitlist)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void isSupersetOf_shouldReturnTrueForEmpty(final SszBitlist bitlist) {
    assertThat(bitlist.isSuperSetOf(bitlist.getSchema().empty())).isTrue();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void isSupersetOf_shouldReturnFalseForLarger(final SszBitlist bitlist) {
    SszBitlist largerBitlist =
        SszBitlistSchema.create(bitlist.size() + 1).ofBits(bitlist.size() + 1, bitlist.size());
    assertThat(bitlist.isSuperSetOf(largerBitlist)).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void isSupersetOf_shouldReturnFalseForNotSelf(final SszBitlist bitlist) {
    if (bitlist.getBitCount() == bitlist.size()) {
      return;
    }
    assertThat(bitlist.isSuperSetOf(SszTestUtils.not(bitlist))).isFalse();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testOr(final SszBitlist bitlist) {
    IntStream.of(1, 2, bitlist.size() - 1, bitlist.size())
        .filter(i -> i >= 0 && i < bitlist.size())
        .distinct()
        .forEach(
            orSize -> {
              SszBitlist orList = random(bitlist.getSchema(), orSize);
              SszBitlist res = bitlist.or(orList);
              assertThat(res.size()).isEqualTo(bitlist.size());
              assertThat(res.getSchema()).isEqualTo(bitlist.getSchema());
              for (int i = 0; i < bitlist.size(); i++) {
                if (i < orSize) {
                  assertThat(res.getBit(i)).isEqualTo(bitlist.getBit(i) || orList.getBit(i));
                } else {
                  assertThat(res.getBit(i)).isEqualTo(bitlist.getBit(i));
                }
              }
            });
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testOrWithEmptyBitlist(final SszBitlist bitlist) {
    SszBitlist empty = bitlist.getSchema().empty();
    assertThat(bitlist.or(empty)).isEqualTo(bitlist);
  }

  @Test
  void testEmptyHashTreeRoot() {
    assertThat(EMPTY_SCHEMA.empty().hashTreeRoot())
        .isEqualTo(Hash.sha256(Bytes.concatenate(Bytes32.ZERO, Bytes32.ZERO)));
    assertThat(SCHEMA.empty().hashTreeRoot())
        .isEqualTo(
            Hash.sha256(Bytes.concatenate(TreeUtil.ZERO_TREES[1].hashTreeRoot(), Bytes32.ZERO)));
    assertThat(HUGE_SCHEMA.empty().hashTreeRoot())
        .isEqualTo(
            Hash.sha256(
                Bytes.concatenate(TreeUtil.ZERO_TREES[62 - 8].hashTreeRoot(), Bytes32.ZERO)));
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void nullableOr_test(final SszBitlist bitlist) {
    assertThatThrownBy(() -> SszBitlist.nullableOr(null, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(SszBitlist.nullableOr(bitlist, null)).isEqualTo(bitlist);
    assertThat(SszBitlist.nullableOr(null, bitlist)).isEqualTo(bitlist);
    assertThat(SszBitlist.nullableOr(bitlist, SszTestUtils.not(bitlist)).getBitCount())
        .isEqualTo(bitlist.size());
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void createWritableCopy_shouldThrow(final SszBitlist bitlist) {
    assertThatThrownBy(bitlist::createWritableCopy)
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  public void basicTest() {
    for (int size :
        new int[] {
          /*100, 255, 256, */
          300, 1000, 1023
        }) {
      int[] bitIndices =
          IntStream.concat(IntStream.range(0, size).filter(i -> i % 2 == 0), IntStream.of(0))
              .toArray();

      SszBitlistSchema<SszBitlist> schema = SszBitlistSchema.create(size);
      SszList<SszBit> bitlist = schema.ofBits(size, bitIndices);
      SszBitlist bitlist1 = schema.sszDeserialize(bitlist.sszSerialize());

      assertThat(bitlist1).isEqualTo(bitlist);
    }
  }

  @Test
  public void tuweniBytesIssue() {
    Bytes slicedBytes = Bytes.wrap(Bytes.wrap(new byte[32]), Bytes.wrap(new byte[6])).slice(0, 37);

    Assertions.assertThatCode(slicedBytes::copy).doesNotThrowAnyException();

    Bytes wrappedBytes = Bytes.wrap(slicedBytes, Bytes.wrap(new byte[1]));

    Assertions.assertThatCode(wrappedBytes::toArrayUnsafe).doesNotThrowAnyException();
    Assertions.assertThatCode(wrappedBytes::toArray).doesNotThrowAnyException();
    Assertions.assertThatCode(() -> Bytes.concatenate(slicedBytes, Bytes.wrap(new byte[1])))
        .doesNotThrowAnyException();
  }

  @ParameterizedTest
  @MethodSource("emptyBitlistArgs")
  void testBitEmptyListSsz(final SszBitlist bitlist) {

    assertThat(bitlist.sszSerialize()).isEqualTo(Bytes.of(1));

    SszBitlist emptyList1 = bitlist.getSchema().sszDeserialize(Bytes.of(1));
    assertThat(emptyList1).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("fromBytesTestCases")
  void testFromBytes(final SszBitlist bitlist, final Bytes serialized) {
    SszBitlist deserialized = bitlist.getSchema().fromBytes(serialized);

    assertThat(deserialized).isEqualTo(bitlist);
    assertThat(deserialized.size()).isEqualTo(bitlist.size());
    assertThatIntCollection(deserialized.getAllSetBits()).isEqualTo(bitlist.getAllSetBits());
    assertThat(deserialized.hashTreeRoot()).isEqualTo(bitlist.hashTreeRoot());
  }

  @ParameterizedTest
  @MethodSource("fromHexStringTestCases")
  void testFromHexString(final SszBitlist bitlist, final String hexString) {
    SszBitlist deserialized = bitlist.getSchema().fromHexString(hexString);

    assertThat(deserialized).isEqualTo(bitlist);
    assertThat(deserialized.size()).isEqualTo(bitlist.size());
    assertThatIntCollection(deserialized.getAllSetBits()).isEqualTo(bitlist.getAllSetBits());
    assertThat(deserialized.hashTreeRoot()).isEqualTo(bitlist.hashTreeRoot());
  }

  private static final SszBitlistSchema<SszBitlist> FROM_HEX_STRING_TEST_SCHEMA =
      SszBitlistSchema.create(100);

  @Test
  public void fromHexString_shouldHandleMinimalValidHexString() {
    String minimalHex = "0x01";
    SszBitlist validResult = FROM_HEX_STRING_TEST_SCHEMA.fromHexString(minimalHex);
    assertThat(validResult).isNotNull();
    assertThat(validResult.sszSerialize()).isEqualTo(Bytes.fromHexString(minimalHex));
    assertThat(validResult.size()).isZero();
  }

  @Test
  public void fromHexString_shouldHandleComplexValidHexString() {
    String complexHex = "0x01020304";
    SszBitlist complexResult = FROM_HEX_STRING_TEST_SCHEMA.fromHexString(complexHex);
    assertThat(complexResult).isNotNull();
    assertThat(complexResult.sszSerialize()).isEqualTo(Bytes.fromHexString(complexHex));
  }

  @Test
  public void fromHexString_shouldThrowForEmptyString() {
    assertThatThrownBy(() -> FROM_HEX_STRING_TEST_SCHEMA.fromHexString(""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void fromHexString_shouldThrowForOnlyPrefix() {
    assertThatThrownBy(() -> FROM_HEX_STRING_TEST_SCHEMA.fromHexString("0x"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void fromHexString_shouldThrowForInvalidHexString() {
    String invalidHex = "i am a string, not a valid hex string";
    assertThatThrownBy(() -> FROM_HEX_STRING_TEST_SCHEMA.fromHexString(invalidHex))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
