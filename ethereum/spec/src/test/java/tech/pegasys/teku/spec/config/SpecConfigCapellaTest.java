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

public class SpecConfigCapellaTest {
  private final Spec spec = TestSpecFactory.createMinimalBellatrix();

  @Test
  public void equals_mainnet() {
    SpecConfigCapella configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionCapella().orElseThrow();
    SpecConfigCapella configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionCapella().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    SpecConfigBellatrix specConfigBellatrix =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionBellatrix().orElseThrow();
    ;
    SpecConfigBellatrix configA = createRandomCapellaConfig(specConfigBellatrix, 1);
    SpecConfigBellatrix configB = createRandomCapellaConfig(specConfigBellatrix, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    SpecConfigBellatrix specConfigBellatrix =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionBellatrix().orElseThrow();
    ;
    SpecConfigBellatrix configA = createRandomCapellaConfig(specConfigBellatrix, 1);
    SpecConfigBellatrix configB = createRandomCapellaConfig(specConfigBellatrix, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_bellatrixConfigDiffer() {
    SpecConfigBellatrix bellatrixA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionBellatrix().orElseThrow();
    SpecConfigBellatrix bellatrixB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.bellatrixBuilder(
                        ab ->
                            ab.maxBytesPerTransaction(bellatrixA.getMaxBytesPerTransaction() + 4)))
            .specConfig()
            .toVersionBellatrix()
            .orElseThrow();

    SpecConfigBellatrix configA = createRandomCapellaConfig(bellatrixA, 1);
    SpecConfigBellatrix configB = createRandomCapellaConfig(bellatrixB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigBellatrix createRandomCapellaConfig(
      final SpecConfigBellatrix bellatrixConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigCapellaImpl(
        bellatrixConfig,
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt());
  }
}
