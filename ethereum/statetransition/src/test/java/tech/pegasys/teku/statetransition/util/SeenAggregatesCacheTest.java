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

package tech.pegasys.teku.statetransition.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitlist;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszBitlistSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SeenAggregatesCacheTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SszBitlistSchema<SszBitlist> bitlistSchema = SszBitlistSchema.create(10);
  private final SeenAggregatesCache<Bytes32> cache = new SeenAggregatesCache<>(3);

  @Test
  void isAlreadySeen_shouldBeTrueWhenValueIsEqual() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    assertThat(cache.add(root, bitlist(true, false, true, false))).isTrue();

    assertThat(cache.isAlreadySeen(root, bitlist(true, false, true, false))).isTrue();
  }

  @Test
  void isAlreadySeen_shouldBeTrueWhenValueIsSubset() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    assertThat(cache.add(root, bitlist(true, false, true, false))).isTrue();

    assertThat(cache.isAlreadySeen(root, bitlist(true, false, false, false))).isTrue();
  }

  @Test
  void isAlreadySeen_shouldBeFalseWhenSuperset() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    assertThat(cache.add(root, bitlist(true, false, true, false))).isTrue();

    assertThat(cache.isAlreadySeen(root, bitlist(true, true, false, false))).isFalse();
  }

  @Test
  void isAlreadySeen_shouldBeFalseWhenNoIndividualSeenValueIsASuperset() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    // Combined we've seen all validators
    assertThat(cache.add(root, bitlist(true, false, true, false))).isTrue();
    assertThat(cache.add(root, bitlist(false, true, false, true))).isTrue();

    // But this aggregate isn't a subset of either previously seen value
    assertThat(cache.isAlreadySeen(root, bitlist(true, true, false, false))).isFalse();
  }

  @Test
  void isAlreadySeen_shouldBeFalseWhenRootIsDifferent() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    // Combined we've seen all validators
    assertThat(cache.add(root, bitlist(true, false, true, false))).isTrue();

    // But this aggregate isn't a subset of either previously seen value
    assertThat(
            cache.isAlreadySeen(
                dataStructureUtil.randomBytes32(), bitlist(true, false, true, false)))
        .isFalse();
  }

  @Test
  void add_shouldBeFalseWhenValueIsEqual() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    assertThat(cache.add(root, bitlist(true, false, true, false))).isTrue();

    assertThat(cache.add(root, bitlist(true, false, true, false))).isFalse();
  }

  @Test
  void add_shouldBeFalseWhenValueIsSubset() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    assertThat(cache.add(root, bitlist(true, false, true, false))).isTrue();

    assertThat(cache.add(root, bitlist(true, false, false, false))).isFalse();
  }

  @Test
  void add_shouldBeTrueWhenSuperset() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    assertThat(cache.add(root, bitlist(true, false, true, false))).isTrue();

    assertThat(cache.add(root, bitlist(true, true, false, false))).isTrue();
  }

  @Test
  void add_shouldBeTrueWhenNoIndividualSeenValueIsASuperset() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    // Combined we've seen all validators
    assertThat(cache.add(root, bitlist(true, false, true, false))).isTrue();
    assertThat(cache.add(root, bitlist(false, true, false, true))).isTrue();

    // But this aggregate isn't a subset of either previously seen value
    assertThat(cache.add(root, bitlist(true, true, false, false))).isTrue();
  }

  private SszBitlist bitlist(final Boolean... values) {
    return bitlistSchema.of(values);
  }
}
