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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import com.google.common.base.Preconditions;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class BeaconNodeRoleConfig {

  static final DeserializableTypeDefinition<List<String>> ENDPOINT_LIST =
      DeserializableTypeDefinition.listOf(STRING_TYPE);

  static final DeserializableTypeDefinition<BeaconNodeRoleConfig> BEACON_NODE_ROLE_CONFIG =
      DeserializableTypeDefinition.object(
              BeaconNodeRoleConfig.class, BeaconNodeRoleConfig.Builder.class)
          .initializer(BeaconNodeRoleConfig.Builder::builder)
          .finisher(BeaconNodeRoleConfig.Builder::build)
          .withField(
              "endpoints",
              ENDPOINT_LIST,
              BeaconNodeRoleConfig::getEndpoints,
              BeaconNodeRoleConfig.Builder::endpoints)
          .build();

  private final List<String> endpoints = new ArrayList<>();

  private BeaconNodeRoleConfig(final List<String> endpoints) {
    this.endpoints.addAll(endpoints);
  }

  public List<String> getEndpoints() {
    return endpoints;
  }

  public List<URI> getEndpointsAsURIs() {
    return endpoints.stream().map(URI::create).toList();
  }

  public static class Builder {

    final List<String> endpoints = new ArrayList<>();

    private Builder() {}

    public static Builder builder() {
      return new Builder();
    }

    public Builder endpoints(final List<String> endpoints) {
      this.endpoints.addAll(endpoints);
      return this;
    }

    public BeaconNodeRoleConfig build() {
      Preconditions.checkArgument(
          !endpoints.isEmpty(),
          "Sentry node role configuration does not support an empty list of endpoints");

      return new BeaconNodeRoleConfig(endpoints);
    }
  }
}
