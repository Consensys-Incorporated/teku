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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS;
import static tech.pegasys.teku.services.executionlayer.ExecutionLayerConfiguration.DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.cli.AbstractBeaconNodeCommandTest;
import tech.pegasys.teku.config.TekuConfiguration;
import tech.pegasys.teku.infrastructure.exceptions.InvalidConfigurationException;

public class ExecutionLayerOptionsTest extends AbstractBeaconNodeCommandTest {

  @Test
  public void shouldReadExecutionLayerOptionsFromConfigurationFile() {
    final TekuConfiguration config =
        getTekuConfigurationFromFile("executionLayerOptions_config.yaml");

    assertThat(config.executionLayer().isEnabled()).isTrue();
    assertThat(config.executionLayer().getEngineEndpoint())
        .isEqualTo("http://example.com:1234/path/");
  }

  @Test
  public void shouldReportEEEnabledIfSpecEnablesBellatrix() {
    final String[] args = {
      "--Xnetwork-altair-fork-epoch",
      "0",
      "--Xnetwork-bellatrix-fork-epoch",
      "1",
      "--ee-endpoint",
      "http://example.com:1234/path/"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionLayer().isEnabled()).isTrue();
    assertThat(config.executionLayer().getEngineEndpoint())
        .isEqualTo("http://example.com:1234/path/");
  }

  @Test
  public void shouldAcceptEngineAndBuilderEndpointIfSpecEnablesBellatrix() {
    final String[] args = {
      "--Xnetwork-altair-fork-epoch",
      "0",
      "--Xnetwork-bellatrix-fork-epoch",
      "1",
      "--ee-endpoint",
      "http://example.com:1234/path/",
      "--builder-endpoint",
      "http://example2.com:1234/path2/"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionLayer().isEnabled()).isTrue();
    assertThat(config.executionLayer().getEngineEndpoint())
        .isEqualTo("http://example.com:1234/path/");
    assertThat(config.executionLayer().getBuilderEndpoint())
        .contains("http://example2.com:1234/path2/");
  }

  @Test
  public void shouldBuilderCircuitBreakerEnabledByDefault() {
    final String[] args = {};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);

    assertThat(config.executionLayer().isBuilderCircuitBreakerEnabled()).isTrue();
    assertThat(config.executionLayer().getBuilderCircuitBreakerWindow())
        .isEqualTo(DEFAULT_BUILDER_CIRCUIT_BREAKER_WINDOW);
    assertThat(config.executionLayer().getBuilderCircuitBreakerAllowedFaults())
        .isEqualTo(DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_FAULTS);
    assertThat(config.executionLayer().getBuilderCircuitBreakerAllowedConsecutiveFaults())
        .isEqualTo(DEFAULT_BUILDER_CIRCUIT_BREAKER_ALLOWED_CONSECUTIVE_FAULTS);
  }

  @Test
  public void shouldAcceptBuilderCircuitBreakerParams() {
    final String[] args = {
      "--Xbuilder-circuit-breaker-enabled",
      "false",
      "--Xbuilder-circuit-breaker-window",
      "40",
      "--Xbuilder-circuit-breaker-allowed-faults",
      "2",
      "--Xbuilder-circuit-breaker-allowed-consecutive-faults",
      "20"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);

    assertThat(config.executionLayer().isBuilderCircuitBreakerEnabled()).isFalse();
    assertThat(config.executionLayer().getBuilderCircuitBreakerWindow()).isEqualTo(40);
    assertThat(config.executionLayer().getBuilderCircuitBreakerAllowedFaults()).isEqualTo(2);
    assertThat(config.executionLayer().getBuilderCircuitBreakerAllowedConsecutiveFaults())
        .isEqualTo(20);
  }

  @Test
  public void shouldThrowWithBuilderCircuitBreakerWindowTooLarge() {
    assertThatThrownBy(
            () ->
                createConfigBuilder()
                    .executionLayer(
                        b ->
                            b.builderCircuitBreakerWindow(
                                    BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP + 1)
                                .build()))
        .isInstanceOf(InvalidConfigurationException.class)
        .hasMessage(
            "Builder Circuit Breaker window cannot exceed "
                + BUILDER_CIRCUIT_BREAKER_WINDOW_HARD_CAP);
  }

  @Test
  public void shouldReportEEDisabledIfEndpointNotSpecified() {
    final TekuConfiguration config = getTekuConfigurationFromArguments("--network=minimal");
    assertThat(config.executionLayer().isEnabled()).isFalse();
  }

  @Test
  void shouldThrowInvalidConfigurationExceptionIfEndpointRequiredButNotSpecified() {
    final String[] args = {
      "--Xnetwork-altair-fork-epoch", "0", "--Xnetwork-bellatrix-fork-epoch", "1"
    };
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThatThrownBy(config.executionLayer()::getEngineEndpoint)
        .isInstanceOf(InvalidConfigurationException.class);
  }

  @Test
  void shouldNotSetJwtClaimIdIfNotConfigured() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.executionLayer().getEngineJwtClaimId()).isEmpty();
  }

  @Test
  void shouldSetJwtClaimIdIfConfigured() {
    final String[] args = {"--ee-jwt-claim-id", "foobar"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionLayer().getEngineJwtClaimId()).hasValue("foobar");
  }

  @Test
  void shouldConsiderP2PBidsDuringBlockProductionByDefault() {
    final TekuConfiguration config = getTekuConfigurationFromArguments();
    assertThat(config.executionLayer().getConsiderP2PBidsDuringBlockProduction()).isTrue();
  }

  @Test
  void shouldNotConsiderP2PBidsDuringBlockProductionIfConfiguredToFalse() {
    final String[] args = {"--Xconsider-p2p-bids-during-block-production=false"};
    final TekuConfiguration config = getTekuConfigurationFromArguments(args);
    assertThat(config.executionLayer().getConsiderP2PBidsDuringBlockProduction()).isFalse();
  }
}
