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

package tech.pegasys.teku.spec.logic.versions.fulu.helpers;

import static com.google.common.base.Preconditions.checkArgument;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.BeaconStateAccessorsElectra;
import tech.pegasys.teku.spec.logic.versions.electra.helpers.PredicatesElectra;

public class BeaconStateAccessorsFulu extends BeaconStateAccessorsElectra {
  private static final Logger LOG = LogManager.getLogger();

  public BeaconStateAccessorsFulu(
      final SpecConfig config,
      final PredicatesElectra predicatesElectra,
      final MiscHelpersFulu miscHelpers) {
    super(SpecConfigFulu.required(config), predicatesElectra, miscHelpers);
  }

  /**
   * get_beacon_proposer_index
   *
   * <p>Return the beacon proposer index at the current slot.
   */
  @Override
  public int getBeaconProposerIndex(final BeaconState state) {
    return getProposerLookaheadValue(
        state, state.getSlot().mod(config.getSlotsPerEpoch()).intValue());
  }

  @Override
  public int getBeaconProposerIndex(final BeaconState state, final UInt64 requestedSlot) {
    validateStateCanCalculateProposerIndexAtSlot(state, requestedSlot);
    final UInt64 stateEpoch = miscHelpers.computeEpochAtSlot(state.getSlot());
    final UInt64 requestedEpoch = miscHelpers.computeEpochAtSlot(requestedSlot);
    final int epochOffset = stateEpoch.equals(requestedEpoch) ? 0 : config.getSlotsPerEpoch();
    final int lookaheadIndex =
        requestedSlot.mod(config.getSlotsPerEpoch()).intValue() + epochOffset;
    final int proposerIndex = getProposerLookaheadValue(state, lookaheadIndex);
    LOG.debug(
        "get proposer index for slot {} from state at slot {}, will be lookahead index {} - proposer will be {}",
        requestedSlot,
        state.getSlot(),
        lookaheadIndex,
        proposerIndex);
    return proposerIndex;
  }

  @Override
  public boolean canCalculateProposerIndexAtSlot(
      final BeaconState state, final UInt64 requestedSlot) {
    final UInt64 epoch = miscHelpers.computeEpochAtSlot(requestedSlot);
    final UInt64 stateEpoch = getCurrentEpoch(state);
    return epoch.equals(stateEpoch) || stateEpoch.increment().equals(epoch);
  }

  public List<Integer> getBeaconProposerIndices(final BeaconState state, final UInt64 epoch) {
    final IntList indices = getActiveValidatorIndices(state, epoch);
    final Bytes32 seed = getSeed(state, epoch, Domain.BEACON_PROPOSER);
    return miscHelpers.computeProposerIndices(state, epoch, seed, indices);
  }

  private int getProposerLookaheadValue(final BeaconState state, final int lookaheadIndex) {
    return BeaconStateFulu.required(state)
        .getProposerLookahead()
        .get(lookaheadIndex)
        .get()
        .intValue();
  }

  public static BeaconStateAccessorsFulu required(final BeaconStateAccessors beaconStateAccessors) {
    checkArgument(
        beaconStateAccessors instanceof BeaconStateAccessorsFulu,
        "Expected %s but it was %s",
        BeaconStateAccessorsFulu.class,
        beaconStateAccessors.getClass());
    return (BeaconStateAccessorsFulu) beaconStateAccessors;
  }
}
