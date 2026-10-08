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

package tech.pegasys.teku.services.executionlayer;

import static com.google.common.base.Preconditions.checkState;
import static tech.pegasys.teku.spec.executionlayer.BuilderBoostFactorEvaluator.BUILDER_BOOST_FACTOR_PREFER_BUILDER;
import static tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel.STUB_ENDPOINT_PREFIX;

import java.util.Optional;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;

public class ExecutionLayerConfiguration {
  private static final Logger LOG = LogManager.getLogger();

  public static final boolean DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED = true;
  public static final int DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW = 32;
  public static final int DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS = 5;
  public static final int DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS = 3;
  public static final int BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP = 64;
  public static final UInt64 DEFAULT_BUILDER_BID_COMPARE_FACTOR = UInt64.valueOf(90);
  public static final boolean DEFAULT_BUILDER_SET_USER_AGENT_HEADER = true;
  public static final boolean DEFAULT_USE_SHOULD_OVERRIDE_BUILDER_FLAG = true;
  public static final boolean DEFAULT_CONSIDER_P2P_BIDS_DURING_BLOCK_PRODUCTION = true;
  public static final boolean DEFAULT_EXCHANGE_CAPABILITIES_MONITORING_ENABLED = true;
  public static final String BUILDER_ALWAYS_KEYWORD = "BUILDER_ALWAYS";

  private final Spec spec;
  private final Optional<String> engineEndpoint;
  private final Optional<String> engineJwtSecretFile;
  private final Optional<String> engineJwtClaimId;
  private final Optional<String> builderEndpoint;
  private final boolean isBuilderCircuitBreakerEnabled;
  private final int builderCircuitBreakerWindow;
  private final int builderCircuitBreakerAllowedFaults;
  private final int builderCircuitBreakerAllowedConsecutiveFaults;
  private final UInt64 builderBidCompareFactor;
  private final boolean builderSetUserAgentHeader;
  private final boolean useShouldOverrideBuilderFlag;
  private final boolean considerP2PBidsDuringBlockProduction;
  private final boolean exchangeCapabilitiesMonitoringEnabled;

  private ExecutionLayerConfiguration(
      final Spec spec,
      final Optional<String> engineEndpoint,
      final Optional<String> engineJwtSecretFile,
      final Optional<String> engineJwtClaimId,
      final Optional<String> builderEndpoint,
      final boolean isBuilderCircuitBreakerEnabled,
      final int builderCircuitBreakerWindow,
      final int builderCircuitBreakerAllowedFaults,
      final int builderCircuitBreakerAllowedConsecutiveFaults,
      final UInt64 builderBidCompareFactor,
      final boolean builderSetUserAgentHeader,
      final boolean useShouldOverrideBuilderFlag,
      final boolean considerP2PBidsDuringBlockProduction,
      final boolean exchangeCapabilitiesMonitoringEnabled) {
    this.spec = spec;
    this.engineEndpoint = engineEndpoint;
    this.engineJwtSecretFile = engineJwtSecretFile;
    this.engineJwtClaimId = engineJwtClaimId;
    this.builderEndpoint = builderEndpoint;
    this.isBuilderCircuitBreakerEnabled = isBuilderCircuitBreakerEnabled;
    this.builderCircuitBreakerWindow = builderCircuitBreakerWindow;
    this.builderCircuitBreakerAllowedFaults = builderCircuitBreakerAllowedFaults;
    this.builderCircuitBreakerAllowedConsecutiveFaults =
        builderCircuitBreakerAllowedConsecutiveFaults;
    this.builderBidCompareFactor = builderBidCompareFactor;
    this.builderSetUserAgentHeader = builderSetUserAgentHeader;
    this.useShouldOverrideBuilderFlag = useShouldOverrideBuilderFlag;
    this.considerP2PBidsDuringBlockProduction = considerP2PBidsDuringBlockProduction;
    this.exchangeCapabilitiesMonitoringEnabled = exchangeCapabilitiesMonitoringEnabled;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return engineEndpoint.isPresent() || spec.isMilestoneSupported(SpecMilestone.BELLATRIX);
  }

  public Spec getSpec() {
    return spec;
  }

  public String getEngineEndpoint() {
    return engineEndpoint.orElseThrow(
        () ->
            new InvalidConfigurationException(
                "Invalid configuration. --ee-endpoint parameter is mandatory when Bellatrix milestone is enabled"));
  }

  public Optional<String> getEngineJwtSecretFile() {
    return engineJwtSecretFile;
  }

  public Optional<String> getEngineJwtClaimId() {
    return engineJwtClaimId;
  }

  public Optional<String> getBuilderEndpoint() {
    return builderEndpoint;
  }

  public boolean isBuilderCircuitBreakerEnabled() {
    return isBuilderCircuitBreakerEnabled;
  }

  public int getBuilderCircuitBreakerWindow() {
    return builderCircuitBreakerWindow;
  }

  public int getBuilderCircuitBreakerAllowedFaults() {
    return builderCircuitBreakerAllowedFaults;
  }

  public int getBuilderCircuitBreakerAllowedConsecutiveFaults() {
    return builderCircuitBreakerAllowedConsecutiveFaults;
  }

  public UInt64 getBuilderBidCompareFactor() {
    return builderBidCompareFactor;
  }

  public boolean getBuilderSetUserAgentHeader() {
    return builderSetUserAgentHeader;
  }

  public boolean getUseShouldOverrideBuilderFlag() {
    return useShouldOverrideBuilderFlag;
  }

  public boolean getConsiderP2PBidsDuringBlockProduction() {
    return considerP2PBidsDuringBlockProduction;
  }

  public boolean isExchangeCapabilitiesMonitoringEnabled() {
    return exchangeCapabilitiesMonitoringEnabled;
  }

  public static class Builder {
    private Spec spec;
    private Optional<String> engineEndpoint = Optional.empty();
    private Optional<String> engineJwtSecretFile = Optional.empty();
    private Optional<String> engineJwtClaimId = Optional.empty();
    private Optional<String> builderEndpoint = Optional.empty();
    private boolean isBuilderCircuitBreakerEnabled = DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED;
    private int builderCircuitBreakerWindow = DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW;
    private int builderCircuitBreakerAllowedFaults = DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS;
    private int builderCircuitBreakerAllowedConsecutiveFaults =
        DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS;
    private String builderBidCompareFactor = DEFAULT_BUILDER_BID_COMPARE_FACTOR.toString();
    private boolean builderSetUserAgentHeader = DEFAULT_BUILDER_SET_USER_AGENT_HEADER;
    private boolean useShouldOverrideBuilderFlag = DEFAULT_USE_SHOULD_OVERRIDE_BUILDER_FLAG;
    private boolean considerP2PBidsDuringBlockProduction =
        DEFAULT_CONSIDER_P2P_BIDS_DURING_BLOCK_PRODUCTION;
    private boolean exchangeCapabilitiesMonitoringEnabled =
        DEFAULT_EXCHANGE_CAPABILITIES_MONITORING_ENABLED;

    private Builder() {}

    public ExecutionLayerConfiguration build() {
      validateStubEndpoints();
      validateBuilderCircuitBreaker();
      final UInt64 builderBidCompareFactor = validateAndParseBuilderBidCompareFactor();

      if (builderEndpoint.isPresent()) {
        if (builderBidCompareFactor.equals(BUILDER_BOOST_FACTOR_PREFER_BUILDER)) {
          LOG.info(
              "During block production, a valid builder bid will always be chosen over locally produced payload.");
        } else {
          final String additionalHint =
              builderBidCompareFactor.equals(DEFAULT_BUILDER_BID_COMPARE_FACTOR)
                  ? " Can be configured via --builder-bid-compare-factor"
                  : "";
          LOG.info(
              "During block production, locally produced payload will be chosen when its value is equal or greater than {}% of the builder bid value."
                  + additionalHint,
              builderBidCompareFactor);
        }
      }

      return new ExecutionLayerConfiguration(
          spec,
          engineEndpoint,
          engineJwtSecretFile,
          engineJwtClaimId,
          builderEndpoint,
          isBuilderCircuitBreakerEnabled,
          builderCircuitBreakerWindow,
          builderCircuitBreakerAllowedFaults,
          builderCircuitBreakerAllowedConsecutiveFaults,
          builderBidCompareFactor,
          builderSetUserAgentHeader,
          useShouldOverrideBuilderFlag,
          considerP2PBidsDuringBlockProduction,
          exchangeCapabilitiesMonitoringEnabled);
    }

    public Builder engineEndpoint(final String engineEndpoint) {
      this.engineEndpoint = Optional.ofNullable(engineEndpoint);
      return this;
    }

    public Builder specProvider(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder engineJwtSecretFile(final String jwtSecretFile) {
      this.engineJwtSecretFile = Optional.ofNullable(jwtSecretFile).filter(StringUtils::isNotBlank);
      return this;
    }

    public Builder engineJwtClaimId(final String jwtClaimId) {
      this.engineJwtClaimId = Optional.ofNullable(jwtClaimId).filter(StringUtils::isNotBlank);
      return this;
    }

    public Builder isBuilderCircuitBreakerEnabled(final boolean isBuilderCircuitBreakerEnabled) {
      this.isBuilderCircuitBreakerEnabled = isBuilderCircuitBreakerEnabled;
      return this;
    }

    public Builder builderCircuitBreakerWindow(final int builderCircuitBreakerWindow) {
      this.builderCircuitBreakerWindow = builderCircuitBreakerWindow;
      return this;
    }

    public Builder builderCircuitBreakerAllowedFaults(
        final int builderCircuitBreakerAllowedFaults) {
      this.builderCircuitBreakerAllowedFaults = builderCircuitBreakerAllowedFaults;
      return this;
    }

    public Builder builderCircuitBreakerAllowedConsecutiveFaults(
        final int builderCircuitBreakerAllowedConsecutiveFaults) {
      this.builderCircuitBreakerAllowedConsecutiveFaults =
          builderCircuitBreakerAllowedConsecutiveFaults;
      return this;
    }

    public Builder builderEndpoint(final String builderEndpoint) {
      this.builderEndpoint = Optional.ofNullable(builderEndpoint);
      return this;
    }

    public Builder builderBidCompareFactor(final String builderBidCompareFactor) {
      this.builderBidCompareFactor = builderBidCompareFactor;
      return this;
    }

    public Builder builderSetUserAgentHeader(final boolean builderSetUserAgentHeader) {
      this.builderSetUserAgentHeader = builderSetUserAgentHeader;
      return this;
    }

    public Builder useShouldOverrideBuilderFlag(final boolean useShouldOverrideBuilderFlag) {
      this.useShouldOverrideBuilderFlag = useShouldOverrideBuilderFlag;
      return this;
    }

    public Builder considerP2PBidsDuringBlockProduction(
        final boolean considerP2PBidsDuringBlockProduction) {
      this.considerP2PBidsDuringBlockProduction = considerP2PBidsDuringBlockProduction;
      return this;
    }

    public Builder exchangeCapabilitiesMonitoringEnabled(
        final boolean exchangeCapabilitiesMonitoringEnabled) {
      this.exchangeCapabilitiesMonitoringEnabled = exchangeCapabilitiesMonitoringEnabled;
      return this;
    }

    private void validateStubEndpoints() {
      final boolean engineIsStub =
          engineEndpoint.map(endpoint -> endpoint.startsWith(STUB_ENDPOINT_PREFIX)).orElse(false);
      final boolean builderIsStub =
          builderEndpoint.map(endpoint -> endpoint.startsWith(STUB_ENDPOINT_PREFIX)).orElse(false);

      checkState(
          engineIsStub == builderIsStub || builderEndpoint.isEmpty(),
          "mixed configuration with stubbed and non-stubbed execution layer endpoints is not supported");
    }

    private void validateBuilderCircuitBreaker() {
      if (builderCircuitBreakerWindow > BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP) {
        throw new InvalidConfigurationException(
            "Builder Circuit Breaker window cannot exceed "
                + BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP);
      }
    }

    private UInt64 validateAndParseBuilderBidCompareFactor() {
      if (builderBidCompareFactor.equalsIgnoreCase(BUILDER_ALWAYS_KEYWORD)) {
        return BUILDER_BOOST_FACTOR_PREFER_BUILDER;
      }
      if (builderBidCompareFactor.endsWith("%")) {
        builderBidCompareFactor =
            builderBidCompareFactor.substring(0, builderBidCompareFactor.length() - 1);
      }
      final UInt64 builderBidCompareFactorUint64;
      try {
        builderBidCompareFactorUint64 = UInt64.valueOf(builderBidCompareFactor);
      } catch (final IllegalArgumentException ex) {
        throw new InvalidConfigurationException(
            "Expecting a number >= 0, percentage or "
                + BUILDER_ALWAYS_KEYWORD
                + " keyword for Builder bid compare factor");
      }
      return builderBidCompareFactorUint64;
    }
  }
}
