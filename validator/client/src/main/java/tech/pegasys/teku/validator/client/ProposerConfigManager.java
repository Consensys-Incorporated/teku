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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.ProposerConfig.BuilderConfig;
import tech.pegasys.teku.validator.client.ProposerConfig.BuilderOverrides;
import tech.pegasys.teku.validator.client.ProposerConfig.Config;
import tech.pegasys.teku.validator.client.ProposerConfig.RegistrationOverrides;
import tech.pegasys.teku.validator.client.ResolvedBuilderConfig.ResolvedBuilderEntry;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;
import tech.pegasys.teku.validator.client.proposerconfig.ProposerConfigProvider;

public class ProposerConfigManager
    implements ProposerConfigPropertiesProvider, ValidatorTimingChannel {
  private static final Logger LOG = LogManager.getLogger();

  private static final String INVALID_BUILDER_URL_LOG_FORMAT =
      "Ignoring builder with invalid url {} configured for validator {}";

  private final ValidatorConfig config;
  private final RuntimeProposerConfig runtimeProposerConfig;
  private final ProposerConfigProvider proposerConfigProvider;
  private final Spec spec;

  private final AtomicReference<Optional<ProposerConfig>> maybeProposerConfig =
      new AtomicReference<>(Optional.empty());
  private final SafeFuture<Void> initializationComplete = new SafeFuture<>();

  private final AtomicReference<UInt64> currentEpoch = new AtomicReference<>(UInt64.ZERO);
  // EIP-8261: the gas limit scheduled for the current epoch, only recomputed on epoch change
  private final AtomicReference<Optional<UInt64>> scheduledGasLimit =
      new AtomicReference<>(Optional.empty());
  // keeps the "configured gas limit is above the recommended one" warning to one per validator
  private final Map<BLSPublicKey, UInt64> warnedGasLimitByPublicKey = new ConcurrentHashMap<>();

  private Optional<OwnedValidators> ownedValidators = Optional.empty();

  public ProposerConfigManager(
      final ValidatorConfig config,
      final RuntimeProposerConfig runtimeProposerConfig,
      final ProposerConfigProvider proposerConfigProvider,
      final Spec spec) {
    checkNotNull(spec, "A spec is expected");
    this.config = config;
    this.runtimeProposerConfig = runtimeProposerConfig;
    this.proposerConfigProvider = proposerConfigProvider;
    this.spec = spec;
    updateScheduledGasLimit(currentEpoch.get());
  }

  @Override
  public void onSlot(final UInt64 slot) {
    final UInt64 epoch = spec.computeEpochAtSlot(slot);
    if (currentEpoch.getAndSet(epoch).equals(epoch)) {
      return;
    }
    updateScheduledGasLimit(epoch);
  }

  /**
   * EIP-8261: resolves the gas limit scheduled for the given epoch, logging whenever the value
   * provided by the schedule changes.
   */
  private void updateScheduledGasLimit(final UInt64 epoch) {
    final Optional<UInt64> maybeScheduledGasLimit = spec.getScheduledGasLimit(epoch);
    if (scheduledGasLimit.getAndSet(maybeScheduledGasLimit).equals(maybeScheduledGasLimit)) {
      return;
    }
    warnedGasLimitByPublicKey.clear();
    maybeScheduledGasLimit.ifPresent(
        gasLimit ->
            LOG.info(
                "Gas limit schedule is active: the network recommends a gas limit of {} from epoch {}.",
                gasLimit,
                epoch));
  }

  public SafeFuture<Void> initialize(final OwnedValidators ownedValidators) {
    this.ownedValidators = Optional.of(ownedValidators);
    internalRefresh().propagateTo(initializationComplete);
    return initializationComplete;
  }

  @Override
  public SafeFuture<Void> refresh() {
    return internalRefresh()
        .exceptionally(
            throwable -> {
              LOG.warn("An error occurred while obtaining proposer config", throwable);
              return null;
            });
  }

  private SafeFuture<Void> internalRefresh() {
    return proposerConfigProvider.getProposerConfig().thenAccept(maybeProposerConfig::set);
  }

  // Cannot set a fee recipient if the key is specified in the configuration file
  // Cannot set fee recipient to 0x00
  public void setFeeRecipient(final BLSPublicKey publicKey, final Eth1Address eth1Address)
      throws SetFeeRecipientException {
    if (eth1Address.equals(Eth1Address.ZERO)) {
      throw new SetFeeRecipientException("Cannot set fee recipient to 0x00 address.");
    }
    if (!isOwnedValidator(publicKey)) {
      throw new SetFeeRecipientException(
          "Validator public key not found when attempting to set fee recipient.");
    }
    Optional<Eth1Address> maybeEth1Address = getFeeRecipientFromProposerConfig(publicKey);
    if (maybeEth1Address.isPresent()) {
      throw new SetFeeRecipientException("Cannot update fee recipient via api.");
    }
    runtimeProposerConfig.updateFeeRecipient(publicKey, eth1Address);
  }

  public void setGasLimit(final BLSPublicKey publicKey, final UInt64 gasLimit)
      throws SetFeeRecipientException {
    if (!isOwnedValidator(publicKey)) {
      throw new SetGasLimitException(
          "Validator public key not found when attempting to set gas limit.");
    }
    Optional<UInt64> maybeGasLimit = getGasLimitFromProposerConfig(publicKey);
    if (maybeGasLimit.isPresent()) {
      throw new SetGasLimitException("Cannot update gas limit via api.");
    }
    runtimeProposerConfig.updateGasLimit(publicKey, gasLimit);
  }

  public boolean deleteFeeRecipient(final BLSPublicKey publicKey) {
    Optional<Eth1Address> maybeEth1Address = getFeeRecipientFromProposerConfig(publicKey);
    if (maybeEth1Address.isPresent()) {
      return false;
    }
    runtimeProposerConfig.deleteFeeRecipient(publicKey);
    return true;
  }

  public boolean deleteGasLimit(final BLSPublicKey publicKey) {
    Optional<UInt64> maybeGasLimit = getGasLimitFromProposerConfig(publicKey);
    if (maybeGasLimit.isPresent()) {
      return false;
    }
    runtimeProposerConfig.deleteGasLimit(publicKey);
    return true;
  }

  // 2 configurations, 2 defaults
  // Priority order
  // - Specifically configured key in --validator-proposer-config file
  // - proposer set via the SET api (runtime configuration)
  // - default set in --validator-proposer-config file
  // - default set by --validators-proposer-default-fee-recipient
  @Override
  public Optional<Eth1Address> getFeeRecipient(final BLSPublicKey publicKey) {
    return getAttributeWithFallback(Config::getFeeRecipient, publicKey)
        .or(config::getProposerDefaultFeeRecipient);
  }

  /**
   * Priority order
   *
   * <ul>
   *   <li>gas limit configured for the key (proposer config file, or the SET api)
   *   <li>default configured via <code>--validators-builder-registration-default-gas-limit</code>
   *   <li>gas limit scheduled by the network for the epoch (EIP-8261)
   *   <li>{@link ValidatorConfig#DEFAULT_BUILDER_REGISTRATION_GAS_LIMIT}
   * </ul>
   */
  @Override
  public UInt64 getGasLimit(final BLSPublicKey publicKey) {
    return getGasLimit(publicKey, currentEpoch.get());
  }

  @Override
  public UInt64 getGasLimit(final BLSPublicKey publicKey, final UInt64 epoch) {
    final Optional<UInt64> maybeConfiguredGasLimit =
        getAttributeWithFallback(Config::getGasLimit, publicKey)
            .or(config::getBuilderRegistrationDefaultGasLimit);

    final Optional<UInt64> maybeScheduledGasLimit = getScheduledGasLimit(epoch);
    if (maybeConfiguredGasLimit.isEmpty()) {
      return maybeScheduledGasLimit.orElse(ValidatorConfig.DEFAULT_BUILDER_REGISTRATION_GAS_LIMIT);
    }

    final UInt64 configuredGasLimit = maybeConfiguredGasLimit.get();
    if (maybeScheduledGasLimit.isPresent()
        && configuredGasLimit.isGreaterThan(maybeScheduledGasLimit.get())) {
      warnAboutGasLimit(publicKey, epoch, configuredGasLimit, maybeScheduledGasLimit.get());
    }
    return configuredGasLimit;
  }

  private Optional<UInt64> getScheduledGasLimit(final UInt64 epoch) {
    if (epoch.equals(currentEpoch.get())) {
      // the common case, resolved once per epoch instead of once per validator
      return scheduledGasLimit.get();
    }
    return spec.getScheduledGasLimit(epoch);
  }

  /** Warns at most once per validator for a given configured gas limit. */
  private void warnAboutGasLimit(
      final BLSPublicKey publicKey,
      final UInt64 epoch,
      final UInt64 configuredGasLimit,
      final UInt64 recommendedGasLimit) {
    final UInt64 previouslyWarnedGasLimit =
        warnedGasLimitByPublicKey.put(publicKey, configuredGasLimit);
    if (configuredGasLimit.equals(previouslyWarnedGasLimit)) {
      return;
    }
    LOG.warn(
        "The gas limit {} configured for validator {} is higher than the {} recommended by the network for epoch {}.",
        configuredGasLimit,
        publicKey,
        recommendedGasLimit,
        epoch);
  }

  @Override
  public boolean isReadyToProvideProperties() {
    return initializationComplete.isCompletedNormally();
  }

  @Override
  public boolean isBuilderEnabled(final BLSPublicKey publicKey) {
    return getAttributeWithFallback(
            config -> config.getBuilder().flatMap(BuilderConfig::isEnabled), publicKey)
        .orElse(config.isBuilderRegistrationDefaultEnabled());
  }

  @Override
  public ResolvedBuilderConfig resolveBuilderConfig(final BLSPublicKey publicKey) {
    final UInt64 minBid =
        getAttributeWithFallback(
                config -> config.getBuilder().flatMap(BuilderConfig::getMinBid), publicKey)
            .orElse(config.getBuilderMinBid());
    final UInt64 builderBoostFactor =
        getAttributeWithFallback(
                config -> config.getBuilder().flatMap(BuilderConfig::getBuilderBoostFactor),
                publicKey)
            .orElse(config.getBuilderBoostFactor());

    // the builder flow is on unless the proposer config disables it
    final boolean builderEnabled =
        getAttributeWithFallback(
                config -> config.getBuilder().flatMap(BuilderConfig::isEnabled), publicKey)
            .orElse(true);

    if (!builderEnabled) {
      return new ResolvedBuilderConfig(minBid, builderBoostFactor, List.of());
    }

    final Optional<Map<String, BuilderOverrides>> maybeProposerConfigBuilders =
        getAttributeWithFallback(
            config ->
                config.getBuilder().map(BuilderConfig::getUrls).filter(urls -> !urls.isEmpty()),
            publicKey);

    final List<ResolvedBuilderEntry> builders;
    if (maybeProposerConfigBuilders.isEmpty()) {
      builders =
          config.getBuilderUrls().stream()
              .map(
                  url ->
                      new ResolvedBuilderEntry(
                          url,
                          Optional.empty(),
                          List.of(),
                          config.getBuilderMaxExecutionPayment(),
                          minBid,
                          builderBoostFactor))
              .toList();
    } else {
      builders = new ArrayList<>();
      maybeProposerConfigBuilders
          .get()
          .forEach(
              (url, overrides) -> {
                final URL builderUrl;
                try {
                  builderUrl = URI.create(url).toURL();
                } catch (final MalformedURLException | IllegalArgumentException e) {
                  LOG.warn(INVALID_BUILDER_URL_LOG_FORMAT, url, publicKey, e);
                  return;
                }
                if (isNullOrEmpty(builderUrl.getHost())) {
                  LOG.warn(INVALID_BUILDER_URL_LOG_FORMAT, url, publicKey);
                  return;
                }
                builders.add(
                    new ResolvedBuilderEntry(
                        builderUrl,
                        overrides.getAuthData(),
                        overrides.getBuilderPubkeys().orElse(List.of()),
                        overrides
                            .getMaxExecutionPayment()
                            .orElse(config.getBuilderMaxExecutionPayment()),
                        overrides.getMinBid().orElse(minBid),
                        overrides.getBuilderBoostFactor().orElse(builderBoostFactor)));
              });
    }
    return new ResolvedBuilderConfig(minBid, builderBoostFactor, builders);
  }

  @Override
  public Optional<UInt64> getBuilderRegistrationTimestampOverride(final BLSPublicKey publicKey) {
    return getAttributeWithFallback(
            config ->
                config
                    .getBuilder()
                    .flatMap(BuilderConfig::getRegistrationOverrides)
                    .flatMap(RegistrationOverrides::getTimestamp),
            publicKey)
        .or(config::getBuilderRegistrationTimestampOverride);
  }

  @Override
  public Optional<BLSPublicKey> getBuilderRegistrationPublicKeyOverride(
      final BLSPublicKey publicKey) {
    return getAttributeWithFallback(
            config ->
                config
                    .getBuilder()
                    .flatMap(BuilderConfig::getRegistrationOverrides)
                    .flatMap(RegistrationOverrides::getPublicKey),
            publicKey)
        .or(config::getBuilderRegistrationPublicKeyOverride);
  }

  private <T> Optional<T> getAttributeWithFallback(
      final Function<Config, Optional<T>> selector, final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .get()
        .map(
            proposerConfig ->
                proposerConfig
                    .getConfigForPubKey(publicKey)
                    .flatMap(selector)
                    .or(() -> runtimeProposerConfig.getProposerConfig(publicKey).flatMap(selector))
                    .or(() -> selector.apply(proposerConfig.getDefaultConfig())))
        .orElseGet(() -> runtimeProposerConfig.getProposerConfig(publicKey).flatMap(selector));
  }

  private Optional<Eth1Address> getFeeRecipientFromProposerConfig(final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .get()
        .flatMap(config -> config.getConfigForPubKey(publicKey))
        .flatMap(Config::getFeeRecipient);
  }

  private Optional<UInt64> getGasLimitFromProposerConfig(final BLSPublicKey publicKey) {
    return maybeProposerConfig
        .get()
        .flatMap(config -> config.getConfigForPubKey(publicKey))
        .flatMap(Config::getGasLimit);
  }

  public boolean isOwnedValidator(final BLSPublicKey publicKey) {
    return ownedValidators.isPresent() && ownedValidators.get().getValidator(publicKey).isPresent();
  }
}
