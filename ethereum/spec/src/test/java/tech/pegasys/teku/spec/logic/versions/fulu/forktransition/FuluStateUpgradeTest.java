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

package tech.pegasys.teku.spec.logic.versions.fulu.forktransition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BeaconStateAccessorsFulu;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class FuluStateUpgradeTest {

  private static final UInt64 FULU_EPOCH = UInt64.valueOf(2L);
  private final Spec spec =
      TestSpecFactory.createMinimalWithCapellaDenebElectraAndFuluForkEpoch(
          UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, FULU_EPOCH);
  private final SpecVersion fuluSpecVersion = spec.atEpoch(UInt64.valueOf(2));
  private final PredicatesElectra predicatesElectra =
      new PredicatesElectra(spec.getGenesisSpecConfig());
  final SchemaDefinitionsFulu schemaDefinitionsFulu =
      SchemaDefinitionsFulu.required(fuluSpecVersion.getSchemaDefinitions());
  private final MiscHelpersFulu miscHelpersFulu =
      new MiscHelpersFulu(
          fuluSpecVersion.getConfig().toVersionFulu().orElseThrow(),
          predicatesElectra,
          schemaDefinitionsFulu);
  final BeaconStateAccessorsFulu stateAccessorsFulu =
      new BeaconStateAccessorsFulu(fuluSpecVersion.getConfig(), predicatesElectra, miscHelpersFulu);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldCopyElectraState() {
    final BeaconStateElectra pre =
        dataStructureUtil
            .randomBeaconStateWithActiveValidators(64, UInt64.ONE)
            .toVersionElectra()
            .orElseThrow();

    final FuluStateUpgrade upgrade =
        new FuluStateUpgrade(
            SpecConfigFulu.required(fuluSpecVersion.getConfig()),
            schemaDefinitionsFulu,
            stateAccessorsFulu,
            miscHelpersFulu);

    final BeaconStateFulu post = upgrade.upgrade(pre);

    // Verify all Electra-specific fields are copied
    assertThat(post.getDepositRequestsStartIndex()).isEqualTo(pre.getDepositRequestsStartIndex());
    assertThat(post.getDepositBalanceToConsume()).isEqualTo(pre.getDepositBalanceToConsume());
    assertThat(post.getExitBalanceToConsume()).isEqualTo(pre.getExitBalanceToConsume());
    assertThat(post.getEarliestExitEpoch()).isEqualTo(pre.getEarliestExitEpoch());
    assertThat(post.getConsolidationBalanceToConsume())
        .isEqualTo(pre.getConsolidationBalanceToConsume());
    assertThat(post.getEarliestConsolidationEpoch()).isEqualTo(pre.getEarliestConsolidationEpoch());
    assertThat(post.getPendingDeposits()).isEqualTo(pre.getPendingDeposits());
    assertThat(post.getPendingPartialWithdrawals()).isEqualTo(pre.getPendingPartialWithdrawals());
    assertThat(post.getPendingConsolidations()).isEqualTo(pre.getPendingConsolidations());

    // Verify some common fields from parent states are also copied
    assertThat(post.getSlot()).isEqualTo(pre.getSlot());
    assertThat(post.getGenesisTime()).isEqualTo(pre.getGenesisTime());
    assertThat(post.getGenesisValidatorsRoot()).isEqualTo(pre.getGenesisValidatorsRoot());
    assertThat(post.getFork()).isNotEqualTo(pre.getFork()); // Fork should change
    assertThat(post.getFork().getPreviousVersion()).isEqualTo(pre.getFork().getCurrentVersion());
    assertThat(post.getFork().getCurrentVersion())
        .isEqualTo(fuluSpecVersion.getConfig().toVersionFulu().orElseThrow().getFuluForkVersion());
    assertThat(post.getLatestBlockHeader()).isEqualTo(pre.getLatestBlockHeader());
    assertThat(post.getBlockRoots()).isEqualTo(pre.getBlockRoots());
    assertThat(post.getStateRoots()).isEqualTo(pre.getStateRoots());
    assertThat(post.getEth1Data()).isEqualTo(pre.getEth1Data());
    assertThat(post.getEth1DataVotes()).isEqualTo(pre.getEth1DataVotes());
    assertThat(post.getEth1DepositIndex()).isEqualTo(pre.getEth1DepositIndex());
    assertThat(post.getValidators()).isEqualTo(pre.getValidators());
    assertThat(post.getBalances()).isEqualTo(pre.getBalances());
    assertThat(post.getRandaoMixes()).isEqualTo(pre.getRandaoMixes());
    assertThat(post.getSlashings()).isEqualTo(pre.getSlashings());
    assertThat(post.getJustificationBits()).isEqualTo(pre.getJustificationBits());
    assertThat(post.getPreviousJustifiedCheckpoint())
        .isEqualTo(pre.getPreviousJustifiedCheckpoint());
    assertThat(post.getCurrentJustifiedCheckpoint()).isEqualTo(pre.getCurrentJustifiedCheckpoint());
    assertThat(post.getFinalizedCheckpoint()).isEqualTo(pre.getFinalizedCheckpoint());
  }

  @Test
  void canUpgradeFromElectra() {
    final BeaconStateElectra pre =
        dataStructureUtil
            .randomBeaconStateWithActiveValidators(64, UInt64.ONE)
            .toVersionElectra()
            .orElseThrow();

    final FuluStateUpgrade upgrade =
        new FuluStateUpgrade(
            SpecConfigFulu.required(fuluSpecVersion.getConfig()),
            schemaDefinitionsFulu,
            stateAccessorsFulu,
            miscHelpersFulu);

    final BeaconStateFulu post = upgrade.upgrade(pre);

    final List<UInt64> expectedProposers =
        miscHelpersFulu.initializeProposerLookahead(pre, stateAccessorsFulu);
    assertThat(post.getProposerLookahead().asListUnboxed()).isEqualTo(expectedProposers);
  }
}
