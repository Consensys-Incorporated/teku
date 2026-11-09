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

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.EventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.forkchoice.VoteTracker;
import tech.pegasys.teku.storage.api.VoteUpdateChannel;

public class BatchingVoteUpdateChannel implements VoteUpdateChannel {
  private final VoteUpdateChannel delegate;
  private final EventThread eventThread;

  private boolean nextExecutionScheduled = false;
  private Map<UInt64, VoteTracker> nextBatch = new HashMap<>();

  public BatchingVoteUpdateChannel(
      final VoteUpdateChannel delegate, final EventThread eventThread) {
    this.delegate = delegate;
    this.eventThread = eventThread;
  }

  @Override
  public void onVotesUpdated(final Map<UInt64, VoteTracker> votes) {
    synchronized (this) {
      nextBatch.putAll(votes);
      if (!nextExecutionScheduled) {
        nextExecutionScheduled = true;
        eventThread.execute(this::processBatch);
      }
    }
  }

  private void processBatch() {
    final Map<UInt64, VoteTracker> batch;
    eventThread.checkOnEventThread();
    synchronized (this) {
      batch = nextBatch;
      nextBatch = new HashMap<>();
      nextExecutionScheduled = false;
    }
    delegate.onVotesUpdated(batch);
  }

  @VisibleForTesting
  public void awaitCompletion() {
    eventThread.executeFuture(() -> SafeFuture.COMPLETE).join();
  }
}
