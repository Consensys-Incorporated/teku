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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.api.response.EventType;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.api.required.SyncingStatus;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.remote.BeaconNodeReadinessManager;
import tech.pegasys.teku.validator.remote.RemoteValidatorApiChannel;
import tech.pegasys.teku.validator.remote.apiclient.ValidatorApiMethod;

public class EventSourceBeaconChainEventAdapterTest {

  @SuppressWarnings("unchecked")
  private final LabelledMetric<Counter> labelledMetricMock =
      (LabelledMetric<Counter>) mock(LabelledMetric.class);

  final MetricsSystem metricsSystemMock = mock(MetricsSystem.class);
  final HttpUrl httpUrlMock = mock(HttpUrl.class);
  final URI uriMock = mock(URI.class);
  final RemoteValidatorApiChannel beaconApiMock = mock(RemoteValidatorApiChannel.class);

  @BeforeEach
  public void setUp() {
    when(metricsSystemMock.createLabelledCounter(any(), any(), any(), any()))
        .thenReturn(labelledMetricMock);
    when(httpUrlMock.resolve(any())).thenReturn(httpUrlMock);
    when(uriMock.getScheme()).thenReturn("http");
    when(httpUrlMock.uri()).thenReturn(uriMock);
    when(beaconApiMock.getEndpoint()).thenReturn(httpUrlMock);
  }

  @ParameterizedTest(name = "subscribe_to_slashing_events: {0}")
  @ValueSource(strings = {"true", "false"})
  public void shouldSubscribeToSlashingEvents(final boolean shutdownWhenValidatorSlashedEnabled) {
    final EventSourceBeaconChainEventAdapter eventSourceBeaconChainEventAdapter =
        initEventSourceBeaconChainEventAdapter(shutdownWhenValidatorSlashedEnabled);
    eventSourceBeaconChainEventAdapter.createEventSource(beaconApiMock);
    verifyEventSourceSubscriptionUrl(httpUrlMock, shutdownWhenValidatorSlashedEnabled);
  }

  @Test
  public void performsPrimaryReadinessCheckWhenFailoverNotReadyAndNoOtherFailoversAvailable() {
    final BeaconNodeReadinessManager beaconNodeReadinessManager =
        mock(BeaconNodeReadinessManager.class);
    final RemoteValidatorApiChannel failover = mock(RemoteValidatorApiChannel.class);
    final EventSourceBeaconChainEventAdapter eventSourceBeaconChainEventAdapter =
        new EventSourceBeaconChainEventAdapter(
            beaconNodeReadinessManager,
            mock(RemoteValidatorApiChannel.class),
            List.of(failover),
            mock(OkHttpClient.class),
            mock(ValidatorLogger.class),
            mock(BeaconChainEventAdapter.class),
            mock(ValidatorTimingChannel.class),
            metricsSystemMock,
            true,
            false,
            mock(Spec.class));

    eventSourceBeaconChainEventAdapter.currentBeaconNodeUsedForEventStreaming = failover;

    eventSourceBeaconChainEventAdapter.onFailoverNodeNotReady(failover);

    verify(beaconNodeReadinessManager).performPrimaryReadinessCheck();
  }

  @Test
  public void doNotSwitchToFailoverWhenCurrentBeaconNodeIsReady() {
    final BeaconNodeReadinessManager beaconNodeReadinessManager =
        mock(BeaconNodeReadinessManager.class);
    final RemoteValidatorApiChannel primaryNode = mock(RemoteValidatorApiChannel.class);
    final RemoteValidatorApiChannel failover1 = mock(RemoteValidatorApiChannel.class);
    final RemoteValidatorApiChannel failover2 = mock(RemoteValidatorApiChannel.class);
    final EventSourceBeaconChainEventAdapter eventSourceBeaconChainEventAdapter =
        new EventSourceBeaconChainEventAdapter(
            beaconNodeReadinessManager,
            primaryNode,
            List.of(failover1, failover2),
            mock(OkHttpClient.class),
            mock(ValidatorLogger.class),
            mock(BeaconChainEventAdapter.class),
            mock(ValidatorTimingChannel.class),
            metricsSystemMock,
            true,
            false,
            mock(Spec.class));

    eventSourceBeaconChainEventAdapter.currentBeaconNodeUsedForEventStreaming = failover1;

    when(beaconNodeReadinessManager.isReady(failover1)).thenReturn(true);
    final SafeFuture<SyncingStatus> someFuture = new SafeFuture<>();
    when(primaryNode.getSyncingStatus()).thenReturn(someFuture);
    eventSourceBeaconChainEventAdapter.onFailoverNodeNotReady(failover1);

    verify(beaconNodeReadinessManager).isReady(failover1);
    // Shouldn't try failover2 when failover1 is good
    verify(beaconNodeReadinessManager, never()).isReady(failover2);
    verify(beaconNodeReadinessManager, never()).getReadinessStatusWeight(failover2);

    // But will try to return to primaryNode when it's possible
    verify(beaconNodeReadinessManager).performPrimaryReadinessCheck();
  }

  private EventSourceBeaconChainEventAdapter initEventSourceBeaconChainEventAdapter(
      final boolean shutdownWhenValidatorSlashedEnabled) {
    return new EventSourceBeaconChainEventAdapter(
        mock(BeaconNodeReadinessManager.class),
        mock(RemoteValidatorApiChannel.class),
        new ArrayList<>(),
        mock(OkHttpClient.class),
        mock(ValidatorLogger.class),
        mock(BeaconChainEventAdapter.class),
        mock(ValidatorTimingChannel.class),
        metricsSystemMock,
        true,
        shutdownWhenValidatorSlashedEnabled,
        mock(Spec.class));
  }

  public void verifyEventSourceSubscriptionUrl(
      final HttpUrl endpoint, final boolean shutdownWhenValidatorSlashedEnabled) {
    Stream<EventType> eventTypes =
        shutdownWhenValidatorSlashedEnabled
            ? Stream.of(
                EventType.head,
                EventType.head_v2,
                EventType.attester_slashing,
                EventType.proposer_slashing)
            : Stream.of(EventType.head, EventType.head_v2);
    verify(endpoint)
        .resolve(
            ValidatorApiMethod.EVENTS.getPath(emptyMap())
                + "?topics="
                + String.join(",", eventTypes.map(EventType::name).toList()));
  }
}
