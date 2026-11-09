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

package tech.pegasys.teku.statetransition.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult.FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.block.BlockImportChannel.BlockImportAndBroadcastValidationResults;

class FailedExecutionPoolTest {

  public static final BlockImportResult FAILED_EXECUTION_TIMEOUT =
      BlockImportResult.failedExecutionPayloadExecution(timeoutException());

  public static final BlockImportResult FAILED_EXECUTION_ERROR =
      BlockImportResult.failedExecutionPayloadExecution(new IOException());
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BlockManager blockManager = mock(BlockManager.class);
  private final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner(timeProvider);

  private final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(1);

  private final FailedExecutionPool failurePool =
      new FailedExecutionPool(blockManager, asyncRunner);

  @Test
  void shouldRetryExecutionAfterShortDelay() {
    withImportResult(FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);
    failurePool.addFailedBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();

    timeProvider.advanceTimeBy(FailedExecutionPool.SHORT_DELAY);
    asyncRunner.executeDueActions();

    verify(blockManager).importBlock(block);
  }

  @Test
  void shouldContinueRetryingWhenExecutionFailsAgain() {
    withImportResult(FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);

    failurePool.addFailedBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(blockManager, times(2)).importBlock(block);
  }

  @Test
  void shouldStopRetryingWhenBlockImports() {
    withImportResult(BlockImportResult.successful(block));

    failurePool.addFailedBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  void shouldStopRetryingWhenPayloadFoundToBeInvalid() {
    withImportResult(
        BlockImportResult.failedStateTransition(new IllegalStateException("Invalid payload")));

    failurePool.addFailedBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  void shouldStopRetryingWhenBlockCanBeOptimisticallyImported() {
    withImportResult(BlockImportResult.optimisticallySuccessful(block));

    failurePool.addFailedBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isTrue();
    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  void shouldNotRetrySameBlockTwice() {
    withImportResult(FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);

    failurePool.addFailedBlock(block);
    failurePool.addFailedBlock(block);

    withImportResult(BlockImportResult.optimisticallySuccessful(block));
    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);

    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  @Test
  void shouldWaitLongerBetweenEachRetry() {
    withImportResult(FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);

    failurePool.addFailedBlock(block);

    timeProvider.advanceTimeBy(FailedExecutionPool.SHORT_DELAY);
    asyncRunner.executeDueActions();
    verify(blockManager, times(1)).importBlock(block);

    // Not retried after the delay
    timeProvider.advanceTimeBy(FailedExecutionPool.SHORT_DELAY);
    asyncRunner.executeDueActions();
    verify(blockManager, times(1)).importBlock(block);

    // But retries after double the time
    timeProvider.advanceTimeBy(FailedExecutionPool.SHORT_DELAY);
    asyncRunner.executeDueActions();
    verify(blockManager, times(2)).importBlock(block);
  }

  @Test
  void shouldResetRetryTimeOnSuccessfulResponse() {
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    withImportResult(FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);

    failurePool.addFailedBlock(block);

    timeProvider.advanceTimeBy(FailedExecutionPool.SHORT_DELAY);
    asyncRunner.executeDueActions();
    verify(blockManager, times(1)).importBlock(block);

    // Succeeds when retried the second time
    withImportResult(BlockImportResult.successful(block));
    timeProvider.advanceTimeBy(FailedExecutionPool.SHORT_DELAY.multipliedBy(2));
    asyncRunner.executeDueActions();
    verify(blockManager, times(2)).importBlock(block);

    // New block fails and should be retried with a short timeout again
    failurePool.addFailedBlock(block2);
    timeProvider.advanceTimeBy(FailedExecutionPool.SHORT_DELAY);
    asyncRunner.executeDueActions();
    verify(blockManager).importBlock(block2);
  }

  @Test
  void shouldOnlyRetryOneBlockAtATime() {
    withImportResult(FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);

    failurePool.addFailedBlock(block);
    failurePool.addFailedBlock(block2);

    asyncRunner.executeQueuedActions();
    verify(blockManager, times(1)).importBlock(any());
  }

  @Test
  void shouldRetryNextBlockAfterFirstOneNoLongerNeedsRetrying() {
    withImportResult(BlockImportResult.optimisticallySuccessful(block));
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);

    failurePool.addFailedBlock(block);
    failurePool.addFailedBlock(block2);

    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);
    // Should immediately try to execute next pending block
    verify(blockManager).importBlock(block2);
  }

  @Test
  void shouldLimitMaximumRetryDelayForSyncingResponses() {
    withImportResult(FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);
    failurePool.addFailedBlock(block);

    Duration expectedDelay = FailedExecutionPool.SHORT_DELAY;
    for (int i = 0; i < 5; i++) {
      timeProvider.advanceTimeBy(expectedDelay);
      asyncRunner.executeDueActions();
      verify(blockManager, times(i + 1)).importBlock(block);
      expectedDelay = expectedDelay.multipliedBy(2);
    }

    // Should not increase delay beyond maximum
    timeProvider.advanceTimeBy(FailedExecutionPool.MAX_RETRY_DELAY);
    asyncRunner.executeDueActions();
    verify(blockManager, times(6)).importBlock(block);
  }

  @Test
  void shouldLimitMaximumRetryDelayForTimeoutResponses() {
    withImportResult(FAILED_EXECUTION_TIMEOUT);
    failurePool.addFailedBlock(block);

    Duration expectedDelay = FailedExecutionPool.SHORT_DELAY;
    for (int i = 0; i < 5; i++) {
      timeProvider.advanceTimeBy(expectedDelay);
      asyncRunner.executeDueActions();
      verify(blockManager, times(i + 1)).importBlock(block);
      expectedDelay = expectedDelay.multipliedBy(2);
    }

    // Should not increase delay beyond maximum
    timeProvider.advanceTimeBy(FailedExecutionPool.MAX_RETRY_DELAY);
    asyncRunner.executeDueActions();
    verify(blockManager, times(6)).importBlock(block);
  }

  @Test
  void shouldLimitMaximumRetryDelayForFailureResponses() {
    withImportResult(FAILED_EXECUTION_ERROR);
    failurePool.addFailedBlock(block);

    Duration expectedDelay = FailedExecutionPool.SHORT_DELAY;
    for (int i = 0; i < 5; i++) {
      timeProvider.advanceTimeBy(expectedDelay);
      asyncRunner.executeDueActions();
      verify(blockManager, times(i + 1)).importBlock(block);
      expectedDelay = expectedDelay.multipliedBy(2);
    }

    // Should not increase delay beyond maximum
    timeProvider.advanceTimeBy(FailedExecutionPool.MAX_RETRY_DELAY);
    asyncRunner.executeDueActions();
    verify(blockManager, times(6)).importBlock(block);
  }

  @Test
  void shouldRetrySameBlockOnTimeoutResponse() {
    withImportResult(FAILED_EXECUTION_TIMEOUT);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);

    failurePool.addFailedBlock(block);
    failurePool.addFailedBlock(block2);

    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);

    asyncRunner.executeQueuedActions();
    verify(blockManager, times(2)).importBlock(block);
  }

  @Test
  void shouldRetryDifferentBlockOnSyncingResponse() {
    withImportResult(FAILED_EXECUTION_PAYLOAD_EXECUTION_SYNCING);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);

    failurePool.addFailedBlock(block);
    failurePool.addFailedBlock(block2);

    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);

    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block2);
  }

  @Test
  void shouldRetryDifferentBlockOnFailureResponse() {
    withImportResult(FAILED_EXECUTION_ERROR);
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);

    failurePool.addFailedBlock(block);
    failurePool.addFailedBlock(block2);

    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);

    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block2);
  }

  @Test
  void shouldStopRetryingBlockWhenImportThrowsExceptionInsteadOfReturningFailedFuture() {
    final SignedBeaconBlock block2 = dataStructureUtil.randomSignedBeaconBlock(2);
    when(blockManager.importBlock(block)).thenThrow(new RuntimeException("Whoops"));
    when(blockManager.importBlock(block2))
        .thenReturn(
            SafeFuture.completedFuture(
                new BlockImportAndBroadcastValidationResults(
                    SafeFuture.completedFuture(BlockImportResult.successful(block2)))));

    failurePool.addFailedBlock(block);
    failurePool.addFailedBlock(block2);

    asyncRunner.executeQueuedActions();
    verify(blockManager).importBlock(block);
    verify(blockManager).importBlock(block2);

    verifyNoMoreInteractions(blockManager);
    assertThat(asyncRunner.hasDelayedActions()).isFalse();
  }

  private void withImportResult(final BlockImportResult result) {
    when(blockManager.importBlock(any()))
        .thenReturn(
            SafeFuture.completedFuture(
                new BlockImportAndBroadcastValidationResults(SafeFuture.completedFuture(result))));
  }

  private static InterruptedIOException timeoutException() {
    // Awkward but this is the timeout exception we actually get.
    final SocketTimeoutException cause = new SocketTimeoutException("Read timed out");
    final InterruptedIOException interruptedIOException = new InterruptedIOException("timeout");
    interruptedIOException.initCause(cause);
    return interruptedIOException;
  }
}
