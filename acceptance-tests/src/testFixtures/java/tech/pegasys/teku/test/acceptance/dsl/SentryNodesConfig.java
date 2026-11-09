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

package tech.pegasys.teku.test.acceptance.dsl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SentryNodesConfig {

  private final List<String> dutiesProviderNodes;
  private final List<String> blockHandlerNodes;
  private final List<String> attestationPublisherNodes;

  private SentryNodesConfig(
      final List<String> dutiesProviderNodes,
      final List<String> blockHandlerNodes,
      final List<String> attestationPublisherNodes) {
    this.dutiesProviderNodes = dutiesProviderNodes;
    this.blockHandlerNodes = blockHandlerNodes;
    this.attestationPublisherNodes = attestationPublisherNodes;
  }

  public String toJson(final ObjectMapper objectMapper) throws JsonProcessingException {
    final HashMap<String, Object> config = new HashMap<>();

    final Map<String, Object> beaconNodes = new HashMap<>();
    if (dutiesProviderNodes != null && !dutiesProviderNodes.isEmpty()) {
      beaconNodes.put("duties_provider", Map.of("endpoints", dutiesProviderNodes));
    }

    if (blockHandlerNodes != null && !blockHandlerNodes.isEmpty()) {
      beaconNodes.put("block_handler", Map.of("endpoints", blockHandlerNodes));
    }

    if (attestationPublisherNodes != null && !attestationPublisherNodes.isEmpty()) {
      beaconNodes.put("attestation_publisher", Map.of("endpoints", attestationPublisherNodes));
    }

    config.put("beacon_nodes", beaconNodes);

    return objectMapper.writeValueAsString(config);
  }

  public static class Builder {

    private List<String> dutiesProviderNodes = new ArrayList<>();
    private List<String> blockHandlerNodes = new ArrayList<>();
    private List<String> attestationPublisherNodes = new ArrayList<>();

    public Builder withDutiesProviders(final TekuBeaconNode... nodes) {
      dutiesProviderNodes =
          Arrays.stream(nodes)
              .map(TekuBeaconNode::getBeaconRestApiUrl)
              .collect(Collectors.toList());
      return this;
    }

    public Builder withBlockHandlers(final TekuBeaconNode... nodes) {
      blockHandlerNodes =
          Arrays.stream(nodes)
              .map(TekuBeaconNode::getBeaconRestApiUrl)
              .collect(Collectors.toList());
      return this;
    }

    public Builder withAttestationPublisher(final TekuBeaconNode... nodes) {
      attestationPublisherNodes =
          Arrays.stream(nodes)
              .map(TekuBeaconNode::getBeaconRestApiUrl)
              .collect(Collectors.toList());
      return this;
    }

    public SentryNodesConfig build() {
      return new SentryNodesConfig(
          dutiesProviderNodes, blockHandlerNodes, attestationPublisherNodes);
    }
  }
}
