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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class SpecConfigHezeTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();

  @Test
  public void equals_mainnet() {
    final SpecConfigHeze configA =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionHeze().orElseThrow();
    final SpecConfigHeze configB =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionHeze().orElseThrow();

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_sameRandomValues() {
    final SpecConfigGloas specConfigGloas =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionGloas().orElseThrow();
    final SpecConfigHeze configA = createRandomHezeConfig(specConfigGloas, 1);
    final SpecConfigHeze configB = createRandomHezeConfig(specConfigGloas, 1);

    assertThat(configA).isEqualTo(configB);
    assertThat(configA.hashCode()).isEqualTo(configB.hashCode());
  }

  @Test
  public void equals_differentRandomValues() {
    final SpecConfigGloas specConfigGloas =
        SpecConfigLoader.loadConfig("mainnet").specConfig().toVersionGloas().orElseThrow();
    final SpecConfigHeze configA = createRandomHezeConfig(specConfigGloas, 1);
    final SpecConfigHeze configB = createRandomHezeConfig(specConfigGloas, 2);

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

    final SpecConfigHeze configA = createRandomHezeConfig(gloasA, 1);
    final SpecConfigHeze configB = createRandomHezeConfig(gloasB, 1);

    assertThat(configA).isNotEqualTo(configB);
    assertThat(configA.hashCode()).isNotEqualTo(configB.hashCode());
  }

  @Test
  public void eip8198Values_defaultToCurrentTimingWhenAbsent() {
    final SpecConfigHeze mainnet = loadHezeConfig("mainnet", Map.of());
    assertThat(mainnet.getSlotDurationMsHeze()).isEqualTo(12000);
    assertThat(mainnet.getMinBlobDataRetentionMs()).isEqualTo(UInt64.valueOf(1572864000L));

    final SpecConfigHeze minimal = loadHezeConfig("minimal", Map.of());
    assertThat(minimal.getSlotDurationMsHeze()).isEqualTo(6000);
    assertThat(minimal.getMinBlobDataRetentionMs()).isEqualTo(UInt64.valueOf(196608000L));

    // 16384 epochs * 16 slots * 5000 ms
    final SpecConfigHeze gnosis = loadHezeConfig("gnosis", Map.of());
    assertThat(gnosis.getSlotDurationMsHeze()).isEqualTo(5000);
    assertThat(gnosis.getMinBlobDataRetentionMs()).isEqualTo(UInt64.valueOf(1310720000L));
  }

  @Test
  public void eip8198Values_acceptedWhenHezeScheduledWithCurrentTiming() {
    final SpecConfigHeze config =
        loadHezeConfig(
            "mainnet",
            Map.of(
                "HEZE_FORK_EPOCH", "10",
                "SLOT_DURATION_MS_HEZE", "12000",
                "MIN_BLOB_DATA_RETENTION_MS", "1572864000"));

    assertThat(config.getHezeForkEpoch()).isEqualTo(UInt64.valueOf(10));
    assertThat(config.getSlotDurationMsHeze()).isEqualTo(12000);
    assertThat(config.getSlotDurationMillis()).isEqualTo(12000);
    assertThat(config.getMinBlobDataRetentionMs()).isEqualTo(UInt64.valueOf(1572864000L));
  }

  @Test
  public void eip8198Values_slotDurationChangeAcceptedWhenHezeScheduled() {
    final SpecConfigHeze config =
        loadHezeConfig(
            "mainnet", Map.of("HEZE_FORK_EPOCH", "10", "SLOT_DURATION_MS_HEZE", "10000"));

    assertThat(config.getSlotDurationMsHeze()).isEqualTo(10000);
    assertThat(config.getSlotDurationMillis()).isEqualTo(12000);
  }

  @Test
  public void eip8198Values_slotDurationMustBeWholeSecondsWhenHezeScheduled() {
    assertThatThrownBy(
            () ->
                loadHezeConfig(
                    "mainnet", Map.of("HEZE_FORK_EPOCH", "10", "SLOT_DURATION_MS_HEZE", "10500")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("SLOT_DURATION_MS_HEZE (10500) must be a positive multiple of 1000");
  }

  @Test
  public void eip8198Values_slotDurationIgnoredWhenHezeNotScheduled() {
    final SpecConfigHeze config =
        loadHezeConfig("mainnet", Map.of("SLOT_DURATION_MS_HEZE", "10500"));

    assertThat(config.getHezeForkEpoch()).isEqualTo(SpecConfig.FAR_FUTURE_EPOCH);
    assertThat(config.getSlotDurationMsHeze()).isEqualTo(10500);
    assertThat(config.getSlotDurationMillis()).isEqualTo(12000);
  }

  @Test
  public void eip8198Values_differentRetentionAcceptedWhenHezeScheduled() {
    final SpecConfigHeze config =
        loadHezeConfig(
            "mainnet", Map.of("HEZE_FORK_EPOCH", "10", "MIN_BLOB_DATA_RETENTION_MS", "1000"));

    assertThat(config.getMinBlobDataRetentionMs()).isEqualTo(UInt64.valueOf(1000));
  }

  private SpecConfigHeze loadHezeConfig(
      final String network, final Map<String, Object> configOverrides) {
    return SpecConfigLoader.loadConfig(network, __ -> {}, configOverrides)
        .specConfig()
        .toVersionHeze()
        .orElseThrow();
  }

  private SpecConfigHeze createRandomHezeConfig(final SpecConfigGloas gloasConfig, final int seed) {
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(seed, spec);
    return new SpecConfigHezeImpl(
        gloasConfig,
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomPositiveInt(),
        dataStructureUtil.randomUInt64()) {};
  }
}
