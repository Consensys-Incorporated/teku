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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore.StoreTransaction;

class TickProcessorTest {
  private final Spec spec = mock(Spec.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final TickProcessor tickProcessor = new TickProcessor(spec, recentChainData);

  @Test
  void shouldProcessTickImmediatelyOnFirstCall() {
    verifyOnTickCompleted(UInt64.valueOf(100));
  }

  @Test
  void shouldNotProcessTickWithTimeBeforePreviousTick() {
    final UInt64 time = UInt64.valueOf(100);
    final UInt64 earlierTime = time.minus(1);
    verifyOnTickCompleted(time);

    // Second call doesn't trigger a new update
    assertThatSafeFuture(tickProcessor.onTick(earlierTime)).isCompleted();
    verifyNoMoreInteractions(spec);
    verify(recentChainData, atMostOnce()).startStoreTransaction();
  }

  @Test
  void shouldConsiderTickWithTimeBeforePreviousTickCompleteWhenPreviousTickCompletes() {
    final UInt64 time = UInt64.valueOf(100);
    final UInt64 earlierTime = time.minus(1);
    final SafeFuture<Void> commitComplete = verifyOnTickStarted(time);

    // Second call doesn't trigger a new update
    final SafeFuture<Void> earlierTickResult = tickProcessor.onTick(earlierTime);
    assertThatSafeFuture(earlierTickResult).isNotDone();
    verifyNoMoreInteractions(spec);

    commitComplete.complete(null);
    assertThatSafeFuture(earlierTickResult).isCompleted();
  }

  @Test
  void shouldNotStartTickWithLaterTimeUntilPreviousCommitCompletes() {
    final UInt64 time = UInt64.valueOf(100);
    final UInt64 laterTime = time.plus(1);
    final SafeFuture<Void> commitComplete = verifyOnTickStarted(time);

    // Second call doesn't trigger a new update
    final SafeFuture<Void> laterCommitComplete = expectTransaction();
    final SafeFuture<Void> laterTickResult = tickProcessor.onTick(laterTime);
    assertThatSafeFuture(laterTickResult).isNotDone();
    verifyNoMoreInteractions(spec);

    // Start processing the second tick only after the first completes
    commitComplete.complete(null);
    verify(spec).onTick(any(), eq(laterTime));
    assertThatSafeFuture(laterTickResult).isNotDone();
    laterCommitComplete.complete(null);
    assertThatSafeFuture(laterTickResult).isCompleted();
  }

  @Test
  void shouldCoalesceMultipleTicksThatOccurWhileFirstProcessing() {
    final UInt64 time = UInt64.valueOf(100);
    final UInt64 laterTime1 = time.plus(1);
    final UInt64 laterTime2 = laterTime1.plus(1);
    final UInt64 laterTime3 = laterTime2.plus(1);
    final SafeFuture<Void> firstTickComplete = verifyOnTickStarted(time);

    // Second call doesn't trigger a new update
    final SafeFuture<Void> later1TickResult = tickProcessor.onTick(laterTime1);
    assertThatSafeFuture(later1TickResult).isNotDone();
    verifyNoMoreInteractions(spec);

    // Third call doesn't trigger a new update
    final SafeFuture<Void> later2TickResult = tickProcessor.onTick(laterTime2);
    assertThatSafeFuture(later2TickResult).isNotDone();
    verifyNoMoreInteractions(spec);

    // Fourth call doesn't trigger a new update yet (but will after first one completes)
    final SafeFuture<Void> later3CommitComplete = expectTransaction();
    final SafeFuture<Void> later3TickResult = tickProcessor.onTick(laterTime3);
    assertThatSafeFuture(later3TickResult).isNotDone();
    verifyNoMoreInteractions(spec);

    // laterTime1 and 2 are skipped and we go straight to processing laterTime3
    firstTickComplete.complete(null);
    verify(spec).onTick(any(), eq(laterTime3));
    assertThatSafeFuture(later1TickResult).isNotDone();
    assertThatSafeFuture(later2TickResult).isNotDone();
    assertThatSafeFuture(later3TickResult).isNotDone();
    later3CommitComplete.complete(null);
    assertThatSafeFuture(later1TickResult).isCompleted();
    assertThatSafeFuture(later2TickResult).isCompleted();
    assertThatSafeFuture(later3TickResult).isCompleted();
  }

  private void verifyOnTickCompleted(final UInt64 time) {
    final SafeFuture<Void> commitComplete = expectTransaction();
    final SafeFuture<Void> result = tickProcessor.onTick(time);
    assertThatSafeFuture(result).isNotDone();
    verify(spec).onTick(any(), eq(time));

    commitComplete.complete(null);
    assertThatSafeFuture(result).isCompleted();
  }

  private SafeFuture<Void> verifyOnTickStarted(final UInt64 time) {
    final SafeFuture<Void> commitComplete = expectTransaction();
    final SafeFuture<Void> result = tickProcessor.onTick(time);
    assertThatSafeFuture(result).isNotDone();
    verify(spec).onTick(any(), eq(time));
    return commitComplete;
  }

  private SafeFuture<Void> expectTransaction() {
    final StoreTransaction transaction = mock(StoreTransaction.class);
    when(recentChainData.startStoreTransaction()).thenReturn(transaction);
    final SafeFuture<Void> commitComplete = new SafeFuture<>();
    when(transaction.commit()).thenReturn(commitComplete);
    return commitComplete;
  }
}
