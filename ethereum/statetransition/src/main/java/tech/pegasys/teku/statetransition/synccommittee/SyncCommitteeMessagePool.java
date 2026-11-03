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

import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class SyncCommitteeMessagePool implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final Subscribers<OperationAddedSubscriber<ValidatableSyncCommitteeMessage>> subscribers =
      Subscribers.create(true);

  private final Spec spec;
  private final SyncCommitteeMessageValidator validator;

  /**
   * Effectively provides a mapping from (slot, blockRoot, subcommitteeIndex) -> ContributionData
   * but using a nested map under slot so that pruning based on slot is efficient.
   */
  private final NavigableMap<UInt64, Map<BlockRootAndCommitteeIndex, ContributionData>>
      committeeContributionData = new TreeMap<>();

  public SyncCommitteeMessagePool(final Spec spec, final SyncCommitteeMessageValidator validator) {
    this.spec = spec;
    this.validator = validator;
  }

  public void subscribeOperationAdded(
      final OperationAddedSubscriber<ValidatableSyncCommitteeMessage> subscriber) {
    subscribers.subscribe(subscriber);
  }

  public SafeFuture<InternalValidationResult> addLocal(
      final ValidatableSyncCommitteeMessage message) {
    return add(message, false);
  }

  public SafeFuture<InternalValidationResult> addRemote(
      final ValidatableSyncCommitteeMessage message, final Optional<UInt64> arrivalTimestamp) {
    return add(message, true);
  }

  private SafeFuture<InternalValidationResult> add(
      final ValidatableSyncCommitteeMessage message, final boolean fromNetwork) {
    return validator
        .validate(message)
        .thenPeek(
            result -> {
              if (result.isAccept()) {
                subscribers.forEach(
                    subscriber -> subscriber.onOperationAdded(message, result, fromNetwork));
                doAdd(message);
              }
            });
  }

  private synchronized void doAdd(final ValidatableSyncCommitteeMessage message) {
    final SyncSubcommitteeAssignments assignments =
        message.getSubcommitteeAssignments().orElseThrow();
    final Map<BlockRootAndCommitteeIndex, ContributionData> blockRootAndCommitteeIndexToMessages =
        committeeContributionData.computeIfAbsent(message.getSlot(), __ -> new HashMap<>());
    final IntSet applicableSubnets;
    if (message.getReceivedSubnetId().isEmpty()) {
      applicableSubnets = assignments.getAssignedSubcommittees();
    } else {
      applicableSubnets = IntSet.of(message.getReceivedSubnetId().getAsInt());
    }
    applicableSubnets.forEach(
        subcommitteeIndex ->
            blockRootAndCommitteeIndexToMessages
                .computeIfAbsent(
                    new BlockRootAndCommitteeIndex(message.getBeaconBlockRoot(), subcommitteeIndex),
                    __ -> new ContributionData())
                .add(
                    assignments.getParticipationBitIndices(subcommitteeIndex),
                    message.getMessage().getSignature()));
  }

  public synchronized Optional<SyncCommitteeContribution> createContribution(
      final UInt64 slot, final Bytes32 blockRoot, final int subcommitteeIndex) {
    return getContributionData(slot, blockRoot, subcommitteeIndex)
        .flatMap(
            contributionData ->
                toContribution(slot, blockRoot, subcommitteeIndex, contributionData));
  }

  private Optional<SyncCommitteeContribution> toContribution(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final int subcommitteeIndex,
      final ContributionData contributionData) {
    final BLSSignature aggregatedSignature = contributionData.getAggregatedSignature();
    // A contribution signature at the point at infinity is invalid with non-empty participation
    if (aggregatedSignature.isInfinity()) {
      LOG.debug(
          "Not creating sync committee contribution with infinity signature for slot {},"
              + " block root {}, subcommittee {}",
          slot,
          blockRoot,
          subcommitteeIndex);
      return Optional.empty();
    }
    return Optional.of(
        spec.getSyncCommitteeUtilRequired(slot)
            .createSyncCommitteeContribution(
                slot,
                blockRoot,
                UInt64.valueOf(subcommitteeIndex),
                contributionData.getParticipationIndices(),
                aggregatedSignature));
  }

  /**
   * Prunes by removing all messages more than one slot old. Theoretically only the current slot
   * messages are required but we provide a one slot tolerance.
   *
   * @param slot the current node slot
   */
  @Override
  public synchronized void onSlot(final UInt64 slot) {
    committeeContributionData.headMap(slot.minusMinZero(1), false).clear();
  }

  private Optional<ContributionData> getContributionData(
      final UInt64 slot, final Bytes32 blockRoot, final int subcommitteeIndex) {
    return Optional.ofNullable(
        committeeContributionData
            .getOrDefault(slot, Collections.emptyMap())
            .get(new BlockRootAndCommitteeIndex(blockRoot, subcommitteeIndex)));
  }

  private static class BlockRootAndCommitteeIndex {
    private final Bytes32 blockRoot;
    private final int committeeIndex;

    private BlockRootAndCommitteeIndex(final Bytes32 blockRoot, final int committeeIndex) {
      this.blockRoot = blockRoot;
      this.committeeIndex = committeeIndex;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final BlockRootAndCommitteeIndex that = (BlockRootAndCommitteeIndex) o;
      return committeeIndex == that.committeeIndex && Objects.equals(blockRoot, that.blockRoot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(blockRoot, committeeIndex);
    }
  }

  private static class ContributionData {
    private final IntSet participationIndices = new IntOpenHashSet();
    private final List<BLSSignature> signatures = new ArrayList<>();

    public void add(final IntSet participationIndices, final BLSSignature signature) {
      IntIterator iterator = participationIndices.iterator();
      while (iterator.hasNext()) {
        final int index = iterator.nextInt();
        if (this.participationIndices.add(index)) {
          this.signatures.add(signature);
        } else {
          LOG.trace(
              "Ignoring already aggregated signature from subcommittee participant index = {}",
              index);
        }
      }
    }

    public IntSet getParticipationIndices() {
      return participationIndices;
    }

    public BLSSignature getAggregatedSignature() {
      return signatures.isEmpty() ? BLSSignature.infinity() : BLS.aggregate(signatures);
    }
  }
}
