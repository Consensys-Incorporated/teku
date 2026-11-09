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

public class SpecConfigBellatrixTest {
  private final Spec spec = TestSpecFactory.createMinimalAltair();

  @Test
  public void equals_mainnet() {
    SpecConfigBellatrix configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionBellatrix().orElseThrow();
    SpecConfigBellatrix configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionBellatrix().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    SpecConfigAltair altair =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionAltair().orElseThrow();
    SpecConfigBellatrix configA = createRandomBellatrixConfig(altair, 1);
    SpecConfigBellatrix configB = createRandomBellatrixConfig(altair, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    SpecConfigAltair altair =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionAltair().orElseThrow();
    SpecConfigBellatrix configA = createRandomBellatrixConfig(altair, 1);
    SpecConfigBellatrix configB = createRandomBellatrixConfig(altair, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_altairConfigDiffer() {
    SpecConfigAltair altairA =
        SpecConfigLoader.loadConfig("mainnet", b -> {})
            .specConfig()
            .toVersionAltair()
            .orElseThrow();
    SpecConfigAltair altairB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.altairBuilder(ab -> ab.syncCommitteeSize(altairA.getSyncCommitteeSize() + 4)))
            .specConfig()
            .toVersionAltair()
            .orElseThrow();

    SpecConfigBellatrix configA = createRandomBellatrixConfig(altairA, 1);
    SpecConfigBellatrix configB = createRandomBellatrixConfig(altairB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigBellatrix createRandomBellatrixConfig(
      final SpecConfigAltair altairConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigBellatrixImpl(
        altairConfig,
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomUInt256(),
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomPositiveInt());
  }
}
