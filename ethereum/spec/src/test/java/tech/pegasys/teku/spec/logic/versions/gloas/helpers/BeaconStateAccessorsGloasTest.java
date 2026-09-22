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

package tech.pegasys.teku.spec.logic.versions.gloas.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestation;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationSchema;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.IndexedPayloadAttestationLight;
import tech.pegasys.teku.spec.datastructures.state.BeaconStateTestBuilder;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconStateCache;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BeaconStateAccessorsGloasTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final BeaconStateAccessorsGloas beaconStateAccessors =
      BeaconStateAccessorsGloas.required(spec.getGenesisSpec().beaconStateAccessors());

  @Test
  void getBuilderIndex_shouldReturnBuilderIndex() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final SszList<Builder> builders = state.getBuilders();
    assertThat(builders).isNotEmpty();
    for (int i = 0; i < builders.size(); i++) {
      final Builder builder = builders.get(i);
      assertThat(beaconStateAccessors.getBuilderIndex(state, builder.getPublicKey())).contains(i);
    }
  }

  @Test
  public void getBuilderIndex_shouldReturnEmptyWhenBuilderNotFound() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Optional<Integer> index =
        beaconStateAccessors.getBuilderIndex(state, dataStructureUtil.randomPublicKey());
    assertThat(index).isEmpty();
  }

  @Test
  public void getBuilderPubKey_shouldReturnBuilderPubKey() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final SszList<Builder> builders = state.getBuilders();
    assertThat(builders).isNotEmpty();
    for (int i = 0; i < builders.size(); i++) {
      final Builder builder = builders.get(i);
      final UInt64 builderIndex = UInt64.valueOf(i);
      assertThat(beaconStateAccessors.getBuilderPubKey(state, builderIndex))
          .contains(builder.getPublicKey());
      // pubKey => builderIndex mapping is pre cached
      assertThat(
              BeaconStateCache.getTransitionCaches(state)
                  .getBuildersPubKeys()
                  .getCached(builderIndex))
          .isPresent();
    }
  }

  @Test
  public void getBuilderPubKey_shouldReturnEmptyWhenBuilderNotExisting() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final Optional<BLSPublicKey> index =
        beaconStateAccessors.getBuilderPubKey(state, UInt64.valueOf(999));
    assertThat(index).isEmpty();
  }

  @Test
  void computeIsMatchingHead_shouldRejectNonZeroIndexForSameSlotWhenTargetDoesNotMatch() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final AttestationData data =
        new AttestationData(
            UInt64.ZERO,
            UInt64.ONE,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomCheckpoint(),
            dataStructureUtil.randomCheckpoint());

    assertThatThrownBy(
            () ->
                beaconStateAccessors.computeIsMatchingHead(false, false, data, state, UInt64.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Index must be set to zero");
  }

  // EIP-8061 churn limit coverage --------------------------------------------------------------

  @Test
  public void churnLimits_shouldFloorAtMinPerEpochChurnLimitWhenActiveBalanceIsLow() {
    // 8 active validators × 32 ETH = 256 ETH active balance. Even / 16 (16 ETH) is below the
    // MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA floor for activations/exits
    final BeaconStateElectra state = activeStateWithValidators(8);
    final SpecConfigGloas config = configGloas();
    final UInt64 floor = config.getMinPerEpochChurnLimitElectra();

    assertThat(beaconStateAccessors.getActivationChurnLimit(state)).isEqualTo(floor);
    assertThat(beaconStateAccessors.getExitChurnLimit(state)).isEqualTo(floor);
    // no floor applied for consolidation churn limit, it is derived independently
    assertThat(beaconStateAccessors.getConsolidationChurnLimit(state)).isLessThan(floor);
  }

  @Test
  public void getActivationChurnLimit_shouldCapAtMaxPerEpochActivationChurnLimitGloas() {
    // 65 validators × 32 ETH = 2080 ETH. active/CHURN_LIMIT_QUOTIENT_GLOAS(=16) = 130 ETH which
    // exceeds the configured cap of 128 ETH, so activation churn is clamped to the cap.
    final BeaconStateElectra state = activeStateWithValidators(65);
    final SpecConfigGloas config = configGloas();

    assertThat(beaconStateAccessors.getActivationChurnLimit(state))
        .isEqualTo(config.getMaxPerEpochActivationChurnLimitGloas());
  }

  @Test
  public void getExitChurnLimit_shouldNotCapAtMaxPerEpochActivationChurnLimitGloas() {
    // Same 65×32 ETH state: exit churn is uncapped, so we get the raw active/quotient value
    // (130 ETH) which is above the activation cap and above the floor.
    final BeaconStateElectra state = activeStateWithValidators(65);
    final SpecConfigGloas config = configGloas();
    final UInt64 expected =
        UInt64.valueOf(65L * 32L).times(UInt64.valueOf(1_000_000_000L)).dividedBy(16L);

    final UInt64 exitChurn = beaconStateAccessors.getExitChurnLimit(state);
    assertThat(exitChurn).isEqualTo(expected);
    assertThat(exitChurn).isGreaterThan(config.getMaxPerEpochActivationChurnLimitGloas());
    assertThat(exitChurn).isGreaterThan(beaconStateAccessors.getActivationChurnLimit(state));
  }

  @Test
  public void getConsolidationChurnLimit_shouldUseIndependentQuotient() {
    // 130 validators × 32 ETH = 4160 ETH. active/CONSOLIDATION_CHURN_LIMIT_QUOTIENT(=32) = 130 ETH
    // which is above the floor; active/CHURN_LIMIT_QUOTIENT_GLOAS(=16) = 260 ETH. The two helpers
    // must produce different values demonstrating the split quotient introduced in EIP-8061.
    final BeaconStateElectra state = activeStateWithValidators(130);
    final UInt64 oneEth = UInt64.valueOf(1_000_000_000L);
    final UInt64 expectedConsolidation = UInt64.valueOf(130L * 32L).times(oneEth).dividedBy(32L);
    final UInt64 expectedExit = UInt64.valueOf(130L * 32L).times(oneEth).dividedBy(16L);

    assertThat(beaconStateAccessors.getConsolidationChurnLimit(state))
        .isEqualTo(expectedConsolidation);
    assertThat(beaconStateAccessors.getExitChurnLimit(state)).isEqualTo(expectedExit);
    assertThat(beaconStateAccessors.getConsolidationChurnLimit(state))
        .isNotEqualTo(beaconStateAccessors.getExitChurnLimit(state));
  }

  @Test
  public void getIndexedPayloadAttestation_selectsPtcMembersForSetBitsAndSortsIndices() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final UInt64 slot = state.getSlot();
    final IntList ptc = beaconStateAccessors.getPtc(state, slot);

    // Set the first three PTC positions; their validator indices are not necessarily ascending, so
    // this also exercises the sort.
    final PayloadAttestation payloadAttestation = payloadAttestation(slot, 0, 1, 2);

    final IndexedPayloadAttestationLight indexed =
        beaconStateAccessors.getIndexedPayloadAttestation(state, payloadAttestation);

    final List<UInt64> expected =
        IntStream.of(ptc.getInt(0), ptc.getInt(1), ptc.getInt(2))
            .mapToObj(UInt64::valueOf)
            .sorted()
            .toList();
    assertThat(indexed.attestingIndices()).containsExactlyElementsOf(expected);
    assertThat(indexed.attestingIndices()).isSorted();
    assertThat(indexed.data()).isEqualTo(payloadAttestation.getData());
    assertThat(indexed.signature()).isEqualTo(payloadAttestation.getSignature());
  }

  @Test
  public void getIndexedPayloadAttestation_returnsEmptyIndicesWhenNoBitsSet() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final PayloadAttestation payloadAttestation = payloadAttestation(state.getSlot());

    final IndexedPayloadAttestationLight indexed =
        beaconStateAccessors.getIndexedPayloadAttestation(state, payloadAttestation);

    assertThat(indexed.attestingIndices()).isEmpty();
  }

  @Test
  public void getPtc_throwsForSlotBeforeGloasFork() {
    final UInt64 forkEpoch = UInt64.valueOf(10);
    final UInt64 forkSlot = spec.computeStartSlotAtEpoch(forkEpoch);
    final BeaconState state = stateWithGloasFork(forkSlot, forkEpoch);
    final UInt64 preForkSlot = forkSlot.decrement();

    assertThatThrownBy(() -> beaconStateAccessors.getPtc(state, preForkSlot))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("before the Gloas fork epoch");
    assertThatThrownBy(
            () ->
                beaconStateAccessors.getIndexedPayloadAttestation(
                    state, payloadAttestation(preForkSlot, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void getPtc_allowsPreviousEpochSlotAtOrAfterGloasFork() {
    final UInt64 forkEpoch = UInt64.valueOf(10);
    final UInt64 stateSlot = spec.computeStartSlotAtEpoch(forkEpoch.increment());
    final BeaconState state = stateWithGloasFork(stateSlot, forkEpoch);
    final UInt64 previousEpochSlot = stateSlot.decrement();

    assertThat(beaconStateAccessors.getPtc(state, previousEpochSlot).isEmpty()).isFalse();
  }

  @Test
  public void getPtc_allowsSlotBeforeForkEpochWhenStateForkVersionIsNotGloas() {
    final UInt64 forkEpoch = UInt64.valueOf(10);
    final UInt64 forkSlot = spec.computeStartSlotAtEpoch(forkEpoch);
    final Fork fork =
        new Fork(
            spec.getGenesisSpecConfig().getElectraForkVersion(),
            spec.getGenesisSpecConfig().getFuluForkVersion(),
            forkEpoch);
    final BeaconState state =
        dataStructureUtil.randomBeaconState(forkSlot).updated(s -> s.setFork(fork));

    assertThat(beaconStateAccessors.getPtc(state, forkSlot.decrement()).isEmpty()).isFalse();
  }

  private BeaconState stateWithGloasFork(final UInt64 slot, final UInt64 forkEpoch) {
    final Fork fork =
        new Fork(
            spec.getGenesisSpecConfig().getFuluForkVersion(),
            spec.getGenesisSpecConfig().getGloasForkVersion(),
            forkEpoch);
    return dataStructureUtil.randomBeaconState(slot).updated(s -> s.setFork(fork));
  }

  private PayloadAttestation payloadAttestation(final UInt64 slot, final int... setBits) {
    final PayloadAttestationSchema schema =
        SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
            .getPayloadAttestationSchema();
    final PayloadAttestationData data = dataStructureUtil.randomPayloadAttestationData(slot);
    return schema.create(
        schema.getAggregationBitsSchema().ofBits(setBits),
        data,
        dataStructureUtil.randomSignature());
  }

  private SpecConfigGloas configGloas() {
    return SpecConfigGloas.required(spec.getGenesisSpecConfig());
  }

  private BeaconStateElectra activeStateWithValidators(final int validatorCount) {
    final UInt64 thirtyTwoEth = UInt64.THIRTY_TWO_ETH;
    final BeaconStateTestBuilder builder = new BeaconStateTestBuilder(dataStructureUtil).slot(0);
    IntStream.range(0, validatorCount).forEach(__ -> builder.activeValidator(thirtyTwoEth));
    return BeaconStateElectra.required(builder.build());
  }
}
