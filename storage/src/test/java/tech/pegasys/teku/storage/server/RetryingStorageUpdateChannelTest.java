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

package tech.pegasys.teku.storage.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.storage.server.RetryingStorageUpdateChannel.MAX_RETRY_TIME;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.exceptions.FatalServiceFailureException;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.storage.api.StorageUpdate;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.api.UpdateResult;
import tech.pegasys.teku.storage.api.WeakSubjectivityUpdate;

class RetryingStorageUpdateChannelTest {
  private final StorageUpdateChannel delegate = mock(StorageUpdateChannel.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100);
  private final RetryingStorageUpdateChannel retryingChannel =
      new RetryingStorageUpdateChannel(delegate, timeProvider, 1);

  @Test
  void onStorageUpdate_shouldRetryUntilSuccess() {
    final StorageUpdate event = mock(StorageUpdate.class);
    when(delegate.onStorageUpdate(event))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 1")))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 2")))
        .thenReturn(SafeFuture.completedFuture(UpdateResult.EMPTY));

    final SafeFuture<UpdateResult> result = retryingChannel.onStorageUpdate(event);

    assertThat(result).isCompletedWithValue(UpdateResult.EMPTY);
    verify(delegate, times(3)).onStorageUpdate(event);
  }

  @Test
  void onFinalizedBlocks_shouldRetryUntilSuccess() {
    final List<SignedBeaconBlock> blocks = Collections.emptyList();
    final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecarsBySlot = Map.of();
    when(delegate.onFinalizedBlocks(blocks, blobSidecarsBySlot, Map.of(), Optional.empty()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 1")))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 2")))
        .thenReturn(SafeFuture.completedFuture(null));

    final SafeFuture<Void> result =
        retryingChannel.onFinalizedBlocks(blocks, blobSidecarsBySlot, Map.of(), Optional.empty());

    assertThat(result).isCompleted();
    verify(delegate, times(3))
        .onFinalizedBlocks(blocks, blobSidecarsBySlot, Map.of(), Optional.empty());
  }

  @Test
  void onWeakSubjectivityUpdate_shouldRetryUntilSuccess() {
    final WeakSubjectivityUpdate update = mock(WeakSubjectivityUpdate.class);
    when(delegate.onWeakSubjectivityUpdate(update))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 1")))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Failed 2")))
        .thenReturn(SafeFuture.completedFuture(null));

    final SafeFuture<Void> result = retryingChannel.onWeakSubjectivityUpdate(update);

    assertThat(result).isCompleted();
    verify(delegate, times(3)).onWeakSubjectivityUpdate(update);
  }

  @Test
  void onChainInitialized_shouldRetryUntilSuccess() {
    final AnchorPoint update = mock(AnchorPoint.class);
    final AtomicInteger callCount = new AtomicInteger(0);
    doAnswer(
            invocation -> {
              final int count = callCount.incrementAndGet();
              if (count < 3) {
                throw new RuntimeException("Failed");
              }
              return null;
            })
        .when(delegate)
        .onChainInitialized(update);

    retryingChannel.onChainInitialized(update);

    verify(delegate, times(3)).onChainInitialized(update);
  }

  @Test
  void shouldThrowFatalExceptionWhenUpdateFailsForTooLong() {
    final int timeAdvanceSecondsPerRetry = 10;
    final StorageUpdate event = mock(StorageUpdate.class);
    when(delegate.onStorageUpdate(event))
        .thenAnswer(
            invocation -> {
              // Increase time a bit on each call
              timeProvider.advanceTimeBySeconds(timeAdvanceSecondsPerRetry);
              return SafeFuture.failedFuture(new RuntimeException("Failed"));
            });

    assertThatThrownBy(() -> retryingChannel.onStorageUpdate(event))
        .isInstanceOf(FatalServiceFailureException.class);
    final int expectedRetries = (int) (MAX_RETRY_TIME.toSeconds() / timeAdvanceSecondsPerRetry + 1);
    verify(delegate, times(expectedRetries)).onStorageUpdate(event);
  }

  @Test
  void shouldNotProcessEventsAfterRetryTimesOut() {
    final StorageUpdate event = mock(StorageUpdate.class);
    when(delegate.onStorageUpdate(event))
        .thenAnswer(
            invocation -> {
              // Increase time so the retry times out.
              timeProvider.advanceTimeBySeconds(1000);
              return SafeFuture.failedFuture(new RuntimeException("Failed"));
            });

    assertThatThrownBy(() -> retryingChannel.onStorageUpdate(event))
        .isInstanceOf(FatalServiceFailureException.class);

    assertThatSafeFuture(
            retryingChannel.onFinalizedBlocks(
                Collections.emptyList(), Map.of(), Map.of(), Optional.empty()))
        .isCompletedExceptionallyWith(ShuttingDownException.class);
    verify(delegate, never()).onFinalizedBlocks(any(), any(), any(), any());
  }
}
