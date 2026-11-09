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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.testcontainers.containers.Network;
import org.testcontainers.images.builder.ImageFromDockerfile;
import tech.pegasys.teku.infrastructure.async.Waiter;

public class ExternalMetricNode extends Node {
  private static final Logger LOG = LogManager.getLogger();

  public static final int STUB_PORT = 8001;
  protected static final String WORKING_DIRECTORY =
      "/opt/tech/pegasys/teku/test/acceptance/stubServer/";

  private boolean started = false;

  private ExternalMetricNode(final Network network, final ImageFromDockerfile image) {
    super(network, image, LOG);
    container.withWorkingDirectory(WORKING_DIRECTORY).withExposedPorts(STUB_PORT);
  }

  public static ExternalMetricNode create(final Network network) {
    final ImageFromDockerfile image =
        new ImageFromDockerfile()
            .withDockerfile(
                Path.of(
                    "src/testFixtures/java/tech/pegasys/teku/test/acceptance/stubServer/Dockerfile"));
    return new ExternalMetricNode(network, image);
  }

  public String getAddress() {
    return "http://" + this.container.getHost() + ":" + this.container.getMappedPort(STUB_PORT);
  }

  public String getPublicationEndpointURL() {
    return "http://" + this.nodeAlias + ":" + STUB_PORT + "/input";
  }

  public String getResponse() throws URISyntaxException, IOException {
    return httpClient.get(new URI(this.getAddress()), "/output");
  }

  private List<Map<String, Object>> getPublishedObjects() throws URISyntaxException, IOException {
    String response = getResponse();
    LOG.debug("Metric data was published " + response);
    JsonNode node = OBJECT_MAPPER.readTree(response);
    final List<Map<String, Object>> result = new ArrayList<>();
    assertThat(node.isArray()).isTrue();
    for (JsonNode child : ImmutableList.copyOf(node.elements())) {
      final Map<String, Object> elements = new HashMap<>();
      for (String field : ImmutableList.copyOf(child.fieldNames())) {
        switch (child.get(field).getNodeType()) {
          case BOOLEAN -> elements.put(field, child.get(field).asBoolean());
          case NUMBER -> elements.put(field, child.get(field).asLong());
          default -> elements.put(field, child.get(field).asText());
        }
      }
      result.add(elements);
    }
    return result;
  }

  public void waitForValidatorMetricPublication(final long validatorCount) {
    Waiter.waitFor(
        () -> {
          Map<String, Object> publishedData = getPublisher("validator", getPublishedObjects());
          assertThat(publishedData).isNotNull();
          assertThat(publishedData.size()).isEqualTo(12);
          checkBaseMetricData(publishedData);
          checkGeneralProcessMetricData(publishedData);
          assertThat(publishedData.get("validator_total")).isNotNull().isEqualTo(validatorCount);
          assertThat(publishedData.get("validator_active")).isNotNull().isEqualTo(validatorCount);
        });
  }

  public void waitForBeaconNodeMetricPublication() {
    Waiter.waitFor(
        () -> {
          Map<String, Object> publishedData = getPublisher("beaconnode", getPublishedObjects());
          assertThat(publishedData).isNotNull();
          assertThat(publishedData.size()).isEqualTo(20);
          checkBaseMetricData(publishedData);
          checkGeneralProcessMetricData(publishedData);
          for (String field :
              List.of(
                  "disk_beaconchain_bytes_total",
                  "network_libp2p_bytes_total_receive",
                  "network_libp2p_bytes_total_transmit",
                  "network_peers_connected",
                  "sync_eth2_synced",
                  "sync_beacon_head_slot",
                  "sync_eth1_connected",
                  "sync_eth1_fallback_configured",
                  "sync_eth1_fallback_connected",
                  "slasher_active")) {
            assertThat(publishedData.containsKey(field)).isTrue();
            assertThat(publishedData.get(field)).isNotNull();
          }
        });
  }

  public void waitForSystemMetricPublication() {
    Waiter.waitFor(
        () -> {
          Map<String, Object> publishedData = getPublisher("system", getPublishedObjects());
          assertThat(publishedData).isNotNull();
          assertThat(publishedData.size()).isEqualTo(22);
          checkBaseMetricData(publishedData);
          for (String field :
              List.of(
                  "cpu_cores",
                  "cpu_threads",
                  "cpu_node_system_seconds_total",
                  "cpu_node_user_seconds_total",
                  "cpu_node_iowait_seconds_total",
                  "cpu_node_idle_seconds_total",
                  "memory_node_bytes_total",
                  "memory_node_bytes_free",
                  "memory_node_bytes_cached",
                  "memory_node_bytes_buffers",
                  "disk_node_bytes_total",
                  "disk_node_bytes_free",
                  "disk_node_io_seconds",
                  "disk_node_reads_total",
                  "disk_node_writes_total",
                  "network_node_bytes_total_receive",
                  "network_node_bytes_total_transmit",
                  "misc_node_boot_ts_seconds",
                  "misc_os")) {
            assertThat(publishedData.containsKey(field)).isTrue();
            assertThat(publishedData.get(field)).isNotNull();
          }
        });
  }

  private Map<String, Object> getPublisher(
      final String publisherName, final List<Map<String, Object>> publishedObjects) {
    for (Map<String, Object> current : publishedObjects) {
      final Object publisher = current.get("process");
      if (publisher instanceof String && publisher.equals(publisherName)) {
        return current;
      }
    }
    LOG.debug("Failed to find publisher {}", publisherName);
    return null;
  }

  private void checkBaseMetricData(final Map<String, Object> publishedData) {
    assertThat(publishedData.get("version")).isEqualTo(1L);
    assertThat(publishedData.get("timestamp")).isNotNull().isInstanceOf(Long.class);
  }

  private void checkGeneralProcessMetricData(final Map<String, Object> publishedData) {
    assertThat(publishedData.get("memory_process_bytes")).isNotNull().isInstanceOf(Long.class);
    assertThat(publishedData.get("cpu_process_seconds_total")).isNotNull().isInstanceOf(Long.class);
    assertThat(publishedData.get("client_name")).isEqualTo("teku");

    assertThat((String) publishedData.get("client_version"))
        .matches(Pattern.compile("^\\d\\d\\.\\d{1,2}\\.\\d.*"));
    assertThat(publishedData.get("client_build")).isEqualTo(0L);
    assertThat(publishedData.get("sync_eth2_fallback_configured")).isEqualTo(false);
    assertThat(publishedData.get("sync_eth2_fallback_connected")).isEqualTo(false);
  }

  public void start() {
    assertThat(started).isFalse();
    LOG.debug("Start node {}", nodeAlias);
    started = true;
    container.start();
  }

  @Override
  public void stop() {
    if (!started) {
      return;
    }
    LOG.debug("Shutting down");
    started = false;
    container.stop();
  }
}
