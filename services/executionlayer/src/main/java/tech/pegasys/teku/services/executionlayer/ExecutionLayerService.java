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

package tech.pegasys.teku.services.executionlayer;

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.ethereum.executionclient.IpcSocketExecutionEngineClient.IPC_READER_ASYNC_RUNNER_NAME;
import static tech.pegasys.teku.ethereum.executionclient.IpcSocketExecutionEngineClient.IPC_READER_MAX_THREADS;
import static tech.pegasys.teku.infrastructure.logging.EventLogger.EVENT_LOG;
import static tech.pegasys.teku.spec.config.Constants.BUILDER_CALL_TIMEOUT;
import static tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.PREVIOUS_STUB_ENDPOINT_PREFIX;
import static tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.STUB_ENDPOINT_PREFIX;

import com.google.common.base.Splitter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.ethereum.events.ExecutionClientEventsChannel;
import tech.pegasys.teku.ethereum.events.SlotEventsChannel;
import tech.pegasys.teku.ethereum.executionclient.BuilderClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClient;
import tech.pegasys.teku.ethereum.executionclient.ExecutionEngineClientFactory;
import tech.pegasys.teku.ethereum.executionclient.OkHttpClientCreator;
import tech.pegasys.teku.ethereum.executionclient.auth.JwtConfig;
import tech.pegasys.teku.ethereum.executionclient.rest.RestClientProvider;
import tech.pegasys.teku.ethereum.executionlayer.BuilderBidValidatorImpl;
import tech.pegasys.teku.ethereum.executionlayer.BuilderCircuitBreaker;
import tech.pegasys.teku.ethereum.executionlayer.BuilderCircuitBreakerImpl;
import tech.pegasys.teku.ethereum.executionlayer.EngineCapabilitiesMonitor;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionClientHandler;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionClientHandlerImpl;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManager;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerImpl;
import tech.pegasys.teku.ethereum.executionlayer.ExecutionLayerManagerStub;
import tech.pegasys.teku.ethereum.executionlayer.MilestoneBasedEngineJsonRpcMethodsResolver;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;

public class ExecutionLayerService extends Service {

  private static final Logger LOG = LogManager.getLogger();

  private final EventChannels eventChannels;
  private final ExecutionLayerManager executionLayerManager;

  public static ExecutionLayerService create(
      final ServiceConfig serviceConfig,
      final ExecutionLayerConfiguration config,
      final Spec spec) {

    final Path beaconDataDirectory = serviceConfig.getDataDirLayout().getBeaconDataDirectory();
    final TimeProvider timeProvider = serviceConfig.getTimeProvider();

    final ExecutionClientEventsChannel executionClientEventsPublisher =
        serviceConfig.getEventChannels().getPublisher(ExecutionClientEventsChannel.class);

    final String engineEndpoint = config.getEngineEndpoint();
    if (engineEndpoint.startsWith(PREVIOUS_STUB_ENDPOINT_PREFIX)) {
      throw new InvalidConfigurationException(
          "Using the stub execution engine is unsafe. This is only designed for testing. Please use a real execution client.");
    }
    final boolean engineIsStub = engineEndpoint.startsWith(STUB_ENDPOINT_PREFIX);

    final Optional<RestClientProvider> builderRestClientProvider =
        config
            .getBuilderEndpoint()
            .map(
                builderEndpoint ->
                    RestClientProvider.create(
                        builderEndpoint,
                        BUILDER_CALL_TIMEOUT,
                        false,
                        Optional.empty(),
                        Optional.empty(),
                        beaconDataDirectory,
                        timeProvider));

    final boolean builderIsStub =
        builderRestClientProvider.map(RestClientProvider::isStub).orElse(false);

    checkState(
        engineIsStub == builderIsStub || builderRestClientProvider.isEmpty(),
        "mixed configuration with stubbed and non-stubbed execution layer endpoints is not supported");

    LOG.info("Using execution engine at {}", engineEndpoint);

    final BuilderCircuitBreaker builderCircuitBreaker;
    if (config.isBuilderCircuitBreakerEnabled()) {
      LOG.info("Enabling Builder Circuit Breaker");
      builderCircuitBreaker =
          new BuilderCircuitBreakerImpl(
              config.getSpec(),
              config.getBuilderCircuitBreakerWindow(),
              config.getBuilderCircuitBreakerAllowedFaults(),
              config.getBuilderCircuitBreakerAllowedConsecutiveFaults());
    } else {
      builderCircuitBreaker = BuilderCircuitBreaker.NOOP;
    }

    final ExecutionLayerManager executionLayerManager;
    if (engineIsStub) {
      executionLayerManager =
          createStubExecutionLayerManager(serviceConfig, config, builderCircuitBreaker);
    } else {
      final Optional<JwtConfig> jwtConfig =
          JwtConfig.createIfNeeded(
              true,
              config.getEngineJwtSecretFile(),
              config.getEngineJwtClaimId(),
              beaconDataDirectory);
      final Supplier<OkHttpClient> okHttpClientSupplier =
          () -> OkHttpClientCreator.create(LOG, jwtConfig, timeProvider);
      final ExecutionEngineClient engineApiClient =
          ExecutionEngineClientFactory.create(
              engineEndpoint,
              timeProvider,
              EVENT_LOG,
              executionClientEventsPublisher,
              okHttpClientSupplier,
              () ->
                  serviceConfig.createAsyncRunner(
                      IPC_READER_ASYNC_RUNNER_NAME, IPC_READER_MAX_THREADS));
      executionLayerManager =
          createRealExecutionLayerManager(
              serviceConfig,
              config,
              spec,
              engineApiClient,
              builderRestClientProvider,
              builderCircuitBreaker);
    }

    return new ExecutionLayerService(serviceConfig.getEventChannels(), executionLayerManager);
  }

  private static ExecutionLayerManager createStubExecutionLayerManager(
      final ServiceConfig serviceConfig,
      final ExecutionLayerConfiguration config,
      final BuilderCircuitBreaker builderCircuitBreaker) {
    EVENT_LOG.executionLayerStubEnabled();

    final List<String> endpointWithAdditionalConfigs =
        Splitter.on(':').splitToList(config.getEngineEndpoint());
    final List<String> additionalConfigs =
        endpointWithAdditionalConfigs.subList(1, endpointWithAdditionalConfigs.size());

    return new ExecutionLayerManagerStub(
        config.getSpec(),
        serviceConfig.getTimeProvider(),
        true,
        additionalConfigs,
        builderCircuitBreaker);
  }

  private static ExecutionLayerManager createRealExecutionLayerManager(
      final ServiceConfig serviceConfig,
      final ExecutionLayerConfiguration config,
      final Spec spec,
      final ExecutionEngineClient rawEngineClient,
      final Optional<RestClientProvider> builderRestClientProvider,
      final BuilderCircuitBreaker builderCircuitBreaker) {
    final MetricsSystem metricsSystem = serviceConfig.getMetricsSystem();
    final TimeProvider timeProvider = serviceConfig.getTimeProvider();

    final ExecutionEngineClient executionEngineClient =
        ExecutionLayerManagerImpl.createEngineClient(rawEngineClient, timeProvider, metricsSystem);

    final MilestoneBasedEngineJsonRpcMethodsResolver engineMethodsResolver =
        new MilestoneBasedEngineJsonRpcMethodsResolver(config.getSpec(), executionEngineClient);

    final ExecutionClientHandler executionClientHandler =
        new ExecutionClientHandlerImpl(
            config.getSpec(), executionEngineClient, engineMethodsResolver);

    if (config.isExchangeCapabilitiesMonitoringEnabled()) {
      final EngineCapabilitiesMonitor engineCapabilitiesMonitor =
          new EngineCapabilitiesMonitor(
              config.getSpec(), EVENT_LOG, engineMethodsResolver, executionEngineClient);
      serviceConfig
          .getEventChannels()
          .subscribe(SlotEventsChannel.class, engineCapabilitiesMonitor);
    }

    final Optional<BuilderClient> builderClient =
        builderRestClientProvider.map(
            restClientProvider ->
                ExecutionLayerManagerImpl.createBuilderClient(
                    restClientProvider.getRestClient(),
                    config.getSpec(),
                    timeProvider,
                    metricsSystem,
                    config.getBuilderSetUserAgentHeader()));

    return ExecutionLayerManagerImpl.create(
        EVENT_LOG,
        executionClientHandler,
        spec,
        builderClient,
        metricsSystem,
        new BuilderBidValidatorImpl(config.getSpec(), EVENT_LOG),
        builderCircuitBreaker,
        config.getBuilderBidCompareFactor(),
        config.getUseShouldOverrideBuilderFlag());
  }

  ExecutionLayerService(
      final EventChannels eventChannels, final ExecutionLayerManager executionLayerManager) {
    this.eventChannels = eventChannels;
    this.executionLayerManager = executionLayerManager;
  }

  @Override
  protected SafeFuture<?> doStart() {
    eventChannels
        .subscribe(SlotEventsChannel.class, executionLayerManager)
        .subscribe(ExecutionLayerChannel.class, executionLayerManager);
    return SafeFuture.COMPLETE;
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.COMPLETE;
  }
}
