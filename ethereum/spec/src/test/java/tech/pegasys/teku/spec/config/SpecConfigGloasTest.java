/*
 * Copyright Consensys Software Inc., 2026
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

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigGloasTest {
  private final Spec spec = TestSpecFactory.createMinimalFulu();

  @Test
  public void equals_mainnet() {
    final SpecConfigGloas configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionGloas().orElseThrow();
    final SpecConfigGloas configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionGloas().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void shouldLoadPayloadDueBps() {
    final SpecConfigGloas config =
        SpecConfigLoader.loadConfig("minimal").specConfig().toVersionGloas().orElseThrow();

    assertThat(config.getPayloadDueBps()).isEqualTo(5000);
  }

  @Test
  public void shouldLoadGloasNetworkMaxSizes() {
    final SpecConfigGloas config =
        SpecConfigLoader.loadConfig("minimal").specConfig().toVersionGloas().orElseThrow();

    assertThat(config.getMaxSignedAggregateAndProofSize()).isEqualTo(16829);
    assertThat(config.getMaxAttesterSlashingSize()).isEqualTo(2097616);
    assertThat(config.getMaxSignedExecutionPayloadBidSize()).isEqualTo(196932);
  }

  @Test
  public void shouldExposeProgrammaticGloasNetworkMaxSizeOverrides() {
    final SpecConfigGloas config =
        SpecConfigLoader.loadConfig(
                "minimal",
                builder ->
                    builder.gloasBuilder(
                        gloasBuilder ->
                            gloasBuilder
                                .maxSignedAggregateAndProofSize(1)
                                .maxAttesterSlashingSize(2)
                                .maxSignedExecutionPayloadBidSize(5)))
            .specConfig()
            .toVersionGloas()
            .orElseThrow();

    assertThat(config.getMaxSignedAggregateAndProofSize()).isEqualTo(1);
    assertThat(config.getMaxAttesterSlashingSize()).isEqualTo(2);
    assertThat(config.getMaxSignedExecutionPayloadBidSize()).isEqualTo(5);
  }

  @Test
  public void equals_sameRandomValues() {
    final SpecConfigFulu specConfigFulu =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionFulu().orElseThrow();
    final SpecConfigGloas configA = createRandomGloasConfig(specConfigFulu, 1);
    final SpecConfigGloas configB = createRandomGloasConfig(specConfigFulu, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    final SpecConfigFulu specConfigFulu =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionFulu().orElseThrow();
    final SpecConfigGloas configA = createRandomGloasConfig(specConfigFulu, 1);
    final SpecConfigGloas configB = createRandomGloasConfig(specConfigFulu, 2);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void equals_fuluConfigDiffer() {
    final SpecConfigFulu fuluA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionFulu().orElseThrow();
    final SpecConfigFulu fuluB =
        SpecConfigLoader.loadConfig(
                "mainnet",
                b ->
                    b.fuluBuilder(
                        fb -> fb.fieldElementsPerCell(fuluA.getFieldElementsPerCell().plus(4))))
            .specConfig()
            .toVersionFulu()
            .orElseThrow();

    final SpecConfigGloas configA = createRandomGloasConfig(fuluA, 1);
    final SpecConfigGloas configB = createRandomGloasConfig(fuluB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  private SpecConfigGloas createRandomGloasConfig(final SpecConfigFulu fuluConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);

    return new SpecConfigGloasImpl(
        fuluConfig,
        dataStructureUtil.randomPositiveInt(12000),
        dataStructureUtil.randomPositiveInt(12000),
        dataStructureUtil.randomPositiveInt(12000),
        dataStructureUtil.randomPositiveInt(256),
        dataStructureUtil.randomPositiveInt(16),
        dataStructureUtil.randomLong(),
        dataStructureUtil.randomLong(),
        dataStructureUtil.randomPositiveInt(16384),
        dataStructureUtil.randomPositiveInt(128),
        dataStructureUtil.randomPositiveInt(512),
        dataStructureUtil.randomPositiveInt(4096),
        dataStructureUtil.randomPositiveInt(12000),
        dataStructureUtil.randomPositiveInt(512),
        dataStructureUtil.randomPositiveInt(12000),
        dataStructureUtil.randomPositiveInt(12000),
        dataStructureUtil.randomPositiveInt(65536),
        dataStructureUtil.randomPositiveInt(65536),
        dataStructureUtil.randomUInt64(),
        dataStructureUtil.randomPositiveInt(1_000_000),
        dataStructureUtil.randomPositiveInt(1_000_000),
        dataStructureUtil.randomPositiveInt(1_000_000),
        List.of(
            new GasLimitScheduleEntry(
                dataStructureUtil.randomUInt64(), dataStructureUtil.randomUInt64()))) {};
  }
}
