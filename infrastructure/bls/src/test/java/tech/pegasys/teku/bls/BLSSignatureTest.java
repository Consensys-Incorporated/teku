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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.impl.BlsException;

abstract class BLSSignatureTest {

  private static final Bytes INFINITY_BYTES =
      Bytes.fromHexString(
          "0x"
              + "c000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000");

  private static BLSSignature infinity() {
    return BLSSignature.fromBytesCompressed(INFINITY_BYTES);
  }

  private static BLSSignature notInG2() {
    // A point on the curve but not in the G2 group
    return BLSSignature.fromBytesCompressed(
        Bytes.fromHexString(
            "0x"
                + "8000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000000"
                + "0000000000000000000000000000000000000000000000000000000000000004"));
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameEmptySignature() {
    BLSSignature signature = BLSSignature.empty();
    assertEquals(signature, signature);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTwoEmptySignatures() {
    BLSSignature signature1 = BLSSignature.empty();
    BLSSignature signature2 = BLSSignature.empty();
    assertEquals(signature1, signature2);
  }

  @Test
  void succeedsIfSerializationOfEmptySignatureIsCorrect() {
    BLSSignature emptySignature = BLSSignature.empty();
    assertEquals(
        "0x0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000"
            + "0000000000000000000000000000000000000000000000000000000000000000",
        emptySignature.toSSZBytes().toHexString());
  }

  @Test
  void succeedsWhenDeserialisationOfEmptySignatureThrowsException() {
    assertThrows(BlsException.class, () -> BLSSignature.empty().getSignature());
  }

  @Test
  void succeedsWhenDeserialisationOfBadDataThrowsException() {
    assertThrows(
        BlsException.class,
        () ->
            BLSSignature.fromBytesCompressed(Bytes.fromHexString("33".repeat(96))).getSignature());
  }

  @Test
  void succeedsIfDeserializationThrowsWithTooFewBytes() {
    Bytes tooFewBytes = Bytes.wrap(new byte[95]);
    assertThrows(
        BlsException.class, () -> BLSSignature.fromBytesCompressed(tooFewBytes).getSignature());
  }

  @Test
  void succeedsIfSSZDeserializationThrowsWithTooFewBytes() {
    Bytes tooFewBytes = Bytes.wrap(new byte[95]);
    assertThrows(BlsException.class, () -> BLSSignature.fromSSZBytes(tooFewBytes));
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForTheSameSignature() {
    BLSSignature signature = BLSTestUtil.randomSignature(42);
    assertEquals(signature, signature);
    assertEquals(signature.hashCode(), signature.hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentSignatures() {
    BLSSignature signature1 = BLSTestUtil.randomSignature(42);
    BLSSignature signature2 = BLSTestUtil.randomSignature(43);
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheSameSignature() {
    BLSSignature signature1 = BLSTestUtil.randomSignature(65);
    BLSSignature signature2 = BLSSignature.fromSSZBytes(signature1.toSSZBytes());
    assertEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenRoundtripSSZReturnsTheEmptySignature() {
    BLSSignature signature1 = BLSSignature.empty();
    BLSSignature signature2 = BLSSignature.fromSSZBytes(signature1.toSSZBytes());
    assertEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenEqualsReturnsTrueForEmptySignatures() {
    assertEquals(BLSSignature.empty(), BLSSignature.empty());
    assertEquals(BLSSignature.empty().hashCode(), BLSSignature.empty().hashCode());
  }

  @Test
  void roundtripEncodeDecodeCompressed() {
    BLSSignature signature = BLSTestUtil.randomSignature(513);
    final BLSSignature result = BLSSignature.fromBytesCompressed(signature.toBytesCompressed());
    assertEquals(signature, result);
    assertEquals(signature.hashCode(), result.hashCode());
  }

  @Test
  void succeedsWhenEqualsReturnsFalseForDifferentInvalidSignatures() {
    final BLSSignature signature1 =
        BLSSignature.fromBytesCompressed(Bytes.fromHexString("11".repeat(96)));
    final BLSSignature signature2 =
        BLSSignature.fromBytesCompressed(Bytes.fromHexString("22".repeat(96)));
    assertNotEquals(signature1, signature2);
  }

  @Test
  void succeedsWhenInfiniteSignatureIsInfinite() {
    assertThat(infinity().isInfinity()).isTrue();
  }

  @Test
  void succeedsWhenInfiniteSignatureIsValid() {
    assertThat(infinity().isValid()).isTrue();
  }

  @Test
  void succeedsWhenNotInG2SignatureIsNotValid() {
    assertThat(notInG2().isValid()).isFalse();
  }

  @Test
  void succeedsWhenJunkSignatureIsNotValid() {
    final BLSSignature junkSignature =
        BLSSignature.fromBytesCompressed(Bytes.fromHexString("11".repeat(96)));
    assertThat(junkSignature.isValid()).isFalse();
  }

  @Test
  void succeedsWhenEmptySignatureIsNotValid() {
    assertThat(BLSSignature.empty().isValid()).isFalse();
  }

  @Test
  void succeedsWhenNonInfiniteSignatureIsNotInfinite() {
    BLSSignature signature = BLSTestUtil.randomSignature(513);
    assertThat(signature.isInfinity()).isFalse();
  }
}
