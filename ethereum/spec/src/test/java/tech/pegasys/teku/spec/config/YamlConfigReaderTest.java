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

package tech.pegasys.teku.spec.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

@SuppressWarnings("unchecked")
public class YamlConfigReaderTest {
  private final YamlConfigReader reader = new YamlConfigReader();

  @Test
  void shouldReadSimpleObjectToStringMap() {
    final String testData =
"""
version: 250
info: the info
""";
    final Map<String, Object> objData = reader.readValues(IOUtils.toInputStream(testData, UTF_8));
    assertThat(objData.get("version")).isInstanceOf(String.class).isEqualTo("250");
    assertThat(objData.get("info")).isInstanceOf(String.class).isEqualTo("the info");
  }

  @Test
  void shouldReadObjectWithListOfObjects() {
    final String testData =
"""
data:
  - a: 1
    aa: 11
  - b: two
    bb: three
""";

    final Map<String, Object> objData = reader.readValues(IOUtils.toInputStream(testData, UTF_8));
    assertThat(objData.get("data")).isInstanceOf(List.class);
    final List<Map<String, Object>> list = (List<Map<String, Object>>) objData.get("data");
    for (Object o : list) {
      assertThat(o).isInstanceOf(Map.class);
      assertThat(((Map) o).size()).isEqualTo(2);
    }
    assertThat(list)
        .containsExactly(Map.of("a", "1", "aa", "11"), Map.of("b", "two", "bb", "three"));
  }

  @Test
  void shouldReadEth1Address() {
    final String testData =
"""
DEPOSIT_CONTRACT_ADDRESS: 0x4242424242424242424242424242424242424242
    """;
    final Map<String, Object> objData = reader.readValues(IOUtils.toInputStream(testData, UTF_8));
    assertThat(objData.get("DEPOSIT_CONTRACT_ADDRESS"))
        .isInstanceOf(String.class)
        .isEqualTo("0x4242424242424242424242424242424242424242");
  }
}
