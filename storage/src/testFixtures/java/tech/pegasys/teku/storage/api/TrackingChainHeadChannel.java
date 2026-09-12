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

package tech.pegasys.teku.storage.api;

import com.google.common.base.MoreObjects;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;

public class TrackingChainHeadChannel implements ChainHeadChannel {
  private final List<ReorgEvent> reorgEvents = new ArrayList<>();
  private final List<HeadEvent> headEvents = new ArrayList<>();

  @Override
  public void chainHeadUpdated(
      final UInt64 slot,
      final Bytes32 stateRoot,
      final Bytes32 bestBlockRoot,
      final boolean epochTransition,
      final boolean executionOptimistic,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Optional<ForkChoicePayloadStatus> payloadStatus,
      final Optional<ReorgContext> optionalReorgContext) {
    headEvents.add(
        new HeadEvent(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            payloadStatus));
    optionalReorgContext.ifPresent(
        context ->
            reorgEvents.add(
                new ReorgEvent(
                    bestBlockRoot,
                    slot,
                    stateRoot,
                    context.getOldBestBlockRoot(),
                    context.getOldBestStateRoot(),
                    context.getCommonAncestorSlot())));
  }

  public List<HeadEvent> getHeadEvents() {
    return headEvents;
  }

  public List<ReorgEvent> getReorgEvents() {
    return reorgEvents;
  }

  public static class HeadEvent {
    private final UInt64 slot;
    private final Bytes32 stateRoot;
    private final Bytes32 bestBlockRoot;
    private final boolean epochTransition;
    private final boolean executionOptimistic;
    private final Bytes32 previousDutyDependentRoot;
    private final Bytes32 currentDutyDependentRoot;
    private final Optional<ForkChoicePayloadStatus> payloadStatus;

    public HeadEvent(
        final UInt64 slot,
        final Bytes32 stateRoot,
        final Bytes32 bestBlockRoot,
        final boolean epochTransition,
        final boolean executionOptimistic,
        final Bytes32 previousDutyDependentRoot,
        final Bytes32 currentDutyDependentRoot) {
      this(
          slot,
          stateRoot,
          bestBlockRoot,
          epochTransition,
          executionOptimistic,
          previousDutyDependentRoot,
          currentDutyDependentRoot,
          Optional.empty());
    }

    public HeadEvent(
        final UInt64 slot,
        final Bytes32 stateRoot,
        final Bytes32 bestBlockRoot,
        final boolean epochTransition,
        final boolean executionOptimistic,
        final Bytes32 previousDutyDependentRoot,
        final Bytes32 currentDutyDependentRoot,
        final Optional<ForkChoicePayloadStatus> payloadStatus) {
      this.slot = slot;
      this.stateRoot = stateRoot;
      this.bestBlockRoot = bestBlockRoot;
      this.epochTransition = epochTransition;
      this.executionOptimistic = executionOptimistic;
      this.previousDutyDependentRoot = previousDutyDependentRoot;
      this.currentDutyDependentRoot = currentDutyDependentRoot;
      this.payloadStatus = payloadStatus;
    }

    public UInt64 getSlot() {
      return slot;
    }

    public Bytes32 getBestBlockRoot() {
      return bestBlockRoot;
    }

    public Optional<ForkChoicePayloadStatus> getPayloadStatus() {
      return payloadStatus;
    }

    public Bytes32 getPreviousDutyDependentRoot() {
      return previousDutyDependentRoot;
    }

    public Bytes32 getCurrentDutyDependentRoot() {
      return currentDutyDependentRoot;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final HeadEvent headEvent = (HeadEvent) o;
      return epochTransition == headEvent.epochTransition
          && executionOptimistic == headEvent.executionOptimistic
          && Objects.equals(slot, headEvent.slot)
          && Objects.equals(stateRoot, headEvent.stateRoot)
          && Objects.equals(bestBlockRoot, headEvent.bestBlockRoot)
          && Objects.equals(previousDutyDependentRoot, headEvent.previousDutyDependentRoot)
          && Objects.equals(currentDutyDependentRoot, headEvent.currentDutyDependentRoot)
          && Objects.equals(payloadStatus, headEvent.payloadStatus);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          slot,
          stateRoot,
          bestBlockRoot,
          epochTransition,
          executionOptimistic,
          previousDutyDependentRoot,
          currentDutyDependentRoot,
          payloadStatus);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("slot", slot)
          .add("stateRoot", stateRoot)
          .add("bestBlockRoot", bestBlockRoot)
          .add("epochTransition", epochTransition)
          .add("executionOptimistic", executionOptimistic)
          .add("previousDutyDependentRoot", previousDutyDependentRoot)
          .add("currentDutyDependentRoot", currentDutyDependentRoot)
          .add("payloadStatus", payloadStatus)
          .toString();
    }
  }

  public static class ReorgEvent {
    private final Bytes32 newBestBlockRoot;
    private final UInt64 bestSlot;
    private final Bytes32 newBestStateRoot;
    private final Bytes32 oldBestBlockRoot;
    private final Bytes32 oldBestStateRoot;
    private final UInt64 commonAncestorSlot;

    public ReorgEvent(
        final Bytes32 newBestBlockRoot,
        final UInt64 bestSlot,
        final Bytes32 newBestStateRoot,
        final Bytes32 oldBestBlockRoot,
        final Bytes32 oldBestStateRoot,
        final UInt64 commonAncestorSlot) {
      this.newBestBlockRoot = newBestBlockRoot;
      this.bestSlot = bestSlot;
      this.newBestStateRoot = newBestStateRoot;
      this.oldBestBlockRoot = oldBestBlockRoot;
      this.oldBestStateRoot = oldBestStateRoot;
      this.commonAncestorSlot = commonAncestorSlot;
    }

    public Bytes32 getNewBestBlockRoot() {
      return newBestBlockRoot;
    }

    public UInt64 getBestSlot() {
      return bestSlot;
    }

    public Bytes32 getOldBestBlockRoot() {
      return oldBestBlockRoot;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final ReorgEvent that = (ReorgEvent) o;
      return Objects.equals(newBestBlockRoot, that.newBestBlockRoot)
          && Objects.equals(bestSlot, that.bestSlot)
          && Objects.equals(newBestStateRoot, that.newBestStateRoot)
          && Objects.equals(oldBestBlockRoot, that.oldBestBlockRoot)
          && Objects.equals(oldBestStateRoot, that.oldBestStateRoot)
          && Objects.equals(commonAncestorSlot, that.commonAncestorSlot);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          newBestBlockRoot,
          bestSlot,
          newBestStateRoot,
          oldBestBlockRoot,
          oldBestStateRoot,
          commonAncestorSlot);
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("newBestBlockRoot", newBestBlockRoot)
          .add("bestSlot", bestSlot)
          .add("newBestStateRoot", newBestStateRoot)
          .add("oldBestBlockRoot", oldBestBlockRoot)
          .add("oldBestStateRoot", oldBestStateRoot)
          .add("commonAncestorSlot", commonAncestorSlot)
          .toString();
    }
  }
}
