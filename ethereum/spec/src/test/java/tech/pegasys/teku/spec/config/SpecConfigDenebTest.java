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

import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigDenebTest {
  private final Spec spec = TestSpecFactory.createMinimalCapella();

  @Test
  public void equals_mainnet() {
    SpecConfigDeneb configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionDeneb().orElseThrow();
    SpecConfigDeneb configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionDeneb().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    SpecConfigCapella specConfigCapella =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionCapella().orElseThrow();
    SpecConfigDeneb configA = createRandomDenebConfig(specConfigCapella, 1);
    SpecConfigDeneb configB = createRandomDenebConfig(specConfigCapella, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    SpecConfigCapella specConfigCapella =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionCapella().orElseThrow();
    SpecConfigDeneb configA = createRandomDenebConfig(specConfigCapella, 1);
    SpecConfigDeneb configB = createRandomDenebConfig(specConfigCapella, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_capellaConfigDiffer() {
    SpecConfigCapella capellaA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionCapella().orElseThrow();
    SpecConfigCapella capellaB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.capellaBuilder(
                        cb ->
                            cb.maxWithdrawalsPerPayload(
                                capellaA.getMaxWithdrawalsPerPayload() + 4)))
            .specConfig()
            .toVersionCapella()
            .orElseThrow();

    SpecConfigDeneb configA = createRandomDenebConfig(capellaA, 1);
    SpecConfigDeneb configB = createRandomDenebConfig(capellaB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigDeneb createRandomDenebConfig(
      final SpecConfigCapella capellaConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigDenebImpl(
        capellaConfig,
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        Optional.empty());
  }
}
