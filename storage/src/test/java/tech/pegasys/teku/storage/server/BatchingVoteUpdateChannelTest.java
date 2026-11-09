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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.HashMap;
import java.util.Map;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.eventthread.AsyncRunnerEventThread;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;

class BatchingVoteUpdateChannelTest {
  private final VoteUpdateChannel delegate = mock(VoteUpdateChannel.class);
  private final StubAsyncRunnerFactory asyncRunnerFactory = new StubAsyncRunnerFactory();
  private final EventThread eventThread =
      new AsyncRunnerEventThread("batch_test", asyncRunnerFactory);
  private StubAsyncRunner stubAsyncRunner;

  private final BatchingVoteUpdateChannel channel =
      new BatchingVoteUpdateChannel(delegate, eventThread);

  @BeforeEach
  void setUp() {
    eventThread.start();
    stubAsyncRunner = asyncRunnerFactory.getStubAsyncRunners().get(0);
  }

  @AfterEach
  void tearDown() {
    eventThread.stop();
  }

  @Test
  void shouldExecuteFirstEventImmediately() {
    final Map<UInt64, VoteTracker> votes = Map.of(UInt64.ONE, VoteTracker.DEFAULT);
    channel.onVotesUpdated(votes);

    assertExecutionScheduled();

    triggerBatchExecution(votes);
  }

  @Test
  void shouldBatchAllVotesReceivedBeforeExecutionOccurs() {
    final Map<UInt64, VoteTracker> votes1 = Map.of(UInt64.ONE, VoteTracker.DEFAULT);
    final Map<UInt64, VoteTracker> votes2 = Map.of(UInt64.valueOf(2), VoteTracker.DEFAULT);
    final Map<UInt64, VoteTracker> votes3 = Map.of(UInt64.valueOf(3), VoteTracker.DEFAULT);

    channel.onVotesUpdated(votes1);

    assertExecutionScheduled();

    channel.onVotesUpdated(votes2);
    channel.onVotesUpdated(votes3);

    final Map<UInt64, VoteTracker> expectedBatch = new HashMap<>();
    expectedBatch.putAll(votes1);
    expectedBatch.putAll(votes2);
    expectedBatch.putAll(votes3);
    triggerBatchExecution(expectedBatch);
  }

  @Test
  void shouldScheduleAnotherBatchWhenVotesReceivedAfterExecutionStarts() {
    final Map<UInt64, VoteTracker> votes1 = Map.of(UInt64.ONE, VoteTracker.DEFAULT);
    final Map<UInt64, VoteTracker> votes2 = Map.of(UInt64.valueOf(2), VoteTracker.DEFAULT);
    final Map<UInt64, VoteTracker> votes3 = Map.of(UInt64.valueOf(3), VoteTracker.DEFAULT);

    channel.onVotesUpdated(votes1);

    assertExecutionScheduled();

    channel.onVotesUpdated(votes2);

    final Map<UInt64, VoteTracker> expectedBatch = new HashMap<>();
    expectedBatch.putAll(votes1);
    expectedBatch.putAll(votes2);
    triggerBatchExecution(expectedBatch);

    channel.onVotesUpdated(votes3);
    assertExecutionScheduled();
    triggerBatchExecution(votes3);
  }

  @Test
  void shouldUseLatestVotes() {
    final Map<UInt64, VoteTracker> votes1 = Map.of(UInt64.ONE, VoteTracker.DEFAULT);
    final Map<UInt64, VoteTracker> votes2 =
        Map.of(UInt64.ONE, new VoteTracker(Bytes32.ZERO, Bytes32.ZERO, true, true));

    channel.onVotesUpdated(votes1);

    assertExecutionScheduled();

    channel.onVotesUpdated(votes2);

    // The vote in votes2 replaces the vote in votes1
    triggerBatchExecution(votes2);
  }

  private void assertExecutionScheduled() {
    assertThat(stubAsyncRunner.hasDelayedActions()).isTrue();
    verifyNoMoreInteractions(delegate);
  }

  private void triggerBatchExecution(final Map<UInt64, VoteTracker> expectedBatch) {
    stubAsyncRunner.executeQueuedActions();
    verify(delegate).onVotesUpdated(expectedBatch);
  }
}
