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

package tech.pegasys.teku.spec.signatures.xmss;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.crypto.Hash;

class VrfKeccakTest {

  private final Random random = new Random(42);
  private final VrfKeccak keccak = new VrfKeccak();

  @Test
  void lanes_roundTrip() {
    final Bytes32 value = randomBytes32();

    assertThat(VrfKeccak.fromLanes(VrfKeccak.toLanes(value), 0)).isEqualTo(value);
  }

  @Test
  void hashLeaf_matchesKeccak256OfSeedAndLittleEndianIndex() {
    for (int i = 0; i < 200; i++) {
      final Bytes32 seed = randomBytes32();
      // Includes indices with the top bit set, which must hash as unsigned
      final long index = random.nextLong();
      final long[] out = new long[VrfKeccak.LANES];

      keccak.hashLeaf(VrfKeccak.toLanes(seed), index, out, 0);

      final Bytes littleEndianIndex = Bytes.ofUnsignedLong(index).reverse();
      assertThat(VrfKeccak.fromLanes(out, 0))
          .isEqualTo(Hash.keccak256(Bytes.concatenate(seed, littleEndianIndex)));
    }
  }

  @Test
  void hashPair_matchesKeccak256OfConcatenation() {
    for (int i = 0; i < 200; i++) {
      final Bytes32 left = randomBytes32();
      final Bytes32 right = randomBytes32();
      final long[] nodes = new long[3 * VrfKeccak.LANES];
      System.arraycopy(VrfKeccak.toLanes(left), 0, nodes, 0, VrfKeccak.LANES);
      System.arraycopy(VrfKeccak.toLanes(right), 0, nodes, 2 * VrfKeccak.LANES, VrfKeccak.LANES);

      keccak.hashPair(nodes, 0, 2 * VrfKeccak.LANES, nodes, VrfKeccak.LANES);

      assertThat(VrfKeccak.fromLanes(nodes, VrfKeccak.LANES))
          .isEqualTo(Hash.keccak256(Bytes.concatenate(left, right)));
    }
  }

  @Test
  void hashPair_canWriteOverItsInput() {
    final Bytes32 left = randomBytes32();
    final Bytes32 right = randomBytes32();
    final long[] nodes = new long[2 * VrfKeccak.LANES];
    System.arraycopy(VrfKeccak.toLanes(left), 0, nodes, 0, VrfKeccak.LANES);
    System.arraycopy(VrfKeccak.toLanes(right), 0, nodes, VrfKeccak.LANES, VrfKeccak.LANES);

    keccak.hashPair(nodes, 0, VrfKeccak.LANES, nodes, VrfKeccak.LANES);

    assertThat(VrfKeccak.fromLanes(nodes, VrfKeccak.LANES))
        .isEqualTo(Hash.keccak256(Bytes.concatenate(left, right)));
  }

  private Bytes32 randomBytes32() {
    final byte[] bytes = new byte[Bytes32.SIZE];
    random.nextBytes(bytes);
    return Bytes32.wrap(bytes);
  }
}
