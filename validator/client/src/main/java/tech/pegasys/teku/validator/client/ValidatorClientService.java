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

package tech.pegasys.teku.validator.client;

import static tech.pegasys.teku.infrastructure.exceptions.ExitConstants.FATAL_EXIT_CODE;
import static tech.pegasys.teku.infrastructure.logging.StatusLogger.STATUS_LOG;
import static tech.pegasys.teku.infrastructure.logging.ValidatorLogger.VALIDATOR_LOGGER;

import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.events.EventChannels;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.io.SyncDataAccessor;
import tech.pegasys.teku.infrastructure.io.SystemSignalListener;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.restapi.RestApi;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.infrastructure.version.VersionProvider;
import tech.pegasys.teku.service.serviceutils.Service;
import tech.pegasys.teku.service.serviceutils.ServiceConfig;
import tech.pegasys.teku.service.serviceutils.layout.DataDirLayout;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.signatures.LocalSlashingProtector;
import tech.pegasys.teku.spec.signatures.LocalSlashingProtectorConcurrentAccess;
import tech.pegasys.teku.spec.signatures.SlashingProtector;
import tech.pegasys.teku.validator.api.GraffitiManager;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.beaconnode.BeaconNodeApi;
import tech.pegasys.teku.validator.beaconnode.GenesisDataProvider;
import tech.pegasys.teku.validator.client.doppelganger.DoppelgangerDetector;
import tech.pegasys.teku.validator.client.duties.BeaconCommitteeSubscriptions;
import tech.pegasys.teku.validator.client.duties.BlockDutyFactory;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;
import tech.pegasys.teku.validator.client.duties.attestations.AggregationDuty;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationDutyFactory;
import tech.pegasys.teku.validator.client.duties.attestations.AttestationProductionDuty;
import tech.pegasys.teku.validator.client.duties.execution.ExecutionPayloadBidEventsChannel;
import tech.pegasys.teku.validator.client.duties.execution.ExecutionPayloadDuty;
import tech.pegasys.teku.validator.client.duties.payloadattestations.PayloadAttestationDutyFactory;
import tech.pegasys.teku.validator.client.duties.synccommittee.ChainHeadTracker;
import tech.pegasys.teku.validator.client.duties.synccommittee.SyncCommitteeScheduledDuties;
import tech.pegasys.teku.validator.client.loader.HttpClientExternalSignerFactory;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.loader.PublicKeyLoader;
import tech.pegasys.teku.validator.client.loader.SlashingProtectionLogger;
import tech.pegasys.teku.validator.client.loader.ValidatorLoader;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;
import tech.pegasys.teku.validator.client.proposerconfig.loader.ProposerConfigLoader;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApi;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;
import tech.pegasys.teku.validator.client.signer.BlockContainerSigner;
import tech.pegasys.teku.validator.client.signer.MilestoneBasedBlockContainerSigner;
import tech.pegasys.teku.validator.client.slashingriskactions.DoppelgangerDetectionAlert;
import tech.pegasys.teku.validator.client.slashingriskactions.SlashingRiskAction;
import tech.pegasys.teku.validator.eventadapter.InProcessBeaconNodeApi;
import tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi;
import tech.pegasys.teku.validator.remote.sentry.SentryBeaconNodeApi;
import tech.pegasys.teku.validator.remote.sentry.SentryNodesConfig;
import tech.pegasys.teku.validator.remote.sentry.SentryNodesConfigLoader;

public class ValidatorClientService extends Service {

  private static final Logger LOG = LogManager.getLogger();
  private static final Duration DOPPELGANGER_DETECTOR_CHECK_DELAY = Duration.ofSeconds(12);
  private static final Duration DOPPELGANGER_DETECTOR_TIMEOUT = Duration.ofMinutes(15);
  private static final int DOPPELGANGER_DETECTOR_MAX_EPOCHS = 2;
  private static final int MIN_SIZE_TO_SCHEDULE_ATTESTATION_DUTIES_IN_BATCHES = 1000;
  private final EventChannels eventChannels;
  private final ValidatorLoader validatorLoader;
  private final BeaconNodeApi beaconNodeApi;
  private final ForkProvider forkProvider;
  private final Spec spec;
  private final TimeProvider timeProvider;
  private final List<ValidatorTimingChannel> validatorTimingChannels = new ArrayList<>();
  private final ValidatorStatusProvider validatorStatusProvider;
  private ValidatorIndexProvider validatorIndexProvider;
  private Optional<DoppelgangerDetector> maybeDoppelgangerDetector = Optional.empty();
  private final SlashingRiskAction doppelgangerDetectionAction;

  private final Optional<SlashingRiskAction> maybeValidatorSlashedAction;
  private Optional<RestApi> maybeValidatorRestApi = Optional.empty();
  private final Optional<BeaconProposerPreparer> beaconProposerPreparer;
  private final Optional<ValidatorRegistrator> validatorRegistrator;

  private final Optional<ProposerConfigManager> proposerConfigManager;
  private final SafeFuture<Void> initializationComplete = new SafeFuture<>();

  private final MetricsSystem metricsSystem;

  private ValidatorClientService(
      final EventChannels eventChannels,
      final ValidatorLoader validatorLoader,
      final BeaconNodeApi beaconNodeApi,
      final ForkProvider forkProvider,
      final ValidatorStatusProvider validatorStatusProvider,
      final Optional<ProposerConfigManager> proposerConfigManager,
      final Optional<BeaconProposerPreparer> beaconProposerPreparer,
      final Optional<ValidatorRegistrator> validatorRegistrator,
      final Spec spec,
      final MetricsSystem metricsSystem,
      final SlashingRiskAction doppelgangerDetectionAction,
      final Optional<SlashingRiskAction> maybeValidatorSlashedAction,
      final TimeProvider timeProvider) {
    this.eventChannels = eventChannels;
    this.validatorLoader = validatorLoader;
    this.beaconNodeApi = beaconNodeApi;
    this.forkProvider = forkProvider;
    this.validatorStatusProvider = validatorStatusProvider;
    this.proposerConfigManager = proposerConfigManager;
    this.beaconProposerPreparer = beaconProposerPreparer;
    this.validatorRegistrator = validatorRegistrator;
    this.spec = spec;
    this.metricsSystem = metricsSystem;
    this.doppelgangerDetectionAction = doppelgangerDetectionAction;
    this.maybeValidatorSlashedAction = maybeValidatorSlashedAction;
    this.timeProvider = timeProvider;
  }

  public static ValidatorClientService create(
      final ServiceConfig services,
      final ValidatorClientConfiguration config,
      final SlashingRiskAction doppelgangerDetectionAction,
      final Optional<SlashingRiskAction> maybeValidatorSlashedAction) {
    final EventChannels eventChannels = services.getEventChannels();
    final ValidatorConfig validatorConfig = config.getValidatorConfig();

    final AsyncRunner asyncRunner =
        services.createAsyncRunnerWithMaxQueueSize(
            "validator", validatorConfig.getExecutorMaxQueueSize());

    final BeaconNodeApi beaconNodeApi = createBeaconNodeApi(services, config, asyncRunner);

    final ValidatorApiChannel validatorApiChannel = beaconNodeApi.getValidatorApi();
    final GenesisDataProvider genesisDataProvider =
        new GenesisDataProvider(asyncRunner, validatorApiChannel);
    final ForkProvider forkProvider = new ForkProvider(config.getSpec(), genesisDataProvider);

    final ValidatorRestApiConfig validatorApiConfig = config.getValidatorRestApiConfig();
    final Optional<GraffitiManager> graffitiManager =
        Optional.ofNullable(
            validatorApiConfig.isRestApiEnabled()
                ? new GraffitiManager(services.getDataDirLayout())
                : null);
    final Function<BLSPublicKey, Optional<Bytes32>> updatableGraffitiProvider =
        (publicKey) -> graffitiManager.flatMap(manager -> manager.getGraffiti(publicKey));

    final ValidatorLoader validatorLoader =
        createValidatorLoader(services, config, asyncRunner, updatableGraffitiProvider);

    final MetricsSystem metricsSystem = services.getMetricsSystem();

    /* Only available when running a standalone VC client */
    if (validatorConfig.getSentryNodeConfigurationFile().isPresent()
        || validatorConfig.getBeaconNodeApiEndpoints().isPresent()) {
      addVersionMetric(metricsSystem);
    }

    final ValidatorStatusProvider validatorStatusProvider =
        new OwnedValidatorStatusProvider(
            metricsSystem,
            validatorLoader.getOwnedValidators(),
            validatorApiChannel,
            config.getSpec(),
            asyncRunner);
    final Optional<ProposerConfigManager> proposerConfigManager;
    Optional<BeaconProposerPreparer> beaconProposerPreparer = Optional.empty();
    Optional<ValidatorRegistrator> maybeValidatorRegistrator = Optional.empty();
    if (config.getSpec().isMilestoneSupported(SpecMilestone.BELLATRIX)) {

      final ProposerConfigProvider proposerConfigProvider =
          ProposerConfigProvider.create(
              asyncRunner,
              validatorConfig.getRefreshProposerConfigFromSource(),
              new ProposerConfigLoader(),
              services.getTimeProvider(),
              validatorConfig.getProposerConfigSource());

      proposerConfigManager =
          Optional.of(
              new ProposerConfigManager(
                  validatorConfig,
                  new RuntimeProposerConfig(
                      Optional.of(
                          ValidatorClientService.getKeyManagerPath(services.getDataDirLayout())
                              .resolve("api-proposer-config.json"))),
                  proposerConfigProvider,
                  config.getSpec()));

      beaconProposerPreparer =
          Optional.of(
              new BeaconProposerPreparer(
                  validatorApiChannel,
                  Optional.empty(),
                  proposerConfigManager.get(),
                  config.getSpec()));
      final ValidatorRegistrator validatorRegistrator =
          new ValidatorRegistrator(
              config.getSpec(),
              validatorLoader.getOwnedValidators(),
              proposerConfigManager.get(),
              new SignedValidatorRegistrationFactory(
                  proposerConfigManager.get(), services.getTimeProvider()),
              validatorApiChannel,
              validatorConfig.getBuilderRegistrationSendingBatchSize(),
              asyncRunner);
      validatorStatusProvider.subscribeValidatorStatusesUpdates(
          validatorRegistrator::onUpdatedValidatorStatuses);
      maybeValidatorRegistrator = Optional.of(validatorRegistrator);
    } else {
      proposerConfigManager = Optional.empty();
    }

    final ValidatorClientService validatorClientService =
        new ValidatorClientService(
            eventChannels,
            validatorLoader,
            beaconNodeApi,
            forkProvider,
            validatorStatusProvider,
            proposerConfigManager,
            beaconProposerPreparer,
            maybeValidatorRegistrator,
            config.getSpec(),
            metricsSystem,
            doppelgangerDetectionAction,
            maybeValidatorSlashedAction,
            services.getTimeProvider());

    asyncRunner
        .runAsync(
            () -> validatorClientService.initializeValidators(validatorApiChannel, asyncRunner))
        .thenCompose(
            __ -> {
              checkNoKeysLoaded(validatorConfig, validatorLoader);

              if (validatorConfig.isDoppelgangerDetectionEnabled()) {
                validatorClientService.initializeDoppelgangerDetector(
                    asyncRunner,
                    validatorApiChannel,
                    validatorClientService.spec,
                    services.getTimeProvider(),
                    genesisDataProvider);
              }
              return SafeFuture.COMPLETE;
            })
        .thenCompose(
            __ -> {
              if (validatorApiConfig.isRestApiEnabled()) {
                validatorClientService.initializeValidatorRestApi(
                    validatorApiConfig,
                    validatorApiChannel,
                    genesisDataProvider,
                    proposerConfigManager,
                    new OwnedKeyManager(
                        validatorLoader,
                        services.getEventChannels().getPublisher(ValidatorTimingChannel.class)),
                    services.getDataDirLayout(),
                    services.getTimeProvider(),
                    validatorClientService.maybeDoppelgangerDetector,
                    graffitiManager.orElseThrow());
              } else {
                LOG.info("validator-api-enabled is false, not starting rest api.");
              }
              return SafeFuture.COMPLETE;
            })
        .thenCompose(
            __ -> {
              asyncRunner
                  .runAsync(
                      () ->
                          validatorClientService.scheduleValidatorsDuties(
                              config, validatorApiChannel, asyncRunner))
                  .propagateTo(validatorClientService.initializationComplete);
              return SafeFuture.COMPLETE;
            })
        .exceptionally(
            error -> {
              ExceptionUtil.getCause(error, InvalidConfigurationException.class)
                  .ifPresentOrElse(
                      cause -> STATUS_LOG.failedToLoadValidatorKey(cause.getMessage()),
                      () -> {
                        STATUS_LOG.failedToStartValidatorClient(
                            ExceptionUtil.getRootCauseMessage(error));
                        LOG.error(
                            "An error was encountered during validator client service start up.",
                            error);
                      });
              // an unhandled exception getting this far means any number of above steps failed to
              // complete,
              // which is fatal, we don't know how to recover at this point, regardless of if we're
              // in VC or BN mode.
              System.exit(FATAL_EXIT_CODE);
              return null;
            })
        .always(() -> LOG.trace("Finished starting validator client service."));

    return validatorClientService;
  }

  private static void checkNoKeysLoaded(
      final ValidatorConfig validatorConfig, final ValidatorLoader validatorLoader) {
    if (validatorConfig.isExitWhenNoValidatorKeysEnabled()
        && validatorLoader.getOwnedValidators().hasNoValidators()) {
      STATUS_LOG.exitOnNoValidatorKeys();
      System.exit(FATAL_EXIT_CODE);
    }
  }

  private void initializeDoppelgangerDetector(
      final AsyncRunner asyncRunner,
      final ValidatorApiChannel validatorApiChannel,
      final Spec spec,
      final TimeProvider timeProvider,
      final GenesisDataProvider genesisDataProvider) {
    final DoppelgangerDetector doppelgangerDetector =
        new DoppelgangerDetector(
            asyncRunner,
            validatorApiChannel,
            spec,
            timeProvider,
            genesisDataProvider,
            DOPPELGANGER_DETECTOR_CHECK_DELAY,
            DOPPELGANGER_DETECTOR_TIMEOUT,
            DOPPELGANGER_DETECTOR_MAX_EPOCHS);
    maybeDoppelgangerDetector = Optional.of(doppelgangerDetector);
  }

  private void initializeValidatorRestApi(
      final ValidatorRestApiConfig validatorRestApiConfig,
      final ValidatorApiChannel validatorApiChannel,
      final GenesisDataProvider genesisDataProvider,
      final Optional<ProposerConfigManager> proposerConfigManager,
      final OwnedKeyManager keyManager,
      final DataDirLayout dataDirLayout,
      final TimeProvider timeProvider,
      final Optional<DoppelgangerDetector> maybeDoppelgangerDetector,
      final GraffitiManager graffitiManager) {
    final RestApi validatorRestApi =
        ValidatorRestApi.create(
            spec,
            validatorRestApiConfig,
            validatorApiChannel,
            genesisDataProvider,
            proposerConfigManager,
            keyManager,
            dataDirLayout,
            timeProvider,
            maybeDoppelgangerDetector,
            new DoppelgangerDetectionAlert(),
            graffitiManager);
    maybeValidatorRestApi = Optional.of(validatorRestApi);
  }

  private static BeaconNodeApi createBeaconNodeApi(
      final ServiceConfig services,
      final ValidatorClientConfiguration validatorClientConfiguration,
      final AsyncRunner asyncRunner) {
    final ValidatorConfig validatorConfig = validatorClientConfiguration.getValidatorConfig();

    final BeaconNodeApi beaconNodeApi;
    if (validatorConfig.getSentryNodeConfigurationFile().isEmpty()) {
      beaconNodeApi =
          validatorConfig
              .getBeaconNodeApiEndpoints()
              .map(
                  beaconNodeApiEndpoints ->
                      RemoteBeaconNodeApi.create(
                          services,
                          validatorConfig,
                          validatorClientConfiguration.getSpec(),
                          beaconNodeApiEndpoints))
              .orElseGet(
                  () ->
                      InProcessBeaconNodeApi.create(
                          services,
                          validatorConfig,
                          asyncRunner,
                          validatorClientConfiguration.getSpec()));
    } else {
      final SentryNodesConfig sentryNodesConfig =
          new SentryNodesConfigLoader()
              .load(validatorConfig.getSentryNodeConfigurationFile().get());
      beaconNodeApi =
          SentryBeaconNodeApi.create(
              services, validatorConfig, validatorClientConfiguration.getSpec(), sentryNodesConfig);
    }

    return beaconNodeApi;
  }

  private static ValidatorLoader createValidatorLoader(
      final ServiceConfig services,
      final ValidatorClientConfiguration config,
      final AsyncRunner asyncRunner,
      final Function<BLSPublicKey, Optional<Bytes32>> updatableGraffitiProvider) {
    final Path slashingProtectionPath = getSlashingProtectionPath(services.getDataDirLayout());
    final SlashingProtector slashingProtector =
        config.getValidatorConfig().isLocalSlashingProtectionSynchronizedModeEnabled()
            ? new LocalSlashingProtector(
                SyncDataAccessor.create(slashingProtectionPath), slashingProtectionPath)
            : new LocalSlashingProtectorConcurrentAccess(
                SyncDataAccessor.create(slashingProtectionPath), slashingProtectionPath);
    final SlashingProtectionLogger slashingProtectionLogger =
        new SlashingProtectionLogger(
            slashingProtector, config.getSpec(), asyncRunner, VALIDATOR_LOGGER);
    final Supplier<HttpClient> externalSignerHttpClientFactory =
        HttpClientExternalSignerFactory.create(config.getValidatorConfig());
    return ValidatorLoader.create(
        config.getSpec(),
        config.getValidatorConfig(),
        config.getInteropConfig(),
        externalSignerHttpClientFactory,
        slashingProtector,
        slashingProtectionLogger,
        new PublicKeyLoader(
            externalSignerHttpClientFactory,
            config.getValidatorConfig().getValidatorExternalSignerUrl()),
        asyncRunner,
        services.getMetricsSystem(),
        config.getValidatorRestApiConfig().isRestApiEnabled()
            ? Optional.of(services.getDataDirLayout())
            : Optional.empty(),
        updatableGraffitiProvider);
  }

  private void initializeValidators(
      final ValidatorApiChannel validatorApiChannel, final AsyncRunner asyncRunner) {
    validatorLoader.loadValidators();
    final OwnedValidators validators = validatorLoader.getOwnedValidators();
    this.validatorIndexProvider =
        new ValidatorIndexProvider(validators, validatorApiChannel, asyncRunner);
  }

  private void scheduleValidatorsDuties(
      final ValidatorClientConfiguration config,
      final ValidatorApiChannel validatorApiChannel,
      final AsyncRunner asyncRunner) {
    validatorTimingChannels.add(validatorStatusProvider);
    final OwnedValidators validators = validatorLoader.getOwnedValidators();
    final BlockContainerSigner blockContainerSigner = new MilestoneBasedBlockContainerSigner(spec);
    final ValidatorDutyMetrics validatorDutyMetrics = ValidatorDutyMetrics.create(metricsSystem);
    final BuilderConfigProvider builderConfigProvider =
        new BuilderConfigProvider(spec, config.getValidatorConfig());
    final BlockDutyFactory blockDutyFactory =
        new BlockDutyFactory(
            forkProvider,
            validatorApiChannel,
            blockContainerSigner,
            spec,
            validatorDutyMetrics,
            eventChannels.getPublisher(ExecutionPayloadBidEventsChannel.class),
            builderConfigProvider);
    final boolean dvtSelectionsEndpointEnabled =
        config.getValidatorConfig().isDvtSelectionsEndpointEnabled();
    final AttestationDutyFactory attestationDutyFactory =
        new AttestationDutyFactory(
            spec,
            forkProvider,
            validatorApiChannel,
            validatorDutyMetrics,
            dvtSelectionsEndpointEnabled);
    final BeaconCommitteeSubscriptions beaconCommitteeSubscriptions =
        new BeaconCommitteeSubscriptions(validatorApiChannel);
    final Function<Bytes32, SlotBasedScheduledDuties<AttestationProductionDuty, AggregationDuty>>
        scheduledAttestationDutiesFactory =
            dependentRoot ->
                new SlotBasedScheduledDuties<>(
                    attestationDutyFactory,
                    dependentRoot,
                    validatorDutyMetrics::performDutyWithMetrics);
    final AttestationDutyDefaultSchedulingStrategy attestationDutyDefaultSchedulingStrategy =
        new AttestationDutyDefaultSchedulingStrategy(
            spec,
            forkProvider,
            scheduledAttestationDutiesFactory,
            validators,
            beaconCommitteeSubscriptions,
            validatorApiChannel,
            dvtSelectionsEndpointEnabled);
    final AttestationDutyBatchSchedulingStrategy attestationDutyBatchSchedulingStrategy =
        new AttestationDutyBatchSchedulingStrategy(
            spec,
            forkProvider,
            scheduledAttestationDutiesFactory,
            validators,
            beaconCommitteeSubscriptions,
            asyncRunner);
    validatorTimingChannels.add(attestationDutyBatchSchedulingStrategy);
    if (dvtSelectionsEndpointEnabled) {
      validatorTimingChannels.add(attestationDutyDefaultSchedulingStrategy);
    }
    final DutyLoader<?> attestationDutyLoader =
        new RetryingDutyLoader<>(
            asyncRunner,
            timeProvider,
            new AttestationDutyLoader(
                validators,
                validatorIndexProvider,
                validatorApiChannel,
                new AttestationDutySchedulingStrategySelector(
                    MIN_SIZE_TO_SCHEDULE_ATTESTATION_DUTIES_IN_BATCHES,
                    dvtSelectionsEndpointEnabled,
                    attestationDutyDefaultSchedulingStrategy,
                    attestationDutyBatchSchedulingStrategy)));
    final BiConsumer<UInt64, ProposerDuties> publishProposerPreferences =
        createPublishProposerPreferences(validators, validatorApiChannel);
    final DutyLoader<?> blockDutyLoader =
        new RetryingDutyLoader<>(
            asyncRunner,
            timeProvider,
            new BlockProductionDutyLoader(
                validatorApiChannel,
                dependentRoot ->
                    new SlotBasedScheduledDuties<>(
                        blockDutyFactory,
                        dependentRoot,
                        validatorDutyMetrics::performDutyWithMetrics),
                validators,
                validatorIndexProvider,
                publishProposerPreferences));
    validatorTimingChannels.add(new BlockDutyScheduler(metricsSystem, blockDutyLoader, spec));
    validatorTimingChannels.add(
        new AttestationDutyScheduler(metricsSystem, attestationDutyLoader, spec));
    validatorTimingChannels.add(validatorLoader.getSlashingProtectionLogger());

    if (spec.isMilestoneSupported(SpecMilestone.ALTAIR)) {
      final ChainHeadTracker chainHeadTracker = new ChainHeadTracker();
      validatorTimingChannels.add(chainHeadTracker);
      final DutyLoader<SyncCommitteeScheduledDuties> syncCommitteeDutyLoader =
          new RetryingDutyLoader<>(
              asyncRunner,
              timeProvider,
              new SyncCommitteeDutyLoader(
                  validators,
                  validatorIndexProvider,
                  spec,
                  validatorApiChannel,
                  chainHeadTracker,
                  forkProvider,
                  metricsSystem,
                  dvtSelectionsEndpointEnabled));
      validatorTimingChannels.add(
          new SyncCommitteeScheduler(
              metricsSystem, spec, syncCommitteeDutyLoader, new Random()::nextInt));
    }

    if (spec.isMilestoneSupported(SpecMilestone.BELLATRIX)) {
      // keeps track of the current epoch to resolve the scheduled gas limit (EIP-8261)
      proposerConfigManager.ifPresent(validatorTimingChannels::add);
      beaconProposerPreparer.ifPresent(
          preparer -> {
            preparer.initialize(Optional.of(validatorIndexProvider));
            validatorTimingChannels.add(preparer);
          });
      validatorRegistrator.ifPresent(validatorTimingChannels::add);
    }

    if (spec.isMilestoneSupported(SpecMilestone.GLOAS)) {
      final ExecutionPayloadDuty executionPayloadDuty =
          new ExecutionPayloadDuty(spec, asyncRunner, validatorApiChannel, VALIDATOR_LOGGER);
      eventChannels.subscribe(ExecutionPayloadBidEventsChannel.class, executionPayloadDuty);

      final PayloadAttestationDutyFactory payloadAttestationDutyFactory =
          new PayloadAttestationDutyFactory(
              spec, forkProvider, validatorApiChannel, validatorDutyMetrics);
      final DutyLoader<?> payloadDutyLoader =
          new RetryingDutyLoader<>(
              asyncRunner,
              timeProvider,
              new PtcDutyLoader(
                  validatorApiChannel,
                  dependentRoot ->
                      new SlotBasedScheduledDuties<>(
                          payloadAttestationDutyFactory,
                          dependentRoot,
                          validatorDutyMetrics::performDutyWithMetrics),
                  validators,
                  validatorIndexProvider));
      validatorTimingChannels.add(new PtcDutyScheduler(metricsSystem, payloadDutyLoader, spec));
    }

    addValidatorCountMetric(metricsSystem, validators);
    final ValidatorStatusLogger validatorStatusLogger = new ValidatorStatusLogger(validators);
    validatorStatusProvider.subscribeValidatorStatusesUpdates(
        validatorStatusLogger::onUpdatedValidatorStatuses);
  }

  private BiConsumer<UInt64, ProposerDuties> createPublishProposerPreferences(
      final OwnedValidators validators, final ValidatorApiChannel validatorApiChannel) {
    if (proposerConfigManager.isEmpty()) {
      return (epoch, duties) -> {};
    }
    final ProposerPreferencesPublisher proposerPreferencesPublisher =
        new ProposerPreferencesPublisher(
            validatorApiChannel, validators, proposerConfigManager.get(), forkProvider, spec);
    return proposerPreferencesPublisher::onProposerDutiesLoaded;
  }

  public static Path getSlashingProtectionPath(final DataDirLayout dataDirLayout) {
    return dataDirLayout.getValidatorDataDirectory().resolve("slashprotection");
  }

  public static Path getManagedLocalKeystorePath(final DataDirLayout dataDirLayout) {
    return getKeyManagerPath(dataDirLayout).resolve("local");
  }

  public static Path getManagedLocalKeystorePasswordPath(final DataDirLayout dataDirLayout) {
    return getKeyManagerPath(dataDirLayout).resolve("local-passwords");
  }

  public static Path getManagedRemoteKeyPath(final DataDirLayout dataDirLayout) {
    return getKeyManagerPath(dataDirLayout).resolve("remote");
  }

  public static Path getKeyManagerPath(final DataDirLayout dataDirLayout) {
    return dataDirLayout.getValidatorDataDirectory().resolve("key-manager");
  }

  private static void addValidatorCountMetric(
      final MetricsSystem metricsSystem, final OwnedValidators validators) {
    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "local_validator_count",
        "Current number of validators running in this validator client",
        validators::getValidatorCount);
  }

  private static void addVersionMetric(final MetricsSystem metricsSystem) {
    final String version = VersionProvider.IMPLEMENTATION_VERSION.replaceAll("^v", "");
    final LabelledMetric<Counter> versionCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.VALIDATOR,
            VersionProvider.CLIENT_IDENTITY + "_version_total",
            "Teku version in use",
            "version");
    versionCounter.labels(version).inc();
  }

  @Override
  protected SafeFuture<?> doStart() {
    return initializationComplete
        .thenCompose(
            __ ->
                proposerConfigManager
                    .map(manager -> manager.initialize(validatorLoader.getOwnedValidators()))
                    .orElse(SafeFuture.COMPLETE))
        .thenCompose(
            __ -> {
              maybeValidatorRestApi.ifPresent(restApi -> restApi.start().finishError(LOG));
              SystemSignalListener.registerReloadConfigListener(validatorLoader::loadValidators);
              validatorIndexProvider.lookupValidators();
              return maybeDoppelgangerDetector
                  .map(
                      doppelgangerDetector ->
                          doppelgangerDetector
                              .performDoppelgangerDetection(
                                  validatorLoader.getOwnedValidators().getPublicKeys())
                              .thenAccept(
                                  doppelgangerDetected -> {
                                    if (!doppelgangerDetected.isEmpty()) {
                                      doppelgangerDetectionAction.perform(
                                          new ArrayList<>(doppelgangerDetected.values()));
                                    }
                                  }))
                  .orElse(SafeFuture.COMPLETE);
            })
        .thenCompose(
            __ -> {
              final ValidatorTimingActions validatorTimingActions =
                  new ValidatorTimingActions(
                      validatorIndexProvider,
                      validatorTimingChannels,
                      spec,
                      metricsSystem,
                      maybeValidatorSlashedAction);
              eventChannels.subscribe(ValidatorTimingChannel.class, validatorTimingActions);
              if (maybeValidatorSlashedAction.isPresent()) {
                validatorStatusProvider.subscribeValidatorStatusesUpdates(
                    validatorTimingActions::onUpdatedValidatorStatuses);
              }
              validatorStatusProvider.start().finishError(LOG);
              return beaconNodeApi.subscribeToEvents();
            });
  }

  @Override
  protected SafeFuture<?> doStop() {
    return SafeFuture.allOf(
        SafeFuture.fromRunnable(
            () -> maybeValidatorRestApi.ifPresent(restApi -> restApi.stop().finishError(LOG))),
        beaconNodeApi.unsubscribeFromEvents());
  }
}
