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

package tech.pegasys.teku.beacon.sync.gossip.executionpayloads;

import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.beacon.sync.fetch.FetchExecutionPayloadTask;
import tech.pegasys.teku.beacon.sync.fetch.FetchTaskFactory;
import tech.pegasys.teku.beacon.sync.forward.ForwardSync;
import tech.pegasys.teku.beacon.sync.gossip.AbstractFetchService;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.subscribers.Subscribers;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationMessage;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadManager;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class RecentExecutionPayloadsFetchService
    extends AbstractFetchService<Bytes32, FetchExecutionPayloadTask, SignedExecutionPayloadEnvelope>
    implements RecentExecutionPayloadsFetcher {

  private static final Logger LOG = LogManager.getLogger();

  private static final int MAX_DEFERRED_REPLAY_REQUESTS = 1024;

  private final Subscribers<ExecutionPayloadSubscriber> executionPayloadSubscribers =
      Subscribers.create(true);

  private final ForwardSync forwardSync;
  private final FetchTaskFactory fetchTaskFactory;
  private final ExecutionPayloadManager executionPayloadManager;
  private final PayloadAttestationPool payloadAttestationPool;
  private final PendingPool<PayloadAttestationMessage> pendingPayloadAttestationsPool;
  private final Set<Bytes32> executionPayloadsRequestedDuringSync =
      LimitedSet.createSynchronizedIterable(MAX_DEFERRED_REPLAY_REQUESTS);

  RecentExecutionPayloadsFetchService(
      final AsyncRunner asyncRunner,
      final int maxConcurrentRequests,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory,
      final ExecutionPayloadManager executionPayloadManager,
      final PayloadAttestationPool payloadAttestationPool,
      final PendingPool<PayloadAttestationMessage> pendingPayloadAttestationsPool) {
    super(asyncRunner, maxConcurrentRequests);
    this.forwardSync = forwardSync;
    this.fetchTaskFactory = fetchTaskFactory;
    this.executionPayloadManager = executionPayloadManager;
    this.payloadAttestationPool = payloadAttestationPool;
    this.pendingPayloadAttestationsPool = pendingPayloadAttestationsPool;
  }

  public static RecentExecutionPayloadsFetchService create(
      final AsyncRunner asyncRunner,
      final ForwardSync forwardSync,
      final FetchTaskFactory fetchTaskFactory,
      final ExecutionPayloadManager executionPayloadManager,
      final PayloadAttestationPool payloadAttestationPool,
      final PendingPool<PayloadAttestationMessage> pendingPayloadAttestationsPool) {
    return new RecentExecutionPayloadsFetchService(
        asyncRunner,
        DEFAULT_MAX_CONCURRENT_BLOCKS_REQUESTS,
        forwardSync,
        fetchTaskFactory,
        executionPayloadManager,
        payloadAttestationPool,
        pendingPayloadAttestationsPool);
  }

  @Override
  protected SafeFuture<?> doStart() {
    setupSubscribers();
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public FetchExecutionPayloadTask createTask(final Bytes32 key) {
    return fetchTaskFactory.createFetchExecutionPayloadTask(key);
  }

  @Override
  public void processFetchedResult(
      final FetchExecutionPayloadTask task, final SignedExecutionPayloadEnvelope executionPayload) {
    LOG.trace("Successfully fetched execution payload: {}", executionPayload);
    executionPayloadSubscribers.forEach(s -> s.onExecutionPayload(executionPayload));
    // After retrieved execution payload has been processed, stop tracking it
    removeTask(task);
  }

  @Override
  public void subscribeExecutionPayloadFetched(final ExecutionPayloadSubscriber subscriber) {
    executionPayloadSubscribers.subscribe(subscriber);
  }

  @Override
  public void requestRecentExecutionPayload(final Bytes32 beaconBlockRoot) {
    if (forwardSync.isSyncActive()) {
      // Forward sync is in progress, remember the request so we can replay it once sync stops
      executionPayloadsRequestedDuringSync.add(beaconBlockRoot);
      return;
    }
    if (executionPayloadManager.isExecutionPayloadRecentlySeen(beaconBlockRoot)) {
      // We've already got this execution payload
      return;
    }
    final FetchExecutionPayloadTask task = createTask(beaconBlockRoot);
    if (allTasks.putIfAbsent(beaconBlockRoot, task) != null) {
      // We're already tracking this task
      task.cancel();
      return;
    }
    LOG.trace("Queue execution payload to be fetched: {}", beaconBlockRoot);
    queueTask(task);
  }

  @Override
  public void cancelRecentExecutionPayloadRequest(final Bytes32 beaconBlockRoot) {
    executionPayloadsRequestedDuringSync.remove(beaconBlockRoot);
    cancelRequest(beaconBlockRoot);
  }

  @Override
  public void onExecutionPayloadValidated(final SignedExecutionPayloadEnvelope executionPayload) {}

  @Override
  public void onExecutionPayloadAvailable(final SignedExecutionPayloadEnvelope executionPayload) {}

  @Override
  public void onExecutionPayloadImported(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    cancelRecentExecutionPayloadRequest(executionPayload.getBeaconBlockRoot());
  }

  private void setupSubscribers() {
    payloadAttestationPool.subscribeOperationAdded(this::onPayloadAttestationAdded);
    pendingPayloadAttestationsPool.subscribeRequiredBlockRootDropped(
        this::cancelRecentExecutionPayloadRequest);
    forwardSync.subscribeToSyncChanges(this::onSyncStatusChanged);
  }

  private void onSyncStatusChanged(final boolean syncActive) {
    if (syncActive) {
      return;
    }
    final Set<Bytes32> executionPayloadRequestsToReplay =
        Set.copyOf(executionPayloadsRequestedDuringSync);
    executionPayloadsRequestedDuringSync.clear();
    executionPayloadRequestsToReplay.forEach(this::requestRecentExecutionPayload);
  }

  private void onPayloadAttestationAdded(
      final PayloadAttestationMessage payloadAttestationMessage,
      final InternalValidationResult validationStatus,
      final boolean fromNetwork) {
    if (!payloadAttestationMessage.getData().isPayloadPresent()) {
      return;
    }
    requestRecentExecutionPayload(payloadAttestationMessage.getData().getBeaconBlockRoot());
  }
}
