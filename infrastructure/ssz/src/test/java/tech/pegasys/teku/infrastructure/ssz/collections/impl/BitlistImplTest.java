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

package tech.pegasys.teku.infrastructure.ssz.collections.impl;

import static com.google.common.base.Preconditions.checkArgument;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.pegasys.teku.infrastructure.collections.PrimitiveCollectionAssert.assertThatIntCollection;

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.MutableBytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class BitlistImplTest {
  private static final int BITLIST_MAX_SIZE = 4000;

  @Test
  void initTest() {
    BitlistImpl bitlist = create();
    assertFalse(bitlist.getBit(0));
    assertFalse(bitlist.getBit(9));
  }

  @Test
  void setTest() {
    BitlistImpl bitlist = create(1, 3, 8);

    assertFalse(bitlist.getBit(0));
    assertTrue(bitlist.getBit(1));
    assertTrue(bitlist.getBit(3));
    assertFalse(bitlist.getBit(4));
    assertTrue(bitlist.getBit(8));
  }

  @Test
  void getAllSetBits() {
    BitlistImpl bitlist = create(0, 1, 3, 8, 9);

    assertThatIntCollection(bitlist.getAllSetBits()).containsExactly(0, 1, 3, 8, 9);
  }

  @Test
  void getAllSetBits_noSetBits() {
    BitlistImpl bitlist = create();

    assertThatIntCollection(bitlist.getAllSetBits()).isEmpty();
  }

  @Test
  void intersects_noOverlap() {
    BitlistImpl bitlist1 = create(1, 3, 5);
    BitlistImpl bitlist2 = create(0, 2, 4);

    assertThat(bitlist1.intersects(bitlist2)).isFalse();
  }

  @Test
  void intersects_withOverlap() {
    BitlistImpl bitlist1 = create(1, 3, 5);
    BitlistImpl bitlist2 = create(0, 3, 4);

    assertThat(bitlist1.intersects(bitlist2)).isTrue();
  }

  @Test
  void isSuperSetOf_sameBitsSet() {
    BitlistImpl bitlist1 = create(1, 3, 5);
    BitlistImpl bitlist2 = create(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isTrue();
  }

  @Test
  void isSuperSetOf_additionalBitsSet() {
    BitlistImpl bitlist1 = create(1, 3, 5, 7, 9);
    BitlistImpl bitlist2 = create(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isTrue();
  }

  @Test
  void isSuperSetOf_notAllBitsSet() {
    BitlistImpl bitlist1 = create(1, 3);
    BitlistImpl bitlist2 = create(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isFalse();
  }

  @Test
  void isSuperSetOf_differentBitsSet() {
    BitlistImpl bitlist1 = create(2, 5, 6);
    BitlistImpl bitlist2 = create(1, 3, 5);
    assertThat(bitlist1.isSuperSetOf(bitlist2)).isFalse();
  }

  @Test
  void countSetBits() {
    assertThat(create(1, 2, 6, 7, 9).getBitCount()).isEqualTo(5);
  }

  @Test
  void serializationTest() {
    BitlistImpl bitlist = createBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    Assertions.assertEquals(bitlistSerialized.toHexString(), "0x721806");
  }

  @Test
  void deserializationTest() {
    BitlistImpl bitlist = createBitlist();

    Bytes bitlistSerialized = bitlist.serialize();
    BitlistImpl newBitlist = BitlistImpl.fromSszBytes(bitlistSerialized, BITLIST_MAX_SIZE);
    Assertions.assertEquals(bitlist, newBitlist);
  }

  @Test
  void serializationTest2() {
    BitlistImpl bitlist = new BitlistImpl(9, BITLIST_MAX_SIZE, 0, 3, 4, 5, 6, 7, 8);

    Bytes bitlistSerialized = bitlist.serialize();
    Assertions.assertEquals(Bytes.fromHexString("0xf903"), bitlistSerialized);
  }

  @Test
  void deserializationTest2() {
    BitlistImpl bitlist = new BitlistImpl(9, BITLIST_MAX_SIZE, 0, 3, 4, 5, 6, 7, 8);

    BitlistImpl newBitlist =
        BitlistImpl.fromSszBytes(Bytes.fromHexString("0xf903"), BITLIST_MAX_SIZE);
    Assertions.assertEquals(bitlist, newBitlist);
  }

  @Test
  void deserializationShouldRejectZeroLengthBytes() {
    assertThatThrownBy(() -> BitlistImpl.fromSszBytes(Bytes.EMPTY, BITLIST_MAX_SIZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("at least one byte");
  }

  @Test
  void deserializationShouldRejectDataWhenEndMarkerBitNotSet() {
    assertThatThrownBy(() -> BitlistImpl.fromSszBytes(Bytes.of(0), BITLIST_MAX_SIZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("marker bit");
  }

  static Stream<Arguments> toByteArrayCases() {
    return Stream.of(
        // empty bitlist
        Arguments.of(0, new int[] {}, new byte[] {}),
        // all zeros - BitSet.toByteArray() omits trailing zero bytes
        Arguments.of(16, new int[] {}, new byte[] {}),
        // single bit set
        Arguments.of(18, new int[] {0}, new byte[] {0x01}),
        // multiple bits in single byte
        Arguments.of(18, new int[] {1, 4, 5}, new byte[] {0x32}),
        // bits across multiple bytes
        Arguments.of(18, new int[] {0, 8}, new byte[] {0x01, 0x01}),
        // typical bitlist
        Arguments.of(18, new int[] {1, 4, 5, 6, 11, 12, 17}, new byte[] {0x72, 0x18, 0x02}));
  }

  @ParameterizedTest
  @MethodSource("toByteArrayCases")
  void toByteArray(final int size, final int[] setBits, final byte[] expectedBytes) {
    final BitlistImpl bitlist = new BitlistImpl(size, BITLIST_MAX_SIZE, setBits);
    assertThat(bitlist.toByteArray()).containsExactly(expectedBytes);
  }

  private static BitlistImpl create(final int... bits) {
    return new BitlistImpl(18, BITLIST_MAX_SIZE, bits);
  }

  private static BitlistImpl createBitlist() {
    return create(1, 4, 5, 6, 11, 12, 17);
  }

  static Stream<Arguments> sszBitlistCases() {
    return Stream.of(
        Arguments.of(Bytes.of(0b1)),
        Arguments.of(Bytes.of(0b11)),
        Arguments.of(Bytes.of(0b10)),
        Arguments.of(Bytes.of(0b101)),
        Arguments.of(Bytes.of(0b110)),
        Arguments.of(Bytes.of(0b111)),
        Arguments.of(Bytes.of(0b100)),
        Arguments.of(Bytes.of(0b1111)),
        Arguments.of(Bytes.of(0b11111)),
        Arguments.of(Bytes.of(0b111111)),
        Arguments.of(Bytes.of(0b1111111)),
        Arguments.of(Bytes.of(0b11111111)),
        Arguments.of(Bytes.of(0b10111111)),
        Arguments.of(Bytes.of(0b10111111, 0b1)),
        Arguments.of(Bytes.of(0b00111111, 0b1)),
        Arguments.of(Bytes.of(0b10111111, 0b11)),
        Arguments.of(Bytes.of(0b10111111, 0b10)),
        Arguments.of(Bytes.of(0b10111111, 0b1111111)),
        Arguments.of(Bytes.of(0b10111111, 0b11111111)),
        Arguments.of(Bytes.of(0b10111111, 0b11111111, 0b1)));
  }

  @ParameterizedTest
  @MethodSource("sszBitlistCases")
  void testSszMethods(final Bytes bitlistSsz) {
    int length = SszBitlistImpl.sszGetLengthAndValidate(bitlistSsz);
    Bytes truncBytes = SszBitlistImpl.sszTruncateLeadingBit(bitlistSsz, length);
    Bytes bitlistSsz1 = sszAppendLeadingBit(truncBytes, length);
    assertThat(bitlistSsz1).isEqualTo(bitlistSsz);

    BitlistImpl bitlist = BitlistImpl.fromSszBytes(bitlistSsz, length);
    Bytes bitlistSsz2 = bitlist.serialize();
    assertThat(bitlistSsz2).isEqualTo(bitlistSsz);
  }

  static Stream<Arguments> sszInvalidBitlistCases() {
    return Stream.of(
        Arguments.of(Bytes.of(0b0)),
        Arguments.of(Bytes.EMPTY),
        Arguments.of(Bytes.of(0b10101010, 0b0)));
  }

  @ParameterizedTest
  @MethodSource("sszInvalidBitlistCases")
  void testSszMethodsInvalid(final Bytes bitlistSsz) {
    assertThatThrownBy(() -> SszBitlistImpl.sszGetLengthAndValidate(bitlistSsz))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> BitlistImpl.fromSszBytes(bitlistSsz, 1024))
        .isInstanceOf(IllegalArgumentException.class);
  }

  public static Bytes sszAppendLeadingBit(final Bytes bytes, final int length) {
    checkArgument(length <= bytes.size() * 8 && length > (bytes.size() - 1) * 8);
    if (length % 8 == 0) {
      return Bytes.wrap(bytes, Bytes.of(1));
    } else {
      int lastByte = 0xFF & bytes.get(bytes.size() - 1);
      int leadingBit = 1 << (length % 8);
      checkArgument((-leadingBit & lastByte) == 0, "Bits higher than length should be 0");
      int lastByteWithLeadingBit = lastByte ^ leadingBit;
      // workaround for Bytes bug. See BitlistViewTest.tuweniBytesIssue() test
      MutableBytes resultBytes = bytes.mutableCopy();
      resultBytes.set(bytes.size() - 1, (byte) lastByteWithLeadingBit);
      return resultBytes;
    }
  }
}
