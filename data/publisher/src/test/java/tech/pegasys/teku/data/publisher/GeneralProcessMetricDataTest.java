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

public class GeneralProcessMetricDataTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void shouldSerializeObject() throws JsonProcessingException {
    final String processField = "system";
    final MetricsPublisherSource source =
        StubMetricsPublisherSource.builder()
            .cpuSecondsTotal(1100L)
            .memoryProcessBytes(2200L)
            .build();
    final GeneralProcessMetricData process =
        new GeneralProcessMetricData(10L, processField, source);
    final String data = mapper.writeValueAsString(process);
    final JsonNode node = mapper.readTree(data);
    assertThat(node.size()).isEqualTo(10);
    assertThat(node.get("client_build").asInt()).isEqualTo(0);
    assertThat(node.get("client_name").asText()).isEqualTo("teku");
    assertThat(node.get("client_version").asText()).isNotEmpty();
    assertThat(node.get("sync_eth2_fallback_connected").asBoolean()).isFalse();
    assertThat(node.get("sync_eth2_fallback_configured").asBoolean()).isFalse();
    assertThat(node.get("cpu_process_seconds_total").asLong()).isEqualTo(1100L);
    assertThat(node.get("memory_process_bytes").asLong()).isEqualTo(2200L);
  }
}
