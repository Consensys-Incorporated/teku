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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.XmssAggregateStatement;

/**
 * Block proofs already merged, by the slot of the block they were merged for, so that a later merge
 * for that block only adds what's new (see {@link
 * tech.pegasys.teku.spec.logic.versions.pq.crypto.StarkProver#isIncrementallyMergeable()}).
 */
final class MergedProofCache {

  /** Merged proofs kept per block slot; the oldest goes first. */
  static final int MAX_PROOFS_PER_SLOT = 4;

  private final NavigableMap<UInt64, List<MergedProof>> proofsBySlot = new TreeMap<>();

  synchronized void add(final UInt64 blockSlot, final MergedProof proof) {
    final List<MergedProof> proofs =
        proofsBySlot.computeIfAbsent(blockSlot, __ -> new ArrayList<>());
    proofs.add(proof);
    if (proofs.size() > MAX_PROOFS_PER_SLOT) {
      proofs.removeFirst();
    }
  }

  /** The proof for {@code blockSlot} of the most claims, all of them among {@code claims}. */
  synchronized Optional<MergedProof> findLargestWithin(
      final UInt64 blockSlot, final Set<Claim> claims) {
    return proofsBySlot.getOrDefault(blockSlot, List.of()).stream()
        .filter(proof -> claims.containsAll(proof.claims()))
        .max(Comparator.comparingInt(proof -> proof.claims().size()));
  }

  /** Forgets proofs for blocks before {@code slot}. */
  synchronized void prune(final UInt64 slot) {
    proofsBySlot.headMap(slot, false).clear();
  }

  /**
   * What a proof proves, as a recursive proof does: that the key signed the message, at whichever
   * leaf.
   */
  record Claim(Bytes32 publicKey, Bytes32 message) {

    static Set<Claim> of(final XmssAggregateStatement statement) {
      final Set<Claim> claims = new HashSet<>();
      statement.pubkeys().forEach(key -> claims.add(new Claim(key, statement.message())));
      return claims;
    }
  }

  /**
   * A merged proof, the statements it proves and their claims.
   *
   * @param round how many merges built the proof: 1 for a merge of committee proofs, one more for
   *     each merge into an earlier merged proof
   * @param included the attestations the proof proves, which a block takes as they are
   */
  record MergedProof(
      Set<Claim> claims,
      List<XmssAggregateStatement> statements,
      Bytes proof,
      int round,
      List<AggregatingAttestationPoolPq.SelectedData> included) {}
}
