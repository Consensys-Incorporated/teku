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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.schemas.registry.SchemaRegistryBuilder;

public class ForkScheduleTest {
  private static final SpecConfig MINIMAL_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()).specConfig();

  // Set up config for a post-genesis altair transition
  private static final UInt64 FORK_EPOCH_ALTAIR = UInt64.valueOf(10);
  private static final UInt64 FORK_SLOT_ALTAIR =
      FORK_EPOCH_ALTAIR.times(MINIMAL_CONFIG.getSlotsPerEpoch());
  private static final SpecConfigAltair TRANSITION_CONFIG =
      SpecConfigAltair.required(
          SpecConfigLoader.loadConfig(
                  Eth2Network.MINIMAL.configName(), c -> c.altairForkEpoch(FORK_EPOCH_ALTAIR))
              .specConfig());

  // Set config starting altair at genesis
  private static final SpecConfigAltair ALTAIR_CONFIG =
      SpecConfigAltair.required(
          SpecConfigLoader.loadConfig(
                  Eth2Network.MINIMAL.configName(), c -> c.altairForkEpoch(UInt64.ZERO))
              .specConfig());

  // Set up default config
  private static final SpecConfig PHASE0_CONFIG =
      SpecConfigLoader.loadConfig(Eth2Network.MINIMAL.configName()).specConfig();

  // Fork versions
  static final Bytes4 PHASE_0_FORK_VERSION = TRANSITION_CONFIG.getGenesisForkVersion();
  static final Bytes4 ALTAIR_FORK_VERSION =
      TRANSITION_CONFIG.toVersionAltair().orElseThrow().getAltairForkVersion();
  static final Bytes4 UNKNOWN_FORK_VERSION = Bytes4.fromHexStringLenient("0xFFFFFFFF");

  final SchemaRegistryBuilder schemaRegistryBuilder = SchemaRegistryBuilder.create();

  @Test
  public void build_validScheduleWithAltairTransition() {
    final SpecVersion phase0 = SpecVersion.createPhase0(TRANSITION_CONFIG, schemaRegistryBuilder);
    final SpecVersion altair = SpecVersion.createAltair(TRANSITION_CONFIG, schemaRegistryBuilder);

    final ForkSchedule forkSchedule =
        ForkSchedule.builder().addNextMilestone(phase0).addNextMilestone(altair).build();

    assertThat(forkSchedule.size()).isEqualTo(2);
  }

  @Test
  public void build_validScheduleWithAltairAtGenesis_phase0AndAltairSupplied() {
    final SpecVersion phase0 = SpecVersion.createPhase0(ALTAIR_CONFIG, schemaRegistryBuilder);
    final SpecVersion altair = SpecVersion.createAltair(ALTAIR_CONFIG, schemaRegistryBuilder);

    final ForkSchedule forkSchedule =
        ForkSchedule.builder().addNextMilestone(phase0).addNextMilestone(altair).build();

    assertThat(forkSchedule.size()).isEqualTo(1);
    assertThat(forkSchedule.getSpecMilestoneAtEpoch(UInt64.ZERO)).isEqualTo(SpecMilestone.ALTAIR);
  }

  @Test
  public void build_validScheduleWithAltairAtGenesis_onlyAltairSupplied() {
    final SpecVersion altair = SpecVersion.createAltair(ALTAIR_CONFIG, schemaRegistryBuilder);

    final ForkSchedule forkSchedule = ForkSchedule.builder().addNextMilestone(altair).build();

    assertThat(forkSchedule.size()).isEqualTo(1);
    assertThat(forkSchedule.getSpecMilestoneAtEpoch(UInt64.ZERO)).isEqualTo(SpecMilestone.ALTAIR);
  }

  @Test
  public void build_validPhase0Schedule() {
    final SpecVersion phase0 = SpecVersion.createPhase0(PHASE0_CONFIG, schemaRegistryBuilder);

    final ForkSchedule forkSchedule = ForkSchedule.builder().addNextMilestone(phase0).build();

    assertThat(forkSchedule.size()).isEqualTo(1);
    assertThat(forkSchedule.getSpecMilestoneAtEpoch(UInt64.ZERO)).isEqualTo(SpecMilestone.PHASE0);
  }

  @Test
  public void builder_milestonesSuppliedOutOfOrder_altairProcessedAtNonZeroSlot() {
    final SpecVersion altair = SpecVersion.createAltair(TRANSITION_CONFIG, schemaRegistryBuilder);
    final ForkSchedule.Builder builder = ForkSchedule.builder();

    assertThatThrownBy(() -> builder.addNextMilestone(altair))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Must provide genesis milestone first");
  }

  @Test
  public void builder_milestonesSuppliedOutOfOrder_processAltairBeforePhase0() {
    final SpecVersion altair = SpecVersion.createAltair(ALTAIR_CONFIG, schemaRegistryBuilder);
    final SpecVersion phase0 =
        SpecVersion.createPhase0(ALTAIR_CONFIG, SchemaRegistryBuilder.create());
    final ForkSchedule.Builder builder = ForkSchedule.builder();

    builder.addNextMilestone(altair);
    assertThatThrownBy(() -> builder.addNextMilestone(phase0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Attempt to process milestones out of order");
  }

  @Test
  public void getSupportedMilestones_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);

    assertThat(forkSchedule.getSupportedMilestones())
        .containsExactly(SpecMilestone.PHASE0, SpecMilestone.ALTAIR);
  }

  @Test
  public void getSupportedMilestones_onlyAltairConfigured() {
    final SpecVersion altair = SpecVersion.createAltair(ALTAIR_CONFIG, schemaRegistryBuilder);

    final ForkSchedule forkSchedule = ForkSchedule.builder().addNextMilestone(altair).build();

    assertThat(forkSchedule.getSupportedMilestones())
        .containsExactly(SpecMilestone.PHASE0, SpecMilestone.ALTAIR);
  }

  @Test
  public void getSupportedMilestones_onlyPhase0Configured() {
    final SpecVersion phase0 = SpecVersion.createPhase0(PHASE0_CONFIG, schemaRegistryBuilder);

    final ForkSchedule forkSchedule = ForkSchedule.builder().addNextMilestone(phase0).build();

    assertThat(forkSchedule.getSupportedMilestones()).containsExactly(SpecMilestone.PHASE0);
  }

  @Test
  public void getActiveMilestones_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);
    final Fork phase0Fork = getPhase0Fork(TRANSITION_CONFIG);
    final Fork altairFork = getAltairFork(TRANSITION_CONFIG);

    assertThat(forkSchedule.getActiveMilestones())
        .containsExactly(
            new ForkAndSpecMilestone(phase0Fork, SpecMilestone.PHASE0),
            new ForkAndSpecMilestone(altairFork, SpecMilestone.ALTAIR));
  }

  @Test
  public void getActiveMilestones_onlyAltairConfigured() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);
    final Fork altairFork = getAltairFork(ALTAIR_CONFIG);

    assertThat(forkSchedule.getActiveMilestones())
        .containsExactly(new ForkAndSpecMilestone(altairFork, SpecMilestone.ALTAIR));

    assertThat(forkSchedule.getSpecMilestoneAtSlot(UInt64.ZERO)).isEqualTo(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getHighestSupportedMilestone()).isEqualTo(SpecMilestone.ALTAIR);
  }

  @Test
  public void getAllForks_shouldReturnCompleteForkList() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);
    final Fork phase0Fork = getPhase0Fork(PHASE0_CONFIG);
    final Fork altairFork = getAltairFork(ALTAIR_CONFIG);
    assertThat(forkSchedule.getFullForkList()).containsExactly(phase0Fork, altairFork);
  }

  @Test
  public void getFork_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);

    final Fork phase0Fork = getPhase0Fork(TRANSITION_CONFIG);
    for (UInt64 epoch = UInt64.ZERO;
        epoch.isLessThan(FORK_EPOCH_ALTAIR);
        epoch = epoch.increment()) {
      assertThat(forkSchedule.getFork(epoch)).isEqualTo(phase0Fork);
    }
    final Fork altairFork = getAltairFork(TRANSITION_CONFIG);
    for (UInt64 epoch = FORK_EPOCH_ALTAIR;
        epoch.isLessThan(FORK_EPOCH_ALTAIR.times(2));
        epoch = epoch.increment()) {
      assertThat(forkSchedule.getFork(epoch)).isEqualTo(altairFork);
    }
  }

  @Test
  public void getFork_altairOnly() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);

    final Fork altairFork = getAltairFork(ALTAIR_CONFIG);
    assertThat(forkSchedule.getFork(UInt64.ZERO)).isEqualTo(altairFork);
    assertThat(forkSchedule.getFork(UInt64.valueOf(10_000))).isEqualTo(altairFork);
    assertThat(forkSchedule.getFork(UInt64.MAX_VALUE)).isEqualTo(altairFork);
  }

  @Test
  public void getNextFork_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);

    final Fork altairFork = getAltairFork(TRANSITION_CONFIG);
    for (UInt64 epoch = UInt64.ZERO;
        epoch.isLessThan(FORK_EPOCH_ALTAIR);
        epoch = epoch.increment()) {
      assertThat(forkSchedule.getNextFork(epoch)).contains(altairFork);
    }
    for (UInt64 epoch = FORK_EPOCH_ALTAIR;
        epoch.isLessThan(FORK_EPOCH_ALTAIR.times(2));
        epoch = epoch.increment()) {
      assertThat(forkSchedule.getNextFork(epoch)).isEmpty();
    }
  }

  @Test
  public void getNextFork_altairOnly() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);

    assertThat(forkSchedule.getNextFork(UInt64.ZERO)).isEmpty();
    assertThat(forkSchedule.getNextFork(UInt64.valueOf(10_000))).isEmpty();
    assertThat(forkSchedule.getNextFork(UInt64.MAX_VALUE.minus(1))).isEmpty();
  }

  @Test
  public void getForks_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);

    final Fork phase0Fork = getPhase0Fork(TRANSITION_CONFIG);
    final Fork altairFork = getAltairFork(TRANSITION_CONFIG);
    assertThat(forkSchedule.getForks()).containsExactly(phase0Fork, altairFork);
  }

  @Test
  public void getForks_altairOnly() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);

    final Fork altairFork = getAltairFork(ALTAIR_CONFIG);
    assertThat(forkSchedule.getForks()).containsExactly(altairFork);
  }

  @Test
  public void getSpecMilestoneAtEpoch_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);

    for (UInt64 epoch = UInt64.ZERO;
        epoch.isLessThan(FORK_EPOCH_ALTAIR);
        epoch = epoch.increment()) {
      assertThat(forkSchedule.getSpecMilestoneAtEpoch(epoch)).isEqualTo(SpecMilestone.PHASE0);
    }
    for (UInt64 epoch = FORK_EPOCH_ALTAIR;
        epoch.isLessThan(FORK_EPOCH_ALTAIR.times(2));
        epoch = epoch.increment()) {
      assertThat(forkSchedule.getSpecMilestoneAtEpoch(epoch)).isEqualTo(SpecMilestone.ALTAIR);
    }
  }

  @Test
  public void getSpecMilestoneAtEpoch_altairOnly() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);

    assertThat(forkSchedule.getSpecMilestoneAtEpoch(UInt64.ZERO)).isEqualTo(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtEpoch(UInt64.valueOf(10_000)))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtEpoch(UInt64.MAX_VALUE))
        .isEqualTo(SpecMilestone.ALTAIR);
  }

  @Test
  public void getSpecMilestoneAtSlot_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);

    for (UInt64 slot = UInt64.ZERO; slot.isLessThan(FORK_SLOT_ALTAIR); slot = slot.increment()) {
      assertThat(forkSchedule.getSpecMilestoneAtSlot(slot)).isEqualTo(SpecMilestone.PHASE0);
    }
    for (UInt64 slot = FORK_SLOT_ALTAIR;
        slot.isLessThan(FORK_SLOT_ALTAIR.times(2));
        slot = slot.increment()) {
      assertThat(forkSchedule.getSpecMilestoneAtSlot(slot)).isEqualTo(SpecMilestone.ALTAIR);
    }
  }

  @Test
  public void getSpecMilestoneAtSlot_altairOnly() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);

    assertThat(forkSchedule.getSpecMilestoneAtSlot(UInt64.ZERO)).isEqualTo(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtSlot(UInt64.valueOf(10_000)))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtSlot(UInt64.MAX_VALUE))
        .isEqualTo(SpecMilestone.ALTAIR);
  }

  @Test
  public void getSpecMilestoneAtTime_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);

    final UInt64 altairGenesisOffset =
        FORK_SLOT_ALTAIR.times(TRANSITION_CONFIG.getSecondsPerSlot());

    // Pre-genesis
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.valueOf(10_000), UInt64.ZERO))
        .isEqualTo(SpecMilestone.PHASE0);
    // At genesis time
    UInt64 genesisOffset = UInt64.ZERO;
    assertThat(forkSchedule.getSpecMilestoneAtTime(genesisOffset, genesisOffset))
        .isEqualTo(SpecMilestone.PHASE0);
    assertThat(
            forkSchedule.getSpecMilestoneAtTime(genesisOffset.plus(1000), genesisOffset.plus(1000)))
        .isEqualTo(SpecMilestone.PHASE0);
    // Post-genesis, before altair
    genesisOffset = altairGenesisOffset.dividedBy(2);
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.ZERO, genesisOffset))
        .isEqualTo(SpecMilestone.PHASE0);
    assertThat(forkSchedule.getSpecMilestoneAtTime(genesisOffset.times(2), genesisOffset.times(3)))
        .isEqualTo(SpecMilestone.PHASE0);
    // Just before altair activates
    genesisOffset = altairGenesisOffset.minus(1);
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.ZERO, genesisOffset))
        .isEqualTo(SpecMilestone.PHASE0);
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.valueOf(2), genesisOffset.plus(2)))
        .isEqualTo(SpecMilestone.PHASE0);
    // At altair start
    genesisOffset = altairGenesisOffset;
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.ZERO, genesisOffset))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.valueOf(2), genesisOffset.plus(2)))
        .isEqualTo(SpecMilestone.ALTAIR);
    // Post altair
    genesisOffset = altairGenesisOffset.plus(10_000);
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.ZERO, genesisOffset))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.valueOf(200), genesisOffset.plus(200)))
        .isEqualTo(SpecMilestone.ALTAIR);
  }

  @Test
  public void getSpecMilestoneAtTime_altairOnly() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);

    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.ZERO, UInt64.ZERO))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.ZERO, UInt64.valueOf(10_000)))
        .isEqualTo(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtTime(UInt64.ZERO, UInt64.MAX_VALUE))
        .isEqualTo(SpecMilestone.ALTAIR);
  }

  @Test
  public void getSpecMilestoneAtForkVersion_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);

    assertThat(forkSchedule.getSpecMilestoneAtForkVersion(PHASE_0_FORK_VERSION))
        .contains(SpecMilestone.PHASE0);
    assertThat(forkSchedule.getSpecMilestoneAtForkVersion(ALTAIR_FORK_VERSION))
        .contains(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtForkVersion(UNKNOWN_FORK_VERSION)).isEmpty();
  }

  @Test
  public void getSpecMilestoneAtForkVersion_altairOnly() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);

    assertThat(forkSchedule.getSpecMilestoneAtForkVersion(PHASE_0_FORK_VERSION)).isEmpty();
    assertThat(forkSchedule.getSpecMilestoneAtForkVersion(ALTAIR_FORK_VERSION))
        .contains(SpecMilestone.ALTAIR);
    assertThat(forkSchedule.getSpecMilestoneAtForkVersion(UNKNOWN_FORK_VERSION)).isEmpty();
  }

  @Test
  public void getGenesisFork_altairOnly() {
    final ForkSchedule forkSchedule = buildForkSchedule(ALTAIR_CONFIG);

    assertThat(forkSchedule.getGenesisFork())
        .isEqualTo(new Fork(ALTAIR_FORK_VERSION, ALTAIR_FORK_VERSION, UInt64.ZERO));
  }

  @Test
  public void getGenesisFork_withTransition() {
    final ForkSchedule forkSchedule = buildForkSchedule(TRANSITION_CONFIG);

    assertThat(forkSchedule.getGenesisFork())
        .isEqualTo(new Fork(PHASE_0_FORK_VERSION, PHASE_0_FORK_VERSION, UInt64.ZERO));
  }

  private ForkSchedule buildForkSchedule(final SpecConfig specConfig) {
    final ForkSchedule.Builder builder = ForkSchedule.builder();
    builder.addNextMilestone(SpecVersion.createPhase0(specConfig, schemaRegistryBuilder));
    specConfig
        .toVersionAltair()
        .ifPresent(
            a -> builder.addNextMilestone(SpecVersion.createAltair(a, schemaRegistryBuilder)));

    return builder.build();
  }

  private Fork getAltairFork(final SpecConfigAltair config) {
    final UInt64 forkEpoch = config.getAltairForkEpoch();
    return new Fork(config.getGenesisForkVersion(), config.getAltairForkVersion(), forkEpoch);
  }

  private Fork getPhase0Fork(final SpecConfig config) {
    return new Fork(config.getGenesisForkVersion(), config.getGenesisForkVersion(), UInt64.ZERO);
  }
}
