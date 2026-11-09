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

package tech.pegasys.teku.spec.logic;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.interop.MockStartDepositGenerator;
import tech.pegasys.teku.spec.datastructures.interop.MockStartValidatorKeyPairFactory;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix.BeaconStateBellatrix;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.BeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb.BeaconStateDeneb;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.datastructures.util.DepositGenerator;

@TestSpecContext(
    milestone = {
      SpecMilestone.ALTAIR,
      SpecMilestone.BELLATRIX,
      SpecMilestone.CAPELLA,
      SpecMilestone.DENEB,
      SpecMilestone.ELECTRA,
      SpecMilestone.FULU,
      SpecMilestone.GLOAS,
      SpecMilestone.HEZE
    },
    doNotGenerateSpec = true)
public class StateUpgradeTransitionTest {
  private static final List<BLSKeyPair> VALIDATOR_KEYS =
      Collections.unmodifiableList(new MockStartValidatorKeyPairFactory().generateKeyPairs(0, 8));

  private final UInt64 milestoneTransitionEpoch = UInt64.ONE;
  private UInt64 milestoneTransitionSlot;

  private BeaconState genesis;
  private StateTransition stateTransition;

  private Class<? extends BeaconState> beforeBeaconStateClass;
  private Class<? extends BeaconState> afterBeaconStateClass;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    final Spec spec =
        switch (specContext.getSpecMilestone()) {
          case PHASE0 -> throw new IllegalArgumentException("Phase0 is an unsupported milestone");
          case ALTAIR -> {
            beforeBeaconStateClass = BeaconStatePhase0.class;
            afterBeaconStateClass = BeaconStateAltair.class;
            yield TestSpecFactory.createMinimalWithAltairForkEpoch(milestoneTransitionEpoch);
          }
          case BELLATRIX -> {
            beforeBeaconStateClass = BeaconStateAltair.class;
            afterBeaconStateClass = BeaconStateBellatrix.class;
            yield TestSpecFactory.createMinimalWithBellatrixForkEpoch(milestoneTransitionEpoch);
          }
          case CAPELLA -> {
            beforeBeaconStateClass = BeaconStateBellatrix.class;
            afterBeaconStateClass = BeaconStateCapella.class;
            yield TestSpecFactory.createMinimalWithCapellaForkEpoch(milestoneTransitionEpoch);
          }
          case DENEB -> {
            beforeBeaconStateClass = BeaconStateCapella.class;
            afterBeaconStateClass = BeaconStateDeneb.class;
            yield TestSpecFactory.createMinimalWithDenebForkEpoch(milestoneTransitionEpoch);
          }
          case ELECTRA -> {
            beforeBeaconStateClass = BeaconStateDeneb.class;
            afterBeaconStateClass = BeaconStateElectra.class;
            yield TestSpecFactory.createMinimalWithElectraForkEpoch(milestoneTransitionEpoch);
          }
          case FULU -> {
            beforeBeaconStateClass = BeaconStateElectra.class;
            afterBeaconStateClass = BeaconStateFulu.class;
            yield TestSpecFactory.createMinimalWithFuluForkEpoch(milestoneTransitionEpoch);
          }
          case GLOAS -> {
            beforeBeaconStateClass = BeaconStateFulu.class;
            afterBeaconStateClass = BeaconStateGloas.class;
            yield TestSpecFactory.createMinimalWithGloasForkEpoch(milestoneTransitionEpoch);
          }
          case HEZE -> {
            beforeBeaconStateClass = BeaconStateGloas.class;
            afterBeaconStateClass = BeaconStateGloas.class;
            yield TestSpecFactory.createMinimalWithHezeForkEpoch(milestoneTransitionEpoch);
          }
        };

    genesis = createGenesis(spec);
    stateTransition = new StateTransition(spec::atSlot);
    milestoneTransitionSlot = spec.computeStartSlotAtEpoch(milestoneTransitionEpoch);
  }

  @TestTemplate
  public void processSlots_acrossAltairFork_slotBySlot() throws Exception {
    BeaconState currentState = genesis;
    for (int i = 1; i <= milestoneTransitionSlot.intValue(); i++) {
      currentState = stateTransition.processSlots(currentState, UInt64.valueOf(i));
      assertThat(currentState.getSlot()).isEqualTo(UInt64.valueOf(i));
      if (i == milestoneTransitionSlot.intValue()) {
        assertThat(currentState).isInstanceOf(afterBeaconStateClass);
      } else {
        assertThat(currentState).isInstanceOf(beforeBeaconStateClass);
      }
    }
  }

  @TestTemplate
  public void processSlots_acrossAltairFork_acrossManySlots() throws Exception {
    final BeaconState result = stateTransition.processSlots(genesis, milestoneTransitionSlot);
    assertThat(result).isInstanceOf(afterBeaconStateClass);
    assertThat(result.getSlot()).isEqualTo(milestoneTransitionSlot);
  }

  @TestTemplate
  public void processSlots_pastAltairFork() throws Exception {
    final UInt64 targetSlot = milestoneTransitionSlot.plus(9);
    final BeaconState result = stateTransition.processSlots(genesis, targetSlot);
    assertThat(result).isInstanceOf(afterBeaconStateClass);
    assertThat(result.getSlot()).isEqualTo(targetSlot);
  }

  private BeaconState createGenesis(final Spec spec) {
    final List<DepositData> initialDepositData =
        new MockStartDepositGenerator(spec, new DepositGenerator(spec, true))
            .createDeposits(VALIDATOR_KEYS, spec.getGenesisSpecConfig().getMaxEffectiveBalance());

    final List<Deposit> deposits = initialDepositData.stream().map(Deposit::new).toList();
    final BeaconState initialState =
        spec.initializeBeaconStateFromEth1(Bytes32.ZERO, UInt64.ZERO, deposits, Optional.empty());
    return initialState.updated(state -> state.setGenesisTime(UInt64.ZERO));
  }
}
