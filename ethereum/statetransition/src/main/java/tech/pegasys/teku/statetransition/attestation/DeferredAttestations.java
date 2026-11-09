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

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.storage.protoarray.DeferredVotes;

/**
 * Holds information about attestations received in the same slot they were created in which can't
 * be applied until the start of the next slot. Added attestations must have already been validated
 * as no further validation will be performed.
 *
 * <p>The aim is to store these in a memory efficient form since a lot of attestations are included
 * in this group but also make them fast to apply.
 *
 * <p>Generally there should only be attestations for one slot (the current slot) stored at a time,
 * however support for multiple slots is provided because at the start of the next slot, new
 * attestations may be received before the attestations from the prior slot have had a chance to be
 * removed and applied.
 */
public class DeferredAttestations {

  private final ConcurrentNavigableMap<UInt64, VoteUpdates> deferredVoteUpdatesBySlot =
      new ConcurrentSkipListMap<>();

  public void addAttestation(
      final IndexedAttestationLight attestation, final boolean fullPayloadHint) {
    deferredVoteUpdatesBySlot
        .computeIfAbsent(attestation.data().getSlot(), VoteUpdates::new)
        .addAttestation(attestation, fullPayloadHint);
  }

  public Collection<DeferredVotes> prune(final UInt64 currentSlot) {
    final ConcurrentNavigableMap<UInt64, VoteUpdates> removedVotes =
        deferredVoteUpdatesBySlot.headMap(currentSlot, false);
    final List<DeferredVotes> votesToApply = new ArrayList<>(removedVotes.values());
    removedVotes.clear();
    return votesToApply;
  }

  @VisibleForTesting
  Optional<DeferredVotes> getDeferredVotesFromSlot(final UInt64 slot) {
    return Optional.ofNullable(deferredVoteUpdatesBySlot.get(slot));
  }
}
