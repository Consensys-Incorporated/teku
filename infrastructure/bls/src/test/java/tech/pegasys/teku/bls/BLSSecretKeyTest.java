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

import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

public abstract class BLSSecretKeyTest {

  @ParameterizedTest
  @ValueSource(
      strings = {
        "0x73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000001", // r
        "0x73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000002",
        "0x74eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00010000",
        "0x79eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00010000",
        "0x80eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00010000",
        "0x83eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00010000",
        "0xe7db4ea6533afa906673b0101343b00aa77b4805fffcb7fdfffffffe00000001",
        "0xe7db4ea6533afa906673b0101343b00aa77b4805fffcb7fdfffffffe00000002", // r * 2
        "0xe7db4ea6533afa906673b0101343b00aa77b4805fffcb7fdfffffffe00000003",
        "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff"
      })
  void secretKeyFromBytes_shouldThrowWhenInvalidBytes(final String skString) {
    Bytes32 sk1 = Bytes32.fromHexString(skString);
    Assertions.assertThatThrownBy(() -> BLSSecretKey.fromBytes(sk1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @MethodSource("getSecretKeysToPubKeys")
  void secretKeyFromBytes_shouldYieldCorrectPublicKey(
      final String skString, final String compressedPubKey) {
    BLSSecretKey sk = BLSSecretKey.fromBytes(Bytes32.fromHexString(skString));
    Assertions.assertThat(sk.toPublicKey().toBytesCompressed())
        .isEqualTo(Bytes48.fromHexString(compressedPubKey));
  }

  public static Stream<Arguments> getSecretKeysToPubKeys() {
    Stream.Builder<Arguments> builder = Stream.builder();

    builder.add(
        Arguments.of(
            "0x0000000000000000000000000000000000000000000000000000000000000000",
            "0xc00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));
    builder.add(
        Arguments.of(
            "0x0000000000000000000000000000000000000000000000000000000000000001",
            "0x97f1d3a73197d7942695638c4fa9ac0fc3688c4f9774b905a14e3a3f171bac586c55e83ff97a1aeffb3af00adb22c6bb"));
    builder.add(
        Arguments.of(
            "0x72ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
            "0xb5d2c2f45a9d8429e2fc28ffe844601b3d87490682f5dab702ac090fd3d1ec3fe3cc3e5ffb63ca36bc640a2b9f73cc3f"));
    builder.add(
        Arguments.of(
            "0x73eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000000",
            "0xb7f1d3a73197d7942695638c4fa9ac0fc3688c4f9774b905a14e3a3f171bac586c55e83ff97a1aeffb3af00adb22c6bb"));

    return builder.build();
  }
}
