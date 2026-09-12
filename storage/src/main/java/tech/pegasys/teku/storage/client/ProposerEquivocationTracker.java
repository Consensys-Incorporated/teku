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

package tech.pegasys.teku.storage.client;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.collections.LimitedMap;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil.BlockTimeliness;

/** Bounded runtime evidence for proposer equivocations confirmed by gossip validation. */
public class ProposerEquivocationTracker {
  private static final int MAX_REPRESENTATIVE_ROOTS = 2;

  private final Map<SlotAndProposer, EquivocationEvidence> evidenceBySlotAndProposer;
  private final UInt64 slotsToRetain;
  private final Function<Bytes32, Optional<BlockTimeliness>> blockTimelinessProvider;
  private UInt64 highestSlot = UInt64.ZERO;

  public ProposerEquivocationTracker(
      final int maxEntries,
      final int slotsToRetain,
      final Function<Bytes32, Optional<BlockTimeliness>> blockTimelinessProvider) {
    this.evidenceBySlotAndProposer = LimitedMap.createNonSynchronized(maxEntries);
    this.slotsToRetain = UInt64.valueOf(slotsToRetain);
    this.blockTimelinessProvider = blockTimelinessProvider;
  }

  public synchronized void onBlockValidated(final SignedBeaconBlock block) {
    highestSlot = highestSlot.max(block.getSlot());
    final UInt64 earliestRetainedSlot = highestSlot.minusMinZero(slotsToRetain);
    evidenceBySlotAndProposer.keySet().removeIf(key -> key.slot().isLessThan(earliestRetainedSlot));

    final Bytes32 blockRoot = block.getRoot();
    final boolean isPtcTimely =
        blockTimelinessProvider.apply(blockRoot).filter(BlockTimeliness::isTimelyPtc).isPresent();
    evidenceBySlotAndProposer
        .computeIfAbsent(SlotAndProposer.fromBlock(block), __ -> new EquivocationEvidence())
        .add(blockRoot, isPtcTimely);
  }

  public synchronized boolean isProposerEquivocation(
      final UInt64 slot, final UInt64 proposerIndex, final Bytes32 blockRoot) {
    return Optional.ofNullable(
            evidenceBySlotAndProposer.get(new SlotAndProposer(slot, proposerIndex)))
        .map(evidence -> evidence.containsDifferentRoot(blockRoot))
        .orElse(false);
  }

  public synchronized boolean isPtcTimelyProposerEquivocation(
      final UInt64 slot, final UInt64 proposerIndex, final Bytes32 blockRoot) {
    return Optional.ofNullable(
            evidenceBySlotAndProposer.get(new SlotAndProposer(slot, proposerIndex)))
        .map(evidence -> evidence.containsDifferentTimelyRoot(blockRoot))
        .orElse(false);
  }

  private static class EquivocationEvidence {
    private final Set<Bytes32> representativeRoots = new LinkedHashSet<>();
    private final Set<Bytes32> representativeTimelyRoots = new LinkedHashSet<>();

    private void add(final Bytes32 blockRoot, final boolean isPtcTimely) {
      addRepresentative(representativeRoots, blockRoot);
      if (isPtcTimely) {
        addRepresentative(representativeTimelyRoots, blockRoot);
      }
    }

    private void addRepresentative(final Set<Bytes32> roots, final Bytes32 blockRoot) {
      if (roots.size() < MAX_REPRESENTATIVE_ROOTS || roots.contains(blockRoot)) {
        roots.add(blockRoot);
      }
    }

    private boolean containsDifferentRoot(final Bytes32 blockRoot) {
      return representativeRoots.stream().anyMatch(root -> !root.equals(blockRoot));
    }

    private boolean containsDifferentTimelyRoot(final Bytes32 blockRoot) {
      return representativeTimelyRoots.stream().anyMatch(root -> !root.equals(blockRoot));
    }
  }

  private record SlotAndProposer(UInt64 slot, UInt64 proposerIndex) {
    private static SlotAndProposer fromBlock(final SignedBeaconBlock block) {
      return new SlotAndProposer(block.getSlot(), block.getProposerIndex());
    }
  }
}
