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

package tech.pegasys.teku.validator.remote.eventsource;

import static java.util.Collections.emptyMap;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.launchdarkly.eventsource.ConnectStrategy;
import com.launchdarkly.eventsource.EventSource;
import com.launchdarkly.eventsource.RetryDelayStrategy;
import com.launchdarkly.eventsource.background.BackgroundEventSource;
import com.launchdarkly.eventsource.background.ConnectionErrorHandler.Action;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.remote.BeaconNodeReadinessChannel;
import tech.pegasys.teku.validator.remote.BeaconNodeReadinessManager;
import tech.pegasys.teku.validator.remote.RemoteValidatorApiChannel;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

public class EventSourceBeaconChainEventAdapter
    implements BeaconChainEventAdapter, BeaconNodeReadinessChannel {

  private static final Logger LOG = LogManager.getLogger();

  private static final Duration MAX_RECONNECT_TIME = Duration.ofSeconds(12);

  private final CountDownLatch runningLatch = new CountDownLatch(1);

  private volatile BackgroundEventSource eventSource;
  @VisibleForTesting volatile RemoteValidatorApiChannel currentBeaconNodeUsedForEventStreaming;

  private final BeaconNodeReadinessManager beaconNodeReadinessManager;
  private final RemoteValidatorApiChannel primaryBeaconNodeApi;
  private final List<? extends RemoteValidatorApiChannel> failoverBeaconNodeApis;
  private final OkHttpClient okHttpClient;
  private final ValidatorLogger validatorLogger;
  private final BeaconChainEventAdapter timeBasedEventAdapter;
  private final EventSourceHandler eventSourceHandler;

  private final boolean shutdownWhenValidatorSlashedEnabled;

  public EventSourceBeaconChainEventAdapter(
      final BeaconNodeReadinessManager beaconNodeReadinessManager,
      final RemoteValidatorApiChannel primaryBeaconNodeApi,
      final List<? extends RemoteValidatorApiChannel> failoverBeaconNodeApis,
      final OkHttpClient okHttpClient,
      final ValidatorLogger validatorLogger,
      final BeaconChainEventAdapter timeBasedEventAdapter,
      final ValidatorTimingChannel validatorTimingChannel,
      final MetricsSystem metricsSystem,
      final boolean generateEarlyAttestations,
      final boolean shutdownWhenValidatorSlashedEnabled,
      final Spec spec) {
    this.beaconNodeReadinessManager = beaconNodeReadinessManager;
    this.primaryBeaconNodeApi = primaryBeaconNodeApi;
    this.failoverBeaconNodeApis = failoverBeaconNodeApis;
    this.okHttpClient = okHttpClient;
    this.validatorLogger = validatorLogger;
    this.timeBasedEventAdapter = timeBasedEventAdapter;
    this.eventSourceHandler =
        new EventSourceHandler(
            validatorTimingChannel, metricsSystem, generateEarlyAttestations, spec);
    this.shutdownWhenValidatorSlashedEnabled = shutdownWhenValidatorSlashedEnabled;
  }

  @Override
  public SafeFuture<Void> start() {
    // EventSource uses a daemon thread which allows the process to exit because all threads are
    // daemons, but while we're subscribed to events we should just wait for the next event, not
    // exit.  So create a non-daemon thread that lives until the adapter is stopped.
    eventSource = createEventSource(primaryBeaconNodeApi);
    currentBeaconNodeUsedForEventStreaming = primaryBeaconNodeApi;
    eventSource.start();
    new Thread(this::waitForExit).start();
    return timeBasedEventAdapter.start();
  }

  @Override
  public SafeFuture<Void> stop() {
    return SafeFuture.of(
            () -> {
              eventSource.close();
              return timeBasedEventAdapter.stop();
            })
        .thenRun(runningLatch::countDown);
  }

  @Override
  public void onPrimaryNodeNotReady() {
    if (currentEventStreamHasSameEndpoint(primaryBeaconNodeApi)) {
      switchToFailoverEventStreamIfAvailable();
    }
  }

  @Override
  @SuppressWarnings("FutureReturnValueIgnored")
  public void onFailoverNodeNotReady(final RemoteValidatorApiChannel failoverNotInSync) {
    if (currentEventStreamHasSameEndpoint(failoverNotInSync)) {
      if (failoverBeaconNodeApis.size() == 1 || !switchToFailoverEventStreamIfAvailable()) {
        // No failover switching is available, and we are currently connected to a failover node
        // with issues, so trigger the readiness check against the primary BN immediately
        beaconNodeReadinessManager.performPrimaryReadinessCheck();
      }
    }
  }

  @Override
  public void onPrimaryNodeReady() {
    if (!currentEventStreamHasSameEndpoint(primaryBeaconNodeApi)) {
      switchBackToPrimaryEventStream();
    }
  }

  @VisibleForTesting
  BackgroundEventSource createEventSource(final RemoteValidatorApiChannel beaconNodeApi) {

    final List<EventType> eventTypes = new ArrayList<>();
    eventTypes.add(EventType.head);
    eventTypes.add(EventType.head_v2);
    if (shutdownWhenValidatorSlashedEnabled) {
      eventTypes.add(EventType.attester_slashing);
      eventTypes.add(EventType.proposer_slashing);
    }
    final HttpUrl eventSourceUrl =
        createEventStreamSourceUrl(beaconNodeApi.getEndpoint(), eventTypes);

    final EventSource.Builder eventSourceBuilder =
        new EventSource.Builder(ConnectStrategy.http(eventSourceUrl).httpClient(okHttpClient))
            .retryDelayStrategy(
                RetryDelayStrategy.defaultStrategy()
                    .maxDelay(MAX_RECONNECT_TIME.toMillis(), TimeUnit.MILLISECONDS));
    return new BackgroundEventSource.Builder(eventSourceHandler, eventSourceBuilder)
        .connectionErrorHandler(
            __ -> {
              switchToFailoverEventStreamIfAvailable();
              return Action.PROCEED;
            })
        .build();
  }

  private HttpUrl createEventStreamSourceUrl(
      final HttpUrl endpoint, final List<EventType> eventTypes) {
    final HttpUrl eventSourceUrl =
        endpoint.resolve(
            ValidatorApiMethod.EVENTS.getPath(emptyMap())
                + "?topics="
                + String.join(",", eventTypes.stream().map(EventType::name).toList()));
    return Preconditions.checkNotNull(eventSourceUrl);
  }

  // synchronized because of the ConnectionErrorHandler and the BeaconNodeReadinessChannel callbacks
  private synchronized boolean switchToFailoverEventStreamIfAvailable() {
    if (failoverBeaconNodeApis.isEmpty()) {
      return false;
    }
    // No need to change anything if current node is READY
    if (beaconNodeReadinessManager.isReady(currentBeaconNodeUsedForEventStreaming)) {
      return false;
    }
    return findReadyFailoverAndSwitch();
  }

  private boolean findReadyFailoverAndSwitch() {
    final Optional<? extends RemoteValidatorApiChannel> readyFailover =
        failoverBeaconNodeApis.stream()
            .filter(beaconNodeReadinessManager::isReady)
            .filter(failover -> !currentEventStreamHasSameEndpoint(failover))
            .max(Comparator.comparing(beaconNodeReadinessManager::getReadinessStatusWeight));
    if (readyFailover.isPresent()) {
      switchToFailoverEventStream(readyFailover.get());
      return true;
    }
    validatorLogger.noFailoverBeaconNodesAvailableForEventStreaming();
    return false;
  }

  private void switchToFailoverEventStream(final RemoteValidatorApiChannel beaconNodeApi) {
    eventSource.close();
    eventSource = createEventSource(beaconNodeApi);
    currentBeaconNodeUsedForEventStreaming = beaconNodeApi;
    validatorLogger.switchingToFailoverBeaconNodeForEventStreaming(
        eventSource.getEventSource().getOrigin());
    eventSource.start();
  }

  private void switchBackToPrimaryEventStream() {
    eventSource.close();
    eventSource = createEventSource(primaryBeaconNodeApi);
    currentBeaconNodeUsedForEventStreaming = primaryBeaconNodeApi;
    validatorLogger.switchingBackToPrimaryBeaconNodeForEventStreaming();
    eventSource.start();
  }

  private boolean currentEventStreamHasSameEndpoint(final RemoteValidatorApiChannel beaconNodeApi) {
    return Objects.equals(
        currentBeaconNodeUsedForEventStreaming.getEndpoint(), beaconNodeApi.getEndpoint());
  }

  private void waitForExit() {
    while (true) {
      try {
        runningLatch.await();
      } catch (InterruptedException e) {
        LOG.debug("Interrupted while waiting for shutdown");
      }
    }
  }
}
