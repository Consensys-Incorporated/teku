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

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.infrastructure.http.UrlSanitizer;
import tech.pegasys.teku.infrastructure.io.resource.ResourceLoader;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class SentryNodesConfigLoader {

  private static final Logger LOG = LogManager.getLogger();
  private final ResourceLoader resourceLoader;

  public SentryNodesConfigLoader() {
    this.resourceLoader = ResourceLoader.urlOrFile("application/json");
  }

  public SentryNodesConfig load(final String resourceLocation) {
    LOG.info("Loading sentry nodes configuration from {}", resourceLocation);

    final Bytes fileAsBytes = loadResourceFromFileOrUrl(resourceLocation);
    final SentryNodesConfig sentryNodesConfig = parseResourceAsConfig(fileAsBytes);

    logConfig(sentryNodesConfig);

    return sentryNodesConfig;
  }

  private Bytes loadResourceFromFileOrUrl(final String resourceLocation) {
    final String sanitizedResource = UrlSanitizer.sanitizePotentialUrl(resourceLocation);

    final Bytes fileAsBytes;
    try {
      fileAsBytes =
          resourceLoader
              .loadBytes(sanitizedResource)
              .orElseThrow(() -> new FileNotFoundException("Not found"));
    } catch (Exception e) {
      throw new RuntimeException(
          "Error loading sentry nodes configuration file from " + resourceLocation, e);
    }
    return fileAsBytes;
  }

  private SentryNodesConfig parseResourceAsConfig(final Bytes fileAsBytes) {
    try {
      // JSON should always be encoded in UTF-8 (https://www.rfc-editor.org/rfc/rfc8259#section-8.1)
      return JsonUtil.parse(
          new String(fileAsBytes.toArray(), StandardCharsets.UTF_8),
          SentryNodesConfig.SENTRY_NODES_CONFIG);
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Invalid sentry nodes configuration file", e);
    }
  }

  private void logConfig(final SentryNodesConfig sentryNodesConfig) {
    LOG.info(
        "Duty provider beacon nodes: {}",
        String.join(
            ",",
            sentryNodesConfig
                .getBeaconNodesSentryConfig()
                .getDutiesProviderNodeConfig()
                .getEndpoints()));

    LOG.info(
        "Block handler beacon nodes: {}",
        sentryNodesConfig
            .getBeaconNodesSentryConfig()
            .getBlockHandlerNodeConfig()
            .map(c -> String.join(",", c.getEndpoints()))
            .orElse("<empty>"));

    LOG.info(
        "Attestation publisher beacon nodes: {}",
        sentryNodesConfig
            .getBeaconNodesSentryConfig()
            .getAttestationPublisherConfig()
            .map(c -> String.join(",", c.getEndpoints()))
            .orElse("<empty>"));
  }
}
