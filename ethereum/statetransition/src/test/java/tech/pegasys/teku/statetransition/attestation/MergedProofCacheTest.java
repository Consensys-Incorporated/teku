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

package tech.pegasys.teku.statetransition.attestation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;
import tech.pegasys.teku.statetransition.attestation.MergedProofCache.Claim;
import tech.pegasys.teku.statetransition.attestation.MergedProofCache.MergedProof;

class MergedProofCacheTest {

  private static final UInt64 SLOT = UInt64.valueOf(10);
  private static final Claim A = claim(1);
  private static final Claim B = claim(2);
  private static final Claim C = claim(3);

  private final MergedProofCache cache = new MergedProofCache();

  @Test
  void shouldFindTheLargestProofWithinTheClaims() {
    final MergedProof a = proof(A);
    final MergedProof ab = proof(A, B);
    final MergedProof abc = proof(A, B, C);
    cache.add(SLOT, a);
    cache.add(SLOT, ab);
    cache.add(SLOT, abc);

    assertThat(cache.findLargestWithin(SLOT, Set.of(A, B))).contains(ab);
    assertThat(cache.findLargestWithin(SLOT, Set.of(A, B, C))).contains(abc);
    assertThat(cache.findLargestWithin(SLOT, Set.of(B, C))).isEmpty();
    assertThat(cache.findLargestWithin(SLOT.plus(1), Set.of(A, B, C))).isEmpty();
  }

  @Test
  void shouldKeepTheNewestProofsPerSlot() {
    final MergedProof first = proof(A, B);
    cache.add(SLOT, first);
    IntStream.range(0, MergedProofCache.MAX_PROOFS_PER_SLOT)
        .forEach(__ -> cache.add(SLOT, proof(A)));

    assertThat(cache.findLargestWithin(SLOT, Set.of(A, B))).isPresent().get().isNotEqualTo(first);
  }

  @Test
  void shouldPruneProofsForEarlierBlocks() {
    final MergedProof current = proof(A);
    cache.add(SLOT, proof(A));
    cache.add(SLOT.plus(1), current);

    cache.prune(SLOT.plus(1));

    assertThat(cache.findLargestWithin(SLOT, Set.of(A))).isEmpty();
    assertThat(cache.findLargestWithin(SLOT.plus(1), Set.of(A))).contains(current);
  }

  @Test
  void claimsShouldPairEachKeyWithTheMessage() {
    final Bytes32 message = Bytes32.fromHexString("0x" + "ab".repeat(32));
    final XmssAggregateStatement statement =
        new XmssAggregateStatement(
            List.of(Bytes32.leftPad(Bytes.of(1)), Bytes32.leftPad(Bytes.of(2))),
            UInt64.valueOf(5),
            message);

    assertThat(Claim.of(statement))
        .containsExactlyInAnyOrder(
            new Claim(Bytes32.leftPad(Bytes.of(1)), message),
            new Claim(Bytes32.leftPad(Bytes.of(2)), message));
  }

  private static Claim claim(final int key) {
    return new Claim(Bytes32.leftPad(Bytes.of(key)), Bytes32.ZERO);
  }

  private static MergedProof proof(final Claim... claims) {
    return new MergedProof(Set.of(claims), List.of(), Bytes.random(4));
  }
}
