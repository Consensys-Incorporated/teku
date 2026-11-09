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

package tech.pegasys.teku;

import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.infrastructure.time.SystemTimeProvider.SYSTEM_TIME_PROVIDER;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.MAX_EPOCHS_STORE_BLOBS;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import oshi.SystemInfo;
import tech.pegasys.teku.beaconrestapi.BeaconRestApiConfig;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.data.publisher.MetricsPublisherManager;
import tech.pegasys.teku.infrastructure.async.AsyncRunnerFactory;
import tech.pegasys.teku.infrastructure.async.Cancellable;
import tech.pegasys.teku.infrastructure.async.MetricTrackingExecutorFactory;
import tech.pegasys.teku.infrastructure.async.OccurrenceCounter;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.logging.StartupLogConfig;
import tech.pegasys.teku.infrastructure.metrics.MetricsEndpoint;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.networks.Eth2NetworkConfiguration;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.services.ServiceController;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;

public abstract class AbstractNode implements Node {
  private static final Logger LOG = LogManager.getLogger();

  private final ExecutorService threadPool =
      Executors.newCachedThreadPool(
          new ThreadFactoryBuilder().setDaemon(true).setNameFormat("events-%d").build());

  private final OccurrenceCounter rejectedExecutionCounter = new OccurrenceCounter(120);

  private Optional<Cancellable> counterMaintainer = Optional.empty();

  private final AsyncRunnerFactory asyncRunnerFactory;
  private final EventChannels eventChannels;
  private final MetricsEndpoint metricsEndpoint;
  private final MetricsPublisherManager metricsPublisher;
  protected final ServiceConfig serviceConfig;

  protected AbstractNode(final TekuConfiguration tekuConfig) {
    final String network =
        tekuConfig
            .eth2NetworkConfiguration()
            .getEth2Network()
            .map(Eth2Network::configName)
            .orElse("empty");
    final String storageMode = tekuConfig.storageConfiguration().getDataStorageMode().name();
    final BeaconRestApiConfig beaconChainRestApiConfig =
        tekuConfig.beaconChain().beaconRestApiConfig();
    final ValidatorRestApiConfig validatorRestApiConfig = tekuConfig.validatorRestApiConfig();

    STATUS_LOG.onStartup(VersionProvider.VERSION);
    STATUS_LOG.startupConfigurations(
        StartupLogConfig.builder()
            .network(network)
            .storageMode(storageMode)
            .hardwareInfo(new SystemInfo().getHardware())
            .maxHeapSize(Runtime.getRuntime().maxMemory())
            .beaconChainRestApiEnabled(beaconChainRestApiConfig.isRestApiEnabled())
            .beaconChainRestApiInterface(beaconChainRestApiConfig.getRestApiInterface())
            .beaconChainRestApiPort(beaconChainRestApiConfig.getRestApiPort())
            .beaconChainRestApiAllow(beaconChainRestApiConfig.getRestApiHostAllowlist())
            .validatorRestApiEnabled(validatorRestApiConfig.isRestApiEnabled())
            .validatorRestApiInterface(validatorRestApiConfig.getRestApiInterface())
            .validatorRestApiPort(validatorRestApiConfig.getRestApiPort())
            .validatorRestApiAllow(validatorRestApiConfig.getRestApiHostAllowlist())
            .build());

    reportOverrides(tekuConfig);
    this.metricsEndpoint = new MetricsEndpoint(tekuConfig.metricsConfig());
    final MetricsSystem metricsSystem = metricsEndpoint.getMetricsSystem();
    final TekuDefaultExceptionHandler subscriberExceptionHandler =
        new TekuDefaultExceptionHandler();
    this.eventChannels =
        new EventChannels(
            subscriberExceptionHandler,
            metricsSystem,
            tekuConfig.beaconNodeConfig().eventChannelVirtualThreadsEnabled());

    asyncRunnerFactory =
        AsyncRunnerFactory.createDefault(
            new MetricTrackingExecutorFactory(metricsSystem, rejectedExecutionCounter));
    final DataDirLayout dataDirLayout = DataDirLayout.createFrom(tekuConfig.dataConfig());
    ValidatorConfig validatorConfig = tekuConfig.validatorClient().getValidatorConfig();

    serviceConfig =
        new ServiceConfig(
            asyncRunnerFactory,
            SYSTEM_TIME_PROVIDER,
            eventChannels,
            metricsSystem,
            dataDirLayout,
            rejectedExecutionCounter::getTotalCount,
            validatorConfig::getExecutorThreads);
    this.metricsPublisher =
        new MetricsPublisherManager(
            asyncRunnerFactory,
            serviceConfig.getTimeProvider(),
            metricsEndpoint,
            dataDirLayout.getBeaconDataDirectory().toFile());
  }

  private void reportOverrides(final TekuConfiguration tekuConfig) {

    for (SpecMilestone specMilestone : SpecMilestone.values()) {
      tekuConfig
          .eth2NetworkConfiguration()
          .getForkEpoch(specMilestone)
          .ifPresent(forkEpoch -> STATUS_LOG.warnForkEpochChanged(specMilestone.name(), forkEpoch));
    }

    reportBellatrixMergeOverrides(tekuConfig);

    // Deneb's epochsStoreBlobs warning, which is not actually a full override
    final Optional<SpecVersion> specVersionDeneb =
        Optional.ofNullable(
            tekuConfig.eth2NetworkConfiguration().getSpec().forMilestone(SpecMilestone.DENEB));
    specVersionDeneb
        .flatMap(__ -> tekuConfig.eth2NetworkConfiguration().getEpochsStoreBlobs())
        .ifPresent(
            epochsStoreBlobsInput -> {
              final SpecConfigDeneb specConfigDeneb =
                  SpecConfigDeneb.required(specVersionDeneb.get().getConfig());
              STATUS_LOG.warnDenebEpochsStoreBlobsParameterSet(
                  epochsStoreBlobsInput.toString(),
                  specConfigDeneb.getEpochsStoreBlobs()
                      != specConfigDeneb.getMinEpochsForBlobSidecarsRequests(),
                  String.valueOf(specConfigDeneb.getMinEpochsForBlobSidecarsRequests()),
                  MAX_EPOCHS_STORE_BLOBS);
            });
  }

  private void reportBellatrixMergeOverrides(final TekuConfiguration tekuConfig) {
    tekuConfig
        .eth2NetworkConfiguration()
        .getTotalTerminalDifficultyOverride()
        .ifPresent(
            ttdo ->
                STATUS_LOG.warnBellatrixParameterChanged(
                    "TERMINAL_TOTAL_DIFFICULTY", ttdo.toString()));

    final Optional<Eth2Network> maybeEth2Network =
        tekuConfig.eth2NetworkConfiguration().getEth2Network();

    final boolean reportTerminalBlockHashOverride;
    final boolean reportTerminalBlockHashEpochOverride;
    if (maybeEth2Network.isEmpty()) {
      reportTerminalBlockHashOverride = true;
      reportTerminalBlockHashEpochOverride = true;
    } else {
      final Eth2NetworkConfiguration defaultConfiguration = tekuConfig.eth2NetworkConfiguration();
      reportTerminalBlockHashOverride =
          !defaultConfiguration
              .getTerminalBlockHashOverride()
              .equals(tekuConfig.eth2NetworkConfiguration().getTerminalBlockHashOverride());
      reportTerminalBlockHashEpochOverride =
          !defaultConfiguration
              .getTerminalBlockHashEpochOverride()
              .equals(tekuConfig.eth2NetworkConfiguration().getTerminalBlockHashEpochOverride());
    }

    if (reportTerminalBlockHashOverride) {
      tekuConfig
          .eth2NetworkConfiguration()
          .getTerminalBlockHashOverride()
          .ifPresent(
              tbho ->
                  STATUS_LOG.warnBellatrixParameterChanged("TERMINAL_BLOCK_HASH", tbho.toString()));
    }

    if (reportTerminalBlockHashEpochOverride) {
      tekuConfig
          .eth2NetworkConfiguration()
          .getTerminalBlockHashEpochOverride()
          .ifPresent(
              tbheo ->
                  STATUS_LOG.warnBellatrixParameterChanged(
                      "TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH", tbheo.toString()));
    }
  }

  @Override
  public abstract ServiceController getServiceController();

  @Override
  public void start() {
    metricsEndpoint.start().join();
    metricsPublisher.start().join();
    getServiceController().start().join();
    counterMaintainer =
        Optional.of(
            serviceConfig
                .createAsyncRunner("RejectedExecutionCounter", 1)
                .runWithFixedDelay(
                    this::pollRejectedExecutions,
                    Duration.ofSeconds(5),
                    (err) -> LOG.debug("rejected execution poll failed", err)));
  }

  private void pollRejectedExecutions() {
    final int rejectedExecutions = rejectedExecutionCounter.poll();
    if (rejectedExecutions > 0) {
      LOG.trace("Rejected execution count from last 5 seconds: " + rejectedExecutions);
    }
  }

  @Override
  public void stop() {
    // Stop processing new events
    eventChannels
        .stop()
        .orTimeout(30, TimeUnit.SECONDS)
        .handleException(error -> LOG.warn("Failed to stop event channels cleanly", error))
        .join();

    threadPool.shutdownNow();
    counterMaintainer.ifPresent(Cancellable::cancel);

    // Stop async actions
    asyncRunnerFactory.shutdown();

    // Stop services. This includes closing the database.
    getServiceController()
        .stop()
        .orTimeout(30, TimeUnit.SECONDS)
        .handleException(error -> LOG.error("Failed to stop services", error))
        .thenCompose(__ -> metricsEndpoint.stop())
        .orTimeout(5, TimeUnit.SECONDS)
        .handleException(error -> LOG.debug("Failed to stop metrics", error))
        .join();
  }
}
