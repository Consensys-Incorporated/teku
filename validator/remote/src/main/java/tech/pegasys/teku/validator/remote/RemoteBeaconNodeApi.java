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

package tech.pegasys.teku.validator.remote;

import com.google.common.base.Preconditions;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.timed.RepeatingTaskScheduler;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
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
import tech.pegasys.teku.validator.remote.apiclient.OkHttpClientAuth;
import tech.pegasys.teku.validator.remote.eventsource.EventSourceBeaconChainEventAdapter;

public class RemoteBeaconNodeApi implements BeaconNodeApi {

  private static final Logger LOG = LogManager.getLogger();

  private static final String USER_AGENT_HEADER_VALUE =
      VersionProvider.CLIENT_IDENTITY + "/" + VersionProvider.IMPLEMENTATION_VERSION;

  /** Time until we timeout the event stream if no events are received. */
  public static final Duration EVENT_STREAM_READ_TIMEOUT = Duration.ofSeconds(60);

  public static final Duration REST_CALL_TIMEOUT = Duration.ofSeconds(10);

  public static final int MAX_API_EXECUTOR_QUEUE_SIZE = 5000;

  private final BeaconChainEventAdapter beaconChainEventAdapter;
  private final ValidatorApiChannel validatorApiChannel;
  private final BeaconNodeReadinessManager beaconNodeReadinessManager;

  private RemoteBeaconNodeApi(
      final BeaconChainEventAdapter beaconChainEventAdapter,
      final ValidatorApiChannel validatorApiChannel,
      final BeaconNodeReadinessManager beaconNodeReadinessManager) {
    this.beaconChainEventAdapter = beaconChainEventAdapter;
    this.validatorApiChannel = validatorApiChannel;
    this.beaconNodeReadinessManager = beaconNodeReadinessManager;
  }

  public static BeaconNodeApi create(
      final ServiceConfig services,
      final ValidatorConfig validatorConfig,
      final Spec spec,
      final List<URI> beaconNodeApiEndpoints) {
    Preconditions.checkArgument(
        !beaconNodeApiEndpoints.isEmpty(),
        "One or more Beacon Node endpoints should be defined for enabling remote connectivity "
            + "from VC to BN.");

    final OkHttpClient okHttpClient =
        createOkHttpClient(convertToOkHttpUrls(beaconNodeApiEndpoints));
    final RemoteBeaconNodeEndpoints remoteBeaconNodeEndpoints =
        new RemoteBeaconNodeEndpoints(beaconNodeApiEndpoints);
    final HttpUrl primaryEndpoint = remoteBeaconNodeEndpoints.getPrimaryEndpoint();
    final List<HttpUrl> failoverEndpoints = remoteBeaconNodeEndpoints.getFailoverEndpoints();

    final int remoteNodeCount = failoverEndpoints.size() + 1;

    final int apiMaxThreads = calculateAPIMaxThreads(remoteNodeCount, validatorConfig);
    final AsyncRunner asyncRunner =
        services.createAsyncRunner(
            "validatorBeaconAPI", apiMaxThreads, MAX_API_EXECUTOR_QUEUE_SIZE);

    final AsyncRunner readinessAsyncRunner;
    if (failoverEndpoints.isEmpty()) {
      readinessAsyncRunner = asyncRunner;
    } else {
      // Use a separate async runner for the readiness-related api calls, so that they do not
      // interfere with the critical path API calls.
      final int apiMaxReadinessThreads =
          calculateReadinessAPIMaxThreads(remoteNodeCount, validatorConfig);
      readinessAsyncRunner =
          services.createAsyncRunner(
              "validatorBeaconAPIReadiness", apiMaxReadinessThreads, MAX_API_EXECUTOR_QUEUE_SIZE);
    }

    final RemoteValidatorApiChannel primaryValidatorApi =
        RemoteValidatorApiHandler.create(
            primaryEndpoint,
            okHttpClient,
            spec,
            validatorConfig.isValidatorClientUseSszBlocksEnabled(),
            validatorConfig.isValidatorClientUsePostValidatorsEndpointEnabled(),
            asyncRunner,
            readinessAsyncRunner,
            validatorConfig.isAttestationsV2ApisEnabled());
    final List<? extends RemoteValidatorApiChannel> failoverValidatorApis =
        failoverEndpoints.stream()
            .map(
                endpoint ->
                    RemoteValidatorApiHandler.create(
                        endpoint,
                        okHttpClient,
                        spec,
                        validatorConfig.isValidatorClientUseSszBlocksEnabled(),
                        validatorConfig.isValidatorClientUsePostValidatorsEndpointEnabled(),
                        asyncRunner,
                        readinessAsyncRunner,
                        validatorConfig.isAttestationsV2ApisEnabled()))
            .toList();

    final EventChannels eventChannels = services.getEventChannels();
    final MetricsSystem metricsSystem = services.getMetricsSystem();

    if (!failoverEndpoints.isEmpty()) {
      LOG.info("Will use {} as failover Beacon Node endpoints", failoverEndpoints);
    }

    final BeaconNodeReadinessChannel beaconNodeReadinessChannel =
        eventChannels.getPublisher(BeaconNodeReadinessChannel.class);

    final ValidatorTimingChannel validatorTimingChannel =
        eventChannels.getPublisher(ValidatorTimingChannel.class);

    final BeaconNodeReadinessManager beaconNodeReadinessManager =
        new BeaconNodeReadinessManager(
            services.getTimeProvider(),
            primaryValidatorApi,
            failoverValidatorApis,
            ValidatorLogger.VALIDATOR_LOGGER,
            beaconNodeReadinessChannel);

    eventChannels.subscribe(ValidatorTimingChannel.class, beaconNodeReadinessManager);

    final ValidatorApiChannel validatorApi =
        new MetricRecordingValidatorApiChannel(
            metricsSystem,
            new FailoverValidatorApiHandler(
                beaconNodeReadinessManager,
                primaryValidatorApi,
                failoverValidatorApis,
                validatorConfig.isFailoversSendSubnetSubscriptionsEnabled(),
                validatorConfig.isFailoversPublishSignedDutiesEnabled(),
                metricsSystem));

    final EventSourceBeaconChainEventAdapter beaconChainEventAdapter =
        new EventSourceBeaconChainEventAdapter(
            beaconNodeReadinessManager,
            primaryValidatorApi,
            failoverValidatorApis,
            createOkHttpClientForStreamFromClient(okHttpClient),
            ValidatorLogger.VALIDATOR_LOGGER,
            new ForkAwareTimeBasedEventAdapter(
                new GenesisDataProvider(asyncRunner, validatorApi),
                new RepeatingTaskScheduler(asyncRunner, services.getTimeProvider()),
                services.getTimeProvider(),
                validatorTimingChannel,
                spec),
            validatorTimingChannel,
            metricsSystem,
            validatorConfig.generateEarlyAttestations(),
            validatorConfig.isShutdownWhenValidatorSlashedEnabled(),
            spec);

    eventChannels.subscribe(BeaconNodeReadinessChannel.class, beaconChainEventAdapter);

    return new RemoteBeaconNodeApi(
        beaconChainEventAdapter, validatorApi, beaconNodeReadinessManager);
  }

  public static int calculateAPIMaxThreads(
      final int remoteNodeCount, final ValidatorConfig validatorConfig) {
    if (validatorConfig.getBeaconApiExecutorThreads().isPresent()) {
      return validatorConfig.getBeaconApiExecutorThreads().getAsInt();
    }
    // Let's allow at least 4 parallel requests per remote node when publishing signed duties to
    // failovers, to reduce the risk of being affected by a slow remote node.
    final int remoteNodeCountMultiplier =
        validatorConfig.isFailoversPublishSignedDutiesEnabled() ? 4 : 2;
    return Math.max(5, remoteNodeCount * remoteNodeCountMultiplier);
  }

  public static int calculateReadinessAPIMaxThreads(
      final int remoteNodeCount, final ValidatorConfig validatorConfig) {
    if (validatorConfig.getBeaconApiReadinessExecutorThreads().isPresent()) {
      return validatorConfig.getBeaconApiReadinessExecutorThreads().getAsInt();
    }

    // we call two methods per remote node to check readiness, so we need at least 2 threads to call
    // them in parallel
    return remoteNodeCount * 2;
  }

  @Override
  public SafeFuture<Void> subscribeToEvents() {
    return beaconChainEventAdapter
        .start()
        .thenCompose(__ -> beaconNodeReadinessManager.start())
        .toVoid();
  }

  @Override
  public SafeFuture<Void> unsubscribeFromEvents() {
    return beaconChainEventAdapter
        .stop()
        .thenCompose(__ -> beaconNodeReadinessManager.stop())
        .toVoid();
  }

  @Override
  public ValidatorApiChannel getValidatorApi() {
    return validatorApiChannel;
  }

  public static List<HttpUrl> convertToOkHttpUrls(final List<URI> beaconNodeApiEndpoints) {
    return beaconNodeApiEndpoints.stream()
        .map(
            endpoint ->
                Preconditions.checkNotNull(
                    HttpUrl.get(endpoint),
                    "Failed to convert remote api endpoint (%s) to a valid url",
                    UrlSanitizer.sanitizePotentialUrl(endpoint.toString())))
        .toList();
  }

  public static OkHttpClient createOkHttpClientForStreamFromClient(final OkHttpClient client) {
    // call timeout must be disabled for event streams, we use read timeout instead
    return client
        .newBuilder()
        .callTimeout(Duration.ZERO)
        .readTimeout(EVENT_STREAM_READ_TIMEOUT)
        .build();
  }

  public static OkHttpClient createOkHttpClient(final List<HttpUrl> endpoints) {
    final OkHttpClient.Builder httpClientBuilder =
        new OkHttpClient.Builder().callTimeout(REST_CALL_TIMEOUT);
    if (endpoints.size() > 1) {
      OkHttpClientAuth.addAuthInterceptorForMultipleEndpoints(endpoints, httpClientBuilder);
    } else {
      OkHttpClientAuth.addAuthInterceptor(endpoints.get(0), httpClientBuilder);
    }
    addInterceptorForUserAgentHeader(httpClientBuilder);

    return httpClientBuilder.build();
  }

  private static void addInterceptorForUserAgentHeader(
      final OkHttpClient.Builder httpClientBuilder) {
    httpClientBuilder.addInterceptor(
        chain ->
            chain.proceed(
                chain
                    .request()
                    .newBuilder()
                    .header("User-Agent", USER_AGENT_HEADER_VALUE)
                    .build()));
  }
}
