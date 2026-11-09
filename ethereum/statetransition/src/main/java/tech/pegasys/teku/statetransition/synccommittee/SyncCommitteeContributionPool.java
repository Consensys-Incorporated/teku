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

package tech.pegasys.teku.statetransition.synccommittee;

import static java.util.Collections.emptyMap;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class SyncCommitteeContributionPool implements SlotEventsChannel {
  private final Spec spec;
  private final SignedContributionAndProofValidator validator;
  private final Subscribers<OperationAddedSubscriber<SignedContributionAndProof>> subscribers =
      Subscribers.create(true);

  /** contribution.slot -> contribution.block -> contribution.subcommitteeIndex -> contribution */
  private final NavigableMap<UInt64, Map<Bytes32, Map<Integer, SyncCommitteeContribution>>>
      contributionsBySlotAndBlockRoot = new TreeMap<>();

  public SyncCommitteeContributionPool(
      final Spec spec, final SignedContributionAndProofValidator validator) {
    this.spec = spec;
    this.validator = validator;
  }

  public void subscribeOperationAdded(
      final OperationAddedSubscriber<SignedContributionAndProof> subscriber) {
    subscribers.subscribe(subscriber);
  }

  public SafeFuture<InternalValidationResult> addLocal(
      final SignedContributionAndProof signedContributionAndProof) {
    return add(signedContributionAndProof, false);
  }

  public SafeFuture<InternalValidationResult> addRemote(
      final SignedContributionAndProof signedContributionAndProof,
      final Optional<UInt64> arrivalTimestamp) {
    return add(signedContributionAndProof, true);
  }

  private SafeFuture<InternalValidationResult> add(
      final SignedContributionAndProof signedContributionAndProof, final boolean fromNetwork) {
    return validator
        .validate(signedContributionAndProof)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                doAdd(signedContributionAndProof.getMessage().getContribution());
                subscribers.forEach(
                    subscriber ->
                        subscriber.onOperationAdded(
                            signedContributionAndProof, result, fromNetwork));
              }
            });
  }

  private synchronized void doAdd(final SyncCommitteeContribution contribution) {
    final int subcommitteeIndex = contribution.getSubcommitteeIndex().intValue();
    contributionsBySlotAndBlockRoot
        .computeIfAbsent(contribution.getSlot(), __ -> new HashMap<>())
        .computeIfAbsent(contribution.getBeaconBlockRoot(), __ -> new Int2ObjectOpenHashMap<>())
        .compute(
            subcommitteeIndex,
            (subcommittee, existingContribution) ->
                betterContribution(existingContribution, contribution));
  }

  private SyncCommitteeContribution betterContribution(
      final SyncCommitteeContribution a, final SyncCommitteeContribution b) {
    if (a == null) {
      return b;
    } else if (b == null) {
      return a;
    } else {
      return a.getAggregationBits().getBitCount() >= b.getAggregationBits().getBitCount() ? a : b;
    }
  }

  /**
   * Creates a {@link SyncAggregate} that is valid for inclusion in the block at {@code slot}.
   *
   * <p>Specifically, the included contributions must be from slot {@code slot - 1} and must be for
   * the beacon root in effect at {@code get_block_root_at_slot(state, slot - 1)} (i.e. the parent
   * root of the block to be created).
   *
   * @param blockSlot the slot of the block to create a SyncAggregate for.
   * @param parentRoot the parentRoot of the block being created.
   * @return the SyncAggregate to be included in the block.
   */
  public synchronized SyncAggregate createSyncAggregateForBlock(
      final UInt64 blockSlot, final Bytes32 parentRoot) {
    final UInt64 slot = blockSlot.minusMinZero(1);
    final Collection<SyncCommitteeContribution> contributions =
        contributionsBySlotAndBlockRoot
            .getOrDefault(slot, emptyMap())
            .getOrDefault(parentRoot, emptyMap())
            .values();
    return spec.getSyncCommitteeUtilRequired(blockSlot).createSyncAggregate(contributions);
  }

  /**
   * Prunes by removing all contributions more than 2 slots prior to the current slot.
   *
   * <p>The contributions from the previous slot are required for the block in the current slot and
   * one more slot worth of contributions is kept to provide some clock tolerance.
   *
   * @param slot the node's current slot
   */
  @Override
  public synchronized void onSlot(final UInt64 slot) {
    contributionsBySlotAndBlockRoot.headMap(slot.minusMinZero(2), false).clear();
  }
}
