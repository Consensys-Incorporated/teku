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

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBit;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveBitlistSchema;

class SszMutableProgressiveBitlistTest {

  private static final SszProgressiveBitlistSchema SCHEMA = new SszProgressiveBitlistSchema();

  @Test
  void isWritableSupported_shouldReturnTrue() {
    final SszBitlist bitlist = SCHEMA.ofBits(10, 0, 3, 9);
    assertThat(bitlist.isWritableSupported()).isTrue();
  }

  @Test
  void createWritableCopy_shouldSucceed() {
    final SszBitlist bitlist = SCHEMA.ofBits(10, 0, 3, 9);
    final SszMutablePrimitiveList<Boolean, SszBit> mutable = bitlist.createWritableCopy();
    assertThat(mutable.size()).isEqualTo(10);
  }

  @Test
  void setIndividualBits() {
    final SszBitlist bitlist = SCHEMA.ofBits(10);
    final SszMutablePrimitiveList<Boolean, SszBit> mutable = bitlist.createWritableCopy();

    // Set bits 2 and 7
    mutable.set(2, SszBit.of(true));
    mutable.set(7, SszBit.of(true));

    assertThat(mutable.get(2).get()).isTrue();
    assertThat(mutable.get(7).get()).isTrue();
    assertThat(mutable.get(0).get()).isFalse();
    assertThat(mutable.get(5).get()).isFalse();
  }

  @Test
  void commitChanges_producesCorrectImmutable() {
    final SszBitlist bitlist = SCHEMA.ofBits(10);
    final SszMutablePrimitiveList<Boolean, SszBit> mutable = bitlist.createWritableCopy();

    mutable.set(2, SszBit.of(true));
    mutable.set(7, SszBit.of(true));
    final SszPrimitiveList<Boolean, SszBit> committed = mutable.commitChanges();

    assertThat(committed.size()).isEqualTo(10);
    assertThat(committed.get(2).get()).isTrue();
    assertThat(committed.get(7).get()).isTrue();
    assertThat(committed.get(0).get()).isFalse();
  }

  @Test
  void commitChanges_hashTreeRootMatchesFreshCreation() {
    final SszBitlist bitlist = SCHEMA.ofBits(10);
    final SszMutablePrimitiveList<Boolean, SszBit> mutable = bitlist.createWritableCopy();

    mutable.set(2, SszBit.of(true));
    mutable.set(7, SszBit.of(true));
    final SszPrimitiveList<Boolean, SszBit> committed = mutable.commitChanges();

    final SszBitlist expected = SCHEMA.ofBits(10, 2, 7);
    assertThat(committed.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void appendBits() {
    final SszBitlist bitlist = SCHEMA.ofBits(5, 0, 2);
    final SszMutablePrimitiveList<Boolean, SszBit> mutable = bitlist.createWritableCopy();

    // Append a true bit
    mutable.set(5, SszBit.of(true));
    assertThat(mutable.size()).isEqualTo(6);

    final SszPrimitiveList<Boolean, SszBit> committed = mutable.commitChanges();
    assertThat(committed.size()).isEqualTo(6);
    assertThat(committed.get(0).get()).isTrue();
    assertThat(committed.get(2).get()).isTrue();
    assertThat(committed.get(5).get()).isTrue();
    assertThat(committed.get(1).get()).isFalse();
  }

  @Test
  void sizeTracking() {
    final SszBitlist bitlist = SCHEMA.ofBits(3, 1);
    final SszMutablePrimitiveList<Boolean, SszBit> mutable = bitlist.createWritableCopy();

    assertThat(mutable.size()).isEqualTo(3);

    // Append
    mutable.set(3, SszBit.of(false));
    assertThat(mutable.size()).isEqualTo(4);

    mutable.set(4, SszBit.of(true));
    assertThat(mutable.size()).isEqualTo(5);
  }

  @Test
  void serializationRoundtripAfterCommit() {
    final SszBitlist bitlist = SCHEMA.ofBits(20, 0, 5, 10, 15, 19);
    final SszMutablePrimitiveList<Boolean, SszBit> mutable = bitlist.createWritableCopy();

    // Flip bit 5 off, set bit 3
    mutable.set(5, SszBit.of(false));
    mutable.set(3, SszBit.of(true));
    final SszPrimitiveList<Boolean, SszBit> committed = mutable.commitChanges();

    final Bytes serialized = committed.sszSerialize();
    final SszBitlist deserialized = SCHEMA.sszDeserialize(serialized);

    assertThat(deserialized.hashTreeRoot()).isEqualTo(committed.hashTreeRoot());
    assertThat(deserialized.size()).isEqualTo(20);
  }

  @Test
  void multipleCommits() {
    final SszBitlist bitlist = SCHEMA.ofBits(5);

    // First mutation
    final SszMutablePrimitiveList<Boolean, SszBit> mutable1 = bitlist.createWritableCopy();
    mutable1.set(0, SszBit.of(true));
    final SszPrimitiveList<Boolean, SszBit> committed1 = mutable1.commitChanges();

    // Second mutation
    final SszMutablePrimitiveList<Boolean, SszBit> mutable2 = committed1.createWritableCopy();
    mutable2.set(4, SszBit.of(true));
    final SszPrimitiveList<Boolean, SszBit> committed2 = mutable2.commitChanges();

    assertThat(committed2.get(0).get()).isTrue();
    assertThat(committed2.get(4).get()).isTrue();
    assertThat(committed2.get(1).get()).isFalse();

    final SszBitlist expected = SCHEMA.ofBits(5, 0, 4);
    assertThat(committed2.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void clear_thenModify() {
    final SszBitlist bitlist = SCHEMA.ofBits(10, 0, 5, 9);
    final SszMutablePrimitiveList<Boolean, SszBit> mutable = bitlist.createWritableCopy();

    mutable.clear();
    assertThat(mutable.size()).isEqualTo(0);

    mutable.set(0, SszBit.of(true));
    final SszPrimitiveList<Boolean, SszBit> committed = mutable.commitChanges();

    assertThat(committed.size()).isEqualTo(1);
    assertThat(committed.get(0).get()).isTrue();
  }

  @Test
  void commitWithNoChanges_returnsSameData() {
    final SszBitlist bitlist = SCHEMA.ofBits(10, 3, 7);
    final SszMutablePrimitiveList<Boolean, SszBit> mutable = bitlist.createWritableCopy();

    final SszPrimitiveList<Boolean, SszBit> committed = mutable.commitChanges();

    assertThat(committed.hashTreeRoot()).isEqualTo(bitlist.hashTreeRoot());
  }
}
