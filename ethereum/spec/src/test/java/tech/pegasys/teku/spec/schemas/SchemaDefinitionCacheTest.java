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

package tech.pegasys.teku.spec.schemas;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions.NonSchema;

public class SchemaDefinitionCacheTest {
  private static final Logger LOG = LogManager.getLogger();

  @ParameterizedTest
  @MethodSource("allNetworksWithAllMilestones")
  void shouldGetSchemasForAllMilestonesOnAllNetworks(
      final Eth2Network network, final SpecMilestone specMilestone) {
    final SpecConfigAndParent<? extends SpecConfig> specConfig =
        SpecConfigLoader.loadConfigStrict(network.configName());
    final Spec spec = SpecFactory.create(specConfig);
    final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);
    assertThat(cache.getSchemaDefinition(specMilestone)).isNotNull();
  }

  static Stream<Arguments> allNetworksWithAllMilestones() {
    return Stream.of(Eth2Network.values())
        .flatMap(
            network ->
                Stream.of(SpecMilestone.values())
                    .map(milestone -> Arguments.of(network, milestone)));
  }

  @Test
  void shouldUseExistingSchemaDefinitionIfPossible() {
    final Spec spec = mock(Spec.class);
    when(spec.forMilestone(SpecMilestone.ALTAIR))
        .thenReturn(TestSpecFactory.createMinimalAltair().forMilestone(SpecMilestone.ALTAIR));
    final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);
    assertThat(cache.getSchemaDefinition(SpecMilestone.ALTAIR)).isNotNull();
    verify(spec).forMilestone(eq(SpecMilestone.ALTAIR));

    assertThat(cache.getSchemaDefinition(SpecMilestone.ALTAIR)).isNotNull();
    verifyNoMoreInteractions(spec);
  }

  @ParameterizedTest
  @EnumSource(SpecMilestone.class)
  void shouldGetSpecMilestoneFromSpecObject(final SpecMilestone specMilestone) {
    final Spec spec = TestSpecFactory.createMinimal(specMilestone);
    final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);
    assertThat(cache.milestoneAtSlot(UInt64.ONE)).isSameAs(specMilestone);
    assertThat(cache.getSchemaDefinition(specMilestone))
        .isSameAs(spec.forMilestone(specMilestone).getSchemaDefinitions());
  }

  @Test
  void shouldCreateSchemaIfMilestoneRequired() {
    final Spec spec = TestSpecFactory.createMinimalPhase0();
    final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);
    assertThat(spec.forMilestone(SpecMilestone.BELLATRIX)).isNull();
    assertThat(cache.getSchemaDefinition(SpecMilestone.BELLATRIX)).isNotNull();
  }

  @ParameterizedTest
  @EnumSource(SpecMilestone.class)
  void subsequentCallsShouldGetTheSameSchemaObject(final SpecMilestone specMilestone) {
    final Spec spec = TestSpecFactory.createMinimal(specMilestone);
    final SchemaDefinitionCache cache = new SchemaDefinitionCache(spec);

    final List<Method> methods = Arrays.asList(SchemaDefinitions.class.getMethods());
    // all the get methods should return the same object on subsequent calls,
    // as creation of the schema objects is relatively expensive
    methods.stream()
        .filter(method -> method.getAnnotation(NonSchema.class) == null)
        .forEach(
            method -> {
              try {
                LOG.info(method.getName());
                final SszSchema<?> first =
                    (SszSchema<?>) method.invoke(cache.getSchemaDefinition(specMilestone));
                final SszSchema<?> second =
                    (SszSchema<?>) method.invoke(cache.getSchemaDefinition(specMilestone));
                assertThat(first.getJsonTypeDefinition()).isEqualTo(second.getJsonTypeDefinition());
              } catch (IllegalAccessException | InvocationTargetException e) {
                Assertions.fail(e);
              }
            });
  }
}
