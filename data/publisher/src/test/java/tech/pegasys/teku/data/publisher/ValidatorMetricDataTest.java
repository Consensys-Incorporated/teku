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

class ValidatorMetricDataTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  public void shouldSerializeObject() throws JsonProcessingException {
    final MetricsPublisherSource source =
        StubMetricsPublisherSource.builder().validatorsActive(33).validatorsTotal(44).build();
    final ValidatorMetricData process = new ValidatorMetricData(10L, source);
    final String data = mapper.writeValueAsString(process);
    final JsonNode node = mapper.readTree(data);
    assertThat(node.size()).isEqualTo(12);
    assertThat(node.get("process").asText()).isEqualTo("validator");
    assertThat(node.get("validator_total").asInt()).isEqualTo(44);
    assertThat(node.get("validator_active").asInt()).isEqualTo(33);
  }
}
