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

package tech.pegasys.teku.beaconrestapi.schema;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.json.JsonTestUtil;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinition;
import tech.pegasys.teku.infrastructure.json.types.SerializableOneOfTypeDefinitionBuilder;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

public class OneOfTest {

  private final Spec spec = TestSpecFactory.createMinimalBellatrix();

  private final Predicate<BeaconBlock> isPhase0 =
      block -> spec.atSlot(block.getSlot()).getMilestone().equals(SpecMilestone.PHASE0);

  private final Predicate<BeaconBlock> isAltair =
      block -> spec.atSlot(block.getSlot()).getMilestone().equals(SpecMilestone.ALTAIR);
  private final Predicate<BeaconBlock> isBellatrix =
      block -> spec.atSlot(block.getSlot()).getMilestone().equals(SpecMilestone.BELLATRIX);

  private final SerializableTypeDefinition<String> type1 =
      SerializableTypeDefinition.object(String.class).name("Type1").build();
  private final SerializableTypeDefinition<String> type2 =
      SerializableTypeDefinition.object(String.class).name("Type2").build();

  @SuppressWarnings("unchecked")
  @Test
  void shouldCreateOneOfBlockDefinition() throws Exception {
    final SerializableOneOfTypeDefinition<BeaconBlock> schema =
        new SerializableOneOfTypeDefinitionBuilder<BeaconBlock>()
            .title("BeaconBlock")
            .withType(
                isPhase0,
                spec.forMilestone(SpecMilestone.PHASE0)
                    .getSchemaDefinitions()
                    .getBeaconBlockSchema()
                    .getJsonTypeDefinition())
            .withType(
                isAltair,
                spec.forMilestone(SpecMilestone.ALTAIR)
                    .getSchemaDefinitions()
                    .getBeaconBlockSchema()
                    .getJsonTypeDefinition())
            .withType(
                isBellatrix,
                spec.forMilestone(SpecMilestone.BELLATRIX)
                    .getSchemaDefinitions()
                    .getBeaconBlockSchema()
                    .getJsonTypeDefinition())
            .build();

    final String openApiType = JsonUtil.serialize(schema::serializeOpenApiType);
    final Map<String, Object> parsed = JsonTestUtil.parse(openApiType);

    List<Map<String, String>> oneOfList = (List<Map<String, String>>) parsed.get("oneOf");
    assertThat(oneOfList.stream().flatMap(z -> z.values().stream()).collect(Collectors.toList()))
        .containsOnly(
            "#/components/schemas/BeaconBlockPhase0",
            "#/components/schemas/BeaconBlockAltair",
            "#/components/schemas/BeaconBlockBellatrix");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldDeDuplicateList() throws Exception {

    final SerializableOneOfTypeDefinition<String> schema =
        new SerializableOneOfTypeDefinitionBuilder<String>()
            .title("TYPES")
            .withType(String::isEmpty, type1)
            .withType(String::isEmpty, type1)
            .withType(String::isEmpty, type2)
            .build();

    final String openApiType = JsonUtil.serialize(schema::serializeOpenApiType);
    final Map<String, Object> parsed = JsonTestUtil.parse(openApiType);
    final List<Map<String, String>> oneOfList = (List<Map<String, String>>) parsed.get("oneOf");
    assertThat(oneOfList.stream().flatMap(z -> z.values().stream()).collect(Collectors.toList()))
        .containsExactly("#/components/schemas/Type1", "#/components/schemas/Type2");
  }

  @SuppressWarnings("unchecked")
  @Test
  void shouldCreateOneOfBlindedBlockDefinition() throws Exception {
    final SerializableOneOfTypeDefinition<BeaconBlock> schema =
        new SerializableOneOfTypeDefinitionBuilder<BeaconBlock>()
            .title("BlindedBlock")
            .withType(
                isPhase0,
                spec.forMilestone(SpecMilestone.PHASE0)
                    .getSchemaDefinitions()
                    .getBlindedBeaconBlockSchema()
                    .getJsonTypeDefinition())
            .withType(
                isAltair,
                spec.forMilestone(SpecMilestone.ALTAIR)
                    .getSchemaDefinitions()
                    .getBlindedBeaconBlockSchema()
                    .getJsonTypeDefinition())
            .withType(
                isBellatrix,
                spec.forMilestone(SpecMilestone.BELLATRIX)
                    .getSchemaDefinitions()
                    .getBlindedBeaconBlockSchema()
                    .getJsonTypeDefinition())
            .build();

    final String openApiType = JsonUtil.serialize(schema::serializeOpenApiType);
    Map<String, Object> parsed = JsonTestUtil.parse(openApiType);

    List<Map<String, String>> oneOfList = (List<Map<String, String>>) parsed.get("oneOf");
    assertThat(oneOfList.stream().flatMap(z -> z.values().stream()).collect(Collectors.toList()))
        .containsOnly(
            "#/components/schemas/BeaconBlockPhase0",
            "#/components/schemas/BeaconBlockAltair",
            "#/components/schemas/BlindedBeaconBlockBellatrix");
  }
}
