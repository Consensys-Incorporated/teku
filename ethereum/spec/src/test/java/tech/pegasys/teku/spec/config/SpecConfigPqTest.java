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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigPqTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();

  @Test
  public void equals_mainnet() {
    final SpecConfigPq configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionPq().orElseThrow();
    final SpecConfigPq configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionPq().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    final SpecConfigGloas specConfigGloas =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionGloas().orElseThrow();
    final SpecConfigPq configA = createRandomPqConfig(specConfigGloas, 1);
    final SpecConfigPq configB = createRandomPqConfig(specConfigGloas, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    final SpecConfigGloas specConfigGloas =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionGloas().orElseThrow();
    final SpecConfigPq configA = createRandomPqConfig(specConfigGloas, 1);
    final SpecConfigPq configB = createRandomPqConfig(specConfigGloas, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_gloasConfigDiffer() {
    final SpecConfigGloas gloasA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionGloas().orElseThrow();
    final SpecConfigGloas gloasB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.gloasBuilder(
                        gb -> gb.builderRegistryLimit(gloasA.getBuilderRegistryLimit() + 4)))
            .specConfig()
            .toVersionGloas()
            .orElseThrow();

    final SpecConfigPq configA = createRandomPqConfig(gloasA, 1);
    final SpecConfigPq configB = createRandomPqConfig(gloasB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigPq createRandomPqConfig(final SpecConfigGloas gloasConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    return new SpecConfigPqImpl(
        new SpecConfigEip8483Impl(gloasConfig, 1024, 16),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt()) {};
  }
}
