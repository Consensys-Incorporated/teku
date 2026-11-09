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

import it.unimi.dsi.fastutil.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.protoarray.DeferredVotes;

class DeferredAttestationsTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final DeferredAttestations deferredAttestations = new DeferredAttestations();

  @Test
  void shouldCombineVotesForSameSlot() {
    final UInt64 slot = UInt64.valueOf(32);
    final Bytes32 root1 = dataStructureUtil.randomBytes32();
    final Bytes32 root2 = dataStructureUtil.randomBytes32();
    deferredAttestations.addAttestation(createAttestation(slot, root1, 10, 20), false);
    deferredAttestations.addAttestation(createAttestation(slot, root1, 11, 21), false);
    deferredAttestations.addAttestation(createAttestation(slot, root2, 15, 25), false);

    assertDeferredVotes(
        slot,
        vote(root1, 10),
        vote(root1, 20),
        vote(root1, 11),
        vote(root1, 21),
        vote(root2, 15),
        vote(root2, 25));
  }

  @Test
  void shouldHandleSameAttestationMultipleTimes() {
    final UInt64 slot = UInt64.valueOf(32);
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final IndexedAttestationLight attestation = createAttestation(slot, root, 10, 20);
    deferredAttestations.addAttestation(attestation, false);
    deferredAttestations.addAttestation(attestation, false);
    assertDeferredVotes(slot, vote(root, 10), vote(root, 20));
  }

  @Test
  void shouldReturnPrunedVotesFromBeforeSlot() {
    final UInt64 earlySlot = UInt64.valueOf(20);
    final UInt64 previousSlot = UInt64.valueOf(25);
    final UInt64 currentSlot = UInt64.valueOf(26);
    final Bytes32 root = dataStructureUtil.randomBytes32();

    deferredAttestations.addAttestation(createAttestation(earlySlot, root, 1), false);
    deferredAttestations.addAttestation(createAttestation(previousSlot, root, 2), false);
    deferredAttestations.addAttestation(createAttestation(currentSlot, root, 3), false);

    final DeferredVotes earlyDeferred =
        deferredAttestations.getDeferredVotesFromSlot(earlySlot).orElseThrow();
    final DeferredVotes previousDeferred =
        deferredAttestations.getDeferredVotesFromSlot(previousSlot).orElseThrow();
    final Collection<DeferredVotes> prunedVotes = deferredAttestations.prune(currentSlot);
    assertThat(prunedVotes).containsExactlyInAnyOrder(earlyDeferred, previousDeferred);

    assertThat(deferredAttestations.getDeferredVotesFromSlot(earlySlot)).isEmpty();
    assertThat(deferredAttestations.getDeferredVotesFromSlot(previousSlot)).isEmpty();
    assertThat(deferredAttestations.getDeferredVotesFromSlot(currentSlot)).isNotEmpty();
  }

  private Pair<Bytes32, UInt64> vote(final Bytes32 root, final int validatorIndex) {
    return Pair.of(root, UInt64.valueOf(validatorIndex));
  }

  @SafeVarargs
  private void assertDeferredVotes(final UInt64 slot, final Pair<Bytes32, UInt64>... expected) {
    final Optional<DeferredVotes> deferredVotes =
        deferredAttestations.getDeferredVotesFromSlot(slot);
    assertThat(deferredVotes).isNotEmpty();
    final List<Pair<Bytes32, UInt64>> votes = new ArrayList<>();
    deferredVotes
        .get()
        .forEachDeferredVote((root, index, fullPayloadHint) -> votes.add(Pair.of(root, index)));
    assertThat(votes).containsExactlyInAnyOrder(expected);
  }

  private IndexedAttestationLight createAttestation(
      final UInt64 slot, final Bytes32 root, final int... attestingValidators) {
    return IndexedAttestationLight.fromSsz(
        dataStructureUtil.randomIndexedAttestation(
            dataStructureUtil.randomAttestationData(slot, root),
            IntStream.of(attestingValidators).mapToObj(UInt64::valueOf).toArray(UInt64[]::new)));
  }
}
