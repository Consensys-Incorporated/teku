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

package tech.pegasys.teku.statetransition.datacolumns;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

class DataColumnSamplingTrackerTest {
  private static final UInt64 SLOT = UInt64.valueOf(42);
  private static final Bytes32 BLOCK_ROOT =
      Bytes32.fromHexString("0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");
  private static final RemoteOrigin MOCK_ORIGIN = mock(RemoteOrigin.class);
  private static final UInt64 COLUMN_INDEX_5 = UInt64.valueOf(5);
  private static final UInt64 COLUMN_INDEX_10 = UInt64.valueOf(10);
  private static final UInt64 COLUMN_INDEX_15 = UInt64.valueOf(15);
  private static final Set<UInt64> SAMPLING_REQUIREMENT =
      Set.of(COLUMN_INDEX_5, COLUMN_INDEX_10, COLUMN_INDEX_15);
  private static final int HALF_COLUMN_COUNT = 64;

  private final CustodyGroupCountManager custodyGroupCountManager =
      mock(CustodyGroupCountManager.class);

  private DataColumnSamplingTracker tracker;

  @BeforeEach
  void setUp() {
    when(custodyGroupCountManager.getSamplingColumnIndices()).thenReturn(SAMPLING_REQUIREMENT);

    tracker =
        DataColumnSamplingTracker.create(
            SLOT, BLOCK_ROOT, custodyGroupCountManager, Optional.of(HALF_COLUMN_COUNT));
  }

  @Test
  void create_shouldInitializeWithAllColumnsMissingAndRPCNotFetched() {
    assertThat(tracker.slot()).isEqualTo(SLOT);
    assertThat(tracker.blockRoot()).isEqualTo(BLOCK_ROOT);
    assertThat(tracker.samplingRequirement()).isEqualTo(SAMPLING_REQUIREMENT);
    assertThat(tracker.missingColumns()).containsExactlyInAnyOrderElementsOf(SAMPLING_REQUIREMENT);
    assertThat(tracker.completionFuture()).isNotDone();
    assertThat(tracker.fullySampled()).isFalse();
    assertThat(tracker.rpcFetchInProgress().get()).isFalse();
  }

  @Test
  void add_shouldReturnTrueAndRemoveColumnWhenItIsMissing() {
    final UInt64 columnIndexToAdd = COLUMN_INDEX_5;
    final Set<UInt64> remainingColumns = Set.of(COLUMN_INDEX_10, COLUMN_INDEX_15);
    final DataColumnSlotAndIdentifier columnIdentifier =
        new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, columnIndexToAdd);

    final boolean result = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(result).isTrue();
    assertThat(tracker.missingColumns()).containsExactlyInAnyOrderElementsOf(remainingColumns);
    assertThat(tracker.completionFuture()).isNotDone();
  }

  @Test
  void add_shouldCompleteFetchFutureWhenLastColumnIsAdded()
      throws ExecutionException, InterruptedException {
    final UInt64 lastColumnIndex = COLUMN_INDEX_15;
    SAMPLING_REQUIREMENT.stream()
        .filter(index -> !index.equals(lastColumnIndex))
        .forEach(
            index ->
                tracker.add(new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, index), MOCK_ORIGIN));
    assertThat(tracker.missingColumns()).hasSize(1);
    assertThat(tracker.completionFuture()).isNotDone();
    final DataColumnSlotAndIdentifier lastColumnIdentifier =
        new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, lastColumnIndex);
    final boolean result = tracker.add(lastColumnIdentifier, MOCK_ORIGIN);

    assertThat(result).isTrue();
    assertThat(tracker.missingColumns()).isEmpty();
    assertThat(tracker.completionFuture()).isCompleted();
    assertThat(tracker.completionFuture().get())
        .containsExactlyInAnyOrderElementsOf(SAMPLING_REQUIREMENT);
    assertThat(tracker.fullySampled()).isTrue();
  }

  @Test
  void add_shouldCompleteEarly_whenHalfColumnsSamplingCompletionIsSet() {
    final Set<UInt64> samplingRequirement =
        Stream.iterate(UInt64.ZERO, UInt64::increment)
            .limit(128)
            .collect(Collectors.toUnmodifiableSet());
    when(custodyGroupCountManager.getSamplingColumnIndices()).thenReturn(samplingRequirement);
    tracker =
        DataColumnSamplingTracker.create(
            SLOT, BLOCK_ROOT, custodyGroupCountManager, Optional.of(HALF_COLUMN_COUNT));

    for (int i = 0; i < HALF_COLUMN_COUNT - 1; i++) {
      tracker.add(
          new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, UInt64.valueOf(i)), MOCK_ORIGIN);
    }
    assertThat(tracker.missingColumns()).hasSize(HALF_COLUMN_COUNT + 1);
    assertThat(tracker.completionFuture()).isNotDone();
    assertThat(tracker.fullySampled()).isFalse();

    tracker.add(
        new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, UInt64.valueOf(HALF_COLUMN_COUNT - 1)),
        MOCK_ORIGIN);
    assertThat(tracker.missingColumns()).hasSize(HALF_COLUMN_COUNT);
    assertThat(tracker.completionFuture()).isDone();
    assertThat(tracker.fullySampled()).isFalse();

    for (int i = HALF_COLUMN_COUNT; i < HALF_COLUMN_COUNT * 2; i++) {
      tracker.add(
          new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, UInt64.valueOf(i)), MOCK_ORIGIN);
    }
    assertThat(tracker.missingColumns()).isEmpty();
    assertThat(tracker.completionFuture()).isDone();
  }

  @Test
  void add_shouldNotCompleteEarly_whenHalfColumnsSamplingCompletionDisabled() {
    final Set<UInt64> samplingRequirement =
        Stream.iterate(UInt64.ZERO, UInt64::increment)
            .limit(128)
            .collect(Collectors.toUnmodifiableSet());
    when(custodyGroupCountManager.getSamplingColumnIndices()).thenReturn(samplingRequirement);
    tracker =
        DataColumnSamplingTracker.create(
            SLOT, BLOCK_ROOT, custodyGroupCountManager, Optional.empty());

    for (int i = 0; i < HALF_COLUMN_COUNT * 2 - 1; i++) {
      tracker.add(
          new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, UInt64.valueOf(i)), MOCK_ORIGIN);
    }
    assertThat(tracker.missingColumns()).hasSize(1);
    assertThat(tracker.completionFuture()).isNotDone();
    assertThat(tracker.fullySampled()).isFalse();

    tracker.add(
        new DataColumnSlotAndIdentifier(
            SLOT, BLOCK_ROOT, UInt64.valueOf(HALF_COLUMN_COUNT * 2 - 1)),
        MOCK_ORIGIN);
    assertThat(tracker.missingColumns()).isEmpty();
    assertThat(tracker.completionFuture()).isDone();
    assertThat(tracker.fullySampled()).isTrue();
  }

  @Test
  void add_shouldReturnFalseForMismatchedSlot() {
    final UInt64 mismatchedSlot = SLOT.plus(1);
    final DataColumnSlotAndIdentifier columnIdentifier =
        new DataColumnSlotAndIdentifier(mismatchedSlot, BLOCK_ROOT, COLUMN_INDEX_5);

    final boolean result = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(result).isFalse();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size());
    assertThat(tracker.completionFuture()).isNotDone();
  }

  @Test
  void add_shouldReturnFalseForMismatchedBlockRoot() {
    final Bytes32 mismatchedRoot = Bytes32.fromHexString("0xff");
    final DataColumnSlotAndIdentifier columnIdentifier =
        new DataColumnSlotAndIdentifier(SLOT, mismatchedRoot, COLUMN_INDEX_5);

    final boolean result = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(result).isFalse();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size());
    assertThat(tracker.completionFuture()).isNotDone();
  }

  @Test
  void add_shouldReturnFalseWhenAddingSameColumnTwice() {
    final UInt64 columnIndexToAdd = COLUMN_INDEX_5;
    final DataColumnSlotAndIdentifier columnIdentifier =
        new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, columnIndexToAdd);

    final boolean firstResult = tracker.add(columnIdentifier, MOCK_ORIGIN);
    assertThat(firstResult).isTrue();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size() - 1);

    final boolean secondResult = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(secondResult).isFalse();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size() - 1);
  }

  @Test
  void add_shouldReturnFalseForColumnNotInOriginalRequirement() {
    final UInt64 nonRequiredIndex = UInt64.valueOf(999);
    final DataColumnSlotAndIdentifier columnIdentifier =
        new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, nonRequiredIndex);

    final boolean result = tracker.add(columnIdentifier, MOCK_ORIGIN);

    assertThat(result).isFalse();
    assertThat(tracker.missingColumns()).hasSize(SAMPLING_REQUIREMENT.size());
  }

  @Test
  void getMissingColumnIdentifiers_shouldReturnFullListWhenNew() {
    final List<DataColumnSlotAndIdentifier> missing = tracker.getMissingColumnIdentifiers();

    assertThat(missing)
        .hasSize(SAMPLING_REQUIREMENT.size())
        .extracting(DataColumnSlotAndIdentifier::columnIndex)
        .containsExactlyInAnyOrderElementsOf(SAMPLING_REQUIREMENT);

    // Verify all identifiers have the correct slot and root
    assertThat(missing).allMatch(id -> id.slot().equals(SLOT) && id.blockRoot().equals(BLOCK_ROOT));
  }

  @Test
  void getMissingColumnIdentifiers_shouldReturnPartialListAfterAddingOne() {
    tracker.add(new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, COLUMN_INDEX_10), MOCK_ORIGIN);

    final List<DataColumnSlotAndIdentifier> missing = tracker.getMissingColumnIdentifiers();

    assertThat(missing)
        .hasSize(SAMPLING_REQUIREMENT.size() - 1)
        .extracting(DataColumnSlotAndIdentifier::columnIndex)
        .containsExactlyInAnyOrder(COLUMN_INDEX_5, COLUMN_INDEX_15);
  }

  @Test
  void getMissingColumnIdentifiers_shouldReturnEmptyListWhenComplete() {
    for (UInt64 index : SAMPLING_REQUIREMENT) {
      tracker.add(new DataColumnSlotAndIdentifier(SLOT, BLOCK_ROOT, index), MOCK_ORIGIN);
    }
    assertThat(tracker.missingColumns()).isEmpty();

    final List<DataColumnSlotAndIdentifier> missing = tracker.getMissingColumnIdentifiers();

    assertThat(missing).isEmpty();
  }
}
