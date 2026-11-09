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

package tech.pegasys.teku.bls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import org.apache.tuweni.ssz.SSZ;
import org.junit.jupiter.api.Test;

abstract class BLSPublicKeyTest {

  private static final Bytes INFINITY_PUBLIC_KEY_BYTES =
      Bytes.fromHexString(
          "0xc00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");

  private static final BLSPublicKey INFINITY_PUBLIC_KEY =
      BLSPublicKey.fromSSZBytes(INFINITY_PUBLIC_KEY_BYTES);

  @Test
  void fromBytesCompressedValidate_okWhenValidBytes() {
    assertThatCode(
            () ->
                BLSPublicKey.fromBytesCompressedValidate(
                    BLSTestUtil.randomPublicKey(1).toBytesCompressed()))
        .doesNotThrowAnyException();
  }

  @Test
  void fromBytesCompressedValidate_throwsOnInvalidData() {
    BLSPublicKey publicKey = BLSTestUtil.randomPublicKey(1);
    assertThatThrownBy(
            () ->
                BLSPublicKey.fromBytesCompressedValidate(
                    Bytes48.wrap(publicKey.toBytesCompressed().shiftLeft(1))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameEmptyPublicKey() {
    BLSPublicKey publicKey = BLSPublicKey.empty();
    assertEquals(publicKey, publicKey);
  }

  @Test
  void succeedsWhenTwoInfinityPublicKeysAreEqual() {
    BLSPublicKey publicKey1 = new BLSPublicKey(INFINITY_PUBLIC_KEY.getPublicKey());
    BLSPublicKey publicKey2 = new BLSPublicKey(INFINITY_PUBLIC_KEY.getPublicKey());
    assertEquals(publicKey1, publicKey2);
  }

  @Test
  void fromBytesCompressedValidate_throwsOnInvalidPubKey() {
    Bytes48 invalidPublicKeyBytes =
        Bytes48.fromHexString(
            "0x9378a6e3984e96d2cd50450c76ca14732f1300efa04aecdb805b22e6d6926a85ef409e8f3acf494a1481090bf32ce3bd");
    assertThatThrownBy(() -> BLSPublicKey.fromBytesCompressedValidate(invalidPublicKeyBytes))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void succeedsWhenComparingInvalidAndValidPublicKeyFails() {
    BLSPublicKey invalidPublicKey =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0x9378a6e3984e96d2cd50450c76ca14732f1300efa04aecdb805b22e6d6926a85ef409e8f3acf494a1481090bf32ce3bd"));
    BLSPublicKey validPublicKey =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0xb51aa9cdb40ed3e7e5a9b3323550fe323ecd5c7f5cb3d8b47af55a061811bc7da0397986cad0d565c0bdbbe99af24355"));
    assertNotEquals(validPublicKey, invalidPublicKey);
  }

  @Test
  void succeedsWhenInvalidPublicReturnsHashCode() {
    BLSPublicKey invalidPublicKey =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0x9378a6e3984e96d2cd50450c76ca14732f1300efa04aecdb805b22e6d6926a85ef409e8f3acf494a1481090bf32ce3bd"));
    BLSPublicKey validPublicKey =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0xb51aa9cdb40ed3e7e5a9b3323550fe323ecd5c7f5cb3d8b47af55a061811bc7da0397986cad0d565c0bdbbe99af24355"));
    assertNotEquals(invalidPublicKey.hashCode(), validPublicKey.hashCode());
    assertEquals(invalidPublicKey.hashCode(), invalidPublicKey.hashCode());
  }

  @Test
  void succeedsIfSerializationOfEmptyPublicKeyIsCorrect() {
    BLSPublicKey emptyPublicKey = BLSPublicKey.empty();
    assertEquals(
        "0x000000000000000000000000000000000000000000000000"
            + "000000000000000000000000000000000000000000000000",
        emptyPublicKey.toSSZBytes().toHexString());
  }

  @Test
  void succeedsIfDeserializationOfInfinityPublicKeyIsCorrect() {
    byte[] pointBytes = new byte[48];
    pointBytes[0] = (byte) 0xc0;
    Bytes infinityBytesSsz =
        SSZ.encode(
            writer -> {
              writer.writeFixedBytes(Bytes.wrap(pointBytes));
            });
    BLSPublicKey deserializedPublicKey = BLSPublicKey.fromSSZBytes(infinityBytesSsz);
    assertEquals(INFINITY_PUBLIC_KEY, deserializedPublicKey);
  }

  @Test
  void succeedsIfDeserializationThrowsWithTooFewBytes() {
    Bytes tooFewBytes = Bytes.wrap(new byte[51]);
    assertThrows(IllegalArgumentException.class, () -> BLSPublicKey.fromSSZBytes(tooFewBytes));
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSamePublicKey() {
    BLSPublicKey publicKey = BLSTestUtil.randomPublicKey(42);
    assertEquals(publicKey, publicKey);
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentPublicKeys() {
    BLSPublicKey publicKey1 = BLSTestUtil.randomPublicKey(1);
    BLSPublicKey publicKey2 = BLSTestUtil.randomPublicKey(2);
    assertNotEquals(publicKey1, publicKey2);
  }

  @Test
  public void succeedsWhenEqualsReturnsTrueForEquivalentPublicKeysCreatedFromDifferentRawBytes() {
    BLSPublicKey publicKey1 = BLSTestUtil.randomPublicKey(1);
    Bytes compressedBytes = publicKey1.toBytesCompressed();

    BLSPublicKey publicKey2 = BLSPublicKey.fromSSZBytes(compressedBytes);
    BLSPublicKey publicKey3 = BLSPublicKey.fromSSZBytes(compressedBytes);
    assertEquals(publicKey1, publicKey2);
    assertEquals(publicKey2, publicKey3);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheSamePublicKey() {
    BLSPublicKey publicKey1 = BLSTestUtil.randomPublicKey(42);
    BLSPublicKey publicKey2 = BLSPublicKey.fromSSZBytes(publicKey1.toSSZBytes());
    assertEquals(publicKey1, publicKey2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheInfinityPublicKey() {
    BLSPublicKey publicKey1 = INFINITY_PUBLIC_KEY;
    BLSPublicKey publicKey2 = BLSPublicKey.fromSSZBytes(publicKey1.toSSZBytes());
    assertEquals(publicKey1, publicKey2);
  }

  @Test
  void aggregateSamePubKeys() {
    BLSPublicKey pk =
        BLSPublicKey.fromBytesCompressedValidate(
            Bytes48.fromHexString(
                "0x89ece308f9d1f0131765212deca99697b112d61f9be9a5f1f3780a51335b3ff981747a0b2ca2179b96d2c0c9024e5224"));

    BLSPublicKey aggrPk = BLSPublicKey.aggregate(List.of(pk, pk));

    BLSPublicKey aggrPkGolden =
        BLSPublicKey.fromBytesCompressedValidate(
            Bytes48.fromHexString(
                "0xa6e82f6da4520f85c5d27d8f329eccfa05944fd1096b20734c894966d12a9e2a9a9744529d7212d33883113a0cadb909"));
    assertThat(aggrPk).isEqualTo(aggrPkGolden);
  }

  @Test
  void aggregateWithInfinitePubKeyShouldFail() {
    BLSPublicKey pk =
        BLSPublicKey.fromBytesCompressedValidate(
            Bytes48.fromHexString(
                "0x89ece308f9d1f0131765212deca99697b112d61f9be9a5f1f3780a51335b3ff981747a0b2ca2179b96d2c0c9024e5224"));

    BLSPublicKey aggrPk = BLSPublicKey.aggregate(List.of(pk, INFINITY_PUBLIC_KEY));

    assertThat(aggrPk).isEqualTo(INFINITY_PUBLIC_KEY);
  }

  @Test
  void aggregateWithNotInGroupPubKeyShouldFail() {
    // This one is in the G1 group
    BLSPublicKey pk1 =
        BLSPublicKey.fromBytesCompressedValidate(
            Bytes48.fromHexString(
                "0x89ece308f9d1f0131765212deca99697b112d61f9be9a5f1f3780a51335b3ff981747a0b2ca2179b96d2c0c9024e5224"));
    // This one is on the curve but not in the G1 group
    BLSPublicKey pk2 =
        BLSPublicKey.fromBytesCompressed(
            Bytes48.fromHexString(
                "0x800000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000004"));

    BLSPublicKey aggrPk = BLSPublicKey.aggregate(List.of(pk1, pk2));

    assertThat(aggrPk).isEqualTo(INFINITY_PUBLIC_KEY);
  }

  @Test
  public void toAbbreviatedString_shouldShowFirstSevenBytesOfPublicKey() {
    Bytes keyBytes =
        Bytes.fromHexString(
            "0xab10fc693d038b73d67279127501a05f0072cbb7147c68650ef6ac4e0a413e5cabd1f35c8711e1f7d9d885bbc3b8eddc");
    BLSPublicKey blsPublicKey = BLSPublicKey.fromSSZBytes(keyBytes);
    assertThat(blsPublicKey.toAbbreviatedString()).isEqualTo("ab10fc6");
  }
}
