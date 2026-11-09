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

package tech.pegasys.teku.weaksubjectivity;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * <a href="https://notes.ethereum.org/@adiasg/weak-subjectvity-eth2">Weak Subjectivity in
 * Eth2.0</a> and the per-fork weak-subjectivity guides under <a
 * href="https://github.com/ethereum/consensus-specs">consensus-specs</a>.
 *
 * <p>Essentially it is a tiny wrapper over {@link
 * tech.pegasys.teku.spec.logic.common.weaksubjectivity.WeakSubjectivityCalculator}
 */
public class WeakSubjectivityCalculator {

  public static WeakSubjectivityCalculator create(final Spec spec) {
    return new WeakSubjectivityCalculator(spec);
  }

  private final Spec spec;

  private WeakSubjectivityCalculator(final Spec spec) {
    this.spec = spec;
  }

  /**
   * @param finalizedCheckpoint The latest finalized checkpoint
   * @param currentSlot The current slot by clock time
   * @return True if the latest finalized checkpoint is still within the weak subjectivity period
   */
  public boolean isWithinWeakSubjectivityPeriod(
      final CheckpointState finalizedCheckpoint, final UInt64 currentSlot) {
    final BeaconState state = finalizedCheckpoint.getState();
    return spec.atSlot(state.getSlot())
        .weakSubjectivityCalculator()
        .isWithinWeakSubjectivityPeriod(state, currentSlot);
  }

  /**
   * @param checkpointState A trusted / effectively finalized checkpoint state
   * @return The weak subjectivity period in epochs
   */
  public UInt64 computeWeakSubjectivityPeriod(final CheckpointState checkpointState) {
    final BeaconState state = checkpointState.getState();
    return spec.atSlot(state.getSlot())
        .weakSubjectivityCalculator()
        .computeWeakSubjectivityPeriod(state);
  }
}
