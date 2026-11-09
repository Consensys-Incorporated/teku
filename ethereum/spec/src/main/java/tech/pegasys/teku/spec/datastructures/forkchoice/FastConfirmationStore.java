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

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;

public record FastConfirmationStore(
    ReadOnlyStore store,
    Bytes32 confirmedRoot,
    Checkpoint previousEpochObservedJustifiedCheckpoint,
    Checkpoint currentEpochObservedJustifiedCheckpoint,
    Checkpoint previousEpochGreatestUnrealizedCheckpoint,
    Bytes32 previousSlotHead,
    Bytes32 currentSlotHead) {

  public static FastConfirmationStore create(final ReadOnlyStore store) {
    final Checkpoint finalizedCheckpoint = store.getFinalizedCheckpoint();
    final Bytes32 finalizedRoot = finalizedCheckpoint.getRoot();
    return new FastConfirmationStore(
        store,
        finalizedRoot,
        finalizedCheckpoint,
        finalizedCheckpoint,
        finalizedCheckpoint,
        finalizedRoot,
        finalizedRoot);
  }

  public FastConfirmationStore withConfirmedRoot(final Bytes32 confirmedRoot) {
    return new FastConfirmationStore(
        store,
        confirmedRoot,
        previousEpochObservedJustifiedCheckpoint,
        currentEpochObservedJustifiedCheckpoint,
        previousEpochGreatestUnrealizedCheckpoint,
        previousSlotHead,
        currentSlotHead);
  }

  public FastConfirmationStore withPreviousEpochObservedJustifiedCheckpoint(
      final Checkpoint previousEpochObservedJustifiedCheckpoint) {
    return new FastConfirmationStore(
        store,
        confirmedRoot,
        previousEpochObservedJustifiedCheckpoint,
        currentEpochObservedJustifiedCheckpoint,
        previousEpochGreatestUnrealizedCheckpoint,
        previousSlotHead,
        currentSlotHead);
  }

  public FastConfirmationStore withCurrentEpochObservedJustifiedCheckpoint(
      final Checkpoint currentEpochObservedJustifiedCheckpoint) {
    return new FastConfirmationStore(
        store,
        confirmedRoot,
        previousEpochObservedJustifiedCheckpoint,
        currentEpochObservedJustifiedCheckpoint,
        previousEpochGreatestUnrealizedCheckpoint,
        previousSlotHead,
        currentSlotHead);
  }

  public FastConfirmationStore withPreviousEpochGreatestUnrealizedCheckpoint(
      final Checkpoint previousEpochGreatestUnrealizedCheckpoint) {
    return new FastConfirmationStore(
        store,
        confirmedRoot,
        previousEpochObservedJustifiedCheckpoint,
        currentEpochObservedJustifiedCheckpoint,
        previousEpochGreatestUnrealizedCheckpoint,
        previousSlotHead,
        currentSlotHead);
  }

  public FastConfirmationStore withPreviousSlotHead(final Bytes32 previousSlotHead) {
    return new FastConfirmationStore(
        store,
        confirmedRoot,
        previousEpochObservedJustifiedCheckpoint,
        currentEpochObservedJustifiedCheckpoint,
        previousEpochGreatestUnrealizedCheckpoint,
        previousSlotHead,
        currentSlotHead);
  }

  public FastConfirmationStore withCurrentSlotHead(final Bytes32 currentSlotHead) {
    return new FastConfirmationStore(
        store,
        confirmedRoot,
        previousEpochObservedJustifiedCheckpoint,
        currentEpochObservedJustifiedCheckpoint,
        previousEpochGreatestUnrealizedCheckpoint,
        previousSlotHead,
        currentSlotHead);
  }
}
