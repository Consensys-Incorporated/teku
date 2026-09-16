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

package tech.pegasys.teku.validator.api;

import static com.google.common.base.Preconditions.checkNotNull;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_VALIDATOR_EXECUTOR_THREADS;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalInt;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorConfig {

  private static final Logger LOG = LogManager.getLogger();

  private static final int DEFAULT_REST_API_PORT = 5051;
  public static final List<URI> DEFAULT_BEACON_NODE_API_ENDPOINTS =
      List.of(URI.create("http://127.0.0.1:" + DEFAULT_REST_API_PORT));
  public static final boolean DEFAULT_FAILOVERS_SEND_SUBNET_SUBSCRIPTIONS_ENABLED = true;
  public static final boolean DEFAULT_FAILOVERS_PUBLISH_SIGNED_DUTIES_ENABLED = true;
  public static final boolean DEFAULT_EXIT_WHEN_NO_VALIDATOR_KEYS_ENABLED = false;
  public static final boolean DEFAULT_VALIDATOR_CLIENT_SSZ_BLOCKS_ENABLED = true;
  public static final boolean DEFAULT_VALIDATOR_CLIENT_USE_POST_VALIDATORS_ENDPOINT_ENABLED = true;
  public static final boolean DEFAULT_DOPPELGANGER_DETECTION_ENABLED = false;
  public static final boolean DEFAULT_SHUTDOWN_WHEN_VALIDATOR_SLASHED_ENABLED = false;
  public static final boolean DEFAULT_VALIDATOR_IS_LOCAL_SLASHING_PROTECTION_SYNCHRONIZED_ENABLED =
      true;
  public static final int DEFAULT_EXECUTOR_MAX_QUEUE_SIZE = 40_000;
  public static final int DEFAULT_EXECUTOR_MAX_QUEUE_SIZE_ALL_SUBNETS = 60_000;
  public static final Duration DEFAULT_VALIDATOR_EXTERNAL_SIGNER_TIMEOUT = Duration.ofSeconds(5);
  public static final int DEFAULT_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT = 32;
  public static final int MINIMUM_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT = 1;
  public static final int MAXIMUM_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT = 1024;
  public static final boolean DEFAULT_VALIDATOR_KEYSTORE_LOCKING_ENABLED = true;
  public static final boolean DEFAULT_VALIDATOR_EXTERNAL_SIGNER_SLASHING_PROTECTION_ENABLED = true;
  public static final boolean DEFAULT_VALIDATOR_SLASHING_PROTECTION_STRICT_MODE_ENABLED = false;
  public static final boolean DEFAULT_GENERATE_EARLY_ATTESTATIONS = true;
  public static final Optional<Bytes32> DEFAULT_GRAFFITI = Optional.empty();
  public static final ClientGraffitiAppendFormat DEFAULT_CLIENT_GRAFFITI_APPEND_FORMAT =
      ClientGraffitiAppendFormat.AUTO;
  public static final boolean DEFAULT_VALIDATOR_PROPOSER_CONFIG_REFRESH_ENABLED = false;
  public static final boolean DEFAULT_BUILDER_REGISTRATION_DEFAULT_ENABLED = false;
  public static final int DEFAULT_VALIDATOR_REGISTRATION_SENDING_BATCH_SIZE = 100;
  public static final UInt64 DEFAULT_BUILDER_REGISTRATION_GAS_LIMIT = UInt64.valueOf(60_000_000);
  public static final boolean DEFAULT_OBOL_DVT_SELECTIONS_ENDPOINT_ENABLED = false;
  public static final boolean DEFAULT_ATTESTATIONS_V2_APIS_ENABLED = false;
  // Builder default options
  public static final UInt64 DEFAULT_BUILDER_MIN_BID = UInt64.ZERO;
  public static final UInt64 DEFAULT_BUILDER_BOOST_FACTOR = UInt64.valueOf(90);

  private final List<String> validatorKeys;
  private final List<String> validatorExternalSignerPublicKeySources;
  private final boolean validatorExternalSignerSlashingProtectionEnabled;
  private final URL validatorExternalSignerUrl;
  private final Optional<String> validatorExternalSignerUserInfo;
  private final Duration validatorExternalSignerTimeout;
  private final Path validatorExternalSignerKeystore;
  private final Path validatorExternalSignerKeystorePasswordFile;
  private final Path validatorExternalSignerTruststore;
  private final Path validatorExternalSignerTruststorePasswordFile;
  private final GraffitiProvider graffitiProvider;
  private final ClientGraffitiAppendFormat clientGraffitiAppendFormat;
  private final ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode;
  private final boolean validatorKeystoreLockingEnabled;
  private final Optional<List<URI>> beaconNodeApiEndpoints;
  private final int validatorExternalSignerConcurrentRequestLimit;
  private final boolean generateEarlyAttestations;
  private final Optional<Eth1Address> proposerDefaultFeeRecipient;
  private final Optional<String> proposerConfigSource;
  private final boolean refreshProposerConfigFromSource;
  private final boolean builderRegistrationDefaultEnabled;
  private final boolean validatorClientUseSszBlocksEnabled;
  private final boolean validatorClientUsePostValidatorsEndpointEnabled;
  private final boolean doppelgangerDetectionEnabled;
  private final boolean failoversSendSubnetSubscriptionsEnabled;
  private final boolean failoversPublishSignedDutiesEnabled;
  private final boolean exitWhenNoValidatorKeysEnabled;
  private final boolean shutdownWhenValidatorSlashedEnabled;
  private final Optional<UInt64> builderRegistrationDefaultGasLimit;
  private final int builderRegistrationSendingBatchSize;
  private final Optional<UInt64> builderRegistrationTimestampOverride;
  private final Optional<BLSPublicKey> builderRegistrationPublicKeyOverride;
  private final int executorMaxQueueSize;
  private final Optional<String> sentryNodeConfigurationFile;

  private final int executorThreads;

  private final OptionalInt beaconApiExecutorThreads;
  private final OptionalInt beaconApiReadinessExecutorThreads;

  private final boolean isLocalSlashingProtectionSynchronizedModeEnabled;
  private final boolean slashingProtectionStrictModeEnabled;
  private final boolean dvtSelectionsEndpointEnabled;
  private final boolean attestationsV2ApisEnabled;

  // Builder options
  private final UInt64 builderMinBid;
  private final UInt64 builderBoostFactor;
  private final List<URL> builderUrls;

  private ValidatorConfig(
      final List<String> validatorKeys,
      final List<String> validatorExternalSignerPublicKeySources,
      final URL validatorExternalSignerUrl,
      final Optional<String> validatorExternalSignerUserInfo,
      final Duration validatorExternalSignerTimeout,
      final Path validatorExternalSignerKeystore,
      final Path validatorExternalSignerKeystorePasswordFile,
      final Path validatorExternalSignerTruststore,
      final Path validatorExternalSignerTruststorePasswordFile,
      final Optional<List<URI>> beaconNodeApiEndpoints,
      final GraffitiProvider graffitiProvider,
      final ClientGraffitiAppendFormat clientGraffitiAppendFormat,
      final ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode,
      final boolean validatorKeystoreLockingEnabled,
      final boolean validatorExternalSignerSlashingProtectionEnabled,
      final int validatorExternalSignerConcurrentRequestLimit,
      final boolean generateEarlyAttestations,
      final Optional<Eth1Address> proposerDefaultFeeRecipient,
      final Optional<String> proposerConfigSource,
      final boolean refreshProposerConfigFromSource,
      final boolean builderRegistrationDefaultEnabled,
      final boolean validatorClientUseSszBlocksEnabled,
      final boolean validatorClientUsePostValidatorsEndpointEnabled,
      final boolean doppelgangerDetectionEnabled,
      final boolean failoversSendSubnetSubscriptionsEnabled,
      final boolean failoversPublishSignedDutiesEnabled,
      final boolean exitWhenNoValidatorKeysEnabled,
      final boolean shutdownWhenValidatorSlashedEnabled,
      final Optional<UInt64> builderRegistrationDefaultGasLimit,
      final int builderRegistrationSendingBatchSize,
      final Optional<UInt64> builderRegistrationTimestampOverride,
      final Optional<BLSPublicKey> builderRegistrationPublicKeyOverride,
      final int executorMaxQueueSize,
      final int executorThreads,
      final OptionalInt beaconApiExecutorThreads,
      final OptionalInt beaconApiReadinessExecutorThreads,
      final Optional<String> sentryNodeConfigurationFile,
      final boolean isLocalSlashingProtectionSynchronizedModeEnabled,
      final boolean slashingProtectionStrictModeEnabled,
      final boolean dvtSelectionsEndpointEnabled,
      final boolean attestationsV2ApisEnabled,
      final UInt64 builderMinBid,
      final UInt64 builderBoostFactor,
      final List<URL> builderUrls) {
    this.validatorKeys = validatorKeys;
    this.validatorExternalSignerPublicKeySources = validatorExternalSignerPublicKeySources;
    this.validatorExternalSignerUrl = validatorExternalSignerUrl;
    this.validatorExternalSignerUserInfo = validatorExternalSignerUserInfo;
    this.validatorExternalSignerTimeout = validatorExternalSignerTimeout;
    this.validatorExternalSignerKeystore = validatorExternalSignerKeystore;
    this.validatorExternalSignerKeystorePasswordFile = validatorExternalSignerKeystorePasswordFile;
    this.validatorExternalSignerTruststore = validatorExternalSignerTruststore;
    this.validatorExternalSignerTruststorePasswordFile =
        validatorExternalSignerTruststorePasswordFile;
    this.graffitiProvider = graffitiProvider;
    this.clientGraffitiAppendFormat = clientGraffitiAppendFormat;
    this.validatorKeystoreLockingEnabled = validatorKeystoreLockingEnabled;
    this.beaconNodeApiEndpoints = beaconNodeApiEndpoints;
    this.validatorPerformanceTrackingMode = validatorPerformanceTrackingMode;
    this.validatorExternalSignerSlashingProtectionEnabled =
        validatorExternalSignerSlashingProtectionEnabled;
    this.validatorExternalSignerConcurrentRequestLimit =
        validatorExternalSignerConcurrentRequestLimit;
    this.generateEarlyAttestations = generateEarlyAttestations;
    this.proposerDefaultFeeRecipient = proposerDefaultFeeRecipient;
    this.proposerConfigSource = proposerConfigSource;
    this.refreshProposerConfigFromSource = refreshProposerConfigFromSource;
    this.builderRegistrationDefaultEnabled = builderRegistrationDefaultEnabled;
    this.validatorClientUseSszBlocksEnabled = validatorClientUseSszBlocksEnabled;
    this.validatorClientUsePostValidatorsEndpointEnabled =
        validatorClientUsePostValidatorsEndpointEnabled;
    this.doppelgangerDetectionEnabled = doppelgangerDetectionEnabled;
    this.failoversSendSubnetSubscriptionsEnabled = failoversSendSubnetSubscriptionsEnabled;
    this.failoversPublishSignedDutiesEnabled = failoversPublishSignedDutiesEnabled;
    this.exitWhenNoValidatorKeysEnabled = exitWhenNoValidatorKeysEnabled;
    this.shutdownWhenValidatorSlashedEnabled = shutdownWhenValidatorSlashedEnabled;
    this.builderRegistrationDefaultGasLimit = builderRegistrationDefaultGasLimit;
    this.builderRegistrationSendingBatchSize = builderRegistrationSendingBatchSize;
    this.builderRegistrationTimestampOverride = builderRegistrationTimestampOverride;
    this.builderRegistrationPublicKeyOverride = builderRegistrationPublicKeyOverride;
    this.executorMaxQueueSize = executorMaxQueueSize;
    this.executorThreads = executorThreads;
    this.beaconApiExecutorThreads = beaconApiExecutorThreads;
    this.beaconApiReadinessExecutorThreads = beaconApiReadinessExecutorThreads;
    this.sentryNodeConfigurationFile = sentryNodeConfigurationFile;
    this.isLocalSlashingProtectionSynchronizedModeEnabled =
        isLocalSlashingProtectionSynchronizedModeEnabled;
    this.slashingProtectionStrictModeEnabled = slashingProtectionStrictModeEnabled;
    this.dvtSelectionsEndpointEnabled = dvtSelectionsEndpointEnabled;
    this.attestationsV2ApisEnabled = attestationsV2ApisEnabled;
    this.builderMinBid = builderMinBid;
    this.builderBoostFactor = builderBoostFactor;
    this.builderUrls = builderUrls;

    LOG.debug(
        "Executor queue - {} threads, max queue size {} ", executorThreads, executorMaxQueueSize);
  }

  public static Builder builder() {
    return new Builder();
  }

  public ValidatorPerformanceTrackingMode getValidatorPerformanceTrackingMode() {
    return validatorPerformanceTrackingMode;
  }

  public boolean isValidatorKeystoreLockingEnabled() {
    return validatorKeystoreLockingEnabled;
  }

  public List<String> getValidatorExternalSignerPublicKeySources() {
    return validatorExternalSignerPublicKeySources;
  }

  public boolean isValidatorExternalSignerSlashingProtectionEnabled() {
    return validatorExternalSignerSlashingProtectionEnabled;
  }

  public URL getValidatorExternalSignerUrl() {
    return validatorExternalSignerUrl;
  }

  public Optional<String> getValidatorExternalSignerUserInfo() {
    return validatorExternalSignerUserInfo;
  }

  public Duration getValidatorExternalSignerTimeout() {
    return validatorExternalSignerTimeout;
  }

  public int getValidatorExternalSignerConcurrentRequestLimit() {
    return validatorExternalSignerConcurrentRequestLimit;
  }

  public Pair<Path, Path> getValidatorExternalSignerKeystorePasswordFilePair() {
    return Pair.of(validatorExternalSignerKeystore, validatorExternalSignerKeystorePasswordFile);
  }

  public Pair<Path, Path> getValidatorExternalSignerTruststorePasswordFilePair() {
    return Pair.of(
        validatorExternalSignerTruststore, validatorExternalSignerTruststorePasswordFile);
  }

  public Optional<URI> getPrimaryBeaconNodeApiEndpoint() {
    return beaconNodeApiEndpoints.map(endpoints -> endpoints.get(0));
  }

  public Optional<List<URI>> getBeaconNodeApiEndpoints() {
    return beaconNodeApiEndpoints;
  }

  public GraffitiProvider getGraffitiProvider() {
    return graffitiProvider;
  }

  public ClientGraffitiAppendFormat getClientGraffitiAppendFormat() {
    return clientGraffitiAppendFormat;
  }

  public List<String> getValidatorKeys() {
    return validatorKeys;
  }

  public boolean generateEarlyAttestations() {
    return generateEarlyAttestations;
  }

  public Optional<Eth1Address> getProposerDefaultFeeRecipient() {
    validateProposerDefaultFeeRecipientOrProposerConfigSource();
    return proposerDefaultFeeRecipient;
  }

  public Optional<String> getProposerConfigSource() {
    validateProposerDefaultFeeRecipientOrProposerConfigSource();
    return proposerConfigSource;
  }

  /**
   * The gas limit explicitly configured by the operator, if any. When not set, the gas limit
   * scheduled for the current epoch (EIP-8261) is used, falling back to {@link
   * #DEFAULT_BUILDER_REGISTRATION_GAS_LIMIT}.
   */
  public Optional<UInt64> getBuilderRegistrationDefaultGasLimit() {
    return builderRegistrationDefaultGasLimit;
  }

  public int getBuilderRegistrationSendingBatchSize() {
    return builderRegistrationSendingBatchSize;
  }

  public Optional<UInt64> getBuilderRegistrationTimestampOverride() {
    return builderRegistrationTimestampOverride;
  }

  public Optional<BLSPublicKey> getBuilderRegistrationPublicKeyOverride() {
    return builderRegistrationPublicKeyOverride;
  }

  public boolean getRefreshProposerConfigFromSource() {
    return refreshProposerConfigFromSource;
  }

  public boolean isValidatorClientUseSszBlocksEnabled() {
    return validatorClientUseSszBlocksEnabled;
  }

  public boolean isValidatorClientUsePostValidatorsEndpointEnabled() {
    return validatorClientUsePostValidatorsEndpointEnabled;
  }

  public boolean isDoppelgangerDetectionEnabled() {
    return doppelgangerDetectionEnabled;
  }

  public boolean isFailoversSendSubnetSubscriptionsEnabled() {
    return failoversSendSubnetSubscriptionsEnabled;
  }

  public boolean isFailoversPublishSignedDutiesEnabled() {
    return failoversPublishSignedDutiesEnabled;
  }

  public boolean isExitWhenNoValidatorKeysEnabled() {
    return exitWhenNoValidatorKeysEnabled;
  }

  public boolean isShutdownWhenValidatorSlashedEnabled() {
    return shutdownWhenValidatorSlashedEnabled;
  }

  public boolean isBuilderRegistrationDefaultEnabled() {
    return builderRegistrationDefaultEnabled;
  }

  public int getExecutorMaxQueueSize() {
    return executorMaxQueueSize;
  }

  public int getExecutorThreads() {
    return executorThreads;
  }

  public OptionalInt getBeaconApiExecutorThreads() {
    return beaconApiExecutorThreads;
  }

  public OptionalInt getBeaconApiReadinessExecutorThreads() {
    return beaconApiReadinessExecutorThreads;
  }

  public Optional<String> getSentryNodeConfigurationFile() {
    return sentryNodeConfigurationFile;
  }

  private void validateProposerDefaultFeeRecipientOrProposerConfigSource() {
    if (proposerDefaultFeeRecipient.isEmpty()
        && proposerConfigSource.isEmpty()
        && !(validatorKeys.isEmpty() && validatorExternalSignerPublicKeySources.isEmpty())) {
      throw new InvalidConfigurationException(
          "Invalid configuration. --validators-proposer-default-fee-recipient or --validators-proposer-config must be specified when Bellatrix milestone is active");
    }
  }

  public boolean isLocalSlashingProtectionSynchronizedModeEnabled() {
    return isLocalSlashingProtectionSynchronizedModeEnabled;
  }

  public boolean isSlashingProtectionStrictModeEnabled() {
    return slashingProtectionStrictModeEnabled;
  }

  public boolean isDvtSelectionsEndpointEnabled() {
    return dvtSelectionsEndpointEnabled;
  }

  public boolean isAttestationsV2ApisEnabled() {
    return attestationsV2ApisEnabled;
  }

  public UInt64 getBuilderMinBid() {
    return builderMinBid;
  }

  public UInt64 getBuilderBoostFactor() {
    return builderBoostFactor;
  }

  public List<URL> getBuilderUrls() {
    return builderUrls;
  }

  public static final class Builder {
    private List<String> validatorKeys = new ArrayList<>();
    private List<String> validatorExternalSignerPublicKeySources = new ArrayList<>();
    private URL validatorExternalSignerUrl;
    private Optional<String> validatorExternalSignerUserInfo = Optional.empty();
    private int validatorExternalSignerConcurrentRequestLimit =
        DEFAULT_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT;
    private Duration validatorExternalSignerTimeout = DEFAULT_VALIDATOR_EXTERNAL_SIGNER_TIMEOUT;
    private Path validatorExternalSignerKeystore;
    private Path validatorExternalSignerKeystorePasswordFile;
    private Path validatorExternalSignerTruststore;
    private Path validatorExternalSignerTruststorePasswordFile;
    private GraffitiProvider graffitiProvider =
        new FileBackedGraffitiProvider(DEFAULT_GRAFFITI, Optional.empty());
    private ClientGraffitiAppendFormat clientGraffitiAppendFormat =
        DEFAULT_CLIENT_GRAFFITI_APPEND_FORMAT;
    private ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode =
        ValidatorPerformanceTrackingMode.DEFAULT_MODE;
    private boolean validatorKeystoreLockingEnabled = DEFAULT_VALIDATOR_KEYSTORE_LOCKING_ENABLED;
    private Optional<List<URI>> beaconNodeApiEndpoints = Optional.empty();
    private boolean validatorExternalSignerSlashingProtectionEnabled =
        DEFAULT_VALIDATOR_EXTERNAL_SIGNER_SLASHING_PROTECTION_ENABLED;
    private boolean generateEarlyAttestations = DEFAULT_GENERATE_EARLY_ATTESTATIONS;
    private Optional<Eth1Address> proposerDefaultFeeRecipient = Optional.empty();
    private Optional<String> proposerConfigSource = Optional.empty();
    private boolean refreshProposerConfigFromSource =
        DEFAULT_VALIDATOR_PROPOSER_CONFIG_REFRESH_ENABLED;
    private boolean validatorsRegistrationDefaultEnabled =
        DEFAULT_BUILDER_REGISTRATION_DEFAULT_ENABLED;
    private boolean validatorClientSszBlocksEnabled = DEFAULT_VALIDATOR_CLIENT_SSZ_BLOCKS_ENABLED;
    private boolean validatorClientUsePostValidatorsEndpointEnabled =
        DEFAULT_VALIDATOR_CLIENT_USE_POST_VALIDATORS_ENDPOINT_ENABLED;
    private boolean doppelgangerDetectionEnabled = DEFAULT_DOPPELGANGER_DETECTION_ENABLED;
    private boolean failoversSendSubnetSubscriptionsEnabled =
        DEFAULT_FAILOVERS_SEND_SUBNET_SUBSCRIPTIONS_ENABLED;
    private boolean failoversPublishSignedDutiesEnabled =
        DEFAULT_FAILOVERS_PUBLISH_SIGNED_DUTIES_ENABLED;
    private boolean exitWhenNoValidatorKeysEnabled = DEFAULT_EXIT_WHEN_NO_VALIDATOR_KEYS_ENABLED;
    private boolean shutdownWhenValidatorSlashedEnabled =
        DEFAULT_SHUTDOWN_WHEN_VALIDATOR_SLASHED_ENABLED;
    private Optional<UInt64> builderRegistrationDefaultGasLimit = Optional.empty();
    private int builderRegistrationSendingBatchSize =
        DEFAULT_VALIDATOR_REGISTRATION_SENDING_BATCH_SIZE;
    private Optional<UInt64> builderRegistrationTimestampOverride = Optional.empty();
    private Optional<BLSPublicKey> builderRegistrationPublicKeyOverride = Optional.empty();
    private OptionalInt executorMaxQueueSize = OptionalInt.empty();
    private OptionalInt beaconApiExecutorThreads = OptionalInt.empty();
    private OptionalInt beaconApiReadinessExecutorThreads = OptionalInt.empty();
    private Optional<String> sentryNodeConfigurationFile = Optional.empty();
    private int executorThreads = DEFAULT_VALIDATOR_EXECUTOR_THREADS;
    private boolean isLocalSlashingProtectionSynchronizedModeEnabled =
        DEFAULT_VALIDATOR_IS_LOCAL_SLASHING_PROTECTION_SYNCHRONIZED_ENABLED;
    private boolean slashingProtectionStrictModeEnabled =
        DEFAULT_VALIDATOR_SLASHING_PROTECTION_STRICT_MODE_ENABLED;
    private boolean dvtSelectionsEndpointEnabled = DEFAULT_OBOL_DVT_SELECTIONS_ENDPOINT_ENABLED;
    private boolean attestationsV2ApisEnabled = DEFAULT_ATTESTATIONS_V2_APIS_ENABLED;
    private UInt64 builderMinBid = DEFAULT_BUILDER_MIN_BID;
    private UInt64 builderBoostFactor = DEFAULT_BUILDER_BOOST_FACTOR;
    private List<URL> builderUrls = new ArrayList<>();

    private Builder() {}

    public Builder validatorKeys(final List<String> validatorKeys) {
      this.validatorKeys = validatorKeys;
      return this;
    }

    public Builder validatorExternalSignerPublicKeySources(
        final List<String> validatorExternalSignerPublicKeySources) {
      checkNotNull(validatorExternalSignerPublicKeySources);
      this.validatorExternalSignerPublicKeySources = validatorExternalSignerPublicKeySources;
      return this;
    }

    public Builder validatorExternalSignerUrl(final URL validatorExternalSignerUrl) {
      if (validatorExternalSignerUrl != null) {
        this.validatorExternalSignerUrl = UrlSanitizer.sanitizeUrl(validatorExternalSignerUrl);
        this.validatorExternalSignerUserInfo =
            Optional.ofNullable(validatorExternalSignerUrl.getUserInfo());
      }
      return this;
    }

    public Builder validatorExternalSignerSlashingProtectionEnabled(
        final boolean validatorExternalSignerSlashingProtectionEnabled) {
      this.validatorExternalSignerSlashingProtectionEnabled =
          validatorExternalSignerSlashingProtectionEnabled;
      return this;
    }

    public Builder validatorExternalSignerTimeout(final Duration validatorExternalSignerTimeout) {
      if (validatorExternalSignerTimeout.isNegative()) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid validatorExternalSignerTimeout: %s", validatorExternalSignerTimeout));
      }
      this.validatorExternalSignerTimeout = validatorExternalSignerTimeout;
      return this;
    }

    public Builder validatorExternalSignerConcurrentRequestLimit(
        final int validatorExternalSignerConcurrentRequestLimit) {
      if (validatorExternalSignerConcurrentRequestLimit
          < MINIMUM_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid validatorExternalSignerConcurrentRequestLimit: %s (must not be less than %s)",
                validatorExternalSignerConcurrentRequestLimit,
                MINIMUM_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT));
      } else if (validatorExternalSignerConcurrentRequestLimit
          > MAXIMUM_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT) {
        throw new InvalidConfigurationException(
            String.format(
                "Invalid validatorExternalSignerConcurrentRequestLimit: %s (must not be greater than %s)",
                validatorExternalSignerConcurrentRequestLimit,
                MAXIMUM_VALIDATOR_EXTERNAL_SIGNER_CONCURRENT_REQUEST_LIMIT));
      }
      this.validatorExternalSignerConcurrentRequestLimit =
          validatorExternalSignerConcurrentRequestLimit;
      return this;
    }

    public Builder validatorExternalSignerKeystore(final Path validatorExternalSignerKeystore) {
      this.validatorExternalSignerKeystore = validatorExternalSignerKeystore;
      return this;
    }

    public Builder validatorExternalSignerKeystorePasswordFile(
        final Path validatorExternalSignerKeystorePasswordFile) {
      this.validatorExternalSignerKeystorePasswordFile =
          validatorExternalSignerKeystorePasswordFile;
      return this;
    }

    public Builder validatorExternalSignerTruststore(final Path validatorExternalSignerTruststore) {
      this.validatorExternalSignerTruststore = validatorExternalSignerTruststore;
      return this;
    }

    public Builder validatorExternalSignerTruststorePasswordFile(
        final Path validatorExternalSignerTruststorePasswordFile) {
      this.validatorExternalSignerTruststorePasswordFile =
          validatorExternalSignerTruststorePasswordFile;
      return this;
    }

    public Builder beaconNodeApiEndpoints(final List<URI> beaconNodeApiEndpoints) {
      this.beaconNodeApiEndpoints = Optional.of(beaconNodeApiEndpoints);
      return this;
    }

    public Builder graffitiProvider(final GraffitiProvider graffitiProvider) {
      this.graffitiProvider = graffitiProvider;
      return this;
    }

    public Builder clientGraffitiAppendFormat(
        final ClientGraffitiAppendFormat clientGraffitiAppendFormat) {
      this.clientGraffitiAppendFormat = clientGraffitiAppendFormat;
      return this;
    }

    public Builder validatorPerformanceTrackingMode(
        final ValidatorPerformanceTrackingMode validatorPerformanceTrackingMode) {
      this.validatorPerformanceTrackingMode = validatorPerformanceTrackingMode;
      return this;
    }

    public Builder validatorKeystoreLockingEnabled(final boolean validatorKeystoreLockingEnabled) {
      this.validatorKeystoreLockingEnabled = validatorKeystoreLockingEnabled;
      return this;
    }

    public Builder generateEarlyAttestations(final boolean generateEarlyAttestations) {
      this.generateEarlyAttestations = generateEarlyAttestations;
      return this;
    }

    public Builder proposerDefaultFeeRecipient(final String proposerDefaultFeeRecipient) {
      if (proposerDefaultFeeRecipient == null) {
        this.proposerDefaultFeeRecipient = Optional.empty();
      } else {
        try {
          this.proposerDefaultFeeRecipient =
              Optional.of(Eth1Address.fromHexString(proposerDefaultFeeRecipient));
        } catch (RuntimeException ex) {
          throw new RuntimeException(
              "Invalid Default Fee Recipient: " + ExceptionUtil.getRootCauseMessage(ex), ex);
        }
      }
      return this;
    }

    public Builder proposerConfigSource(final String proposerConfigSource) {
      this.proposerConfigSource = Optional.ofNullable(proposerConfigSource);
      return this;
    }

    public Builder refreshProposerConfigFromSource(final boolean refreshProposerConfigFromSource) {
      this.refreshProposerConfigFromSource = refreshProposerConfigFromSource;
      return this;
    }

    public Builder builderRegistrationDefaultEnabled(
        final boolean validatorsRegistrationDefaultEnabled) {
      this.validatorsRegistrationDefaultEnabled = validatorsRegistrationDefaultEnabled;
      return this;
    }

    public Builder validatorClientUseSszBlocksEnabled(
        final boolean validatorClientUseSszBlocksEnabled) {
      this.validatorClientSszBlocksEnabled = validatorClientUseSszBlocksEnabled;
      return this;
    }

    public Builder validatorClientUsePostValidatorsEndpointEnabled(
        final boolean validatorClientUsePostValidatorsEndpointEnabled) {
      this.validatorClientUsePostValidatorsEndpointEnabled =
          validatorClientUsePostValidatorsEndpointEnabled;
      return this;
    }

    public Builder doppelgangerDetectionEnabled(final boolean doppelgangerDetectionEnabled) {
      this.doppelgangerDetectionEnabled = doppelgangerDetectionEnabled;
      return this;
    }

    public Builder executorThreads(final int executorThreads) {
      if (executorThreads < 1 || executorThreads > 5_000) {
        throw new InvalidConfigurationException(
            "Invalid configuration. --Xvalidator-client-executor-threads must be greater than 0 and less than 5000.");
      }

      this.executorThreads = executorThreads;
      return this;
    }

    public Builder failoversSendSubnetSubscriptionsEnabled(
        final boolean failoversSendSubnetSubscriptionsEnabled) {
      this.failoversSendSubnetSubscriptionsEnabled = failoversSendSubnetSubscriptionsEnabled;
      return this;
    }

    public Builder failoversPublishSignedDutiesEnabled(
        final boolean failoversPublishSignedDutiesEnabled) {
      this.failoversPublishSignedDutiesEnabled = failoversPublishSignedDutiesEnabled;
      return this;
    }

    public Builder exitWhenNoValidatorKeysEnabled(final boolean exitWhenNoValidatorKeysEnabled) {
      this.exitWhenNoValidatorKeysEnabled = exitWhenNoValidatorKeysEnabled;
      return this;
    }

    public Builder shutdownWhenValidatorSlashedEnabled(
        final boolean shutdownWhenValidatorSlashedEnabled) {
      this.shutdownWhenValidatorSlashedEnabled = shutdownWhenValidatorSlashedEnabled;
      return this;
    }

    public Builder builderRegistrationDefaultGasLimit(
        final Optional<UInt64> builderRegistrationDefaultGasLimit) {
      this.builderRegistrationDefaultGasLimit = builderRegistrationDefaultGasLimit;
      return this;
    }

    public Builder builderRegistrationSendingBatchSize(
        final int builderRegistrationSendingBatchSize) {
      this.builderRegistrationSendingBatchSize = builderRegistrationSendingBatchSize;
      return this;
    }

    public Builder builderRegistrationTimestampOverride(
        final UInt64 builderRegistrationTimestampOverride) {
      this.builderRegistrationTimestampOverride =
          Optional.ofNullable(builderRegistrationTimestampOverride);
      return this;
    }

    public Builder builderRegistrationPublicKeyOverride(
        final String builderRegistrationPublicKeyOverride) {
      this.builderRegistrationPublicKeyOverride =
          Optional.ofNullable(builderRegistrationPublicKeyOverride)
              .map(BLSPublicKey::fromHexString);
      return this;
    }

    public Builder executorMaxQueueSize(final OptionalInt executorMaxQueueSize) {
      this.executorMaxQueueSize = executorMaxQueueSize;
      return this;
    }

    public Builder beaconApiExecutorThreads(final OptionalInt beaconApiExecutorThreads) {
      this.beaconApiExecutorThreads = beaconApiExecutorThreads;
      return this;
    }

    public Builder beaconApiReadinessExecutorThreads(
        final OptionalInt beaconApiReadinessExecutorThreads) {
      this.beaconApiReadinessExecutorThreads = beaconApiReadinessExecutorThreads;
      return this;
    }

    public Builder executorMaxQueueSizeIfDefault(final int executorMaxQueueSize) {
      if (this.executorMaxQueueSize.isEmpty()) {
        this.executorMaxQueueSize = OptionalInt.of(executorMaxQueueSize);
      }
      return this;
    }

    public Builder sentryNodeConfigurationFile(final String configFile) {
      this.sentryNodeConfigurationFile = Optional.ofNullable(configFile);
      return this;
    }

    public Builder isLocalSlashingProtectionSynchronizedModeEnabled(
        final boolean isLocalSlashingProtectionSynchronizedModeEnabled) {
      this.isLocalSlashingProtectionSynchronizedModeEnabled =
          isLocalSlashingProtectionSynchronizedModeEnabled;
      return this;
    }

    public Builder slashingProtectionStrictModeEnabled(
        final boolean slashingProtectionStrictModeEnabled) {
      this.slashingProtectionStrictModeEnabled = slashingProtectionStrictModeEnabled;
      return this;
    }

    public Builder obolDvtSelectionsEndpointEnabled(final boolean dvtSelectionsEndpointEnabled) {
      this.dvtSelectionsEndpointEnabled = dvtSelectionsEndpointEnabled;
      return this;
    }

    public Builder attestationsV2ApisEnabled(final boolean attestationsV2ApisEnabled) {
      this.attestationsV2ApisEnabled = attestationsV2ApisEnabled;
      return this;
    }

    public Builder builderMinBid(final UInt64 builderMinBid) {
      this.builderMinBid = builderMinBid;
      return this;
    }

    public Builder builderBoostFactor(final UInt64 builderBoostFactor) {
      this.builderBoostFactor = builderBoostFactor;
      return this;
    }

    public Builder builderUrls(final List<URL> builderUrls) {
      this.builderUrls = builderUrls;
      return this;
    }

    public ValidatorConfig build() {
      validateExternalSignerUrlAndPublicKeys();
      validateExternalSignerKeystoreAndPasswordFileConfig();
      validateExternalSignerTruststoreAndPasswordFileConfig();
      validateExternalSignerURLScheme();
      return new ValidatorConfig(
          validatorKeys,
          validatorExternalSignerPublicKeySources,
          validatorExternalSignerUrl,
          validatorExternalSignerUserInfo,
          validatorExternalSignerTimeout,
          validatorExternalSignerKeystore,
          validatorExternalSignerKeystorePasswordFile,
          validatorExternalSignerTruststore,
          validatorExternalSignerTruststorePasswordFile,
          beaconNodeApiEndpoints,
          graffitiProvider,
          clientGraffitiAppendFormat,
          validatorPerformanceTrackingMode,
          validatorKeystoreLockingEnabled,
          validatorExternalSignerSlashingProtectionEnabled,
          validatorExternalSignerConcurrentRequestLimit,
          generateEarlyAttestations,
          proposerDefaultFeeRecipient,
          proposerConfigSource,
          refreshProposerConfigFromSource,
          validatorsRegistrationDefaultEnabled,
          validatorClientSszBlocksEnabled,
          validatorClientUsePostValidatorsEndpointEnabled,
          doppelgangerDetectionEnabled,
          failoversSendSubnetSubscriptionsEnabled,
          failoversPublishSignedDutiesEnabled,
          exitWhenNoValidatorKeysEnabled,
          shutdownWhenValidatorSlashedEnabled,
          builderRegistrationDefaultGasLimit,
          builderRegistrationSendingBatchSize,
          builderRegistrationTimestampOverride,
          builderRegistrationPublicKeyOverride,
          executorMaxQueueSize.orElse(DEFAULT_EXECUTOR_MAX_QUEUE_SIZE),
          executorThreads,
          beaconApiExecutorThreads,
          beaconApiReadinessExecutorThreads,
          sentryNodeConfigurationFile,
          isLocalSlashingProtectionSynchronizedModeEnabled,
          slashingProtectionStrictModeEnabled,
          dvtSelectionsEndpointEnabled,
          attestationsV2ApisEnabled,
          builderMinBid,
          builderBoostFactor,
          builderUrls);
    }

    private void validateExternalSignerUrlAndPublicKeys() {
      if (validatorExternalSignerPublicKeySources.isEmpty()) {
        return;
      }

      if (validatorExternalSignerUrl == null) {
        final String errorMessage =
            "Invalid configuration. '--validators-external-signer-url' and '--validators-external-signer-public-keys' must be specified together";
        throw new InvalidConfigurationException(errorMessage);
      }

      validatorExternalSignerPublicKeySources.forEach(
          source -> {
            if (source.contains(":")) {
              try {
                final URL url = new URI(source).toURL();
                if (hostAndPortMatching(url, validatorExternalSignerUrl)) {
                  LOG.warn(
                      "'--validators-external-signer-public-keys' contains an URL matching the external-signer-url host and port. Use 'external-signer' instead if you want to use all public keys exposed by the external signer");
                }
              } catch (MalformedURLException | URISyntaxException e) {
                throw new InvalidConfigurationException(
                    "Invalid configuration. '--validators-external-signer-public-keys' contains a malformed URL: "
                        + source);
              }
            }
          });
    }

    private boolean hostAndPortMatching(final URL url1, final URL url2) {
      return urlToMatchingPart(url1).equals(urlToMatchingPart(url2));
    }

    private String urlToMatchingPart(final URL url) {
      int port = url.getPort();
      if (port == -1) {
        port = url.getDefaultPort();
      }

      return url.getHost().toLowerCase(Locale.ROOT) + port;
    }

    private void validateExternalSignerKeystoreAndPasswordFileConfig() {
      if (onlyOneInitialized(
          validatorExternalSignerKeystore, validatorExternalSignerKeystorePasswordFile)) {
        final String errorMessage =
            "Invalid configuration. '--validators-external-signer-keystore' and '--validators-external-signer-keystore-password-file' must be specified together";
        throw new InvalidConfigurationException(errorMessage);
      }
    }

    private void validateExternalSignerTruststoreAndPasswordFileConfig() {
      if (onlyOneInitialized(
          validatorExternalSignerTruststore, validatorExternalSignerTruststorePasswordFile)) {
        final String errorMessage =
            "Invalid configuration. '--validators-external-signer-truststore' and '--validators-external-signer-truststore-password-file' must be specified together";
        throw new InvalidConfigurationException(errorMessage);
      }
    }

    private void validateExternalSignerURLScheme() {
      if (validatorExternalSignerPublicKeySources.isEmpty() || validatorExternalSignerUrl == null) {
        return;
      }

      if (validatorExternalSignerKeystore != null
          || validatorExternalSignerTruststore != null
          || validatorExternalSignerUserInfo.isPresent()) {
        if (!isURLSchemeHttps(validatorExternalSignerUrl)) {
          final String errorMessage =
              String.format(
                  "Invalid configuration. --validators-external-signer-url (%s) must start with https because external signer keystore/truststore are defined or basic authentication is used",
                  validatorExternalSignerUrl);
          throw new InvalidConfigurationException(errorMessage);
        }
      }
    }

    private static boolean isURLSchemeHttps(final URL url) {
      return "https".equalsIgnoreCase(url.getProtocol());
    }

    private boolean onlyOneInitialized(final Object o1, final Object o2) {
      return (o1 == null) != (o2 == null);
    }
  }
}
