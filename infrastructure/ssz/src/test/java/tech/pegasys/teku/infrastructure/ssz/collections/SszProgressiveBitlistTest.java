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
import static tech.pegasys.teku.infrastructure.collections.PrimitiveCollectionAssert.assertThatIntCollection;

import java.util.BitSet;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.ssz.SszDataAssert;
import tech.pegasys.teku.infrastructure.ssz.SszTestUtils;
import tech.pegasys.teku.infrastructure.ssz.impl.AbstractSszPrimitive;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveBitlistSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;

public class SszProgressiveBitlistTest implements SszPrimitiveCollectionTestBase {

  static final Random RANDOM = new Random(1);
  static final SszProgressiveBitlistSchema SCHEMA = new SszProgressiveBitlistSchema();

  static SszBitlist random(final SszProgressiveBitlistSchema schema, final int size) {
    return schema.ofBits(
        size, IntStream.range(0, size).filter(__ -> RANDOM.nextBoolean()).toArray());
  }

  @Override
  public Stream<SszBitlist> sszData() {
    return Stream.of(
        SCHEMA.ofBits(0),
        SCHEMA.ofBits(1),
        SCHEMA.ofBits(1, 0),
        random(SCHEMA, 7),
        random(SCHEMA, 8),
        random(SCHEMA, 9),
        random(SCHEMA, 254),
        SCHEMA.ofBits(255),
        SCHEMA.ofBits(255, IntStream.range(0, 255).toArray()),
        random(SCHEMA, 256),
        SCHEMA.ofBits(256),
        SCHEMA.ofBits(256, IntStream.range(0, 256).toArray()),
        random(SCHEMA, 257),
        random(SCHEMA, 512),
        random(SCHEMA, 513),
        random(SCHEMA, 10000));
  }

  public Stream<Arguments> bitlistArgs() {
    return sszData().map(Arguments::of);
  }

  public Stream<Arguments> nonEmptyBitlistArgs() {
    return sszData().filter(b -> !b.isEmpty()).map(Arguments::of);
  }

  public Stream<Arguments> emptyBitlistArgs() {
    return sszData().filter(b -> b.size() == 0).map(Arguments::of);
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
    final Bytes ssz1 = bitlist1.sszSerialize();
    final SszBitlist bitlist2 = bitlist1.getSchema().sszDeserialize(ssz1);

    assertThatIntCollection(bitlist2.getAllSetBits()).isEqualTo(bitlist1.getAllSetBits());
    assertThat(bitlist2.size()).isEqualTo(bitlist1.size());
    for (int i = 0; i < bitlist1.size(); i++) {
      assertThat(bitlist2.getBit(i)).isEqualTo(bitlist1.getBit(i));
      assertThat(bitlist2.get(i)).isEqualTo(bitlist1.get(i));
    }
    assertThat(bitlist2.hashTreeRoot()).isEqualTo(bitlist1.hashTreeRoot());

    final Bytes ssz2 = bitlist2.sszSerialize();
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

    assertThat(bitlist2.hashTreeRoot()).isEqualTo(bitlist1.hashTreeRoot());
    assertThat(bitlist2.sszSerialize()).isEqualTo(bitlist1.sszSerialize());
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testTreeRoundtrip(final SszBitlist bitlist1) {
    final TreeNode tree = bitlist1.getBackingNode();
    final SszBitlist bitlist2 = bitlist1.getSchema().createFromBackingNode(tree);

    assertThatIntCollection(bitlist2.getAllSetBits()).isEqualTo(bitlist1.getAllSetBits());
    assertThat(bitlist2.size()).isEqualTo(bitlist1.size());
    for (int i = 0; i < bitlist1.size(); i++) {
      assertThat(bitlist2.getBit(i)).isEqualTo(bitlist1.getBit(i));
      assertThat(bitlist2.get(i)).isEqualTo(bitlist1.get(i));
    }
    assertThat(bitlist2.hashTreeRoot()).isEqualTo(bitlist1.hashTreeRoot());
    assertThat(bitlist2.sszSerialize()).isEqualTo(bitlist1.sszSerialize());
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void or_testEqualList(final SszBitlist bitlist) {
    final SszBitlist res = bitlist.or(bitlist);
    assertThat(res.sszSerialize()).isEqualTo(bitlist.sszSerialize());
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void testOrWithEmptyBitlist(final SszBitlist bitlist) {
    final SszBitlist empty = SCHEMA.ofBits(0);
    final SszBitlist res = bitlist.or(empty);
    assertThat(res.size()).isEqualTo(bitlist.size());
    for (int i = 0; i < bitlist.size(); i++) {
      assertThat(res.getBit(i)).isEqualTo(bitlist.getBit(i));
    }
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
  @MethodSource("bitlistArgs")
  void intersects_shouldNotIntersectWithEmpty(final SszBitlist bitlist) {
    assertThat(bitlist.intersects(SCHEMA.ofBits(0))).isFalse();
  }

  @ParameterizedTest
  @MethodSource("nonEmptyBitlistArgs")
  void intersects_shouldNotIntersectWithNotSelf(final SszBitlist bitlist) {
    assertThat(bitlist.intersects(SszTestUtils.not(bitlist))).isFalse();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void isSupersetOf_shouldReturnTrueForSelf(final SszBitlist bitlist) {
    assertThat(bitlist.isSuperSetOf(bitlist)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void isSupersetOf_shouldReturnTrueForEmpty(final SszBitlist bitlist) {
    assertThat(bitlist.isSuperSetOf(SCHEMA.ofBits(0))).isTrue();
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void getBitCount_shouldReturnCorrectCount(final SszBitlist bitlist) {
    final long bitCount = bitlist.stream().filter(AbstractSszPrimitive::get).count();
    assertThat(bitlist.getBitCount()).isEqualTo(bitCount);
  }

  @ParameterizedTest
  @MethodSource("bitlistArgs")
  void createWritableCopy_shouldSucceed(final SszBitlist bitlist) {
    assertThat(bitlist.createWritableCopy()).isNotNull();
  }

  @Test
  void testEmptyBitlistSsz() {
    final SszBitlist empty = SCHEMA.ofBits(0);
    assertThat(empty.sszSerialize()).isEqualTo(Bytes.of(1));
  }

  @Test
  void testEmptyHashTreeRoot() {
    assertThat(SCHEMA.ofBits(0).hashTreeRoot())
        .isEqualTo(Hash.sha256(Bytes.concatenate(Bytes32.ZERO, Bytes32.ZERO)));
  }

  @ParameterizedTest
  @MethodSource("fromBytesTestCases")
  void testFromBytes(final SszBitlist bitlist, final Bytes serialized) {
    final SszBitlist deserialized = SCHEMA.fromBytes(serialized);

    assertThat(deserialized.size()).isEqualTo(bitlist.size());
    assertThatIntCollection(deserialized.getAllSetBits()).isEqualTo(bitlist.getAllSetBits());
    assertThat(deserialized.hashTreeRoot()).isEqualTo(bitlist.hashTreeRoot());
  }

  @ParameterizedTest
  @MethodSource("fromHexStringTestCases")
  void testFromHexString(final SszBitlist bitlist, final String hexString) {
    final SszBitlist deserialized = SCHEMA.fromHexString(hexString);

    assertThat(deserialized.size()).isEqualTo(bitlist.size());
    assertThatIntCollection(deserialized.getAllSetBits()).isEqualTo(bitlist.getAllSetBits());
    assertThat(deserialized.hashTreeRoot()).isEqualTo(bitlist.hashTreeRoot());
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
}
