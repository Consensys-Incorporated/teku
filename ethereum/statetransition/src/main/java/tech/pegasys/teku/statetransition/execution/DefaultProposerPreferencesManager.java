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

package tech.pegasys.teku.statetransition.execution;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ProposerPreferencesGossipValidator;

public class DefaultProposerPreferencesManager
    implements ProposerPreferencesManager, SlotEventsChannel, ReceivedBlockEventsChannel {

  private static final Logger LOG = LogManager.getLogger();

  private final ProposerPreferencesGossipValidator proposerPreferencesGossipValidator;
  private final PendingPool<PendingProposerPreferences> pendingProposerPreferences;
  private final ConcurrentNavigableMap<UInt64, Map<Bytes32, ProposerPreferences>>
      acceptedProposerPreferences = new ConcurrentSkipListMap<>();
  private final Subscribers<OperationAddedSubscriber<SignedProposerPreferences>> subscribers =
      Subscribers.create(true);

  public DefaultProposerPreferencesManager(
      final ProposerPreferencesGossipValidator proposerPreferencesGossipValidator,
      final PendingPool<PendingProposerPreferences> pendingProposerPreferences) {
    this.proposerPreferencesGossipValidator = proposerPreferencesGossipValidator;
    this.pendingProposerPreferences = pendingProposerPreferences;
  }

  @Override
  public SafeFuture<InternalValidationResult> addLocal(
      final SignedProposerPreferences signedProposerPreferences) {
    return validateAndAdd(signedProposerPreferences, false);
  }

  @Override
  public SafeFuture<InternalValidationResult> addRemote(
      final SignedProposerPreferences signedProposerPreferences) {
    return validateAndAdd(signedProposerPreferences, true);
  }

  @Override
  public Optional<ProposerPreferences> getProposerPreferences(
      final UInt64 slot, final Bytes32 dependentRoot) {
    return Optional.ofNullable(acceptedProposerPreferences.get(slot))
        .map(preferencesByDependentRoot -> preferencesByDependentRoot.get(dependentRoot));
  }

  @Override
  public Collection<ProposerPreferences> getProposerPreferencesForSlot(final UInt64 slot) {
    return Optional.ofNullable(acceptedProposerPreferences.get(slot))
        .map(preferencesByDependentRoot -> List.copyOf(preferencesByDependentRoot.values()))
        .orElse(List.of());
  }

  @Override
  public void subscribeOperationAdded(
      final OperationAddedSubscriber<SignedProposerPreferences> subscriber) {
    subscribers.subscribe(subscriber);
  }

  @Override
  public void onSlot(final UInt64 slot) {
    acceptedProposerPreferences.headMap(slot, false).clear();
    pendingProposerPreferences.onSlot(slot);
    pendingProposerPreferences.removeItemsMatching(
        pendingPreferences ->
            pendingPreferences
                .signedProposerPreferences()
                .getMessage()
                .getProposalSlot()
                .isLessThan(slot));
    final List<PendingProposerPreferences> preferencesToRetry =
        pendingProposerPreferences.removeItemsMatching(__ -> true);
    retryPendingPreferences(preferencesToRetry);
  }

  @Override
  public void onBlockValidated(final SignedBeaconBlock block) {}

  @Override
  public void onBlockImported(final SignedBeaconBlock block, final boolean executionOptimistic) {
    retryPendingPreferences(pendingProposerPreferences.removeItemsDependingOn(block.getRoot()));
  }

  private SafeFuture<InternalValidationResult> validateAndAdd(
      final SignedProposerPreferences signedProposerPreferences, final boolean fromNetwork) {
    return proposerPreferencesGossipValidator
        .validate(signedProposerPreferences)
        .thenApply(
            result -> {
              processValidationResult(signedProposerPreferences, fromNetwork, result);
              return result;
            });
  }

  private void processValidationResult(
      final SignedProposerPreferences signedProposerPreferences,
      final boolean fromNetwork,
      final InternalValidationResult result) {
    switch (result.code()) {
      case ACCEPT -> {
        removePendingPreferences(signedProposerPreferences);
        final ProposerPreferences proposerPreferences = signedProposerPreferences.getMessage();
        acceptedProposerPreferences
            .computeIfAbsent(proposerPreferences.getProposalSlot(), __ -> new ConcurrentHashMap<>())
            .put(proposerPreferences.getDependentRoot(), proposerPreferences);
        subscribers.forEach(
            subscriber ->
                subscriber.onOperationAdded(signedProposerPreferences, result, fromNetwork));
      }
      case SAVE_FOR_FUTURE ->
          pendingProposerPreferences.add(
              new PendingProposerPreferences(signedProposerPreferences, fromNetwork));
      case REJECT, IGNORE -> removePendingPreferences(signedProposerPreferences);
    }
  }

  private void retryPendingPreferences(
      final Collection<PendingProposerPreferences> proposerPreferences) {
    proposerPreferences.forEach(
        pendingPreferences ->
            validateAndAdd(
                    pendingPreferences.signedProposerPreferences(),
                    pendingPreferences.fromNetwork())
                .finishError(LOG));
  }

  private void removePendingPreferences(final SignedProposerPreferences signedProposerPreferences) {
    // PendingPool identifies preferences by their hash tree root, so this removes the entry
    // regardless of its stored fromNetwork value.
    pendingProposerPreferences.remove(
        new PendingProposerPreferences(signedProposerPreferences, false));
  }
}
