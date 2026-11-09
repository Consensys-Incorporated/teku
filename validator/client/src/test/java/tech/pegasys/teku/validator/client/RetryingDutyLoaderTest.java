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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.validator.api.NodeSyncingException;
import tech.pegasys.teku.validator.client.duties.ScheduledDuties;

class RetryingDutyLoaderTest {

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  @SuppressWarnings("unchecked")
  private final DutyLoader<ScheduledDuties> delegate = mock(DutyLoader.class);

  private final ScheduledDuties scheduledDuties = mock(ScheduledDuties.class);
  private final Optional<ScheduledDuties> scheduledDutiesOptional = Optional.of(scheduledDuties);
  final TimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
  private final RetryingDutyLoader<ScheduledDuties> dutyLoader =
      new RetryingDutyLoader<>(asyncRunner, timeProvider, delegate);

  @Test
  public void shouldReturnDutiesWhenLoadedSuccessfully() {
    when(delegate.loadDutiesForEpoch(ONE))
        .thenReturn(SafeFuture.completedFuture(scheduledDutiesOptional));

    assertThat(dutyLoader.loadDutiesForEpoch(ONE)).isCompletedWithValue(scheduledDutiesOptional);
  }

  @Test
  public void shouldRetryWhenRequestForDutiesFailsBecauseNodeIsSyncing() {
    when(delegate.loadDutiesForEpoch(ONE))
        .thenReturn(NodeSyncingException.failedFuture())
        .thenReturn(SafeFuture.completedFuture(scheduledDutiesOptional));

    final SafeFuture<Optional<ScheduledDuties>> result = dutyLoader.loadDutiesForEpoch(ONE);
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(scheduledDutiesOptional);
  }

  @Test
  public void shouldRetryWhenRequestForDutiesFailsBecauseNodeDataIsUnavailable() {
    when(delegate.loadDutiesForEpoch(ONE))
        .thenReturn(SafeFuture.failedFuture(new NodeDataUnavailableException("Sorry")))
        .thenReturn(SafeFuture.completedFuture(scheduledDutiesOptional));

    final SafeFuture<Optional<ScheduledDuties>> result = dutyLoader.loadDutiesForEpoch(ONE);
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(scheduledDutiesOptional);
  }

  @Test
  public void shouldRetryWhenUnexpectedErrorOccurs() {
    when(delegate.loadDutiesForEpoch(ONE))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("No way")))
        .thenReturn(SafeFuture.completedFuture(scheduledDutiesOptional));

    final SafeFuture<Optional<ScheduledDuties>> result = dutyLoader.loadDutiesForEpoch(ONE);
    assertThat(result).isNotDone();
    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    asyncRunner.executeQueuedActions();
    assertThat(result).isCompletedWithValue(scheduledDutiesOptional);
  }

  @Test
  public void shouldStopRetryingWhenFutureIsCancelled() {
    final RuntimeException error = new RuntimeException("No way");
    final SafeFuture<Optional<ScheduledDuties>> delegateResponse = new SafeFuture<>();
    when(delegate.loadDutiesForEpoch(ONE)).thenReturn(delegateResponse);

    final SafeFuture<Optional<ScheduledDuties>> result = dutyLoader.loadDutiesForEpoch(ONE);
    verify(delegate).loadDutiesForEpoch(ONE);
    assertThat(result).isNotDone();

    result.cancel(false);

    // When the request then fails, we don't retry it.
    delegateResponse.completeExceptionally(error);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
    verifyNoMoreInteractions(delegate);
  }
}
