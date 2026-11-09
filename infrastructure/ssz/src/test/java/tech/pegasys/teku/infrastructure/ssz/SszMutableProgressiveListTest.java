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

package tech.pegasys.teku.infrastructure.ssz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas;
import tech.pegasys.teku.infrastructure.ssz.schema.SszProgressiveListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SszMutableProgressiveListTest {

  private static final SszProgressiveListSchema<SszUInt64> UINT64_LIST_SCHEMA =
      SszProgressiveListSchema.create(SszPrimitiveSchemas.UINT64_SCHEMA);

  private static SszList<SszUInt64> createUInt64List(final int count) {
    final List<SszUInt64> elements =
        IntStream.range(0, count)
            .mapToObj(i -> SszUInt64.of(UInt64.valueOf(i)))
            .collect(Collectors.toList());
    final TreeNode tree = UINT64_LIST_SCHEMA.createTreeFromElements(elements);
    return UINT64_LIST_SCHEMA.createFromBackingNode(tree);
  }

  @Test
  void isWritableSupported_shouldReturnTrue() {
    final SszList<SszUInt64> list = createUInt64List(1);
    assertThat(list.isWritableSupported()).isTrue();
  }

  @Test
  void createWritableCopy_shouldSucceed() {
    final SszList<SszUInt64> list = createUInt64List(3);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();
    assertThat(mutable.size()).isEqualTo(3);
    for (int i = 0; i < 3; i++) {
      assertThat(mutable.get(i).get()).isEqualTo(UInt64.valueOf(i));
    }
  }

  @Test
  void setAndGet_roundtrip() {
    final SszList<SszUInt64> list = createUInt64List(5);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    mutable.set(2, SszUInt64.of(UInt64.valueOf(999)));

    assertThat(mutable.get(2).get()).isEqualTo(UInt64.valueOf(999));
    assertThat(mutable.size()).isEqualTo(5);
  }

  @Test
  void commitChanges_producesCorrectImmutable() {
    final SszList<SszUInt64> list = createUInt64List(5);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    mutable.set(2, SszUInt64.of(UInt64.valueOf(999)));
    final SszList<SszUInt64> committed = mutable.commitChanges();

    assertThat(committed.size()).isEqualTo(5);
    assertThat(committed.get(0).get()).isEqualTo(UInt64.ZERO);
    assertThat(committed.get(1).get()).isEqualTo(UInt64.ONE);
    assertThat(committed.get(2).get()).isEqualTo(UInt64.valueOf(999));
    assertThat(committed.get(3).get()).isEqualTo(UInt64.valueOf(3));
    assertThat(committed.get(4).get()).isEqualTo(UInt64.valueOf(4));
  }

  @Test
  void commitChanges_hashTreeRootMatchesFreshCreation() {
    final SszList<SszUInt64> list = createUInt64List(5);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    mutable.set(2, SszUInt64.of(UInt64.valueOf(999)));
    final SszList<SszUInt64> committed = mutable.commitChanges();

    // Build expected from scratch
    final List<SszUInt64> expectedElements =
        List.of(
            SszUInt64.of(UInt64.ZERO),
            SszUInt64.of(UInt64.ONE),
            SszUInt64.of(UInt64.valueOf(999)),
            SszUInt64.of(UInt64.valueOf(3)),
            SszUInt64.of(UInt64.valueOf(4)));
    final TreeNode expectedTree = UINT64_LIST_SCHEMA.createTreeFromElements(expectedElements);
    final SszList<SszUInt64> expected = UINT64_LIST_SCHEMA.createFromBackingNode(expectedTree);

    assertThat(committed.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void append_incrementsSize() {
    final SszList<SszUInt64> list = createUInt64List(3);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    mutable.append(SszUInt64.of(UInt64.valueOf(100)));

    assertThat(mutable.size()).isEqualTo(4);
    assertThat(mutable.get(3).get()).isEqualTo(UInt64.valueOf(100));
  }

  @Test
  void append_crossesLevelBoundary() {
    // Create list with 5 elements (fills levels 0 and 1)
    final SszList<SszUInt64> list = createUInt64List(5);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    // Append element at index 5 (starts level 2)
    mutable.append(SszUInt64.of(UInt64.valueOf(500)));
    final SszList<SszUInt64> committed = mutable.commitChanges();

    assertThat(committed.size()).isEqualTo(6);
    for (int i = 0; i < 5; i++) {
      assertThat(committed.get(i).get()).isEqualTo(UInt64.valueOf(i));
    }
    assertThat(committed.get(5).get()).isEqualTo(UInt64.valueOf(500));

    // Verify hash matches fresh creation
    final List<SszUInt64> expectedElements =
        IntStream.range(0, 5)
            .mapToObj(i -> SszUInt64.of(UInt64.valueOf(i)))
            .collect(Collectors.toList());
    expectedElements.add(SszUInt64.of(UInt64.valueOf(500)));
    final TreeNode expectedTree = UINT64_LIST_SCHEMA.createTreeFromElements(expectedElements);
    final SszList<SszUInt64> expected = UINT64_LIST_SCHEMA.createFromBackingNode(expectedTree);

    assertThat(committed.hashTreeRoot()).isEqualTo(expected.hashTreeRoot());
  }

  @Test
  void multipleCommits_worksCorrectly() {
    final SszList<SszUInt64> list = createUInt64List(3);

    // First mutation
    final SszMutableList<SszUInt64> mutable1 = list.createWritableCopy();
    mutable1.set(0, SszUInt64.of(UInt64.valueOf(10)));
    final SszList<SszUInt64> committed1 = mutable1.commitChanges();

    // Second mutation on the committed result
    final SszMutableList<SszUInt64> mutable2 = committed1.createWritableCopy();
    mutable2.set(1, SszUInt64.of(UInt64.valueOf(20)));
    final SszList<SszUInt64> committed2 = mutable2.commitChanges();

    assertThat(committed2.get(0).get()).isEqualTo(UInt64.valueOf(10));
    assertThat(committed2.get(1).get()).isEqualTo(UInt64.valueOf(20));
    assertThat(committed2.get(2).get()).isEqualTo(UInt64.valueOf(2));
  }

  @Test
  void clear_thenAppend() {
    final SszList<SszUInt64> list = createUInt64List(5);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    mutable.clear();
    assertThat(mutable.size()).isEqualTo(0);

    mutable.append(SszUInt64.of(UInt64.valueOf(42)));
    final SszList<SszUInt64> committed = mutable.commitChanges();

    assertThat(committed.size()).isEqualTo(1);
    assertThat(committed.get(0).get()).isEqualTo(UInt64.valueOf(42));
  }

  @Test
  void commitWithNoChanges_returnsSameData() {
    final SszList<SszUInt64> list = createUInt64List(3);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    final SszList<SszUInt64> committed = mutable.commitChanges();

    assertThat(committed.hashTreeRoot()).isEqualTo(list.hashTreeRoot());
  }

  @Test
  void appendFromEmpty() {
    final SszList<SszUInt64> list = createUInt64List(0);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    mutable.append(SszUInt64.of(UInt64.valueOf(1)));
    mutable.append(SszUInt64.of(UInt64.valueOf(2)));
    final SszList<SszUInt64> committed = mutable.commitChanges();

    assertThat(committed.size()).isEqualTo(2);
    assertThat(committed.get(0).get()).isEqualTo(UInt64.ONE);
    assertThat(committed.get(1).get()).isEqualTo(UInt64.valueOf(2));

    // Verify hash matches fresh creation
    final List<SszUInt64> elems =
        List.of(SszUInt64.of(UInt64.ONE), SszUInt64.of(UInt64.valueOf(2)));
    final TreeNode expectedTree = UINT64_LIST_SCHEMA.createTreeFromElements(elems);
    final SszList<SszUInt64> expectedList = UINT64_LIST_SCHEMA.createFromBackingNode(expectedTree);
    assertThat(committed.hashTreeRoot()).isEqualTo(expectedList.hashTreeRoot());
  }

  @Test
  void modifyMultipleElementsInSameChunk() {
    // UInt64 is 8 bytes, so 4 UInt64 values per 32-byte chunk
    final SszList<SszUInt64> list = createUInt64List(8);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    // Modify two values in the same chunk (indices 1 and 2 are in chunk 0 at level 0)
    // Actually for progressive list: chunk 0 = elements 0-3, chunk 1 = elements 4-7
    // chunk 0 is at level 0, chunk 1 is at level 1
    mutable.set(0, SszUInt64.of(UInt64.valueOf(100)));
    mutable.set(1, SszUInt64.of(UInt64.valueOf(200)));
    final SszList<SszUInt64> committed = mutable.commitChanges();

    assertThat(committed.get(0).get()).isEqualTo(UInt64.valueOf(100));
    assertThat(committed.get(1).get()).isEqualTo(UInt64.valueOf(200));
    assertThat(committed.get(2).get()).isEqualTo(UInt64.valueOf(2));
  }

  @Test
  void largeList_modifyAndVerify() {
    final SszList<SszUInt64> list = createUInt64List(100);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();

    // Modify several elements
    mutable.set(0, SszUInt64.of(UInt64.valueOf(1000)));
    mutable.set(50, SszUInt64.of(UInt64.valueOf(2000)));
    mutable.set(99, SszUInt64.of(UInt64.valueOf(3000)));
    final SszList<SszUInt64> committed = mutable.commitChanges();

    assertThat(committed.size()).isEqualTo(100);
    assertThat(committed.get(0).get()).isEqualTo(UInt64.valueOf(1000));
    assertThat(committed.get(50).get()).isEqualTo(UInt64.valueOf(2000));
    assertThat(committed.get(99).get()).isEqualTo(UInt64.valueOf(3000));
    assertThat(committed.get(1).get()).isEqualTo(UInt64.ONE);
  }

  @Test
  void sszSerializationRoundtrip_afterMutation() {
    final SszList<SszUInt64> list = createUInt64List(5);
    final SszMutableList<SszUInt64> mutable = list.createWritableCopy();
    mutable.set(2, SszUInt64.of(UInt64.valueOf(42)));
    final SszList<SszUInt64> committed = mutable.commitChanges();

    // Serialize and deserialize
    final Bytes serialized = committed.sszSerialize();
    final SszList<SszUInt64> deserialized = UINT64_LIST_SCHEMA.sszDeserialize(serialized);

    assertThat(deserialized.size()).isEqualTo(5);
    assertThat(deserialized.get(2).get()).isEqualTo(UInt64.valueOf(42));
    assertThat(deserialized.hashTreeRoot()).isEqualTo(committed.hashTreeRoot());
  }
}
