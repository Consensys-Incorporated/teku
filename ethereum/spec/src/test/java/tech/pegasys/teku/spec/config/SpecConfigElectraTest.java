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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigElectraTest {
  private final Spec spec = TestSpecFactory.createMinimalDeneb();

  @Test
  public void equals_mainnet() {
    final SpecConfigElectra configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionElectra().orElseThrow();
    final SpecConfigElectra configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionElectra().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionDeneb().orElseThrow();
    final SpecConfigElectra configA = createRandomElectraConfig(specConfigDeneb, 1);
    final SpecConfigElectra configB = createRandomElectraConfig(specConfigDeneb, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    final SpecConfigDeneb specConfigDeneb =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionDeneb().orElseThrow();
    final SpecConfigElectra configA = createRandomElectraConfig(specConfigDeneb, 1);
    final SpecConfigElectra configB = createRandomElectraConfig(specConfigDeneb, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_denebConfigDiffer() {
    final SpecConfigDeneb denebA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionDeneb().orElseThrow();
    final SpecConfigDeneb denebB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b -> b.denebBuilder(db -> db.maxBlobsPerBlock(denebA.getMaxBlobsPerBlock() + 4)))
            .specConfig()
            .toVersionDeneb()
            .orElseThrow();

    final SpecConfigElectra configA = createRandomElectraConfig(denebA, 1);
    final SpecConfigElectra configB = createRandomElectraConfig(denebB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @ParameterizedTest
  @EnumSource(SpecMilestone.class)
  public void getMaxValidatorsPerAttestation_matchesMilestoneFormula(
      final SpecMilestone milestone) {
    final SpecConfig config = TestSpecFactory.createMinimal(milestone).getGenesisSpecConfig();
    final long expected =
        milestone.isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)
            ? (long) config.getMaxValidatorsPerCommittee() * config.getMaxCommitteesPerSlot()
            : config.getMaxValidatorsPerCommittee();

    assertThat(config.getMaxValidatorsPerAttestation()).isEqualTo(expected);
  }

  private SpecConfigElectra createRandomElectraConfig(
      final SpecConfigDeneb denebConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigElectraImpl(
        denebConfig,
        dataStructureUtil.randomUInt64(128000000000L),
        dataStructureUtil.randomUInt64(32000000000L),
        dataStructureUtil.randomUInt64(2048000000000L),
        dataStructureUtil.randomPositiveInt(134217728),
        dataStructureUtil.randomPositiveInt(134217728),
        dataStructureUtil.randomPositiveInt(262144),
        dataStructureUtil.randomPositiveInt(4096),
        dataStructureUtil.randomPositiveInt(4096),
        dataStructureUtil.randomPositiveInt(1),
        dataStructureUtil.randomPositiveInt(8),
        dataStructureUtil.randomPositiveInt(1),
        dataStructureUtil.randomPositiveInt(8192),
        dataStructureUtil.randomPositiveInt(16),
        dataStructureUtil.randomPositiveInt(8),
        dataStructureUtil.randomPositiveInt(16),
        dataStructureUtil.randomPositiveInt(8),
        dataStructureUtil.randomPositiveInt(1024),
        dataStructureUtil.randomPositiveInt(8)) {};
  }
}
