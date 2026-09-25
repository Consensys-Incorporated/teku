/*
 * Copyright Consensys Software Inc., 2026
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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class SeenAttestingValidatorsCacheTest {

  private final SeenAttestingValidatorsCache cache = new SeenAttestingValidatorsCache(2);

  @Test
  void isAlreadySeen_shouldBeFalseWhenNeverAdded() {
    assertThat(cache.isAlreadySeen(UInt64.ZERO, 1)).isFalse();
  }

  @Test
  void addIfAbsent_shouldBeTrueTheFirstTimeAndFalseOnDuplicate() {
    assertThat(cache.addIfAbsent(UInt64.ZERO, 1)).isTrue();
    assertThat(cache.isAlreadySeen(UInt64.ZERO, 1)).isTrue();

    assertThat(cache.addIfAbsent(UInt64.ZERO, 1)).isFalse();
  }

  @Test
  void addIfAbsent_shouldTrackValidatorsIndependently() {
    assertThat(cache.addIfAbsent(UInt64.ZERO, 1)).isTrue();
    assertThat(cache.addIfAbsent(UInt64.ZERO, 2)).isTrue();

    assertThat(cache.isAlreadySeen(UInt64.ZERO, 1)).isTrue();
    assertThat(cache.isAlreadySeen(UInt64.ZERO, 2)).isTrue();
    assertThat(cache.isAlreadySeen(UInt64.ZERO, 3)).isFalse();
  }

  @Test
  void addIfAbsent_shouldTrackEpochsIndependently() {
    assertThat(cache.addIfAbsent(UInt64.ZERO, 1)).isTrue();
    assertThat(cache.addIfAbsent(UInt64.ONE, 1)).isTrue();

    assertThat(cache.isAlreadySeen(UInt64.ZERO, 1)).isTrue();
    assertThat(cache.isAlreadySeen(UInt64.ONE, 1)).isTrue();
  }

  @Test
  void addIfAbsent_shouldPruneEpochsOutsideTheRetentionWindow() {
    assertThat(cache.addIfAbsent(UInt64.ZERO, 1)).isTrue();

    // Advance well past the retention window (maxCachedEpochs = 2).
    assertThat(cache.addIfAbsent(UInt64.valueOf(10), 1)).isTrue();

    assertThat(cache.isAlreadySeen(UInt64.ZERO, 1)).isFalse();
  }

  @Test
  void addIfAbsent_shouldNotResurrectAnAlreadyPrunedEpoch() {
    final UInt64 staleEpoch = UInt64.ZERO;

    // A validation for epoch 0 completes first.
    assertThat(cache.addIfAbsent(staleEpoch, 1)).isTrue();

    // Epoch 10 attestations arrive and get recorded, well past the retention window, pruning
    // epoch 0's entry.
    assertThat(cache.addIfAbsent(UInt64.valueOf(10), 1)).isTrue();
    assertThat(cache.isAlreadySeen(staleEpoch, 1)).isFalse();

    // A delayed validation for epoch 0 (e.g. queued behind a signature-verification backlog)
    // completes late, after epoch 0 was already pruned. Pruning tracks a monotonic watermark, so
    // this stale call must not resurrect epoch 0 as a live, separately-tracked entry -- if it
    // did, a second late arrival for the same validator/epoch would wrongly look like a fresh
    // "first" attestation instead of a duplicate.
    assertThat(cache.addIfAbsent(staleEpoch, 1)).isTrue();
    assertThat(cache.addIfAbsent(staleEpoch, 1)).isTrue();
  }
}
