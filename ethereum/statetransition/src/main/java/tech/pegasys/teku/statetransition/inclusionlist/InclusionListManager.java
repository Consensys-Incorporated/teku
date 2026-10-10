/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.inclusionlist;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBitvector;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.forkchoice.InclusionListStore;
import tech.pegasys.teku.spec.datastructures.inclusionlist.SignedInclusionListListener;
import tech.pegasys.teku.spec.logic.common.statetransition.results.InclusionListImportResult;
import tech.pegasys.teku.spec.logic.versions.heze.util.InclusionListUtil;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.util.ShufflingDependentRootUtil;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.SignedInclusionListValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class InclusionListManager implements SlotEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private static final UInt64 SLOTS_TO_RETAIN = UInt64.valueOf(4);
  private final SignedInclusionListValidator signedInclusionListValidator;
  private final ForkChoice forkChoice;
  private final Spec spec;
  private final RecentChainData recentChainData;
  private final InclusionListStore inclusionListStore;
  private final NavigableMap<UInt64, ConcurrentMap<UInt64, List<SignedInclusionList>>>
      slotToInclusionListsByValidatorIndex = new ConcurrentSkipListMap<>();
  private final Subscribers<SignedInclusionListListener> inclusionListsSubscribers =
      Subscribers.create(true);

  public InclusionListManager(
      final SignedInclusionListValidator signedInclusionListValidator,
      final ForkChoice forkChoice,
      final Spec spec,
      final RecentChainData recentChainData,
      final InclusionListStore inclusionListStore) {
    this.signedInclusionListValidator = signedInclusionListValidator;
    this.forkChoice = forkChoice;
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.inclusionListStore = inclusionListStore;
  }

  @Override
  public synchronized void onSlot(final UInt64 slot) {
    slotToInclusionListsByValidatorIndex.headMap(slot.minusMinZero(SLOTS_TO_RETAIN)).clear();
  }

  public void add(final SignedInclusionList signedInclusionList) {
    final UInt64 validatorIndex = signedInclusionList.getMessage().getValidatorIndex();
    final UInt64 slot = signedInclusionList.getMessage().getSlot();
    slotToInclusionListsByValidatorIndex
        .computeIfAbsent(slot, index -> new ConcurrentHashMap<>())
        .compute(
            validatorIndex,
            (index, inclusionLists) -> {
              if (inclusionLists == null) {
                return List.of(signedInclusionList);
              } else {
                final List<SignedInclusionList> updatedList = new ArrayList<>(inclusionLists);
                updatedList.add(signedInclusionList);
                return updatedList;
              }
            });
  }

  public SafeFuture<InternalValidationResult> addSignedInclusionList(
      final SignedInclusionList signedInclusionList, final Optional<UInt64> arrivalTimestamp) {
    final SafeFuture<InternalValidationResult> validationResult =
        signedInclusionListValidator.validate(signedInclusionList);
    processInternallyInclusionList(validationResult, signedInclusionList);
    return validationResult;
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  private void processInternallyInclusionList(
      final SafeFuture<InternalValidationResult> validationResult,
      final SignedInclusionList signedInclusionList) {
    validationResult.thenAccept(
        internalValidationResult -> {
          if (internalValidationResult.isAccept()) {
            onInclusionList(signedInclusionList)
                .finish(
                    inclusionListImportResult -> {
                      if (!inclusionListImportResult.isSuccessful()) {
                        LOG.debug(
                            "Rejected received inclusion list: {}",
                            inclusionListImportResult.getFailureReason());
                      }
                    },
                    err -> LOG.error("Failed to process received inclusion list.", err));
            notifyInclusionListsSubscribers(signedInclusionList);
          }
        });
  }

  // TODO EIP7805 we could use different inclusion list pools (pending, future)
  public SafeFuture<InclusionListImportResult> onInclusionList(
      final SignedInclusionList signedInclusionList) {
    return forkChoice
        .onInclusionList(signedInclusionList)
        .thenApply(
            result -> {
              if (result.isSuccessful()) {
                LOG.trace(
                    "Processed inclusion list {} successfully", signedInclusionList::hashTreeRoot);
                add(signedInclusionList);
              } else {
                LOG.trace("Ignoring inclusion list {}", signedInclusionList::hashTreeRoot);
              }
              return result;
            });
  }

  /**
   * Inclusion lists for an {@code InclusionListsByIndices} request. {@code committeeIndices} are
   * positions in {@code get_inclusion_list_committee(state, slot)}, where {@code state} is the
   * state of the {@code dependentRoot} block processed up to {@code slot}. Lists from validators
   * that equivocated for this slot and dependent root are not returned.
   */
  public SafeFuture<List<SignedInclusionList>> getInclusionLists(
      final UInt64 slot, final Bytes32 dependentRoot, final SszBitvector committeeIndices) {
    final Optional<InclusionListUtil> maybeInclusionListUtil =
        spec.atSlot(slot).getInclusionListUtil();
    // The slot and dependent root come from the peer, so only regenerate the dependent state when
    // there is something to serve: lists are only retained for recent slots, which bounds the
    // slot processing a request can cause.
    if (maybeInclusionListUtil.isEmpty()
        || !isServableSlot(slot)
        || !hasInclusionListsFor(slot, dependentRoot)
        || !isDependentBlockAtOrBefore(dependentRoot, slot)) {
      return SafeFuture.completedFuture(List.of());
    }
    return recentChainData
        .retrieveBlockState(new SlotAndBlockRoot(slot, dependentRoot))
        .thenApply(
            maybeState ->
                maybeState
                    .map(
                        state -> {
                          final IntList committee =
                              maybeInclusionListUtil.get().getInclusionListCommittee(state, slot);
                          final Set<UInt64> requestedValidatorIndices =
                              committeeIndices
                                  .getAllSetBits()
                                  .intStream()
                                  .filter(position -> position < committee.size())
                                  .mapToObj(position -> UInt64.valueOf(committee.getInt(position)))
                                  .collect(Collectors.toCollection(LinkedHashSet::new));
                          return getInclusionLists(slot, dependentRoot, requestedValidatorIndices);
                        })
                    .orElse(List.of()));
  }

  /** Inclusion lists are only valid on gossip up to one slot ahead, within clock disparity. */
  private boolean isServableSlot(final UInt64 slot) {
    return recentChainData
        .getCurrentSlot()
        .map(currentSlot -> slot.isLessThanOrEqualTo(currentSlot.increment()))
        .orElse(false);
  }

  private boolean hasInclusionListsFor(final UInt64 slot, final Bytes32 dependentRoot) {
    final Map<UInt64, List<SignedInclusionList>> inclusionListsForSlot =
        slotToInclusionListsByValidatorIndex.get(slot);
    return inclusionListsForSlot != null
        && inclusionListsForSlot.values().stream()
            .flatMap(List::stream)
            .anyMatch(
                signedInclusionList ->
                    signedInclusionList.getMessage().getDependentRoot().equals(dependentRoot));
  }

  /** A block after {@code slot} can't be the dependent root for inclusion lists at that slot. */
  private boolean isDependentBlockAtOrBefore(final Bytes32 dependentRoot, final UInt64 slot) {
    return recentChainData
        .getSlotForBlockRoot(dependentRoot)
        .map(dependentSlot -> dependentSlot.isLessThanOrEqualTo(slot))
        .orElse(false);
  }

  private List<SignedInclusionList> getInclusionLists(
      final UInt64 slot, final Bytes32 dependentRoot, final Set<UInt64> validatorIndices) {
    final Map<UInt64, List<SignedInclusionList>> inclusionListsForSlot =
        slotToInclusionListsByValidatorIndex.getOrDefault(slot, new ConcurrentHashMap<>());
    return validatorIndices.stream()
        .flatMap(
            validatorIndex -> {
              final List<SignedInclusionList> matchingInclusionLists =
                  inclusionListsForSlot.getOrDefault(validatorIndex, List.of()).stream()
                      .filter(
                          signedInclusionList ->
                              signedInclusionList
                                  .getMessage()
                                  .getDependentRoot()
                                  .equals(dependentRoot))
                      .toList();
              if (matchingInclusionLists.isEmpty()) {
                return Stream.empty();
              }
              final SignedInclusionList firstInclusionList = matchingInclusionLists.getFirst();
              // Distinct messages from the same validator for this slot and root are equivocations.
              return matchingInclusionLists.stream()
                      .allMatch(
                          inclusionList ->
                              inclusionList.getMessage().equals(firstInclusionList.getMessage()))
                  ? Stream.of(firstInclusionList)
                  : Stream.empty();
            })
        .toList();
  }

  public SafeFuture<Optional<SszBitvector>> getInclusionListBits(
      final UInt64 proposalSlot, final Bytes32 parentRoot) {
    return spec.atSlot(proposalSlot)
        .getInclusionListUtil()
        .map(
            inclusionListUtil -> {
              final UInt64 inclusionListSlot = proposalSlot.decrement();
              return recentChainData
                  .retrieveBlockState(new SlotAndBlockRoot(proposalSlot, parentRoot))
                  .thenApply(
                      maybeState ->
                          maybeState.map(
                              state -> {
                                final Bytes32 dependentRoot =
                                    recentChainData
                                        .getForkChoiceStrategy()
                                        .flatMap(
                                            strategy ->
                                                ShufflingDependentRootUtil
                                                    .getShufflingDependentRoot(
                                                        spec,
                                                        strategy,
                                                        parentRoot,
                                                        inclusionListSlot))
                                        .orElseGet(
                                            () ->
                                                ShufflingDependentRootUtil
                                                    .getShufflingDependentRoot(
                                                        spec, state, inclusionListSlot));
                                return inclusionListStore.getInclusionListBits(
                                    inclusionListUtil.getInclusionListCommittee(
                                        state, inclusionListSlot),
                                    new SlotAndBlockRoot(inclusionListSlot, dependentRoot),
                                    false);
                              }));
            })
        .orElseGet(() -> SafeFuture.completedFuture(Optional.empty()));
  }

  public void subscribeToInclusionLists(final SignedInclusionListListener listener) {
    inclusionListsSubscribers.subscribe(listener);
  }

  private void notifyInclusionListsSubscribers(final SignedInclusionList signedInclusionList) {
    inclusionListsSubscribers.forEach(
        signedInclusionListListener -> signedInclusionListListener.accept(signedInclusionList));
  }
}
