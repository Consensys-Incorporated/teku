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

import static tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi.convertToOkHttpUrls;
import static tech.pegasys.teku.validator.remote.RemoteBeaconNodeApi.createOkHttpClient;
import static tech.pegasys.teku.validator.remote.sentry.BeaconNodeRoleConfig.BEACON_NODE_ROLE_CONFIG;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class BeaconNodesSentryConfig {

  static final DeserializableTypeDefinition<BeaconNodesSentryConfig> BEACON_NODES_SENTRY_CONFIG =
      DeserializableTypeDefinition.object(
              BeaconNodesSentryConfig.class, BeaconNodesSentryConfig.Builder.class)
          .initializer(BeaconNodesSentryConfig.Builder::builder)
          .finisher(BeaconNodesSentryConfig.Builder::build)
          .withField(
              "duties_provider",
              BEACON_NODE_ROLE_CONFIG,
              BeaconNodesSentryConfig::getDutiesProviderNodeConfig,
              BeaconNodesSentryConfig.Builder::dutiesProviderNodeConfig)
          .withOptionalField(
              "block_handler",
              BEACON_NODE_ROLE_CONFIG,
              BeaconNodesSentryConfig::getBlockHandlerNodeConfig,
              BeaconNodesSentryConfig.Builder::blockHandlerNodeConfig)
          .withOptionalField(
              "attestation_publisher",
              BEACON_NODE_ROLE_CONFIG,
              BeaconNodesSentryConfig::getAttestationPublisherConfig,
              BeaconNodesSentryConfig.Builder::attestationPublisherConfig)
          .build();

  private final BeaconNodeRoleConfig dutiesProviderNodeConfig;
  private final Optional<BeaconNodeRoleConfig> blockHandlerNodeConfig;
  private final Optional<BeaconNodeRoleConfig> attestationPublisherConfig;

  private BeaconNodesSentryConfig(
      final BeaconNodeRoleConfig dutiesProviderNodeConfig,
      final Optional<BeaconNodeRoleConfig> blockHandlerNodeConfig,
      final Optional<BeaconNodeRoleConfig> attestationPublisherConfig) {
    this.dutiesProviderNodeConfig = dutiesProviderNodeConfig;
    this.blockHandlerNodeConfig = blockHandlerNodeConfig;
    this.attestationPublisherConfig = attestationPublisherConfig;
  }

  public BeaconNodeRoleConfig getDutiesProviderNodeConfig() {
    return dutiesProviderNodeConfig;
  }

  public Optional<BeaconNodeRoleConfig> getBlockHandlerNodeConfig() {
    return blockHandlerNodeConfig;
  }

  public Optional<BeaconNodeRoleConfig> getAttestationPublisherConfig() {
    return attestationPublisherConfig;
  }

  public OkHttpClient createOkHttpClientForSentryNodes() {
    final List<HttpUrl> allBeaconNodeEndpoints =
        new ArrayList<>(convertToOkHttpUrls(getDutiesProviderNodeConfig().getEndpointsAsURIs()));
    getBlockHandlerNodeConfig()
        .ifPresent(c -> allBeaconNodeEndpoints.addAll(convertToOkHttpUrls(c.getEndpointsAsURIs())));
    getAttestationPublisherConfig()
        .ifPresent(c -> allBeaconNodeEndpoints.addAll(convertToOkHttpUrls(c.getEndpointsAsURIs())));

    return createOkHttpClient(allBeaconNodeEndpoints);
  }

  public static class Builder {

    private BeaconNodeRoleConfig dutiesProviderNodeConfig;
    private Optional<BeaconNodeRoleConfig> blockHandlerNodeConfig = Optional.empty();
    private Optional<BeaconNodeRoleConfig> attestationPublisherConfig = Optional.empty();

    private Builder() {}

    public static Builder builder() {
      return new Builder();
    }

    public Builder dutiesProviderNodeConfig(final BeaconNodeRoleConfig config) {
      this.dutiesProviderNodeConfig = config;
      return this;
    }

    public Builder blockHandlerNodeConfig(final Optional<BeaconNodeRoleConfig> config) {
      this.blockHandlerNodeConfig = config;
      return this;
    }

    public Builder attestationPublisherConfig(final Optional<BeaconNodeRoleConfig> config) {
      this.attestationPublisherConfig = config;
      return this;
    }

    public BeaconNodesSentryConfig build() {
      return new BeaconNodesSentryConfig(
          dutiesProviderNodeConfig, blockHandlerNodeConfig, attestationPublisherConfig);
    }
  }
}
