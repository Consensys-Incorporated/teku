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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Objects.requireNonNullElse;
import static tech.pegasys.teku.spec.config.SpecConfigGloas.MAX_BUILDER_AUTH_DATA_SIZE;
import static tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfigSchema.MAX_BUILDER_ENTRIES;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.MAX_BUILDER_PUBKEYS;
import static tech.pegasys.teku.spec.schemas.ApiSchemas.MAX_BUILDER_URL_SIZE;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ProposerConfig {
  @JsonProperty(value = "proposer_config")
  private final Map<Bytes48, Config> proposerConfig;

  @JsonProperty(value = "default_config")
  private final Config defaultConfig;

  @JsonCreator
  public ProposerConfig(
      @JsonProperty(value = "proposer_config") final Map<Bytes48, Config> proposerConfig,
      @JsonProperty(value = "default_config") final Config defaultConfig) {
    checkNotNull(defaultConfig, "\"default_config\" is required");
    checkNotNull(defaultConfig.feeRecipient, "\"fee_recipient\" is required in \"default_config\"");
    checkState(
        defaultConfig.builder == null || defaultConfig.builder.enabled != null,
        "\"enabled\" is required in \"default_config.builder\"");
    checkState(
        defaultConfig.builder == null
            || defaultConfig.builder.registrationOverrides == null
            || defaultConfig.builder.registrationOverrides.publicKey == null,
        "\"publicKey\" is not allowed in \"default_config.builder.registrationOverrides\"");
    this.proposerConfig = requireNonNullElse(proposerConfig, ImmutableMap.of());
    this.defaultConfig = defaultConfig;
  }

  public Optional<Config> getConfigForPubKey(final BLSPublicKey pubKey) {
    return getConfigForPubKey(pubKey.toBytesCompressed());
  }

  public Optional<Config> getConfigForPubKey(final String pubKey) {
    return getConfigForPubKey(Bytes48.fromHexString(pubKey));
  }

  public Config getDefaultConfig() {
    return defaultConfig;
  }

  public int getNumberOfProposerConfigs() {
    return proposerConfig.size();
  }

  private Optional<Config> getConfigForPubKey(final Bytes48 pubKey) {
    return Optional.ofNullable(proposerConfig.get(pubKey));
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ProposerConfig that = (ProposerConfig) o;
    return Objects.equals(proposerConfig, that.proposerConfig)
        && Objects.equals(defaultConfig, that.defaultConfig);
  }

  @Override
  public int hashCode() {
    return Objects.hash(proposerConfig, defaultConfig);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Config {
    @JsonProperty(value = "fee_recipient")
    private final Eth1Address feeRecipient;

    @JsonProperty(value = "builder")
    private final BuilderConfig builder;

    @JsonCreator
    public Config(
        @JsonProperty(value = "fee_recipient") final Eth1Address feeRecipient,
        @JsonProperty(value = "builder") final BuilderConfig builder) {
      this.feeRecipient = feeRecipient;
      this.builder = builder;
    }

    public Optional<Eth1Address> getFeeRecipient() {
      return Optional.ofNullable(feeRecipient);
    }

    public Optional<UInt64> getGasLimit() {
      return getBuilder().flatMap(BuilderConfig::getGasLimit);
    }

    public Optional<BuilderConfig> getBuilder() {
      return Optional.ofNullable(builder);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final Config that = (Config) o;
      return Objects.equals(feeRecipient, that.feeRecipient)
          && Objects.equals(builder, that.builder);
    }

    @Override
    public int hashCode() {
      return Objects.hash(feeRecipient, builder);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BuilderConfig {
    @JsonProperty(value = "enabled")
    private final Boolean enabled;

    @JsonProperty(value = "gas_limit")
    private final UInt64 gasLimit;

    @JsonProperty(value = "registration_overrides")
    private final RegistrationOverrides registrationOverrides;

    @JsonProperty(value = "min_bid")
    private final UInt64 minBid;

    @JsonProperty(value = "builder_boost_factor")
    private final UInt64 builderBoostFactor;

    @JsonProperty(value = "urls")
    private final Map<String, BuilderOverrides> urls;

    // the urls parsed once on creation, in the order they were configured
    private final List<BuilderUrl> builders;

    @JsonCreator
    public BuilderConfig(
        @JsonProperty(value = "enabled") final Boolean enabled,
        @JsonProperty(value = "gas_limit") final UInt64 gasLimit,
        @JsonProperty(value = "registration_overrides")
            final RegistrationOverrides registrationOverrides,
        @JsonProperty(value = "min_bid") final UInt64 minBid,
        @JsonProperty(value = "builder_boost_factor") final UInt64 builderBoostFactor,
        @JsonProperty(value = "urls") final Map<String, BuilderOverrides> urls) {
      this.enabled = enabled;
      this.gasLimit = gasLimit;
      this.registrationOverrides = registrationOverrides;
      this.minBid = minBid;
      this.builderBoostFactor = builderBoostFactor;
      this.urls = urls == null ? ImmutableMap.of() : replaceNullOverrides(urls);
      this.builders = parseUrls(this.urls);
    }

    private static Map<String, BuilderOverrides> replaceNullOverrides(
        final Map<String, BuilderOverrides> urls) {
      final Map<String, BuilderOverrides> result = new LinkedHashMap<>();
      urls.forEach(
          (url, overrides) ->
              result.put(url, requireNonNullElse(overrides, BuilderOverrides.EMPTY)));
      return Collections.unmodifiableMap(result);
    }

    private static List<BuilderUrl> parseUrls(final Map<String, BuilderOverrides> urls) {
      checkArgument(
          urls.size() <= MAX_BUILDER_ENTRIES,
          "\"urls\" must contain at most %s entries",
          MAX_BUILDER_ENTRIES);
      return urls.entrySet().stream()
          .map(entry -> new BuilderUrl(parseUrl(entry.getKey()), entry.getValue()))
          .toList();
    }

    private static URL parseUrl(final String url) {
      checkArgument(
          url != null && !url.isEmpty() && url.length() <= MAX_BUILDER_URL_SIZE,
          "builder url must be between 1 and %s characters",
          MAX_BUILDER_URL_SIZE);
      try {
        final URL parsedUrl = URI.create(url).toURL();
        checkArgument(
            !isNullOrEmpty(parsedUrl.getHost()),
            "Invalid proposer config. Builder URL (%s) must contain a valid host",
            parsedUrl);
        return parsedUrl;
      } catch (MalformedURLException ex) {
        throw new IllegalArgumentException(
            "Invalid proposer config. Builder URL (" + url + ") has invalid syntax", ex);
      }
    }

    public Optional<Boolean> isEnabled() {
      return Optional.ofNullable(enabled);
    }

    public Optional<UInt64> getGasLimit() {
      return Optional.ofNullable(gasLimit);
    }

    public Optional<RegistrationOverrides> getRegistrationOverrides() {
      return Optional.ofNullable(registrationOverrides);
    }

    public Optional<UInt64> getMinBid() {
      return Optional.ofNullable(minBid);
    }

    public Optional<UInt64> getBuilderBoostFactor() {
      return Optional.ofNullable(builderBoostFactor);
    }

    public Map<String, BuilderOverrides> getUrls() {
      return urls;
    }

    @JsonIgnore
    public List<BuilderUrl> getBuilders() {
      return builders;
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final BuilderConfig that = (BuilderConfig) o;
      return Objects.equals(enabled, that.enabled)
          && Objects.equals(gasLimit, that.gasLimit)
          && Objects.equals(registrationOverrides, that.registrationOverrides)
          && Objects.equals(minBid, that.minBid)
          && Objects.equals(builderBoostFactor, that.builderBoostFactor)
          && Objects.equals(urls, that.urls);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          enabled, gasLimit, registrationOverrides, minBid, builderBoostFactor, urls);
    }
  }

  public static class RegistrationOverrides {
    @JsonProperty(value = "timestamp")
    private final UInt64 timestamp;

    @JsonProperty(value = "public_key")
    private final BLSPublicKey publicKey;

    @JsonCreator
    public RegistrationOverrides(
        @JsonProperty(value = "timestamp") final UInt64 timestamp,
        @JsonProperty(value = "public_key") final BLSPublicKey publicKey) {
      this.timestamp = timestamp;
      this.publicKey = publicKey;
    }

    public Optional<UInt64> getTimestamp() {
      return Optional.ofNullable(timestamp);
    }

    public Optional<BLSPublicKey> getPublicKey() {
      return Optional.ofNullable(publicKey);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final RegistrationOverrides that = (RegistrationOverrides) o;
      return Objects.equals(timestamp, that.timestamp) && Objects.equals(publicKey, that.publicKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(timestamp, publicKey);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class BuilderOverrides {
    @JsonProperty(value = "auth_data")
    private final Bytes authData;

    @JsonProperty(value = "builder_pubkeys")
    private final List<BLSPublicKey> builderPubkeys;

    @JsonProperty(value = "min_bid")
    private final UInt64 minBid;

    @JsonProperty(value = "builder_boost_factor")
    private final UInt64 builderBoostFactor;

    @JsonProperty(value = "max_execution_payment")
    private final UInt64 maxExecutionPayment;

    public static final BuilderOverrides EMPTY = new BuilderOverrides(null, null, null, null, null);

    @JsonCreator
    public BuilderOverrides(
        @JsonProperty(value = "auth_data") final Bytes authData,
        @JsonProperty(value = "builder_pubkeys") final List<BLSPublicKey> builderPubkeys,
        @JsonProperty(value = "min_bid") final UInt64 minBid,
        @JsonProperty(value = "builder_boost_factor") final UInt64 builderBoostFactor,
        @JsonProperty(value = "max_execution_payment") final UInt64 maxExecutionPayment) {
      checkArgument(
          authData == null
              || (!authData.isEmpty() && authData.size() <= MAX_BUILDER_AUTH_DATA_SIZE),
          "\"auth_data\" must be between 1 and %s bytes",
          MAX_BUILDER_AUTH_DATA_SIZE);
      checkArgument(
          builderPubkeys == null || builderPubkeys.size() <= MAX_BUILDER_PUBKEYS,
          "\"builder_pubkeys\" must contain at most %s entries",
          MAX_BUILDER_PUBKEYS);
      this.authData = authData;
      this.builderPubkeys = builderPubkeys;
      this.minBid = minBid;
      this.builderBoostFactor = builderBoostFactor;
      this.maxExecutionPayment = maxExecutionPayment;
    }

    public Optional<Bytes> getAuthData() {
      return Optional.ofNullable(authData);
    }

    public Optional<List<BLSPublicKey>> getBuilderPubkeys() {
      return Optional.ofNullable(builderPubkeys);
    }

    public Optional<UInt64> getMinBid() {
      return Optional.ofNullable(minBid);
    }

    public Optional<UInt64> getBuilderBoostFactor() {
      return Optional.ofNullable(builderBoostFactor);
    }

    public Optional<UInt64> getMaxExecutionPayment() {
      return Optional.ofNullable(maxExecutionPayment);
    }

    @Override
    public boolean equals(final Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      final BuilderOverrides that = (BuilderOverrides) o;
      return Objects.equals(authData, that.authData)
          && Objects.equals(builderPubkeys, that.builderPubkeys)
          && Objects.equals(minBid, that.minBid)
          && Objects.equals(builderBoostFactor, that.builderBoostFactor)
          && Objects.equals(maxExecutionPayment, that.maxExecutionPayment);
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          authData, builderPubkeys, minBid, builderBoostFactor, maxExecutionPayment);
    }
  }

  /** A configured builder url, parsed, with its overrides. */
  public record BuilderUrl(URL url, BuilderOverrides overrides) {}
}
