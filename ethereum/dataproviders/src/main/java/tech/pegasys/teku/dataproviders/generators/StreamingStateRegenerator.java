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

package tech.pegasys.teku.dataproviders.generators;

import java.util.stream.Stream;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;

/**
 * This class is only suitable for regenerating states we have previously performed full validation
 * on. It assumes that the pre-state and supplied blocks are valid and does not recheck state roots
 * or signatures.
 */
public class StreamingStateRegenerator {

  private final Spec spec;
  private BeaconState state;

  private StreamingStateRegenerator(final Spec spec, final BeaconState preState) {
    this.spec = spec;
    this.state = preState;
  }

  private void processBlock(final SignedBeaconBlock block) {
    try {
      state = spec.replayValidatedBlock(state, block);
    } catch (StateTransitionException e) {
      throw new IllegalStateException("Regenerating state failed", e);
    }
  }

  public static BeaconState regenerate(
      final Spec spec, final BeaconState initialState, final Stream<SignedBeaconBlock> blocks) {
    final StreamingStateRegenerator regenerator = new StreamingStateRegenerator(spec, initialState);
    blocks.forEach(regenerator::processBlock);
    return regenerator.state;
  }
}
