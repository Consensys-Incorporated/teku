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

package tech.pegasys.teku.beacon.sync.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tech.pegasys.teku.infrastructure.logging.EventLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.ChainHeadChannel;
import tech.pegasys.teku.storage.api.ReorgContext;

class CoalescingChainHeadChannelTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  private final ChainHeadChannel delegate = Mockito.mock(ChainHeadChannel.class);

  private final EventLogger eventLogger = Mockito.mock(EventLogger.class);

  private final CoalescingChainHeadChannel channel =
      new CoalescingChainHeadChannel(delegate, eventLogger);

  @Test
  void shouldNotBeSyncingInitially() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final boolean executionOptimistic = false;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();
    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        executionOptimistic,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        Optional.empty(),
        reorgContext);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            Optional.empty(),
            reorgContext);
  }

  @Test
  void shouldDelegateEventImmediatelyWhenNotSyncing() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final boolean executionOptimistic = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.onSyncingChange(false);

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        executionOptimistic,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        Optional.empty(),
        reorgContext);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            Optional.empty(),
            reorgContext);
  }

  @Test
  void shouldNotDelegateEventsWhileSyncing() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final boolean executionOptimistic = false;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.onSyncingChange(true);

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        executionOptimistic,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        Optional.empty(),
        reorgContext);
    verifyNoInteractions(delegate);
  }

  @Test
  void shouldSendLatestHeadUpdateWhenSyncingCompletes() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final boolean executionOptimistic = false;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.onSyncingChange(true);

    channel.chainHeadUpdated(
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        false,
        true,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        Optional.empty(),
        Optional.empty());

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        executionOptimistic,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        Optional.empty(),
        reorgContext);

    verifyNoInteractions(delegate);

    channel.onSyncingChange(false);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            Optional.empty(),
            reorgContext);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldPreservePayloadStatusWhenSyncingCompletes() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final boolean executionOptimistic = false;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.onSyncingChange(true);

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        executionOptimistic,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        Optional.of(PAYLOAD_STATUS_FULL),
        reorgContext);

    verifyNoInteractions(delegate);

    channel.onSyncingChange(false);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            Optional.of(PAYLOAD_STATUS_FULL),
            reorgContext);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldNotSendHeadEventWhenSyncingFinishesIfNoneOccurredDuringSyncing() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final boolean executionOptimistic = false;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        executionOptimistic,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        Optional.empty(),
        reorgContext);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            Optional.empty(),
            reorgContext);

    channel.onSyncingChange(true);
    channel.onSyncingChange(false);

    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldNotResendPreviousCoalescedHeadEventWhenSyncingFinishesASecondTime() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final boolean executionOptimistic = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext = Optional.empty();

    channel.onSyncingChange(true);
    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        executionOptimistic,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        Optional.empty(),
        reorgContext);
    channel.onSyncingChange(false);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            Optional.empty(),
            reorgContext);

    channel.onSyncingChange(true);
    channel.onSyncingChange(false);

    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldNotifyReorg() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final boolean executionOptimistic = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();

    final Bytes32 oldBestBlockRoot = dataStructureUtil.randomBytes32();
    final UInt64 oldBestBlockSlot = dataStructureUtil.randomUInt64();
    final Bytes32 oldBestStateRoot = dataStructureUtil.randomBytes32();
    final UInt64 commonAncestorSlot = dataStructureUtil.randomUInt64();
    final Bytes32 commonAncestorRoot = dataStructureUtil.randomBytes32();

    final Optional<ReorgContext> reorgContext =
        Optional.of(
            new ReorgContext(
                oldBestBlockRoot,
                oldBestBlockSlot,
                oldBestStateRoot,
                commonAncestorSlot,
                commonAncestorRoot));
    channel.onSyncingChange(false);

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        executionOptimistic,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        Optional.empty(),
        reorgContext);

    verify(eventLogger, times(1))
        .reorgEvent(
            oldBestBlockRoot,
            oldBestBlockSlot,
            bestBlockRoot,
            slot,
            commonAncestorRoot,
            commonAncestorSlot,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty());
  }

  @Test
  void shouldUseReorgContextWithEarliestCommonAncestorSlotWhenMultipleEventsReceived() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 stateRoot = dataStructureUtil.randomBytes32();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final boolean epochTransition = true;
    final boolean executionOptimistic = true;
    final Bytes32 previousDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDutyDependentRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext1 =
        Optional.of(
            new ReorgContext(
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomBytes32(),
                UInt64.valueOf(100),
                dataStructureUtil.randomBytes32()));
    final Optional<ReorgContext> reorgContext2 =
        Optional.of(
            new ReorgContext(
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomBytes32(),
                UInt64.valueOf(80),
                dataStructureUtil.randomBytes32()));

    channel.onSyncingChange(true);

    channel.chainHeadUpdated(
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        false,
        false,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        Optional.empty(),
        reorgContext2);

    channel.chainHeadUpdated(
        slot,
        stateRoot,
        bestBlockRoot,
        epochTransition,
        executionOptimistic,
        previousDutyDependentRoot,
        currentDutyDependentRoot,
        Optional.empty(),
        reorgContext1);

    verifyNoInteractions(delegate);

    channel.onSyncingChange(false);
    verify(delegate)
        .chainHeadUpdated(
            slot,
            stateRoot,
            bestBlockRoot,
            epochTransition,
            executionOptimistic,
            previousDutyDependentRoot,
            currentDutyDependentRoot,
            Optional.empty(),
            reorgContext2);
    verifyNoMoreInteractions(delegate);
  }

  @Test
  void shouldLogPayloadReorg() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 payloadBlockHash = dataStructureUtil.randomBytes32();
    final Bytes32 parentPayloadBlockHash = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext =
        Optional.of(
            ReorgContext.payloadReorg(
                bestBlockRoot,
                slot,
                dataStructureUtil.randomBytes32(),
                payloadBlockHash,
                parentPayloadBlockHash));

    sendHeadUpdate(slot, bestBlockRoot, PAYLOAD_STATUS_EMPTY, reorgContext);

    verify(eventLogger, times(1))
        .payloadReorgEvent(bestBlockRoot, slot, payloadBlockHash, parentPayloadBlockHash);
    verify(eventLogger, never())
        .reorgEvent(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    verify(delegate)
        .chainHeadUpdated(
            eq(slot),
            any(),
            eq(bestBlockRoot),
            anyBoolean(),
            anyBoolean(),
            any(),
            any(),
            eq(Optional.of(PAYLOAD_STATUS_EMPTY)),
            eq(reorgContext));
  }

  @Test
  void shouldNotLogPayloadReorgWhileSyncing() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext =
        Optional.of(
            ReorgContext.payloadReorg(
                bestBlockRoot,
                slot,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32()));

    channel.onSyncingChange(true);
    sendHeadUpdate(slot, bestBlockRoot, PAYLOAD_STATUS_EMPTY, reorgContext);
    channel.onSyncingChange(false);

    verifyNoInteractions(eventLogger);
    verify(delegate)
        .chainHeadUpdated(
            eq(slot),
            any(),
            eq(bestBlockRoot),
            anyBoolean(),
            anyBoolean(),
            any(),
            any(),
            eq(Optional.of(PAYLOAD_STATUS_EMPTY)),
            eq(reorgContext));
  }

  @Test
  void shouldPassPayloadStatusesOfBlockReorgToLogger() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 oldBestBlockRoot = dataStructureUtil.randomBytes32();
    final UInt64 oldBestBlockSlot = dataStructureUtil.randomUInt64();
    final Bytes32 commonAncestorRoot = dataStructureUtil.randomBytes32();
    final UInt64 commonAncestorSlot = dataStructureUtil.randomUInt64();
    final Optional<ReorgContext> reorgContext =
        Optional.of(
            ReorgContext.blockReorg(
                oldBestBlockRoot,
                oldBestBlockSlot,
                dataStructureUtil.randomBytes32(),
                PAYLOAD_STATUS_FULL,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                commonAncestorSlot,
                commonAncestorRoot,
                PAYLOAD_STATUS_FULL,
                PAYLOAD_STATUS_EMPTY));

    sendHeadUpdate(slot, bestBlockRoot, PAYLOAD_STATUS_EMPTY, reorgContext);

    verify(eventLogger, times(1))
        .reorgEvent(
            oldBestBlockRoot,
            oldBestBlockSlot,
            bestBlockRoot,
            slot,
            commonAncestorRoot,
            commonAncestorSlot,
            Optional.of("EMPTY"),
            Optional.of("FULL"),
            Optional.of("FULL"),
            Optional.of("EMPTY"));
    verify(eventLogger, never()).payloadReorgEvent(any(), any(), any(), any());
  }

  @Test
  void shouldPassCommonAncestorPayloadStatusOfBothBranchesToLogger() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext =
        Optional.of(
            ReorgContext.blockReorg(
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomBytes32(),
                PAYLOAD_STATUS_EMPTY,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomBytes32(),
                PAYLOAD_STATUS_FULL,
                PAYLOAD_STATUS_FULL));

    sendHeadUpdate(slot, bestBlockRoot, PAYLOAD_STATUS_FULL, reorgContext);

    verify(eventLogger, times(1))
        .reorgEvent(
            any(),
            any(),
            eq(bestBlockRoot),
            eq(slot),
            any(),
            any(),
            eq(Optional.of("FULL")),
            eq(Optional.of("EMPTY")),
            eq(Optional.of("FULL")),
            eq(Optional.of("FULL")));
  }

  @Test
  void shouldNotPassPendingPayloadStatusesToLogger() {
    final UInt64 slot = dataStructureUtil.randomUInt64();
    final Bytes32 bestBlockRoot = dataStructureUtil.randomBytes32();
    final Optional<ReorgContext> reorgContext =
        Optional.of(
            ReorgContext.blockReorg(
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomBytes32(),
                PAYLOAD_STATUS_PENDING,
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomBytes32(),
                PAYLOAD_STATUS_PENDING,
                PAYLOAD_STATUS_EMPTY));

    sendHeadUpdate(slot, bestBlockRoot, PAYLOAD_STATUS_PENDING, reorgContext);

    verify(eventLogger, times(1))
        .reorgEvent(
            any(),
            any(),
            eq(bestBlockRoot),
            eq(slot),
            any(),
            any(),
            eq(Optional.empty()),
            eq(Optional.empty()),
            eq(Optional.empty()),
            eq(Optional.of("EMPTY")));
  }

  private void sendHeadUpdate(
      final UInt64 slot,
      final Bytes32 bestBlockRoot,
      final ForkChoicePayloadStatus payloadStatus,
      final Optional<ReorgContext> reorgContext) {
    channel.chainHeadUpdated(
        slot,
        dataStructureUtil.randomBytes32(),
        bestBlockRoot,
        false,
        false,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        Optional.of(payloadStatus),
        reorgContext);
  }
}
