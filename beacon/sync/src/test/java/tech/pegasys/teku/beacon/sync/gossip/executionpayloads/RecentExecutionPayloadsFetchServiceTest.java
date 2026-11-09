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

package tech.pegasys.teku.beacon.sync.gossip.executionpayloads;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.beacon.sync.fetch.FetchExecutionPayloadTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PendingPool.RequiredBlockRootDroppedSubscriber;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

class RecentExecutionPayloadsFetchServiceTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final ExecutionPayloadManager executionPayloadManager =
      mock(ExecutionPayloadManager.class);

  private final FetchTaskFactory fetchTaskFactory = mock(FetchTaskFactory.class);

  private final ForwardSync forwardSync = mock(ForwardSync.class);

  private final PayloadAttestationPool payloadAttestationPool = mock(PayloadAttestationPool.class);

  @SuppressWarnings("unchecked")
  private final PendingPool<PayloadAttestationMessage> pendingPayloadAttestationsPool =
      mock(PendingPool.class);

  private final int maxConcurrentRequests = 2;

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final List<FetchExecutionPayloadTask> tasks = new ArrayList<>();
  private final List<SafeFuture<FetchResult<SignedExecutionPayloadEnvelope>>> taskFutures =
      new ArrayList<>();
  private final List<SignedExecutionPayloadEnvelope> importedExecutionPayloads = new ArrayList<>();

  private RecentExecutionPayloadsFetchService recentExecutionPayloadsFetchService;

  @BeforeEach
  public void setup() {
    recentExecutionPayloadsFetchService =
        new RecentExecutionPayloadsFetchService(
            asyncRunner,
            maxConcurrentRequests,
            forwardSync,
            fetchTaskFactory,
            executionPayloadManager,
            payloadAttestationPool,
            pendingPayloadAttestationsPool);

    lenient()
        .when(fetchTaskFactory.createFetchExecutionPayloadTask(any()))
        .thenAnswer(this::createMockTask);
    recentExecutionPayloadsFetchService.subscribeExecutionPayloadFetched(
        importedExecutionPayloads::add);
  }

  @Test
  public void fetchSingleBlockSuccessfully() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);

    assertTaskCounts(1, 1, 0);
    assertThat(importedExecutionPayloads).isEmpty();

    final SafeFuture<FetchResult<SignedExecutionPayloadEnvelope>> future = taskFutures.get(0);
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    future.complete(FetchResult.createSuccessful(executionPayload));

    assertThat(importedExecutionPayloads).containsExactly(executionPayload);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handleDuplicateRequiredExecutionPayloads() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);

    assertTaskCounts(1, 1, 0);
    assertThat(importedExecutionPayloads).isEmpty();

    final SafeFuture<FetchResult<SignedExecutionPayloadEnvelope>> future = taskFutures.get(0);
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    future.complete(FetchResult.createSuccessful(executionPayload));

    assertThat(importedExecutionPayloads).containsExactly(executionPayload);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void ignoreKnownExecutionPayload() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    when(executionPayloadManager.isExecutionPayloadRecentlySeen(beaconBlockRoot)).thenReturn(true);
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);

    assertTaskCounts(0, 0, 0);
    assertThat(importedExecutionPayloads).isEmpty();
  }

  @Test
  public void cancelExecutionPayloadRequest() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);
    recentExecutionPayloadsFetchService.cancelRecentExecutionPayloadRequest(beaconBlockRoot);

    verify(tasks.getFirst()).cancel();
    // Manually cancel future
    taskFutures.getFirst().complete(FetchResult.createFailed(Status.CANCELLED));

    // Task should be removed
    assertTaskCounts(0, 0, 0);
    assertThat(importedExecutionPayloads).isEmpty();
  }

  @Test
  public void fetchSingleExecutionPayloadWithRetry() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);

    assertTaskCounts(1, 1, 0);
    assertThat(importedExecutionPayloads).isEmpty();

    final SafeFuture<FetchResult<SignedExecutionPayloadEnvelope>> future = taskFutures.get(0);
    future.complete(FetchResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.getFirst()).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Executor should requeue task
    when(tasks.getFirst().run()).thenReturn(new SafeFuture<>());
    asyncRunner.executeQueuedActions();
    assertTaskCounts(1, 1, 0);
  }

  @Test
  public void cancelTaskWhileWaitingToRetry() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);

    assertTaskCounts(1, 1, 0);
    assertThat(importedExecutionPayloads).isEmpty();

    final SafeFuture<FetchResult<SignedExecutionPayloadEnvelope>> future = taskFutures.getFirst();
    future.complete(FetchResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.getFirst()).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Cancel task
    recentExecutionPayloadsFetchService.cancelRecentExecutionPayloadRequest(beaconBlockRoot);
    verify(tasks.getFirst()).cancel();
    when(tasks.getFirst().run())
        .thenReturn(SafeFuture.completedFuture(FetchResult.createFailed(Status.CANCELLED)));

    // Executor should requeue task, it should complete immediately and be removed
    asyncRunner.executeQueuedActions();
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handlesPeersUnavailable() {
    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);

    assertTaskCounts(1, 1, 0);
    assertThat(importedExecutionPayloads).isEmpty();

    final SafeFuture<FetchResult<SignedExecutionPayloadEnvelope>> future = taskFutures.getFirst();
    future.complete(FetchResult.createFailed(Status.NO_AVAILABLE_PEERS));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.getFirst(), never()).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Executor should requeue task
    when(tasks.getFirst().run()).thenReturn(new SafeFuture<>());
    asyncRunner.executeQueuedActions();
    assertTaskCounts(1, 1, 0);
  }

  @Test
  public void queueFetchTaskWhenConcurrencyLimitReached() {
    final int taskCount = maxConcurrentRequests + 1;
    for (int i = 0; i < taskCount; i++) {
      final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
      recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);
    }

    assertTaskCounts(taskCount, taskCount - 1, 1);

    // Complete first task
    final SafeFuture<FetchResult<SignedExecutionPayloadEnvelope>> future = taskFutures.getFirst();
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    future.complete(FetchResult.createSuccessful(executionPayload));

    // After first task completes, remaining pending count should become active
    assertTaskCounts(taskCount - 1, taskCount - 1, 0);
  }

  @Test
  void onPayloadAttestationAdded_requestsExecutionPayloadWhenPayloadPresent() {
    assertThat(recentExecutionPayloadsFetchService.start()).isCompleted();

    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    captureOperationAddedSubscriber()
        .onOperationAdded(
            mockMessage(beaconBlockRoot, true), InternalValidationResult.ACCEPT, true);

    assertTaskCounts(1, 1, 0);
  }

  @Test
  void onPayloadAttestationAdded_ignoresWhenPayloadNotPresent() {
    assertThat(recentExecutionPayloadsFetchService.start()).isCompleted();

    captureOperationAddedSubscriber()
        .onOperationAdded(
            mockMessage(dataStructureUtil.randomBytes32(), false),
            InternalValidationResult.ACCEPT,
            true);

    assertTaskCounts(0, 0, 0);
  }

  @Test
  void shouldCancelRequestWhenRequiredBlockRootDropped() {
    assertThat(recentExecutionPayloadsFetchService.start()).isCompleted();

    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);
    assertTaskCounts(1, 1, 0);

    final ArgumentCaptor<RequiredBlockRootDroppedSubscriber> captor =
        ArgumentCaptor.forClass(RequiredBlockRootDroppedSubscriber.class);
    verify(pendingPayloadAttestationsPool).subscribeRequiredBlockRootDropped(captor.capture());
    captor.getValue().onRequiredBlockRootDropped(beaconBlockRoot);

    verify(tasks.getFirst()).cancel();
  }

  @Test
  void onSyncStopped_replaysRequestsMadeDuringSync() {
    assertThat(recentExecutionPayloadsFetchService.start()).isCompleted();
    when(forwardSync.isSyncActive()).thenReturn(true);

    final Bytes32 firstRoot = dataStructureUtil.randomBytes32();
    final Bytes32 secondRoot = dataStructureUtil.randomBytes32();
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(firstRoot);
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(secondRoot);
    assertTaskCounts(0, 0, 0);

    when(forwardSync.isSyncActive()).thenReturn(false);
    captureSyncSubscriber().onSyncingChange(false);

    assertTaskCounts(2, 2, 0);
  }

  @Test
  void onSyncStopped_doesNotReplayCanceledRequests() {
    assertThat(recentExecutionPayloadsFetchService.start()).isCompleted();
    when(forwardSync.isSyncActive()).thenReturn(true);

    final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(beaconBlockRoot);
    recentExecutionPayloadsFetchService.cancelRecentExecutionPayloadRequest(beaconBlockRoot);

    when(forwardSync.isSyncActive()).thenReturn(false);
    captureSyncSubscriber().onSyncingChange(false);

    assertTaskCounts(0, 0, 0);
  }

  @Test
  void shouldNotFetchBlocksWhileForwardSyncIsInProgress() {
    when(forwardSync.isSyncActive()).thenReturn(true);

    recentExecutionPayloadsFetchService.requestRecentExecutionPayload(
        dataStructureUtil.randomBytes32());
    assertTaskCounts(0, 0, 0);
  }

  private SyncSubscriber captureSyncSubscriber() {
    final ArgumentCaptor<SyncSubscriber> captor = ArgumentCaptor.forClass(SyncSubscriber.class);
    verify(forwardSync).subscribeToSyncChanges(captor.capture());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private OperationAddedSubscriber<PayloadAttestationMessage> captureOperationAddedSubscriber() {
    final ArgumentCaptor<OperationAddedSubscriber<PayloadAttestationMessage>> captor =
        ArgumentCaptor.forClass(OperationAddedSubscriber.class);
    verify(payloadAttestationPool).subscribeOperationAdded(captor.capture());
    return captor.getValue();
  }

  private PayloadAttestationMessage mockMessage(
      final Bytes32 beaconBlockRoot, final boolean payloadPresent) {
    final PayloadAttestationMessage message = mock(PayloadAttestationMessage.class);
    final PayloadAttestationData data = mock(PayloadAttestationData.class);
    when(message.getData()).thenReturn(data);
    when(data.isPayloadPresent()).thenReturn(payloadPresent);
    lenient().when(data.getBeaconBlockRoot()).thenReturn(beaconBlockRoot);
    return message;
  }

  private FetchExecutionPayloadTask createMockTask(final InvocationOnMock invocationOnMock) {
    final Bytes32 beaconBlockRoot = invocationOnMock.getArgument(0);
    final FetchExecutionPayloadTask task = mock(FetchExecutionPayloadTask.class);

    lenient().when(task.getKey()).thenReturn(beaconBlockRoot);
    lenient().when(task.getNumberOfRetries()).thenReturn(0);
    final SafeFuture<FetchResult<SignedExecutionPayloadEnvelope>> future = new SafeFuture<>();
    lenient().when(task.run()).thenReturn(future);
    taskFutures.add(future);
    tasks.add(task);

    return task;
  }

  private void assertTaskCounts(
      final int totalTasks, final int activeTasks, final int queuedTasks) {
    assertThat(recentExecutionPayloadsFetchService.countTrackedTasks())
        .describedAs("Tracked tasks")
        .isEqualTo(totalTasks);
    assertThat(recentExecutionPayloadsFetchService.countActiveTasks())
        .describedAs("Active tasks")
        .isEqualTo(activeTasks);
    assertThat(recentExecutionPayloadsFetchService.countPendingTasks())
        .describedAs("Pending tasks")
        .isEqualTo(queuedTasks);
  }
}
