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

package tech.pegasys.teku.data.slashinginterchange;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.data.slashinginterchange.Metadata.INTERCHANGE_VERSION;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonUtil;

public class MetadataTest {
  private final String jsonData;
  final Bytes32 root =
      Bytes32.fromHexString("0x6e2c5d8a89dfe121a92c8812bea69fe9f84ae48f63aafe34ef7e18c7eac9af70");

  final Metadata expectedMetadata =
      new Metadata(
          Optional.empty(), INTERCHANGE_VERSION, Optional.of(Bytes32.fromHexString("0x123456")));

  public MetadataTest() throws IOException {
    jsonData = Resources.toString(Resources.getResource("metadata.json"), StandardCharsets.UTF_8);
  }

  @Test
  public void shouldSerializeMinimalFormat() throws JsonProcessingException {
    final Metadata metadata =
        new Metadata(Optional.empty(), INTERCHANGE_VERSION, Optional.of(root));
    assertThat(JsonUtil.prettySerialize(metadata, Metadata.getJsonTypeDefinition()))
        .isEqualToNormalizingNewlines(jsonData);
  }

  @Test
  public void shouldSerializeWithoutRoot() throws JsonProcessingException {
    final Metadata metadata = new Metadata(Optional.empty(), INTERCHANGE_VERSION, Optional.empty());
    assertThat(JsonUtil.prettySerialize(metadata, Metadata.getJsonTypeDefinition()))
        .isEqualToIgnoringWhitespace("{\"interchange_format_version\":\"5\"}");
  }

  @Test
  public void shouldSerializeCompleteFormat() throws JsonProcessingException {
    final Metadata metadata =
        new Metadata(Optional.empty(), INTERCHANGE_VERSION, Optional.of(root));
    assertThat(JsonUtil.prettySerialize(metadata, Metadata.getJsonTypeDefinition()))
        .isEqualToNormalizingNewlines(jsonData);
  }

  @Test
  public void shouldDeserialize() throws JsonProcessingException {
    final Metadata metadata = JsonUtil.parse(jsonData, Metadata.getJsonTypeDefinition());
    assertThat(metadata.interchangeFormatVersion()).isEqualTo(INTERCHANGE_VERSION);
    assertThat(metadata.genesisValidatorsRoot()).contains(root);
  }

  @Test
  public void shouldReadMetadataFromCompleteJson() throws IOException {
    final String completeJson =
        Resources.toString(Resources.getResource("format1_complete.json"), StandardCharsets.UTF_8);
    final SlashingProtectionInterchangeFormat format =
        JsonUtil.parse(completeJson, SlashingProtectionInterchangeFormat.getJsonTypeDefinition());

    assertThat(format.metadata()).isEqualTo(expectedMetadata);
  }

  @Test
  public void shouldReadMetadataFromJson() throws IOException {
    final String minimalJson =
        Resources.toString(Resources.getResource("format2_minimal.json"), StandardCharsets.UTF_8);
    final SlashingProtectionInterchangeFormat format =
        JsonUtil.parse(minimalJson, SlashingProtectionInterchangeFormat.getJsonTypeDefinition());
    assertThat(format.metadata()).isEqualTo(expectedMetadata);
  }
}
