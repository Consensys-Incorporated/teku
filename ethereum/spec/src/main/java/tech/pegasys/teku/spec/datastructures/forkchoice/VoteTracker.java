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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import java.util.Objects;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

/**
 * Teku representation of fork-choice latest-message state.
 *
 * <p>Gloas extends the spec's `LatestMessage` helper to track `slot` and a forkchoice hint for
 * whether a later-slot attestation prefers the FULL node. The corresponding Python definition lives
 * in:
 * https://github.com/ethereum/consensus-specs/blob/master/specs/gloas/fork-choice.md#modified-latestmessage
 */
public class VoteTracker {

  public static final VoteTracker DEFAULT = new VoteTracker(Bytes32.ZERO, Bytes32.ZERO);

  private final Bytes32 currentRoot;
  private final Bytes32 nextRoot;
  private final boolean nextEquivocating;
  private final boolean currentEquivocating;

  // Gloas: LatestMessage uses slot instead of epoch, and tracks a FULL-node vote hint
  private final UInt64 nextSlot;
  private final boolean nextFullPayloadHint;
  private final UInt64 currentSlot;
  private final boolean currentFullPayloadHint;

  @VisibleForTesting
  public VoteTracker(final Bytes32 currentRoot, final Bytes32 nextRoot) {
    this(currentRoot, nextRoot, false, false);
  }

  @VisibleForTesting
  public VoteTracker(
      final Bytes32 currentRoot,
      final Bytes32 nextRoot,
      final boolean nextEquivocating,
      final boolean currentEquivocating) {
    this(
        currentRoot,
        nextRoot,
        nextEquivocating,
        currentEquivocating,
        UInt64.ZERO,
        false,
        UInt64.ZERO,
        false);
  }

  public VoteTracker(
      final Bytes32 currentRoot,
      final Bytes32 nextRoot,
      final boolean nextEquivocating,
      final boolean currentEquivocating,
      final UInt64 nextSlot,
      final boolean nextFullPayloadHint,
      final UInt64 currentSlot,
      final boolean currentFullPayloadHint) {
    this.currentRoot = currentRoot;
    this.nextRoot = nextRoot;
    this.nextEquivocating = nextEquivocating;
    this.currentEquivocating = currentEquivocating;
    this.nextSlot = nextSlot;
    this.nextFullPayloadHint = nextFullPayloadHint;
    this.currentSlot = currentSlot;
    this.currentFullPayloadHint = currentFullPayloadHint;
  }

  public Bytes32 getCurrentRoot() {
    return currentRoot;
  }

  public Bytes32 getNextRoot() {
    return nextRoot;
  }

  public UInt64 getNextSlot() {
    return nextSlot;
  }

  public boolean isNextFullPayloadHint() {
    return nextFullPayloadHint;
  }

  public UInt64 getCurrentSlot() {
    return currentSlot;
  }

  public boolean isCurrentFullPayloadHint() {
    return currentFullPayloadHint;
  }

  public boolean isNextEquivocating() {
    return nextEquivocating;
  }

  public boolean isCurrentEquivocating() {
    return currentEquivocating;
  }

  public boolean isEquivocating() {
    return nextEquivocating || currentEquivocating;
  }

  public VoteTracker createNextEquivocating() {
    return new VoteTracker(
        currentRoot,
        nextRoot,
        true,
        false,
        nextSlot,
        nextFullPayloadHint,
        currentSlot,
        currentFullPayloadHint);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VoteTracker that = (VoteTracker) o;
    return nextEquivocating == that.nextEquivocating
        && currentEquivocating == that.currentEquivocating
        && nextFullPayloadHint == that.nextFullPayloadHint
        && currentFullPayloadHint == that.currentFullPayloadHint
        && Objects.equals(currentRoot, that.currentRoot)
        && Objects.equals(nextRoot, that.nextRoot)
        && Objects.equals(nextSlot, that.nextSlot)
        && Objects.equals(currentSlot, that.currentSlot);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        currentRoot,
        nextRoot,
        nextEquivocating,
        currentEquivocating,
        nextSlot,
        nextFullPayloadHint,
        currentSlot,
        currentFullPayloadHint);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("currentRoot", currentRoot)
        .add("nextRoot", nextRoot)
        .add("nextEquivocating", nextEquivocating)
        .add("currentEquivocating", currentEquivocating)
        .add("nextSlot", nextSlot)
        .add("nextFullPayloadHint", nextFullPayloadHint)
        .add("currentSlot", currentSlot)
        .add("currentFullPayloadHint", currentFullPayloadHint)
        .toString();
  }
}
