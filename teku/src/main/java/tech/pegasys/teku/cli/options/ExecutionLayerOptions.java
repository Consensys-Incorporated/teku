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

package tech.pegasys.teku.cli.options;

import static tech.pegasys.teku.config.TekuConfiguration.Builder;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.BUILDER_ALWAYS_KEYWORD;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_BID_COMPARE_FACTOR;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_SET_USER_AGENT_HEADER;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_CONSIDER_P2P_BIDS_DURING_BLOCK_PRODUCTION;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_EXCHANGE_CAPABILITIES_MONITORING_ENABLED;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_USE_SHOULD_OVERRIDE_BUILDER_FLAG;

import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Option;

public class ExecutionLayerOptions {

  @Option(
      names = {"--ee-endpoint"},
      paramLabel = "<NETWORK>",
      description = "URL for Execution Engine node.",
      arity = "1")
  private String executionEngineEndpoint = null;

  @Option(
      names = {"--ee-jwt-secret-file"},
      paramLabel = "<FILENAME>",
      description =
          "Location of the file specifying the hex-encoded 256 bit secret key to be used for verifying/generating jwt tokens",
      arity = "1")
  private String engineJwtSecretFile = null;

  @Option(
      names = {"--ee-jwt-claim-id"},
      paramLabel = "<STRING>",
      description =
          "A unique identifier for the consensus layer client. This identifier will be added to the JWT claims as an 'id' claim.",
      arity = "1")
  private String engineJwtClaimId = null;

  @Option(
      names = {"--builder-endpoint"},
      paramLabel = "<NETWORK>",
      description = "URL for an external Builder node (optional).",
      showDefaultValue = Visibility.ALWAYS,
      arity = "1")
  private String builderEndpoint = null;

  @Option(
      names = {"--Xbuilder-circuit-breaker-enabled"},
      paramLabel = "<BOOLEAN>",
      description = "Enables Circuit Breaker logic for builder usage.",
      arity = "0..1",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      hidden = true)
  private boolean builderCircuitBreakerEnabled = DEFAULT_BUILDER_CIRCUIT_BREAKER_ENABLED;

  @Option(
      names = {"--Xbuilder-circuit-breaker-window"},
      paramLabel = "<INTEGER>",
      description = "Circuit Breaker fault inspection window.",
      arity = "1",
      showDefaultValue = Visibility.ALWAYS,
      hidden = true)
  private int builderCircuitBreakerWindow = DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW;

  @Option(
      names = {"--Xbuilder-circuit-breaker-allowed-faults"},
      paramLabel = "<INTEGER>",
      description =
          "Circuit Breaker maximum allowed faults (missing block) within the specified inspection window.",
      arity = "1",
      hidden = true)
  private int builderCircuitBreakerAllowedFaults = DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS;

  @Option(
      names = {"--Xbuilder-circuit-breaker-allowed-consecutive-faults"},
      paramLabel = "<INTEGER>",
      description = "Circuit Breaker maximum allowed consecutive faults (missing block).",
      arity = "1",
      hidden = true)
  private int builderCircuitBreakerAllowedConsecutiveFaults =
      DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS;

  @Option(
      names = {"--builder-bid-compare-factor"},
      paramLabel = "<STRING>",
      showDefaultValue = Visibility.ALWAYS,
      description =
          "Set the compare factor applied to the builder bid value when comparing it with locally produced payload."
              + " Factor is expressed in percentage (e.g. '100' means locally produced payload will be chosen when its value is equal or greater than the entire builder bid value, "
              + "'80' means local payload will be chosen when its value is at least 80%% of builder bid value).\n"
              + "Set it to '"
              + BUILDER_ALWAYS_KEYWORD
              + "' to always use builder bid. In this configuration locally produced payload will be used only when the bid is invalid.",
      arity = "1")
  private String builderBidCompareFactor = DEFAULT_BUILDER_BID_COMPARE_FACTOR.toString();

  @Option(
      names = {"--builder-set-user-agent-header"},
      paramLabel = "<BOOLEAN>",
      description =
          "Set User-Agent header to teku/v<version> (e.g. teku/v23.4.0) when making a builder bid request to help builders identify clients and versions",
      arity = "0..1",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true")
  private boolean builderSetUserAgentHeader = DEFAULT_BUILDER_SET_USER_AGENT_HEADER;

  @Option(
      names = {"--Xuse-should-override-builder-flag"},
      paramLabel = "<BOOLEAN>",
      description =
          "Whether or not to use the shouldOverrideBuilder flag provided by the Engine API.",
      arity = "0..1",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      hidden = true)
  private boolean useShouldOverrideBuilderFlag = DEFAULT_USE_SHOULD_OVERRIDE_BUILDER_FLAG;

  @Option(
      names = {"--Xconsider-p2p-bids-during-block-production"},
      paramLabel = "<BOOLEAN>",
      description =
          "Whether or not to consider execution payload bids received over p2p when producing a block.",
      arity = "0..1",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true",
      hidden = true)
  private boolean considerP2PBidsDuringBlockProduction =
      DEFAULT_CONSIDER_P2P_BIDS_DURING_BLOCK_PRODUCTION;

  @Option(
      names = {"--exchange-capabilities-monitoring-enabled"},
      paramLabel = "<BOOLEAN>",
      description =
          "Enables querying EL periodically for the Engine API methods it supports. If incompatibility is detected, there will be a warning raised in the logs.",
      arity = "0..1",
      showDefaultValue = Visibility.ALWAYS,
      fallbackValue = "true")
  private boolean exchangeCapabilitiesMonitoringEnabled =
      DEFAULT_EXCHANGE_CAPABILITIES_MONITORING_ENABLED;

  public void configure(final Builder builder) {
    builder.executionLayer(
        b ->
            b.engineEndpoint(executionEngineEndpoint)
                .engineJwtSecretFile(engineJwtSecretFile)
                .engineJwtClaimId(engineJwtClaimId)
                .builderEndpoint(builderEndpoint)
                .isBuilderCircuitBreakerEnabled(builderCircuitBreakerEnabled)
                .builderCircuitBreakerWindow(builderCircuitBreakerWindow)
                .builderCircuitBreakerAllowedFaults(builderCircuitBreakerAllowedFaults)
                .builderCircuitBreakerAllowedConsecutiveFaults(
                    builderCircuitBreakerAllowedConsecutiveFaults)
                .builderBidCompareFactor(builderBidCompareFactor)
                .builderSetUserAgentHeader(builderSetUserAgentHeader)
                .useShouldOverrideBuilderFlag(useShouldOverrideBuilderFlag)
                .considerP2PBidsDuringBlockProduction(considerP2PBidsDuringBlockProduction)
                .exchangeCapabilitiesMonitoringEnabled(exchangeCapabilitiesMonitoringEnabled));
  }
}
