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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.AltairBuilder;
import tech.pegasys.teku.spec.config.builder.BellatrixBuilder;
import tech.pegasys.teku.spec.config.builder.CapellaBuilder;
import tech.pegasys.teku.spec.config.builder.DenebBuilder;
import tech.pegasys.teku.spec.config.builder.ElectraBuilder;
import tech.pegasys.teku.spec.config.builder.FuluBuilder;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SpecConfigBuilderTest {

  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private static final Set<Class<?>> BUILDERS =
      Set.of(
          SpecConfigBuilder.class,
          AltairBuilder.class,
          BellatrixBuilder.class,
          CapellaBuilder.class,
          DenebBuilder.class,
          ElectraBuilder.class,
          FuluBuilder.class);

  /**
   * Ensures Builders have actually non-primitive setters, because primitive setters are silently
   * filled with 0's and doesn't lead to client startup failure with clear missing field error
   */
  @Test
  public void shouldHaveOnlyNonPrimitiveSetters() {
    for (final Class<?> builderClass : BUILDERS) {
      final Method[] methods = builderClass.getDeclaredMethods();

      for (Method method : methods) {
        final Parameter[] parameters = method.getParameters();
        // Check if method has exactly one parameter, most of these methods are setters, others have
        // non-primitive inputs
        if (parameters.length == 1) {
          final Class<?> paramType = parameters[0].getType();
          assertThat(paramType.isPrimitive())
              .withFailMessage(
                  "Builder [%s] method \"%s(%s)\" has primitive input",
                  builderClass.getSimpleName(), method.getName(), paramType)
              .isFalse();
        }
      }
    }
  }

  /**
   * Ensures Builders have actually non-primitive non-final fields, because primitive fields are
   * silently filled with 0's and doesn't lead to client startup failure with clear missing field
   * error
   */
  @Test
  public void shouldHaveOnlyNonPrimitiveFields() {
    for (final Class<?> builderClass : BUILDERS) {
      Arrays.stream(builderClass.getDeclaredFields())
          .filter(field -> !Modifier.isFinal(field.getModifiers()))
          .forEach(
              field -> {
                final Class<?> fieldType = field.getType();
                assertThat(fieldType.isPrimitive())
                    .withFailMessage(
                        "Builder [%s] field \"%s %s\" has primitive type",
                        builderClass.getSimpleName(), fieldType, field.getName())
                    .isFalse();
              });
    }
  }

  @Test
  public void shouldCreateSpecVersionWithEffectiveSpecConfigVersion() {
    final Spec spec = getSpec(__ -> {});
    spec.getForkSchedule().getActiveMilestones().stream()
        .map(ForkAndSpecMilestone::getSpecMilestone)
        .forEach(
            milestone ->
                assertThat(spec.forMilestone(milestone).getConfig().getMilestone())
                    .isEqualTo(milestone));
  }

  @Test
  @Disabled("There is no non-active config at the moment, revisit this when we add Fulu")
  public void shouldCreateSpecExposingNonActiveConfig() {
    final Spec spec = getSpec(__ -> {});
    assertThat(spec.getForkSchedule().getHighestSupportedMilestone()).isNotEqualTo(ELECTRA);
    assertThat(spec.getSpecConfigAndParent().specConfig().getMilestone()).isEqualTo(FULU);
  }

  @Test
  public void shouldLoadAltairForkEpoch() {
    final UInt64 randomEpoch = dataStructureUtil.randomUInt64(100_000);
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.altairForkEpoch(randomEpoch).bellatrixForkEpoch(randomEpoch.plus(1)));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("ALTAIR_FORK_EPOCH"))
        .isEqualTo(randomEpoch);
  }

  @Test
  public void shouldLoadBellatrixForkEpoch() {
    final UInt64 randomEpoch = dataStructureUtil.randomUInt64(100_000);
    final Spec spec = getSpec(phase0Builder -> phase0Builder.bellatrixForkEpoch(randomEpoch));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("BELLATRIX_FORK_EPOCH"))
        .isEqualTo(randomEpoch);
  }

  @Test
  public void shouldLoadTerminalTotalDifficulty() {
    final UInt256 randomUInt256 = dataStructureUtil.randomUInt256();
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder -> mergeBuilder.terminalTotalDifficulty(randomUInt256)));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("TERMINAL_TOTAL_DIFFICULTY"))
        .isEqualTo(randomUInt256);
  }

  @Test
  public void shouldLoadTerminalBlockHash() {
    final Bytes32 randomBytes32 = dataStructureUtil.randomBytes32();
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder -> mergeBuilder.terminalBlockHash(randomBytes32)));

    assertThat(spec.getGenesisSpec().getConfig().getRawConfig().get("TERMINAL_BLOCK_HASH"))
        .isEqualTo(randomBytes32);
  }

  @Test
  public void shouldLoadTerminalBlockHashActivationEpoch() {
    final UInt64 randomUInt64 = dataStructureUtil.randomUInt64();
    final Spec spec =
        getSpec(
            phase0Builder ->
                phase0Builder.bellatrixBuilder(
                    mergeBuilder -> mergeBuilder.terminalBlockHashActivationEpoch(randomUInt64)));

    assertThat(
            spec.getGenesisSpec()
                .getConfig()
                .getRawConfig()
                .get("TERMINAL_BLOCK_HASH_ACTIVATION_EPOCH"))
        .isEqualTo(randomUInt64);
  }

  private Spec getSpec(final Consumer<SpecConfigBuilder> consumer) {
    final SpecConfigAndParent<? extends SpecConfig> config =
        SpecConfigLoader.loadConfig("mainnet", consumer);
    return SpecFactory.create(config);
  }
}
