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

package tech.pegasys.teku.spec;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;
import org.assertj.core.api.AssertionsForClassTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigElectra;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.networks.Eth2Network;

public class SpecMilestoneTest {
  private static final SpecConfigGloas GLOAS_SPEC_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionGloas()
          .orElseThrow();
  private static final SpecConfigFulu FULU_SPEC_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionFulu()
          .orElseThrow();
  private static final SpecConfigElectra ELECTRA_SPEC_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionElectra()
          .orElseThrow();
  private static final SpecConfigDeneb DENEB_SPEC_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionDeneb()
          .orElseThrow();
  private static final SpecConfigCapella CAPELLA_SPEC_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionCapella()
          .orElseThrow();
  private static final SpecConfigBellatrix BELLATRIX_SPEC_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionBellatrix()
          .orElseThrow();
  private static final SpecConfigAltair ALTAIR_SPEC_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName())
          .specConfig()
          .toVersionAltair()
          .orElseThrow();
  private static final SpecConfig PHASE0_SPEC_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()).specConfig();

  public static Stream<Arguments> isLessThanPermutations() {
    return Stream.of(
        Arguments.of(PHASE0, PHASE0, false),
        Arguments.of(PHASE0, ALTAIR, true),
        Arguments.of(PHASE0, BELLATRIX, true),
        Arguments.of(PHASE0, CAPELLA, true),
        Arguments.of(PHASE0, DENEB, true),
        Arguments.of(PHASE0, ELECTRA, true),
        Arguments.of(ALTAIR, PHASE0, false),
        Arguments.of(ALTAIR, ALTAIR, false),
        Arguments.of(ALTAIR, BELLATRIX, true),
        Arguments.of(ALTAIR, CAPELLA, true),
        Arguments.of(ALTAIR, DENEB, true),
        Arguments.of(ALTAIR, ELECTRA, true),
        Arguments.of(BELLATRIX, PHASE0, false),
        Arguments.of(BELLATRIX, ALTAIR, false),
        Arguments.of(BELLATRIX, BELLATRIX, false),
        Arguments.of(BELLATRIX, CAPELLA, true),
        Arguments.of(BELLATRIX, DENEB, true),
        Arguments.of(BELLATRIX, ELECTRA, true),
        Arguments.of(CAPELLA, PHASE0, false),
        Arguments.of(CAPELLA, ALTAIR, false),
        Arguments.of(CAPELLA, BELLATRIX, false),
        Arguments.of(CAPELLA, CAPELLA, false),
        Arguments.of(CAPELLA, DENEB, true),
        Arguments.of(CAPELLA, ELECTRA, true),
        Arguments.of(DENEB, PHASE0, false),
        Arguments.of(DENEB, ALTAIR, false),
        Arguments.of(DENEB, BELLATRIX, false),
        Arguments.of(DENEB, CAPELLA, false),
        Arguments.of(DENEB, DENEB, false),
        Arguments.of(DENEB, ELECTRA, true),
        Arguments.of(ELECTRA, PHASE0, false),
        Arguments.of(ELECTRA, ALTAIR, false),
        Arguments.of(ELECTRA, BELLATRIX, false),
        Arguments.of(ELECTRA, CAPELLA, false),
        Arguments.of(ELECTRA, DENEB, false),
        Arguments.of(ELECTRA, ELECTRA, false),
        Arguments.of(FULU, PHASE0, false),
        Arguments.of(FULU, ALTAIR, false),
        Arguments.of(FULU, BELLATRIX, false),
        Arguments.of(FULU, CAPELLA, false),
        Arguments.of(FULU, DENEB, false),
        Arguments.of(FULU, ELECTRA, false),
        Arguments.of(FULU, FULU, false),
        Arguments.of(GLOAS, PHASE0, false),
        Arguments.of(GLOAS, ALTAIR, false),
        Arguments.of(GLOAS, BELLATRIX, false),
        Arguments.of(GLOAS, CAPELLA, false),
        Arguments.of(GLOAS, DENEB, false),
        Arguments.of(GLOAS, ELECTRA, false),
        Arguments.of(GLOAS, FULU, false),
        Arguments.of(GLOAS, GLOAS, false));
  }

  public static Stream<Arguments> isGreaterThanOrEqualToPermutations() {
    return Stream.of(
        Arguments.of(PHASE0, PHASE0, true),
        Arguments.of(PHASE0, ALTAIR, false),
        Arguments.of(PHASE0, BELLATRIX, false),
        Arguments.of(PHASE0, CAPELLA, false),
        Arguments.of(PHASE0, DENEB, false),
        Arguments.of(PHASE0, ELECTRA, false),
        Arguments.of(ALTAIR, PHASE0, true),
        Arguments.of(ALTAIR, ALTAIR, true),
        Arguments.of(ALTAIR, BELLATRIX, false),
        Arguments.of(ALTAIR, CAPELLA, false),
        Arguments.of(ALTAIR, DENEB, false),
        Arguments.of(ALTAIR, ELECTRA, false),
        Arguments.of(BELLATRIX, PHASE0, true),
        Arguments.of(BELLATRIX, ALTAIR, true),
        Arguments.of(BELLATRIX, BELLATRIX, true),
        Arguments.of(BELLATRIX, CAPELLA, false),
        Arguments.of(BELLATRIX, DENEB, false),
        Arguments.of(BELLATRIX, ELECTRA, false),
        Arguments.of(CAPELLA, PHASE0, true),
        Arguments.of(CAPELLA, ALTAIR, true),
        Arguments.of(CAPELLA, BELLATRIX, true),
        Arguments.of(CAPELLA, CAPELLA, true),
        Arguments.of(CAPELLA, DENEB, false),
        Arguments.of(CAPELLA, ELECTRA, false),
        Arguments.of(DENEB, PHASE0, true),
        Arguments.of(DENEB, ALTAIR, true),
        Arguments.of(DENEB, BELLATRIX, true),
        Arguments.of(DENEB, CAPELLA, true),
        Arguments.of(DENEB, DENEB, true),
        Arguments.of(DENEB, ELECTRA, false),
        Arguments.of(ELECTRA, PHASE0, true),
        Arguments.of(ELECTRA, ALTAIR, true),
        Arguments.of(ELECTRA, BELLATRIX, true),
        Arguments.of(ELECTRA, CAPELLA, true),
        Arguments.of(ELECTRA, DENEB, true),
        Arguments.of(ELECTRA, ELECTRA, true),
        Arguments.of(FULU, PHASE0, true),
        Arguments.of(FULU, ALTAIR, true),
        Arguments.of(FULU, BELLATRIX, true),
        Arguments.of(FULU, CAPELLA, true),
        Arguments.of(FULU, DENEB, true),
        Arguments.of(FULU, ELECTRA, true),
        Arguments.of(FULU, FULU, true),
        Arguments.of(GLOAS, PHASE0, true),
        Arguments.of(GLOAS, ALTAIR, true),
        Arguments.of(GLOAS, BELLATRIX, true),
        Arguments.of(GLOAS, CAPELLA, true),
        Arguments.of(GLOAS, DENEB, true),
        Arguments.of(GLOAS, ELECTRA, true),
        Arguments.of(GLOAS, FULU, true),
        Arguments.of(GLOAS, GLOAS, true));
  }

  public static Stream<Arguments> getPreviousPermutations() {
    return Stream.of(
        Arguments.of(ALTAIR, PHASE0),
        Arguments.of(BELLATRIX, ALTAIR),
        Arguments.of(CAPELLA, BELLATRIX),
        Arguments.of(DENEB, CAPELLA),
        Arguments.of(ELECTRA, DENEB),
        Arguments.of(FULU, ELECTRA),
        Arguments.of(GLOAS, FULU));
  }

  @ParameterizedTest
  @MethodSource("isGreaterThanOrEqualToPermutations")
  public void isGreaterThanOrEqualTo(
      final SpecMilestone a, final SpecMilestone b, final boolean comparisonResult) {
    assertThat(a.isGreaterThanOrEqualTo(b)).isEqualTo(comparisonResult);
  }

  @ParameterizedTest
  @MethodSource("isLessThanPermutations")
  public void isLessThan(
      final SpecMilestone a, final SpecMilestone b, final boolean comparisonResult) {
    AssertionsForClassTypes.assertThat(a.isLessThan(b)).isEqualTo(comparisonResult);
  }

  @ParameterizedTest
  @MethodSource("getPreviousPermutations")
  public void getPreviousMilestone(final SpecMilestone current, final SpecMilestone previous) {
    assertThat(current.getPreviousMilestone()).isEqualTo(previous);
  }

  @Test
  void getPreviousMilestone_throws() {
    assertThrows(IllegalArgumentException.class, PHASE0::getPreviousMilestone);
  }

  @Test
  void getPreviousMilestoneIfPresent() {
    assertThat(PHASE0.getPreviousMilestoneIfExists()).isEmpty();
    assertThat(ALTAIR.getPreviousMilestoneIfExists()).contains(PHASE0);
    assertThat(BELLATRIX.getPreviousMilestoneIfExists()).contains(ALTAIR);
    assertThat(CAPELLA.getPreviousMilestoneIfExists()).contains(BELLATRIX);
    assertThat(DENEB.getPreviousMilestoneIfExists()).contains(CAPELLA);
    assertThat(ELECTRA.getPreviousMilestoneIfExists()).contains(DENEB);
    assertThat(FULU.getPreviousMilestoneIfExists()).contains(ELECTRA);
    assertThat(GLOAS.getPreviousMilestoneIfExists()).contains(FULU);
  }

  @ParameterizedTest
  @MethodSource("getPriorMilestonePermutations")
  public void getAllPriorMilestones(
      final SpecMilestone current, final Collection<SpecMilestone> priorMilestones) {
    assertThat(SpecMilestone.getAllPriorMilestones(current)).containsAll(priorMilestones);
  }

  public static Stream<Arguments> getPriorMilestonePermutations() {
    return Stream.of(
        Arguments.of(PHASE0, List.of()),
        Arguments.of(ALTAIR, List.of(PHASE0)),
        Arguments.of(BELLATRIX, List.of(PHASE0, ALTAIR)),
        Arguments.of(CAPELLA, List.of(PHASE0, ALTAIR, BELLATRIX)),
        Arguments.of(DENEB, List.of(PHASE0, ALTAIR, BELLATRIX, CAPELLA)),
        Arguments.of(ELECTRA, List.of(PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB)),
        Arguments.of(FULU, List.of(PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA)),
        Arguments.of(GLOAS, List.of(PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA, FULU)));
  }

  @ParameterizedTest
  @MethodSource("getMilestonesUpToPermutations")
  public void getMilestonesUpTo(
      final SpecMilestone current, final Collection<SpecMilestone> upToMilestones) {
    assertThat(SpecMilestone.getMilestonesUpTo(current)).containsAll(upToMilestones);
  }

  public static Stream<Arguments> getMilestonesUpToPermutations() {
    return Stream.of(
        Arguments.of(PHASE0, List.of(PHASE0)),
        Arguments.of(ALTAIR, List.of(PHASE0, ALTAIR)),
        Arguments.of(BELLATRIX, List.of(PHASE0, ALTAIR, BELLATRIX)),
        Arguments.of(CAPELLA, List.of(PHASE0, ALTAIR, BELLATRIX, CAPELLA)),
        Arguments.of(DENEB, List.of(PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB)),
        Arguments.of(ELECTRA, List.of(PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA)),
        Arguments.of(FULU, List.of(PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA, FULU)),
        Arguments.of(
            GLOAS, List.of(PHASE0, ALTAIR, BELLATRIX, CAPELLA, DENEB, ELECTRA, FULU, GLOAS)));
  }

  @Test
  public void areMilestonesInOrder() {
    assertThat(SpecMilestone.areMilestonesInOrder(PHASE0, SpecMilestone.ALTAIR)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(PHASE0)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ALTAIR)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ALTAIR, PHASE0)).isFalse();
    assertThat(
            SpecMilestone.areMilestonesInOrder(
                PHASE0, SpecMilestone.ALTAIR, SpecMilestone.BELLATRIX))
        .isTrue();
    assertThat(
            SpecMilestone.areMilestonesInOrder(
                SpecMilestone.ALTAIR, PHASE0, SpecMilestone.BELLATRIX))
        .isFalse();
    assertThat(
            SpecMilestone.areMilestonesInOrder(
                PHASE0, SpecMilestone.BELLATRIX, SpecMilestone.ALTAIR))
        .isFalse();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.BELLATRIX, CAPELLA)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(CAPELLA, SpecMilestone.BELLATRIX)).isFalse();
    assertThat(SpecMilestone.areMilestonesInOrder(CAPELLA, DENEB)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(DENEB, CAPELLA)).isFalse();
    assertThat(SpecMilestone.areMilestonesInOrder(DENEB, SpecMilestone.ELECTRA)).isTrue();
    assertThat(SpecMilestone.areMilestonesInOrder(SpecMilestone.ELECTRA, DENEB)).isFalse();
  }

  @Test
  public void getForkVersion_phase0() {
    final Bytes4 expected = PHASE0_SPEC_CONFIG.getGenesisForkVersion();
    assertThat(SpecMilestone.getForkVersion(PHASE0_SPEC_CONFIG, PHASE0)).contains(expected);
  }

  @Test
  public void getForkVersion_altair() {
    final Bytes4 expected = ALTAIR_SPEC_CONFIG.getAltairForkVersion();
    assertThat(SpecMilestone.getForkVersion(ALTAIR_SPEC_CONFIG, SpecMilestone.ALTAIR))
        .contains(expected);
  }

  @Test
  public void getForkVersion_bellatrix() {
    final Bytes4 expected = BELLATRIX_SPEC_CONFIG.getBellatrixForkVersion();
    assertThat(SpecMilestone.getForkVersion(BELLATRIX_SPEC_CONFIG, SpecMilestone.BELLATRIX))
        .contains(expected);
  }

  @Test
  public void getForkVersion_capella() {
    final Bytes4 expected = CAPELLA_SPEC_CONFIG.getCapellaForkVersion();
    assertThat(SpecMilestone.getForkVersion(CAPELLA_SPEC_CONFIG, CAPELLA)).contains(expected);
  }

  @Test
  public void getForkVersion_deneb() {
    final Bytes4 expected = DENEB_SPEC_CONFIG.getDenebForkVersion();
    assertThat(SpecMilestone.getForkVersion(DENEB_SPEC_CONFIG, DENEB)).contains(expected);
  }

  @Test
  public void getForkVersion_electra() {
    final Bytes4 expected = ELECTRA_SPEC_CONFIG.getElectraForkVersion();
    assertThat(SpecMilestone.getForkVersion(ELECTRA_SPEC_CONFIG, SpecMilestone.ELECTRA))
        .contains(expected);
  }

  @Test
  public void getForkVersion_fulu() {
    final Bytes4 expected = FULU_SPEC_CONFIG.getFuluForkVersion();
    assertThat(SpecMilestone.getForkVersion(FULU_SPEC_CONFIG, SpecMilestone.FULU))
        .contains(expected);
  }

  @Test
  public void getForkVersion_gloas() {
    final Bytes4 expected = GLOAS_SPEC_CONFIG.getGloasForkVersion();
    assertThat(SpecMilestone.getForkVersion(GLOAS_SPEC_CONFIG, GLOAS)).contains(expected);
  }

  @Test
  public void getForkEpoch_phase0() {
    final UInt64 expected = UInt64.ZERO;
    assertThat(SpecMilestone.getForkEpoch(PHASE0_SPEC_CONFIG, PHASE0)).contains(expected);
  }

  @Test
  public void getForEpoch_altair() {
    final UInt64 expected = ALTAIR_SPEC_CONFIG.getAltairForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(ALTAIR_SPEC_CONFIG, SpecMilestone.ALTAIR))
        .contains(expected);
  }

  @Test
  public void getForkEpoch_bellatrix() {
    final UInt64 expected = BELLATRIX_SPEC_CONFIG.getBellatrixForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(BELLATRIX_SPEC_CONFIG, SpecMilestone.BELLATRIX))
        .contains(expected);
  }

  @Test
  public void getForkEpoch_capella() {
    final UInt64 expected = CAPELLA_SPEC_CONFIG.getCapellaForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(CAPELLA_SPEC_CONFIG, CAPELLA)).contains(expected);
  }

  @Test
  public void getForkEpoch_deneb() {
    final UInt64 expected = DENEB_SPEC_CONFIG.getDenebForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(DENEB_SPEC_CONFIG, DENEB)).contains(expected);
  }

  @Test
  public void getForkEpoch_electra() {
    final UInt64 expected = ELECTRA_SPEC_CONFIG.getElectraForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(ELECTRA_SPEC_CONFIG, SpecMilestone.ELECTRA))
        .contains(expected);
  }

  @Test
  public void getForkEpoch_fulu() {
    final UInt64 expected = FULU_SPEC_CONFIG.getElectraForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(FULU_SPEC_CONFIG, FULU)).contains(expected);
  }

  @Test
  public void getForkEpoch_gloas() {
    final UInt64 expected = GLOAS_SPEC_CONFIG.getGloasForkEpoch();
    assertThat(SpecMilestone.getForkEpoch(GLOAS_SPEC_CONFIG, GLOAS)).contains(expected);
  }

  @Test
  public void getForkSlot_altairNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(PHASE0_SPEC_CONFIG, SpecMilestone.ALTAIR))
        .contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkSlot_bellatrixNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(PHASE0_SPEC_CONFIG, SpecMilestone.BELLATRIX))
        .contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkEpoch_capellaNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(BELLATRIX_SPEC_CONFIG, CAPELLA))
        .contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkEpoch_denebNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(CAPELLA_SPEC_CONFIG, DENEB)).contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkEpoch_electraNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(DENEB_SPEC_CONFIG, SpecMilestone.ELECTRA))
        .contains(UInt64.MAX_VALUE);
  }

  @Test
  public void getForkEpoch_fuluNotScheduled() {
    assertThat(SpecMilestone.getForkEpoch(ELECTRA_SPEC_CONFIG, FULU)).contains(UInt64.MAX_VALUE);
  }
}
