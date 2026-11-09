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

package tech.pegasys.teku.validator.eventadapter;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconChainEventAdapter;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.ForkAwareTimeBasedEventAdapter;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.beaconnode.metrics.MetricRecordingValidatorApiChannel;

public class InProcessBeaconNodeApi implements BeaconNodeApi {

  private final ValidatorApiChannel validatorApiChannel;
  private final BeaconChainEventAdapter beaconChainEventAdapter;

  private InProcessBeaconNodeApi(
      final ValidatorApiChannel validatorApiChannel,
      final BeaconChainEventAdapter beaconChainEventAdapter) {
    this.validatorApiChannel = validatorApiChannel;
    this.beaconChainEventAdapter = beaconChainEventAdapter;
  }

  public static BeaconNodeApi create(
      final ServiceConfig services,
      final ValidatorConfig validatorConfig,
      final AsyncRunner asyncRunner,
      final Spec spec) {
    final MetricsSystem metricsSystem = services.getMetricsSystem();
    final EventChannels eventChannels = services.getEventChannels();
    final ValidatorApiChannel validatorApiChannel =
        new MetricRecordingValidatorApiChannel(
            metricsSystem, eventChannels.getPublisher(ValidatorApiChannel.class, asyncRunner));
    final ValidatorTimingChannel validatorTimingChannel =
        eventChannels.getPublisher(ValidatorTimingChannel.class);
    final ForkAwareTimeBasedEventAdapter timeBasedEventAdapter =
        new ForkAwareTimeBasedEventAdapter(
            new GenesisDataProvider(asyncRunner, validatorApiChannel),
            new RepeatingTaskScheduler(asyncRunner, services.getTimeProvider()),
            services.getTimeProvider(),
            validatorTimingChannel,
            spec);
    final BeaconChainEventAdapter beaconChainEventAdapter =
        new IndependentTimerEventChannelEventAdapter(
            eventChannels,
            validatorConfig.generateEarlyAttestations(),
            timeBasedEventAdapter,
            validatorTimingChannel);
    return new InProcessBeaconNodeApi(validatorApiChannel, beaconChainEventAdapter);
  }

  @Override
  public SafeFuture<Void> subscribeToEvents() {
    return beaconChainEventAdapter.start();
  }

  @Override
  public SafeFuture<Void> unsubscribeFromEvents() {
    return beaconChainEventAdapter.stop();
  }

  @Override
  public ValidatorApiChannel getValidatorApi() {
    return validatorApiChannel;
  }
}
