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

package tech.pegasys.teku.ethereum.executionlayer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;

public class BuilderCircuitBreakerImplTest {
  private static final int INSPECTION_WINDOW = 10;
  private static final int ALLOWED_FAULTS = 5;
  private static final int ALLOWED_CONSECUTIVE_FAULTS = 2;

  private final Spec spec =
      TestSpecFactory.createMinimalBellatrix(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));

  private final BuilderCircuitBreakerImpl builderCircuitBreaker =
      new BuilderCircuitBreakerImpl(
          spec, INSPECTION_WINDOW, ALLOWED_FAULTS, ALLOWED_CONSECUTIVE_FAULTS);

  private final ChainBuilder chainBuilder = ChainBuilder.create(spec);

  @BeforeEach
  void setUp() {
    // all tests assume 64 block roots history
    assertThat(spec.getGenesisSpec().getSlotsPerHistoricalRoot()).isEqualTo(64);

    chainBuilder.generateGenesis();
  }

  @Test
  void shouldNotEngage_noMissedSlots() throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(32);

    final BeaconState preState = advance(31, false);

    final BeaconState state = spec.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isFalse();
    assertCounters(state, 10, 0);
  }

  @Test
  void shouldNotEngage_minimalAllowedFaults()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // window from 59 to 68 (10 slots)

    // no missing slot up to 61 (unique count: 3)
    advance(61, false);

    // 62, 63 and 64 missing, 65 with block (unique count: 3 + 1)
    advance(65, true);

    // 66, 67 missing, 68 with block (unique count: 3 + 1 + 1)
    final BeaconState preState = advance(68, true);

    // generate state for the building slot
    final BeaconState state = spec.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isFalse();
    assertCounters(state, 5, 0);
  }

  @Test
  void shouldNotEngage_minimalConsecutiveAllowedFaults()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // window from 59 to 68 (10 slots)

    // no missing slot up to 66 (unique count: 8)
    final BeaconState preState = advance(66, false);

    // generate state for the building slot
    final BeaconState state = spec.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isFalse();
    assertCounters(state, 8, 2);
  }

  @Test
  void shouldEngage_belowAllowedFaultsWithLastSlotNotEmpty()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // window from 59 to 68 (10 slots)

    // no missing slot up to 61 (unique count: 3)
    advance(61, false);

    // 62, 63, 64, 65 66, 67 missing, 68 with block (unique count: 3 + 1)
    final BeaconState preState = advance(68, true);

    // generate state for the building slot
    final BeaconState state = spec.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertCounters(state, 4, 0);
  }

  @Test
  void shouldEngage_belowAllowedFaultsWithLastSlotEmpty()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // window from 59 to 68 (10 slots)

    // no missing slot up to 61 (unique count: 3)
    advance(61, false);

    // 62, 63, 64, 65 66, 67 with block (unique count: 3 + 1)
    final BeaconState preState = advance(67, true);

    // generate state for the building slot
    // last block header will be from slot 67
    final BeaconState state = spec.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertCounters(state, 4, 1);
  }

  @Test
  void shouldEngage_belowAllowedFaultsWithTooManyLastSlotsEmpty()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // window from 59 to 68 (10 slots)

    // no missing slot up to 63 (unique count: 5)
    advance(63, false);

    // 64, 65 with block (unique count: 5 + 1)
    final BeaconState preState = advance(65, true);

    // generate state for the building slot
    // last block header will be from slot 65
    final BeaconState state = spec.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertCounters(state, 6, 3);
  }

  @Test
  void shouldEngage_belowAllowedFaults_lastBlockHeaderWellOff()
      throws SlotProcessingException, EpochProcessingException {
    final UInt64 blockBuildingSlot = UInt64.valueOf(69);

    // build up to 58
    final BeaconState preState = advance(58, false);

    final BeaconState state = spec.processSlots(preState, blockBuildingSlot);

    assertThat(builderCircuitBreaker.isEngaged(state)).isTrue();
    assertCounters(state, 0, 10);
  }

  private BeaconState advance(final long toSlot, final boolean missing) {
    if (missing) {
      chainBuilder.generateBlockAtSlot(toSlot);
    } else {
      chainBuilder.generateBlocksUpToSlot(toSlot);
    }
    return chainBuilder.getLatestBlockAndState().getState();
  }

  private void assertCounters(
      final BeaconState state,
      final int uniqueBlockRootsCount,
      final int lastConsecutiveIdenticalBlockRoots) {
    assertThat(builderCircuitBreaker.getInspectionWindowCounters(state))
        .matches(
            counters -> counters.uniqueBlockRootsCount == uniqueBlockRootsCount,
            "uniqueBlockRootsCount must match")
        .matches(
            counters -> counters.lastConsecutiveEmptySlots == lastConsecutiveIdenticalBlockRoots,
            "lastConsecutiveIdenticalBlockRoots must match");
  }
}
