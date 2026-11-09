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

public class SpecConfigAltairTest {
  private final Spec spec = TestSpecFactory.createMinimalPhase0();

  @Test
  public void equals_mainnet() {
    SpecConfigAltair configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionAltair().orElseThrow();
    SpecConfigAltair configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionAltair().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    SpecConfig phase0 = SpecConfigLoader.loadConfig("mainnet").specConfig();
    SpecConfigAltair configA = createRandomAltairConfig(phase0, 1);
    SpecConfigAltair configB = createRandomAltairConfig(phase0, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    SpecConfig phase0 = SpecConfigLoader.loadConfig("mainnet").specConfig();
    SpecConfigAltair configA = createRandomAltairConfig(phase0, 1);
    SpecConfigAltair configB = createRandomAltairConfig(phase0, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_phase0ConfigDiffer() {
    SpecConfig phase0A = SpecConfigLoader.loadConfig("swift", b -> {}).specConfig();
    SpecConfig phase0B =
        SpecConfigLoader.loadConfig("swift", b -> b.maxValidatorsPerCommittee(1)).specConfig();

    SpecConfigAltair configA = createRandomAltairConfig(phase0A, 1);
    SpecConfigAltair configB = createRandomAltairConfig(phase0B, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigAltair createRandomAltairConfig(final SpecConfig phase0Config, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigAltairImpl(
        phase0Config,
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt());
  }
}
