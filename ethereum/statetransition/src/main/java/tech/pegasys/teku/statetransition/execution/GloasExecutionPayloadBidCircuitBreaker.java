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

package tech.pegasys.teku.statetransition.execution;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;

import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class GloasExecutionPayloadBidCircuitBreaker implements ExecutionPayloadBidCircuitBreaker {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final int faultInspectionWindow;
  private final int allowedFaults;
  private final int consecutiveAllowedFaults;
  private final Supplier<Optional<ReadOnlyForkChoiceStrategy>> forkChoiceStrategySupplier;
  private final Map<Bytes32, BlockPayloadStatus> blockPayloadStatusByRoot = new HashMap<>();
  private final Map<UInt64, BuilderCircuitBreakerStatus> builderStatusByIndex = new HashMap<>();
  private UInt64 latestObservedBlockSlot = UInt64.ZERO;

  public GloasExecutionPayloadBidCircuitBreaker(
      final Spec spec,
      final int faultInspectionWindow,
      final int allowedFaults,
      final int consecutiveAllowedFaults,
      final Supplier<Optional<ReadOnlyForkChoiceStrategy>> forkChoiceStrategySupplier) {
    checkArgument(
        faultInspectionWindow > allowedFaults,
        "FaultInspectionWindow must be greater than AllowedFaults");
    this.spec = spec;
    this.faultInspectionWindow = faultInspectionWindow;
    this.allowedFaults = allowedFaults;
    this.consecutiveAllowedFaults = consecutiveAllowedFaults;
    this.forkChoiceStrategySupplier = forkChoiceStrategySupplier;
  }

  @Override
  public synchronized boolean isEngaged(final Bytes32 parentRoot, final BeaconState state) {
    prune(state.getSlot());
    final Optional<ReadOnlyForkChoiceStrategy> maybeForkChoiceStrategy =
        forkChoiceStrategySupplier.get();
    if (maybeForkChoiceStrategy.isEmpty()) {
      LOG.debug(
          "Builder circuit breaker engaged because fork choice data is unavailable at slot {}",
          state.getSlot());
      return true;
    }

    if (inspectPayloadAvailability(maybeForkChoiceStrategy.get(), parentRoot, state)) {
      LOG.debug(
          "Builder circuit breaker engaged because fork choice data is incomplete for parent root {} at slot {}",
          parentRoot,
          state.getSlot());
      return true;
    }

    LOG.debug(
        "Builder circuit breaker has not engaged for parent root {} at slot {}",
        parentRoot,
        state.getSlot());
    return false;
  }

  @Override
  public synchronized boolean isBuilderAllowed(final UInt64 builderIndex, final BeaconState state) {
    prune(state.getSlot());
    return getCurrentBuilderStatus(builderIndex, state)
        .map(
            builderStatus -> {
              final boolean builderAllowed = !builderStatus.isBannedAt(state.getSlot());
              if (!builderAllowed) {
                LOG.debug(
                    "Rejecting builder index {} because it is banned until slot {} at slot {}",
                    builderIndex,
                    builderStatus.banUntilSlot(),
                    state.getSlot());
              }
              return builderAllowed;
            })
        .orElse(true);
  }

  @Override
  public synchronized void observeImportedBlock(final SignedBeaconBlock block) {
    block
        .getMessage()
        .getBody()
        .getOptionalSignedExecutionPayloadBid()
        .ifPresent(
            signedBid ->
                recordBlockBuilder(
                    block.getRoot(), block.getSlot(), signedBid.getMessage().getBuilderIndex()));
  }

  @VisibleForTesting
  void recordBlockBuilder(final Bytes32 blockRoot, final UInt64 slot, final UInt64 builderIndex) {
    if (slot.isGreaterThan(latestObservedBlockSlot)) {
      latestObservedBlockSlot = slot;
    }
    blockPayloadStatusByRoot.put(blockRoot, new BlockPayloadStatus(slot, builderIndex));
    prune(latestObservedBlockSlot);
  }

  @VisibleForTesting
  int getTrackedBlockPayloadStatusCount() {
    return blockPayloadStatusByRoot.size();
  }

  private boolean inspectPayloadAvailability(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final Bytes32 parentRoot,
      final BeaconState state) {
    if (!forkChoiceStrategy.contains(parentRoot)) {
      return true;
    }

    final List<ObservedBlock> observedBlocks = new ArrayList<>();
    final Set<Bytes32> inspectedBlockRoots = new HashSet<>();

    final UInt64 slot = state.getSlot();
    final UInt64 firstSlotOfInspectionWindow = slot.minusMinZero(faultInspectionWindow);
    UInt64 currentSlot = slot.minusMinZero(1);
    while (currentSlot.isGreaterThanOrEqualTo(firstSlotOfInspectionWindow)) {
      final Optional<Bytes32> maybeAncestorRoot =
          forkChoiceStrategy.getAncestor(parentRoot, currentSlot);
      if (maybeAncestorRoot.isPresent() && inspectedBlockRoots.add(maybeAncestorRoot.get())) {
        final Bytes32 ancestorRoot = maybeAncestorRoot.get();
        final Optional<UInt64> maybeAncestorSlot = forkChoiceStrategy.blockSlot(ancestorRoot);
        if (maybeAncestorSlot.isEmpty()) {
          return true;
        }
        if (maybeAncestorSlot.get().isGreaterThanOrEqualTo(firstSlotOfInspectionWindow)) {
          getBlockPayloadStatus(ancestorRoot)
              .ifPresent(
                  blockPayloadStatus ->
                      observedBlocks.add(new ObservedBlock(ancestorRoot, blockPayloadStatus)));
        }
      }
      if (currentSlot.isZero()) {
        break;
      }
      currentSlot = currentSlot.minusMinZero(1);
    }

    final Map<UInt64, Integer> consecutiveUnavailablePayloadsByBuilderIndex = new HashMap<>();
    observedBlocks.stream()
        .sorted(Comparator.comparing(ObservedBlock::slot))
        .forEachOrdered(
            observedBlock ->
                recordObservedPayload(
                    forkChoiceStrategy,
                    state,
                    observedBlock,
                    consecutiveUnavailablePayloadsByBuilderIndex));

    return false;
  }

  private void recordObservedPayload(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy,
      final BeaconState state,
      final ObservedBlock observedBlock,
      final Map<UInt64, Integer> consecutiveUnavailablePayloadsByBuilderIndex) {
    if (hasAvailablePayload(forkChoiceStrategy, observedBlock.blockRoot())) {
      recordAvailablePayload(observedBlock, state);
      consecutiveUnavailablePayloadsByBuilderIndex.remove(observedBlock.builderIndex());
    } else {
      final int consecutiveUnavailablePayloads =
          consecutiveUnavailablePayloadsByBuilderIndex.merge(
              observedBlock.builderIndex(), 1, Integer::sum);
      recordUnavailablePayload(observedBlock, state, consecutiveUnavailablePayloads);
    }
  }

  private boolean hasAvailablePayload(
      final ReadOnlyForkChoiceStrategy forkChoiceStrategy, final Bytes32 ancestorRoot) {
    return forkChoiceStrategy.getBlockData(ancestorRoot, PAYLOAD_STATUS_FULL).isPresent();
  }

  private void recordAvailablePayload(final ObservedBlock observedBlock, final BeaconState state) {
    final boolean payloadFaultWasRecorded =
        observedBlock.blockPayloadStatus().markPayloadAvailable();
    if (payloadFaultWasRecorded) {
      getCurrentBuilderStatus(observedBlock.builderIndex(), state)
          .ifPresent(builderStatus -> builderStatus.retractFault(observedBlock.toPayloadFault()));
    }
  }

  private void recordUnavailablePayload(
      final ObservedBlock observedBlock,
      final BeaconState state,
      final int consecutiveUnavailablePayloads) {
    final BlockPayloadStatus blockPayloadStatus = observedBlock.blockPayloadStatus();
    final Optional<BLSPublicKey> maybeBuilderPubKey =
        spec.getBuilderPubKey(state, observedBlock.builderIndex());
    if (maybeBuilderPubKey.isEmpty()) {
      return;
    }

    if (!blockPayloadStatus.markPayloadUnavailable()) {
      return;
    }

    final PayloadFault payloadFault = observedBlock.toPayloadFault();

    final BuilderCircuitBreakerStatus builderStatus =
        builderStatusByIndex.compute(
            observedBlock.builderIndex(),
            (__, existingStatus) -> {
              // Builder indices are reusable. This check resets the builder status if the public
              // key associated to the builder index has changed
              if (existingStatus == null
                  || !existingStatus.isForBuilder(maybeBuilderPubKey.get())) {
                return new BuilderCircuitBreakerStatus(maybeBuilderPubKey.get());
              }
              return existingStatus;
            });
    builderStatus.recordFault(payloadFault);

    if (builderStatus.payloadFaultCount() > allowedFaults
        || consecutiveUnavailablePayloads > consecutiveAllowedFaults) {
      final UInt64 previousBanUntilSlot = builderStatus.banUntilSlot();
      builderStatus.banUntilSlot(state.getSlot().plus(faultInspectionWindow));
      LOG.debug(
          "Banning builder index {} until slot {} at slot {} after {} unavailable payloads in window and {} consecutive unavailable payloads. Previous ban until slot {}",
          observedBlock.builderIndex(),
          builderStatus.banUntilSlot(),
          state.getSlot(),
          builderStatus.payloadFaultCount(),
          consecutiveUnavailablePayloads,
          previousBanUntilSlot);
    }
  }

  private Optional<BuilderCircuitBreakerStatus> getCurrentBuilderStatus(
      final UInt64 builderIndex, final BeaconState state) {
    final BuilderCircuitBreakerStatus builderStatus = builderStatusByIndex.get(builderIndex);
    if (builderStatus == null) {
      return Optional.empty();
    }

    final Optional<BLSPublicKey> maybeCurrentBuilderPubKey =
        spec.getBuilderPubKey(state, builderIndex);
    if (maybeCurrentBuilderPubKey.isEmpty()
        || !builderStatus.isForBuilder(maybeCurrentBuilderPubKey.get())) {
      LOG.debug(
          "Clearing builder circuit breaker status for builder index {} at slot {} because the index is no longer associated with the tracked builder public key",
          builderIndex,
          state.getSlot());
      builderStatusByIndex.remove(builderIndex);
      return Optional.empty();
    }

    return Optional.of(builderStatus);
  }

  private Optional<BlockPayloadStatus> getBlockPayloadStatus(final Bytes32 blockRoot) {
    return Optional.ofNullable(blockPayloadStatusByRoot.get(blockRoot));
  }

  private void prune(final UInt64 currentSlot) {
    final UInt64 firstSlotOfInspectionWindow = currentSlot.minusMinZero(faultInspectionWindow);
    blockPayloadStatusByRoot
        .entrySet()
        .removeIf(entry -> entry.getValue().slot().isLessThan(firstSlotOfInspectionWindow));

    builderStatusByIndex
        .entrySet()
        .removeIf(
            entry -> {
              final BuilderCircuitBreakerStatus builderStatus = entry.getValue();
              builderStatus.pruneFaultsBefore(firstSlotOfInspectionWindow);
              builderStatus
                  .clearBanIfExpired(currentSlot)
                  .ifPresent(
                      expiredBanUntilSlot ->
                          LOG.debug(
                              "Unbanning builder index {} at slot {}. Ban expired at slot {}",
                              entry.getKey(),
                              currentSlot,
                              expiredBanUntilSlot));
              return builderStatus.canBeRemoved();
            });
  }

  private record ObservedBlock(Bytes32 blockRoot, BlockPayloadStatus blockPayloadStatus) {
    private UInt64 slot() {
      return blockPayloadStatus.slot();
    }

    private UInt64 builderIndex() {
      return blockPayloadStatus.builderIndex();
    }

    private PayloadFault toPayloadFault() {
      return new PayloadFault(blockRoot, slot(), builderIndex());
    }
  }

  private record PayloadFault(Bytes32 blockRoot, UInt64 slot, UInt64 builderIndex) {}

  private static class BlockPayloadStatus {
    private final UInt64 slot;
    private final UInt64 builderIndex;
    private boolean payloadFaultRecorded;

    private BlockPayloadStatus(final UInt64 slot, final UInt64 builderIndex) {
      this.slot = slot;
      this.builderIndex = builderIndex;
    }

    private UInt64 slot() {
      return slot;
    }

    private UInt64 builderIndex() {
      return builderIndex;
    }

    private boolean markPayloadUnavailable() {
      if (payloadFaultRecorded) {
        return false;
      }
      payloadFaultRecorded = true;
      return true;
    }

    private boolean markPayloadAvailable() {
      final boolean wasUnavailable = payloadFaultRecorded;
      payloadFaultRecorded = false;
      return wasUnavailable;
    }
  }

  private static class BuilderCircuitBreakerStatus {
    private final BLSPublicKey builderPubKey;
    private final Deque<PayloadFault> payloadFaults = new ArrayDeque<>();
    private UInt64 banUntilSlot = UInt64.ZERO;

    private BuilderCircuitBreakerStatus(final BLSPublicKey builderPubKey) {
      this.builderPubKey = builderPubKey;
    }

    private boolean isForBuilder(final BLSPublicKey builderPubKey) {
      return this.builderPubKey.equals(builderPubKey);
    }

    private void recordFault(final PayloadFault payloadFault) {
      payloadFaults.addLast(payloadFault);
    }

    private void retractFault(final PayloadFault payloadFault) {
      payloadFaults.remove(payloadFault);
    }

    private void pruneFaultsBefore(final UInt64 firstSlotOfInspectionWindow) {
      payloadFaults.removeIf(
          payloadFault -> payloadFault.slot().isLessThan(firstSlotOfInspectionWindow));
    }

    private int payloadFaultCount() {
      return payloadFaults.size();
    }

    private UInt64 banUntilSlot() {
      return banUntilSlot;
    }

    private boolean isBannedAt(final UInt64 slot) {
      return banUntilSlot.isGreaterThan(slot);
    }

    private void banUntilSlot(final UInt64 banUntilSlot) {
      this.banUntilSlot = banUntilSlot;
    }

    private Optional<UInt64> clearBanIfExpired(final UInt64 currentSlot) {
      if (banUntilSlot.isZero() || banUntilSlot.isGreaterThan(currentSlot)) {
        return Optional.empty();
      }
      final UInt64 expiredBanUntilSlot = banUntilSlot;
      banUntilSlot = UInt64.ZERO;
      return Optional.of(expiredBanUntilSlot);
    }

    private boolean canBeRemoved() {
      return payloadFaults.isEmpty() && banUntilSlot.isZero();
    }
  }
}
