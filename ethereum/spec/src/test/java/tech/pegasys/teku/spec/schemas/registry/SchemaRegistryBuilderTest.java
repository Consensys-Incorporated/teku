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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import java.util.EnumSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.schemas.registry.SchemaTypes.SchemaId;

public class SchemaRegistryBuilderTest {
  private final SpecConfig specConfig = mock(SpecConfig.class);
  private final SchemaCache cache = spy(SchemaCache.createDefault());
  private final SchemaRegistryBuilder builder = new SchemaRegistryBuilder(cache);
  private final SchemaId<String> stringId = SchemaTypes.create("stringType");
  private final String stringSchema = "stringSchema";

  @SuppressWarnings("unchecked")
  private final SchemaProvider<String> mockProvider = mock(SchemaProvider.class);

  private final EnumSet<SpecMilestone> supportedMilestones = EnumSet.of(PHASE0, ALTAIR);

  @BeforeEach
  void setUp() {
    when(mockProvider.getSchemaId()).thenReturn(stringId);
    when(mockProvider.getSchema(any())).thenReturn(stringSchema);
    when(mockProvider.getSupportedMilestones()).thenReturn(supportedMilestones);
    when(mockProvider.getBaseMilestone(any())).thenReturn(PHASE0);
  }

  @Test
  void shouldAddProviderForSupportedMilestone() {

    builder.addProvider(mockProvider);

    for (final SpecMilestone milestone : SpecMilestone.values()) {
      final SchemaRegistry registry = builder.build(milestone, specConfig);
      if (supportedMilestones.contains(milestone)) {
        assertThat(registry.isProviderRegistered(mockProvider)).isTrue();
      } else {
        assertThat(registry.isProviderRegistered(mockProvider)).isFalse();
      }
    }

    verify(mockProvider, times(SpecMilestone.values().length)).getSupportedMilestones();
  }

  @Test
  void shouldPrimeRegistry() {
    builder.addProvider(mockProvider);
    builder.build(PHASE0, specConfig);

    // we should have it in cache immediately
    verify(cache).put(PHASE0, stringId, stringSchema);
  }

  @Test
  void shouldAutomaticallyBuildPreviousMilestones() {
    builder.addProvider(mockProvider);
    builder.build(ALTAIR, specConfig);

    verify(cache).put(PHASE0, stringId, stringSchema);
    verify(cache).put(ALTAIR, stringId, stringSchema);
  }

  @Test
  void shouldThrowWhenNotBuildingInOrder() {
    builder.build(PHASE0, specConfig);

    assertThatThrownBy(() -> builder.build(BELLATRIX, specConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Build must follow the milestone ordering. Last built milestone: PHASE0, requested milestone: BELLATRIX");
  }

  @Test
  void shouldThrowWhenBuildingSameMilestoneTwice() {
    builder.build(PHASE0, specConfig);
    builder.build(ALTAIR, specConfig);

    assertThatThrownBy(() -> builder.build(ALTAIR, specConfig))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(
            "Build must follow the milestone ordering. Last built milestone: ALTAIR, requested milestone: ALTAIR");
  }

  @Test
  void shouldThrowWhenAddingTheSameProviderTwice() {
    builder.addProvider(mockProvider);
    assertThatThrownBy(() -> builder.addProvider(mockProvider))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("has been already added");
  }

  @Test
  void shouldThrowWhenAddingTwoProvidersReferencingTheSameSchemaId() {
    @SuppressWarnings("unchecked")
    final SchemaProvider<String> mockProvider2 = mock(SchemaProvider.class);
    when(mockProvider2.getSchemaId()).thenReturn(stringId);

    builder.addProvider(mockProvider);

    assertThatThrownBy(() -> builder.addProvider(mockProvider2))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("A previously added provider was already providing the");
  }
}
