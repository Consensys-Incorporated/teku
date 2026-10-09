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

package tech.pegasys.teku.spec.logic.versions.heze.util;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.forkchoice.InclusionListStore;
import tech.pegasys.teku.spec.logic.versions.gloas.block.BlockProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateMutatorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.statetransition.epoch.EpochProcessorGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.AttestationUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.util.ForkChoiceUtilGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.withdrawals.WithdrawalsHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.heze.helpers.BeaconStateAccessorsHeze;

public class ForkChoiceUtilHeze extends ForkChoiceUtilGloas {

  public ForkChoiceUtilHeze(
      final SpecConfigHeze specConfig,
      final BeaconStateAccessorsHeze beaconStateAccessors,
      final BeaconStateMutatorsGloas beaconStateMutators,
      final EpochProcessorGloas epochProcessor,
      final AttestationUtilGloas attestationUtil,
      final MiscHelpersGloas miscHelpers,
      final WithdrawalsHelpersGloas withdrawalsHelpers,
      final BlockProcessorGloas blockProcessor) {
    super(
        specConfig,
        beaconStateAccessors,
        beaconStateMutators,
        epochProcessor,
        attestationUtil,
        miscHelpers,
        withdrawalsHelpers,
        blockProcessor);
  }

  @Override
  public Optional<Integer> getInclusionListDueMillis() {
    final SpecConfigHeze configHeze = SpecConfigHeze.required(specConfig);
    return Optional.of(getSlotComponentDurationMillis(configHeze.getInclusionListDueBps()));
  }

  @Override
  public Optional<List<InclusionList>> getInclusionListsForPayloadValidation(
      final InclusionListStore inclusionListStore, final SlotAndBlockRoot inclusionListKey) {
    return Optional.of(inclusionListStore.getTimelyInclusionLists(inclusionListKey));
  }
}
