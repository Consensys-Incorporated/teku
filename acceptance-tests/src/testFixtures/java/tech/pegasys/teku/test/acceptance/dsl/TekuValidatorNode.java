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

import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withLabelValueSubstring;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withLabelsContaining;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withNameEqualsTo;
import static tech.pegasys.teku.test.acceptance.dsl.metrics.MetricConditions.withValueGreaterThan;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import tech.pegasys.teku.test.acceptance.dsl.tools.ValidatorKeysApi;

public class TekuValidatorNode extends TekuNode {

  private static final Logger LOG = LogManager.getLogger();
  protected static final String VALIDATOR_PATH = DATA_PATH + "validator/";

  private final TekuNodeConfig config;
  private final ValidatorKeysApi validatorKeysApi =
      new ValidatorKeysApi(
          new TrustingSimpleHttpsClient(), this::getValidatorApiUrl, this::getApiPassword);

  private TekuValidatorNode(
      final Network network, final TekuDockerVersion version, final TekuNodeConfig config) {
    super(network, TEKU_DOCKER_IMAGE_NAME, version, LOG);
    this.config = config;
    if (config.getConfigMap().containsKey("validator-api-enabled")) {
      container.addExposedPort(VALIDATOR_API_PORT);
    }
    container.addExposedPort(METRICS_PORT);

    container
        .withWorkingDirectory(WORKING_DIRECTORY)
        .withCommand("validator-client", "--config-file", CONFIG_FILE_PATH);
  }

  public static TekuValidatorNode create(
      final Network network, final TekuDockerVersion version, final TekuNodeConfig tekuNodeConfig) {
    return new TekuValidatorNode(network, version, tekuNodeConfig);
  }

  public ValidatorKeysApi getValidatorKeysApi() {
    if (!config.getConfigMap().containsKey("validator-api-enabled")) {
      LOG.error("Retrieving validator keys api but api is not enabled");
    }
    return validatorKeysApi;
  }

  @Override
  public TekuNodeConfig getConfig() {
    return config;
  }

  public void waitForDutiesRequestedFrom(final TekuBeaconNode node) {
    waitForMetric(
        withNameEqualsTo("validator_remote_beacon_nodes_requests_total"),
        withLabelsContaining(
            Map.of(
                "endpoint", node.getBeaconRestApiUrl() + "/",
                "method", "get_proposer_duties",
                "outcome", "success")),
        withValueGreaterThan(0));
  }

  /**
   * Duties are also driven by the slot timer, so they complete even when the event stream delivers
   * nothing at all. Matches both head and head_v2 events.
   */
  public void waitForHeadEventsReceived() {
    waitForMetric(
        withNameEqualsTo("validator_event_stream_events_total"),
        withLabelValueSubstring("type", "head"),
        withValueGreaterThan(0));
  }

  public void waitForAttestationPublishedTo(final TekuBeaconNode node) {
    waitForMetric(
        withNameEqualsTo("validator_remote_beacon_nodes_requests_total"),
        withLabelsContaining(
            Map.of(
                "endpoint", node.getBeaconRestApiUrl() + "/",
                "method", "publish_attestation",
                "outcome", "success")),
        withValueGreaterThan(0));
  }

  public void waitForBlockPublishedTo(final TekuBeaconNode node) {
    waitForMetric(
        withNameEqualsTo("validator_remote_beacon_nodes_requests_total"),
        withLabelsContaining(
            Map.of(
                "endpoint", node.getBeaconRestApiUrl() + "/",
                "method", "publish_block",
                "outcome", "success")),
        withValueGreaterThan(0));
  }

  private String getApiPassword() {
    return container.copyFileFromContainer(
        VALIDATOR_PATH + "key-manager/validator-api-bearer",
        in -> IOUtils.toString(in, StandardCharsets.UTF_8));
  }
}
