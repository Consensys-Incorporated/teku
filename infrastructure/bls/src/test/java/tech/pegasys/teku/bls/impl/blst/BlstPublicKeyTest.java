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

package tech.pegasys.teku.bls.impl.blst;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.impl.AbstractPublicKeyTest;
import tech.pegasys.teku.bls.impl.BLS12381;

public class BlstPublicKeyTest extends AbstractPublicKeyTest {

  private static BLS12381 bls;

  @BeforeAll
  static void setup() {
    bls = BlstLoader.INSTANCE.orElseThrow();
  }

  @Override
  protected BLS12381 getBls() {
    return bls;
  }

  // The infinite public key is now considered to be invalid
  @Test
  void infinityPublicKey() {
    Bytes48 infinitePublicKeyBytes =
        Bytes48.fromHexString(
            "0xc00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
    assertThatThrownBy(
            () -> {
              BlstPublicKey publicKey = BlstPublicKey.fromBytes(infinitePublicKeyBytes);
              publicKey.forceValidation();
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void succeedsWhenInvalidPublicKeyIsInvalid() {
    Bytes48 invalidPublicKeyBytes =
        Bytes48.fromHexString(
            "0x9378a6e3984e96d2cd50450c76ca14732f1300efa04aecdb805b22e6d6926a85ef409e8f3acf494a1481090bf32ce3bd");
    assertThatThrownBy(
            () -> {
              BlstPublicKey publicKey = BlstPublicKey.fromBytes(invalidPublicKeyBytes);
              publicKey.forceValidation();
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aggregateOfSingleValidKeyReturnsTheKeyItself() {
    final BlstPublicKey publicKey = (BlstPublicKey) bls.generateKeyPair(42).getPublicKey();

    assertThat(BlstPublicKey.aggregate(List.of(publicKey))).isSameAs(publicKey);
  }

  @Test
  void aggregateOfSingleInfinityKeyIsInfinity() {
    final BlstPublicKey infinity =
        BlstPublicKey.fromBytes(
            Bytes48.fromHexString(
                "0xc00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000"));

    final BlstPublicKey aggregate = BlstPublicKey.aggregate(List.of(infinity));

    assertThat(aggregate.isInfinity()).isTrue();
    assertThat(aggregate.isValid()).isFalse();
  }

  @Test
  void aggregateOfSingleNotInGroupKeyIsInfinity() {
    final BlstPublicKey notInGroup =
        BlstPublicKey.fromBytes(
            Bytes48.fromHexString(
                "0x9378a6e3984e96d2cd50450c76ca14732f1300efa04aecdb805b22e6d6926a85ef409e8f3acf494a1481090bf32ce3bd"));

    final BlstPublicKey aggregate = BlstPublicKey.aggregate(List.of(notInGroup));

    assertThat(aggregate.isInfinity()).isTrue();
    assertThat(aggregate.isValid()).isFalse();
  }

  @Test
  void aggregateOfTwoKeysMatchesAddingThem() {
    final BlstPublicKey key1 = (BlstPublicKey) bls.generateKeyPair(1).getPublicKey();
    final BlstPublicKey key2 = (BlstPublicKey) bls.generateKeyPair(2).getPublicKey();

    final BlstPublicKey aggregate = BlstPublicKey.aggregate(List.of(key1, key2));

    assertThat(aggregate).isNotSameAs(key1).isNotSameAs(key2);
    assertThat(aggregate).isEqualTo(bls.aggregatePublicKeys(List.of(key2, key1)));
  }
}
