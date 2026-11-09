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

package tech.pegasys.teku.spec.schemas.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.schemas.registry.BaseSchemaProvider.providerBuilder;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

class BaseSchemaProviderTest {
  @SuppressWarnings("unchecked")
  private static final SchemaId<String> STRING_SCHEMA_ID = mock(SchemaId.class);

  private final SchemaRegistry mockRegistry = mock(SchemaRegistry.class);

  @Test
  void shouldCallCreatorWithCorrectParams() {
    final AtomicReference<SchemaRegistry> registryRef = new AtomicReference<>();
    final AtomicReference<SpecConfig> configRef = new AtomicReference<>();
    final AtomicReference<String> schemaNameRef = new AtomicReference<>();
    final SpecConfig specConfig = mock(SpecConfig.class);

    final SchemaProvider<?> provider =
        providerBuilder(STRING_SCHEMA_ID)
            .withCreator(
                PHASE0,
                (r, c, n) -> {
                  registryRef.set(r);
                  configRef.set(c);
                  schemaNameRef.set(n);
                  return "TestSchema";
                })
            .build();

    when(mockRegistry.getMilestone()).thenReturn(PHASE0);
    when(mockRegistry.getSpecConfig()).thenReturn(specConfig);
    when(STRING_SCHEMA_ID.getSchemaName(PHASE0)).thenReturn("TestSchemaPhase0");
    when(mockRegistry.getMilestone()).thenReturn(PHASE0);
    when(mockRegistry.getSpecConfig()).thenReturn(specConfig);

    assertEquals("TestSchema", provider.getSchema(mockRegistry));

    assertEquals(mockRegistry, registryRef.get());
    assertEquals(specConfig, configRef.get());
    assertEquals("TestSchemaPhase0", schemaNameRef.get());
  }

  @Test
  void shouldSupportContinuousUntilHighestMilestone() {
    final SchemaProvider<?> provider =
        providerBuilder(STRING_SCHEMA_ID)
            .withCreator(ALTAIR, (r, c, n) -> "TestSchemaAltair")
            .withCreator(BELLATRIX, (r, c, n) -> "TestSchemaBellatrix")
            .build();

    assertEquals(ALTAIR, provider.getBaseMilestone(ALTAIR));
    assertEquals(BELLATRIX, provider.getBaseMilestone(BELLATRIX));

    when(mockRegistry.getMilestone()).thenReturn(ALTAIR);
    assertEquals("TestSchemaAltair", provider.getSchema(mockRegistry));

    when(mockRegistry.getMilestone()).thenReturn(BELLATRIX);
    assertEquals("TestSchemaBellatrix", provider.getSchema(mockRegistry));

    assertThat(provider.getSupportedMilestones())
        .containsAll(SpecMilestone.getAllMilestonesFrom(ALTAIR));
  }

  @Test
  void shouldSupportContinuousConstantWithUntil() {
    final SchemaProvider<?> provider =
        providerBuilder(STRING_SCHEMA_ID)
            .withCreator(PHASE0, (r, c, n) -> "TestSchemaPhase0")
            .withCreator(BELLATRIX, (r, c, n) -> "TestSchemaBellatrix")
            .until(CAPELLA)
            .build();

    assertEquals(PHASE0, provider.getBaseMilestone(PHASE0));
    assertEquals(PHASE0, provider.getBaseMilestone(ALTAIR));
    assertEquals(BELLATRIX, provider.getBaseMilestone(BELLATRIX));
    assertEquals(BELLATRIX, provider.getBaseMilestone(CAPELLA));

    when(mockRegistry.getMilestone()).thenReturn(PHASE0);
    assertEquals("TestSchemaPhase0", provider.getSchema(mockRegistry));

    when(mockRegistry.getMilestone()).thenReturn(ALTAIR);
    assertEquals("TestSchemaPhase0", provider.getSchema(mockRegistry));

    when(mockRegistry.getMilestone()).thenReturn(BELLATRIX);
    assertEquals("TestSchemaBellatrix", provider.getSchema(mockRegistry));

    when(mockRegistry.getMilestone()).thenReturn(CAPELLA);
    assertEquals("TestSchemaBellatrix", provider.getSchema(mockRegistry));

    assertThat(provider.getSupportedMilestones())
        .containsExactly(PHASE0, ALTAIR, BELLATRIX, CAPELLA);
  }

  @Test
  void shouldAlwaysCreateNewSchemaDisabledByDefault() {
    final SchemaProvider<?> provider =
        providerBuilder(STRING_SCHEMA_ID)
            .withCreator(PHASE0, (r, c, n) -> "TestSchema" + r.getMilestone())
            .build();

    assertFalse(provider.alwaysCreateNewSchema());
  }

  @Test
  void shouldSupportAlwaysCreateNewSchema() {
    final SchemaProvider<?> provider =
        providerBuilder(STRING_SCHEMA_ID)
            .withCreator(PHASE0, (r, c, n) -> "TestSchema" + r.getMilestone())
            .alwaysCreateNewSchema()
            .build();

    assertTrue(provider.alwaysCreateNewSchema());
  }

  @Test
  void shouldThrowWhenNoCreators() {
    assertThatThrownBy(() -> providerBuilder(STRING_SCHEMA_ID).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("There should be at least 1 creator");
  }

  @Test
  void shouldThrowWhenAskingForAnUnsupportedMilestone() {
    final SchemaProvider<?> provider =
        providerBuilder(STRING_SCHEMA_ID)
            .withCreator(ALTAIR, (r, c, n) -> "TestSchemaAltair")
            .until(ALTAIR)
            .build();

    when(mockRegistry.getMilestone()).thenReturn(DENEB);

    assertThatThrownBy(() -> provider.getSchema(mockRegistry))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("It is not supposed to create a specific version for DENEB");
  }

  @Test
  void shouldThrowWhenNotAscendingMilestones() {
    assertThatThrownBy(
            () ->
                providerBuilder(STRING_SCHEMA_ID)
                    .withCreator(PHASE0, (r, c, n) -> "TestSchema")
                    .withCreator(PHASE0, (r, c, n) -> "TestSchema"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Creator's milestones must added in strict ascending order");

    assertThatThrownBy(
            () ->
                providerBuilder(STRING_SCHEMA_ID)
                    .withCreator(ALTAIR, (r, c, n) -> "TestSchema")
                    .withCreator(PHASE0, (r, c, n) -> "TestSchema"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("Creator's milestones must added in strict ascending order");
  }

  @Test
  void shouldThrowWhenWithUntilIsPriorToMilestone() {
    assertThatThrownBy(
            () ->
                providerBuilder(STRING_SCHEMA_ID)
                    .withCreator(PHASE0, (r, c, n) -> "TestSchema")
                    .withCreator(CAPELLA, (r, c, n) -> "TestSchema")
                    .until(ALTAIR)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("until must be greater or equal than last creator milestone");
  }
}
