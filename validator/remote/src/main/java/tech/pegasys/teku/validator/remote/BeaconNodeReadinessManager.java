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

package tech.pegasys.teku.validator.remote;

import com.google.common.collect.Maps;
import com.google.common.collect.Streams;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import okhttp3.HttpUrl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.ethereum.json.types.node.PeerCount;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

/**
 * If there are failovers configured, this class will track the readiness of every Beacon Node by
 * using the <a
 * href="https://ethereum.github.io/beacon-APIs/#/ValidatorRequiredApi/getSyncingStatus">Syncing
 * Status API</a>
 */
public class BeaconNodeReadinessManager extends Service implements ValidatorTimingChannel {

  private static final Logger LOG = LogManager.getLogger();
  private static final UInt64 MIN_REQUIRED_CONNECTED_PEER_COUNT = UInt64.valueOf(50);
  private static final UInt64 ERRORED_SECONDARY_READINESS_CHECK_INTERVAL_DELAY_MS =
      UInt64.valueOf(60_000);

  private final Map<RemoteValidatorApiChannel, ReadinessWithErrorTimestamp> readinessStatusCache =
      Maps.newConcurrentMap();

  private final Map<RemoteValidatorApiChannel, SafeFuture<Void>> inflightReadinessCheckFutures =
      Collections.synchronizedMap(Maps.newHashMap());

  private final AtomicBoolean latestPrimaryNodeReadiness = new AtomicBoolean(true);

  private final RemoteValidatorApiChannel primaryBeaconNodeApi;
  private final List<? extends RemoteValidatorApiChannel> failoverBeaconNodeApis;
  private final ValidatorLogger validatorLogger;
  private final BeaconNodeReadinessChannel beaconNodeReadinessChannel;
  private final TimeProvider timeProvider;

  public BeaconNodeReadinessManager(
      final TimeProvider timeProvider,
      final RemoteValidatorApiChannel primaryBeaconNodeApi,
      final List<? extends RemoteValidatorApiChannel> failoverBeaconNodeApis,
      final ValidatorLogger validatorLogger,
      final BeaconNodeReadinessChannel beaconNodeReadinessChannel) {
    this.timeProvider = timeProvider;
    this.primaryBeaconNodeApi = primaryBeaconNodeApi;
    this.failoverBeaconNodeApis = failoverBeaconNodeApis;
    this.validatorLogger = validatorLogger;
    this.beaconNodeReadinessChannel = beaconNodeReadinessChannel;
  }

  public boolean isReady(final RemoteValidatorApiChannel beaconNodeApi) {
    return getReadiness(beaconNodeApi).isReady();
  }

  public int getReadinessStatusWeight(final RemoteValidatorApiChannel beaconNodeApi) {
    return getReadiness(beaconNodeApi).readiness.weight;
  }

  public Iterator<? extends RemoteValidatorApiChannel> getFailoversInOrderOfReadiness() {
    return failoverBeaconNodeApis.stream()
        .sorted(Comparator.comparing(this::getReadinessStatusWeight).reversed())
        .iterator();
  }

  public SafeFuture<Void> performPrimaryReadinessCheck() {
    return performReadinessCheckWithInflightCheck(primaryBeaconNodeApi);
  }

  @Override
  protected SafeFuture<?> doStart() {
    return performReadinessCheckAgainstAllNodes();
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }

  @Override
  public void onAttestationAggregationDue(final UInt64 slot) {
    performReadinessCheckAgainstAllNodes().finishError(LOG);
  }

  private ReadinessWithErrorTimestamp getReadiness(final RemoteValidatorApiChannel beaconNodeApi) {
    return readinessStatusCache.computeIfAbsent(
        beaconNodeApi,
        beacon -> {
          if (isPrimary(beacon)) {
            return ReadinessWithErrorTimestamp.READY;
          }
          if (failoverBeaconNodeApis.contains(beacon)) {
            return ReadinessWithErrorTimestamp.NOT_READY;
          }
          // we don't monitor this node, let's default to READY
          return ReadinessWithErrorTimestamp.READY;
        });
  }

  private SafeFuture<Void> performReadinessCheckAgainstAllNodes() {
    // no readiness check needed when no failovers are configured
    if (failoverBeaconNodeApis.isEmpty()) {
      return SafeFuture.COMPLETE;
    }
    final SafeFuture<Void> primaryReadinessCheck = performPrimaryReadinessCheck();
    final Stream<SafeFuture<?>> failoverReadinessChecks =
        failoverBeaconNodeApis.stream().map(this::performFailoverReadinessCheck);
    return SafeFuture.allOf(
        Streams.concat(Stream.of(primaryReadinessCheck), failoverReadinessChecks));
  }

  private SafeFuture<Void> performFailoverReadinessCheck(final RemoteValidatorApiChannel failover) {
    return performReadinessCheckWithInflightCheck(failover);
  }

  private SafeFuture<Void> performReadinessCheckWithInflightCheck(
      final RemoteValidatorApiChannel beaconNodeApi) {
    return inflightReadinessCheckFutures.compute(
        beaconNodeApi,
        (beacon, future) -> {
          if (future != null && !future.isDone()) {
            LOG.debug("Readiness check for {} is already in progress", beaconNodeApi.getEndpoint());
            return future;
          }
          return performReadinessCheck(beacon);
        });
  }

  private boolean isTimeToPerformReadinessCheck(final RemoteValidatorApiChannel beaconNodeApi) {
    if (isPrimary(beaconNodeApi)) {
      return true;
    }

    final Optional<UInt64> lastErrorTimestamp =
        Optional.ofNullable(readinessStatusCache.get(beaconNodeApi))
            .flatMap(ReadinessWithErrorTimestamp::lastErrorTimestamp);
    if (lastErrorTimestamp.isEmpty()) {
      // not previously errored, so we can perform the check
      return true;
    }
    final UInt64 currentTime = timeProvider.getTimeInMillis();
    return currentTime.isGreaterThanOrEqualTo(
        lastErrorTimestamp.get().plus(ERRORED_SECONDARY_READINESS_CHECK_INTERVAL_DELAY_MS));
  }

  @SuppressWarnings("ReferenceComparison")
  private boolean isPrimary(final RemoteValidatorApiChannel beaconNodeApi) {
    return beaconNodeApi == primaryBeaconNodeApi;
  }

  private SafeFuture<Void> performReadinessCheck(final RemoteValidatorApiChannel beaconNodeApi) {
    final HttpUrl beaconNodeApiEndpoint = beaconNodeApi.getEndpoint();

    LOG.debug("Performing beacon node readiness check for {}", beaconNodeApiEndpoint);
    if (!isTimeToPerformReadinessCheck(beaconNodeApi)) {
      LOG.debug(
          "Skipping readiness check for {} as it was already checked recently",
          beaconNodeApiEndpoint);
      return SafeFuture.COMPLETE;
    }

    return beaconNodeApi
        .getSyncingStatus()
        .thenCombine(
            beaconNodeApi.getPeerCount(),
            (syncingStatus, peerCount) -> {
              if (syncingStatus.isReady()) {
                LOG.debug(
                    "{} is ready to accept requests: {}", beaconNodeApiEndpoint, syncingStatus);
                final boolean optimistic = syncingStatus.getIsOptimistic().orElse(false);
                if (optimistic) {
                  return ReadinessWithErrorTimestamp.READY_OPTIMISTIC;
                }
                if (!hasEnoughConnectedPeers(peerCount)) {
                  return ReadinessWithErrorTimestamp.READY_NOT_ENOUGH_PEERS;
                }
                return ReadinessWithErrorTimestamp.READY;
              }
              LOG.debug(
                  "{} is NOT ready to accept requests: {}", beaconNodeApiEndpoint, syncingStatus);
              return ReadinessWithErrorTimestamp.NOT_READY;
            })
        .exceptionally(
            throwable -> {
              LOG.debug(
                  "{} is NOT ready to accept requests because the syncing status request failed: {}",
                  beaconNodeApiEndpoint,
                  throwable);
              return ReadinessWithErrorTimestamp.errored(timeProvider.getTimeInMillis());
            })
        .thenAccept(
            readinessStatus -> {
              readinessStatusCache.put(beaconNodeApi, readinessStatus);
              if (readinessStatus.isReady()) {
                processReadyResult(isPrimary(beaconNodeApi));
              } else {
                processNotReadyResult(beaconNodeApi);
              }
            });
  }

  private static boolean hasEnoughConnectedPeers(final Optional<PeerCount> peerCount) {
    return peerCount
        .map(PeerCount::getConnected)
        .orElse(UInt64.ZERO)
        .isGreaterThanOrEqualTo(MIN_REQUIRED_CONNECTED_PEER_COUNT);
  }

  private void processReadyResult(final boolean isPrimaryNode) {
    if (!isPrimaryNode) {
      return;
    }
    // Filtering of duplicates if needed happens on receiver's side
    beaconNodeReadinessChannel.onPrimaryNodeReady();
    if (latestPrimaryNodeReadiness.compareAndSet(false, true)) {
      validatorLogger.primaryBeaconNodeIsBackAndReady();
    }
  }

  private void processNotReadyResult(final RemoteValidatorApiChannel beaconNodeApi) {
    if (isPrimary(beaconNodeApi)) {
      if (latestPrimaryNodeReadiness.compareAndSet(true, false)) {
        validatorLogger.primaryBeaconNodeNotReady();
      }
      beaconNodeReadinessChannel.onPrimaryNodeNotReady();
    } else {
      beaconNodeReadinessChannel.onFailoverNodeNotReady(beaconNodeApi);
    }
  }

  private enum Readiness {
    ERRORED(-1),
    NOT_READY(0),
    READY_OPTIMISTIC(1),
    READY_NOT_ENOUGH_PEERS(2),
    READY(3);

    private final int weight;

    Readiness(final int weight) {
      this.weight = weight;
    }

    boolean isReady() {
      return this.weight > 0;
    }
  }

  private record ReadinessWithErrorTimestamp(
      Readiness readiness, Optional<UInt64> lastErrorTimestamp) {
    static ReadinessWithErrorTimestamp errored(final UInt64 errorTimestamp) {
      return new ReadinessWithErrorTimestamp(Readiness.ERRORED, Optional.of(errorTimestamp));
    }

    static final ReadinessWithErrorTimestamp READY =
        new ReadinessWithErrorTimestamp(Readiness.READY, Optional.empty());
    static final ReadinessWithErrorTimestamp NOT_READY =
        new ReadinessWithErrorTimestamp(Readiness.NOT_READY, Optional.empty());
    static final ReadinessWithErrorTimestamp READY_OPTIMISTIC =
        new ReadinessWithErrorTimestamp(Readiness.READY_OPTIMISTIC, Optional.empty());
    static final ReadinessWithErrorTimestamp READY_NOT_ENOUGH_PEERS =
        new ReadinessWithErrorTimestamp(Readiness.READY_NOT_ENOUGH_PEERS, Optional.empty());

    public boolean isReady() {
      return readiness.isReady();
    }
  }
}
