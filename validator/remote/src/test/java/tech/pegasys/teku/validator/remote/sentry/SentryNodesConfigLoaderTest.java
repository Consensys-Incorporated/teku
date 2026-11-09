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

package tech.pegasys.teku.validator.remote.sentry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@SuppressWarnings("OptionalGetWithoutIsPresent")
class SentryNodesConfigLoaderTest {

  private SentryNodesConfigLoader configLoader;

  @BeforeEach
  public void before() {
    configLoader = new SentryNodesConfigLoader();
  }

  @Test
  public void shouldLoadConfigWithCorrectValues() {
    final BeaconNodesSentryConfig config =
        configLoader.load(pathFor("sentry_node_full_config.json")).getBeaconNodesSentryConfig();

    assertThat(config.getDutiesProviderNodeConfig().getEndpoints()).contains("http://duties:5051");
    assertThat(config.getBlockHandlerNodeConfig().get().getEndpoints())
        .contains("http://block:5051");
    assertThat(config.getAttestationPublisherConfig().get().getEndpoints())
        .contains("http://attestation:5051");
  }

  @Test
  public void shouldLoadConfigOnlyWithDutiesProvider() {
    final BeaconNodesSentryConfig config =
        configLoader.load(pathFor("sentry_node_minimum_config.json")).getBeaconNodesSentryConfig();

    assertThat(config.getDutiesProviderNodeConfig().getEndpoints()).contains("http://duties:5051");
    assertThat(config.getBlockHandlerNodeConfig()).isEmpty();
    assertThat(config.getAttestationPublisherConfig()).isEmpty();
  }

  @Test
  public void shouldFailLoadingConfigMissingNestedEndpoints() {
    assertThatThrownBy(
            () -> configLoader.load(pathFor("sentry_node_missing_endpoints_config.json")))
        .isInstanceOf(RuntimeException.class)
        .hasCauseInstanceOf(JsonProcessingException.class)
        .hasMessage("Invalid sentry nodes configuration file");
  }

  @Test
  public void shouldFailLoadingConfigWhenMissingRequiredDutiesProvider() {
    assertThatThrownBy(
            () -> configLoader.load(pathFor("sentry_node_missing_duties_provider_config.json")))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Invalid sentry nodes configuration file")
        .hasCauseInstanceOf(JsonProcessingException.class)
        .hasRootCauseMessage("required fields: (duties_provider) were not set");
  }

  @Test
  public void shouldLoadConfigSuccessfullyWhenMissingOptionalBlockHandler() {
    final BeaconNodesSentryConfig config =
        configLoader
            .load(pathFor("sentry_node_missing_block_handler_config.json"))
            .getBeaconNodesSentryConfig();

    assertThat(config.getDutiesProviderNodeConfig().getEndpoints()).contains("http://duties:5051");
    assertThat(config.getAttestationPublisherConfig().get().getEndpoints())
        .contains("http://attestation:5051");
    assertThat(config.getBlockHandlerNodeConfig()).isEmpty();
  }

  @Test
  public void shouldLoadConfigSuccessfullyWhenMissingOptionalAttestationPublisher() {
    final BeaconNodesSentryConfig config =
        configLoader
            .load(pathFor("sentry_node_missing_attestation_publisher_config.json"))
            .getBeaconNodesSentryConfig();

    assertThat(config.getDutiesProviderNodeConfig().getEndpoints()).contains("http://duties:5051");
    assertThat(config.getBlockHandlerNodeConfig().get().getEndpoints())
        .contains("http://block:5051");
    assertThat(config.getAttestationPublisherConfig()).isEmpty();
  }

  @Test
  public void shouldRethrowExceptionWhenResourceLoaderFails() {
    assertThatThrownBy(() -> configLoader.load("foo"))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Error loading sentry nodes configuration file from foo")
        .hasCauseInstanceOf(IOException.class)
        .hasRootCauseMessage("Not found");
  }

  private String pathFor(final String filename) {
    return Resources.getResource(SentryNodesConfigLoaderTest.class, filename).toString();
  }
}
