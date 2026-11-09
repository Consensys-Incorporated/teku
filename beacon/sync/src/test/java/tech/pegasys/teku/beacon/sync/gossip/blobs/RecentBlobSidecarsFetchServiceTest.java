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

package tech.pegasys.teku.beacon.sync.gossip.blobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import tech.pegasys.teku.beacon.sync.fetch.FetchBlobSidecarTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync.SyncSubscriber;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;

class RecentBlobSidecarsFetchServiceTest {

  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPool.class);

  private final FetchTaskFactory fetchTaskFactory = mock(FetchTaskFactory.class);

  private final ForwardSync forwardSync = mock(ForwardSync.class);

  private final int maxConcurrentRequests = 2;

  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final List<FetchBlobSidecarTask> tasks = new ArrayList<>();
  private final List<SafeFuture<FetchResult<BlobSidecar>>> taskFutures = new ArrayList<>();
  private final List<BlobSidecar> importedBlobSidecars = new ArrayList<>();

  private RecentBlobSidecarsFetchService recentBlobSidecarsFetcher;

  @BeforeEach
  public void setup() {
    recentBlobSidecarsFetcher =
        new RecentBlobSidecarsFetchService(
            asyncRunner,
            blockBlobSidecarsTrackersPool,
            forwardSync,
            fetchTaskFactory,
            maxConcurrentRequests);

    lenient()
        .when(fetchTaskFactory.createFetchBlobSidecarTask(any()))
        .thenAnswer(this::createMockTask);
    recentBlobSidecarsFetcher.subscribeBlobSidecarFetched(importedBlobSidecars::add);
  }

  @Test
  public void fetchSingleBlobSidecarSuccessfully() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();

    recentBlobSidecarsFetcher.requestRecentBlobSidecar(
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex()));

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<BlobSidecar>> future = taskFutures.get(0);
    future.complete(FetchResult.createSuccessful(blobSidecar));

    assertThat(importedBlobSidecars).containsExactly(blobSidecar);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handleDuplicateRequiredBlobSidecars() {
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(blobSidecar.getBlockRoot(), blobSidecar.getIndex());

    recentBlobSidecarsFetcher.requestRecentBlobSidecar(blobIdentifier);
    recentBlobSidecarsFetcher.requestRecentBlobSidecar(blobIdentifier);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<BlobSidecar>> future = taskFutures.get(0);
    future.complete(FetchResult.createSuccessful(blobSidecar));

    assertThat(importedBlobSidecars).containsExactly(blobSidecar);
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void ignoreKnownBlobSidecar() {
    final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier();
    when(blockBlobSidecarsTrackersPool.containsBlobSidecar(blobIdentifier)).thenReturn(true);
    recentBlobSidecarsFetcher.requestRecentBlobSidecar(blobIdentifier);

    assertTaskCounts(0, 0, 0);
    assertThat(importedBlobSidecars).isEmpty();
  }

  @Test
  public void cancelBlobSidecarRequest() {
    final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier();
    recentBlobSidecarsFetcher.requestRecentBlobSidecar(blobIdentifier);
    recentBlobSidecarsFetcher.cancelRecentBlobSidecarRequest(blobIdentifier);

    verify(tasks.get(0)).cancel();
    // Manually cancel future
    taskFutures.get(0).complete(FetchResult.createFailed(Status.CANCELLED));

    // Task should be removed
    assertTaskCounts(0, 0, 0);
    assertThat(importedBlobSidecars).isEmpty();
  }

  @Test
  public void fetchSingleBlobSidecarWithRetry() {
    final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier();
    recentBlobSidecarsFetcher.requestRecentBlobSidecar(blobIdentifier);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<BlobSidecar>> future = taskFutures.get(0);
    future.complete(FetchResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.get(0)).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Executor should requeue task
    when(tasks.get(0).run()).thenReturn(new SafeFuture<>());
    asyncRunner.executeQueuedActions();
    assertTaskCounts(1, 1, 0);
  }

  @Test
  public void cancelTaskWhileWaitingToRetry() {
    final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier();
    recentBlobSidecarsFetcher.requestRecentBlobSidecar(blobIdentifier);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<BlobSidecar>> future = taskFutures.get(0);
    future.complete(FetchResult.createFailed(Status.FETCH_FAILED));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.get(0)).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Cancel task
    recentBlobSidecarsFetcher.cancelRecentBlobSidecarRequest(blobIdentifier);
    verify(tasks.get(0)).cancel();
    when(tasks.get(0).run())
        .thenReturn(SafeFuture.completedFuture(FetchResult.createFailed(Status.CANCELLED)));

    // Executor should requeue task, it should complete immediately and be removed
    asyncRunner.executeQueuedActions();
    assertTaskCounts(0, 0, 0);
  }

  @Test
  public void handlesPeersUnavailable() {
    final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier();
    recentBlobSidecarsFetcher.requestRecentBlobSidecar(blobIdentifier);

    assertTaskCounts(1, 1, 0);
    assertThat(importedBlobSidecars).isEmpty();

    final SafeFuture<FetchResult<BlobSidecar>> future = taskFutures.get(0);
    future.complete(FetchResult.createFailed(Status.NO_AVAILABLE_PEERS));

    // Task should be queued for a retry via the scheduled executor
    verify(tasks.get(0), never()).getNumberOfRetries();
    assertThat(asyncRunner.countDelayedActions()).isEqualTo(1);
    assertTaskCounts(1, 0, 0);

    // Executor should requeue task
    when(tasks.get(0).run()).thenReturn(new SafeFuture<>());
    asyncRunner.executeQueuedActions();
    assertTaskCounts(1, 1, 0);
  }

  @Test
  public void queueFetchTaskWhenConcurrencyLimitReached() {
    final int taskCount = maxConcurrentRequests + 1;
    for (int i = 0; i < taskCount; i++) {
      final BlobIdentifier blobIdentifier = dataStructureUtil.randomBlobIdentifier();
      recentBlobSidecarsFetcher.requestRecentBlobSidecar(blobIdentifier);
    }

    assertTaskCounts(taskCount, taskCount - 1, 1);

    // Complete first task
    final SafeFuture<FetchResult<BlobSidecar>> future = taskFutures.get(0);
    final BlobSidecar blobSidecar = dataStructureUtil.randomBlobSidecar();
    future.complete(FetchResult.createSuccessful(blobSidecar));

    // After first task completes, remaining pending count should become active
    assertTaskCounts(taskCount - 1, taskCount - 1, 0);
  }

  @Test
  void shouldNotFetchBlobSidecarsWhileForwardSyncIsInProgress() {
    when(forwardSync.isSyncActive()).thenReturn(true);

    recentBlobSidecarsFetcher.requestRecentBlobSidecar(dataStructureUtil.randomBlobIdentifier());
    assertTaskCounts(0, 0, 0);
  }

  @Test
  void shouldRequestRemainingRequiredBlobSidecarsWhenForwardSyncCompletes() {
    final Set<BlobIdentifier> requiredBlobIdentifiers =
        new HashSet<>(dataStructureUtil.randomBlobIdentifiers(2));
    when(blockBlobSidecarsTrackersPool.getAllRequiredBlobSidecars())
        .thenReturn(requiredBlobIdentifiers);

    final ArgumentCaptor<SyncSubscriber> syncListenerCaptor =
        ArgumentCaptor.forClass(SyncSubscriber.class);
    assertThat(recentBlobSidecarsFetcher.start()).isCompleted();
    verify(forwardSync).subscribeToSyncChanges(syncListenerCaptor.capture());
    final SyncSubscriber syncSubscriber = syncListenerCaptor.getValue();

    syncSubscriber.onSyncingChange(false);
    assertTaskCounts(2, 2, 0);
    final Set<BlobIdentifier> requestingBlobIdentifiers =
        tasks.stream().map(FetchBlobSidecarTask::getKey).collect(Collectors.toSet());
    assertThat(requestingBlobIdentifiers)
        .containsExactlyInAnyOrderElementsOf(requiredBlobIdentifiers);
  }

  private FetchBlobSidecarTask createMockTask(final InvocationOnMock invocationOnMock) {
    final BlobIdentifier blobIdentifier = invocationOnMock.getArgument(0);
    final FetchBlobSidecarTask task = mock(FetchBlobSidecarTask.class);

    lenient().when(task.getKey()).thenReturn(blobIdentifier);
    lenient().when(task.getNumberOfRetries()).thenReturn(0);
    final SafeFuture<FetchResult<BlobSidecar>> future = new SafeFuture<>();
    lenient().when(task.run()).thenReturn(future);
    taskFutures.add(future);
    tasks.add(task);

    return task;
  }

  private void assertTaskCounts(
      final int totalTasks, final int activeTasks, final int queuedTasks) {
    assertThat(recentBlobSidecarsFetcher.countTrackedTasks())
        .describedAs("Tracked tasks")
        .isEqualTo(totalTasks);
    assertThat(recentBlobSidecarsFetcher.countActiveTasks())
        .describedAs("Active tasks")
        .isEqualTo(activeTasks);
    assertThat(recentBlobSidecarsFetcher.countPendingTasks())
        .describedAs("Pending tasks")
        .isEqualTo(queuedTasks);
  }
}
