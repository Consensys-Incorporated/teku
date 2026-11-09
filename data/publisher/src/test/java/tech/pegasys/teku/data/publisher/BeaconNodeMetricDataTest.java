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

package tech.pegasys.teku.data.publisher;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.test.data.publisher.StubMetricsPublisherSource;

public class BeaconNodeMetricDataTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void shouldSerialize() throws JsonProcessingException {
    final MetricsPublisherSource source =
        StubMetricsPublisherSource.builder()
            .gossipBytesReceived(998L)
            .headSlot(1012L)
            .gossipBytesSent(776L)
            .isEth2Synced(true)
            .build();
    final BeaconNodeMetricData process = new BeaconNodeMetricData(10L, source);
    final String data = mapper.writeValueAsString(process);
    final JsonNode node = mapper.readTree(data);
    assertThat(node.size()).isEqualTo(20);
    assertThat(node.get("process").asText()).isEqualTo("beaconnode");
    assertThat(node.get("disk_beaconchain_bytes_total").asLong()).isEqualTo(0L);
    assertThat(node.get("network_libp2p_bytes_total_receive").asLong()).isEqualTo(998L);
    assertThat(node.get("network_libp2p_bytes_total_transmit").asLong()).isEqualTo(776L);
    assertThat(node.get("sync_eth1_connected").asInt()).isEqualTo(0);
    assertThat(node.get("sync_eth2_synced").asBoolean()).isTrue();
    assertThat(node.get("sync_beacon_head_slot").asLong()).isEqualTo(1012L);
    assertThat(node.get("sync_eth1_fallback_configured").asBoolean()).isFalse();
    assertThat(node.get("sync_eth1_fallback_connected").asBoolean()).isFalse();
    assertThat(node.get("slasher_active").asBoolean()).isFalse();
  }
}
