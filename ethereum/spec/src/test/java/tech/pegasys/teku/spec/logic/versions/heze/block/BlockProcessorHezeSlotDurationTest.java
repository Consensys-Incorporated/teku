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

package tech.pegasys.teku.spec.logic.versions.heze.block;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.PROPOSER_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.TIMELY_SOURCE_WEIGHT;
import static tech.pegasys.teku.spec.constants.IncentivizationWeights.WEIGHT_DENOMINATOR;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.logic.versions.altair.block.BlockProcessorAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

/**
 * EIP-8198: the proposer reward for an attestation is priced at its target epoch. Minimal preset
 * with 6000 ms slots and Heze at epoch 2 switching to 5000 ms slots, so an attestation included in
 * the first Heze epoch for a target in the previous epoch is priced at 6000 ms slots.
 */
class BlockProcessorHezeSlotDurationTest {

  private final Spec spec =
      TestSpecFactory.createMinimalHeze(
          builder -> builder.hezeForkEpoch(UInt64.valueOf(2)).slotDurationMsHeze(5000));
  private final SpecVersion hezeSpec = spec.forMilestone(SpecMilestone.HEZE);
  private final BlockProcessorAltair blockProcessor =
      BlockProcessorAltair.required(hezeSpec.getBlockProcessor());
  private final BeaconStateAccessorsAltair beaconStateAccessors =
      BeaconStateAccessorsAltair.required(hezeSpec.beaconStateAccessors());
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void processAttestation_shouldPriceProposerRewardAtTheTargetEpoch() {
    final UInt64 previousEpoch = UInt64.ONE;
    final BeaconState preState =
        dataStructureUtil.randomBeaconState().updated(state -> state.setSlot(UInt64.valueOf(17)));
    final List<UInt64> attesters = List.of(UInt64.ZERO, UInt64.ONE);
    // only a timely source flag: the source matches and the inclusion delay is 2 slots
    final AttestationData data =
        new AttestationData(
            UInt64.valueOf(15),
            UInt64.ZERO,
            dataStructureUtil.randomBytes32(),
            preState.getPreviousJustifiedCheckpoint(),
            new Checkpoint(previousEpoch, dataStructureUtil.randomBytes32()));
    final Attestation attestation = dataStructureUtil.randomAttestation(data);

    final UInt64 expectedReward =
        proposerReward(preState, attesters, previousEpoch, TIMELY_SOURCE_WEIGHT);
    // sanity check that the epochs are priced differently
    assertThat(expectedReward)
        .isNotEqualTo(
            proposerReward(
                preState,
                attesters,
                beaconStateAccessors.getCurrentEpoch(preState),
                TIMELY_SOURCE_WEIGHT));

    final AtomicReference<Optional<UInt64>> actualReward = new AtomicReference<>();
    preState.updated(
        mutableState -> {
          final MutableBeaconStateAltair state = MutableBeaconStateAltair.required(mutableState);
          attesters.forEach(
              index -> state.getPreviousEpochParticipation().set(index.intValue(), SszByte.ZERO));
          actualReward.set(
              blockProcessor
                  .processAttestation(
                      state,
                      attestation,
                      __ ->
                          new IndexedAttestationLight(
                              attesters, data, attestation.getAggregateSignature()))
                  .proposerReward());
        });

    assertThat(actualReward.get()).contains(expectedReward);
  }

  private UInt64 proposerReward(
      final BeaconState state,
      final List<UInt64> attesters,
      final UInt64 epoch,
      final UInt64 weight) {
    final UInt64 numerator =
        attesters.stream()
            .map(index -> beaconStateAccessors.getBaseReward(state, index.intValue(), epoch))
            .map(baseReward -> baseReward.times(weight))
            .reduce(UInt64.ZERO, UInt64::plus);
    return numerator.dividedBy(
        WEIGHT_DENOMINATOR
            .minus(PROPOSER_WEIGHT)
            .times(WEIGHT_DENOMINATOR)
            .dividedBy(PROPOSER_WEIGHT));
  }
}
