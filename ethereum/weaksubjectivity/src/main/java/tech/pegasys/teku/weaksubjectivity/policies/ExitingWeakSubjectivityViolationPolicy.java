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

package tech.pegasys.teku.weaksubjectivity.policies;

import static tech.pegasys.teku.infrastructure.exceptions.ExitConstants.FATAL_EXIT_CODE;

import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;

class ExitingWeakSubjectivityViolationPolicy implements WeakSubjectivityViolationPolicy {

  @Override
  public void onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
      final UInt64 currentEpoch,
      final CheckpointState latestFinalizedCheckpoint,
      final UInt64 wsPeriod) {
    exitClient();
  }

  @Override
  public void onChainInconsistentWithWeakSubjectivityCheckpoint(
      final Checkpoint wsCheckpoint, final Bytes32 blockRoot, final UInt64 blockSlot) {
    exitClient();
  }

  @Override
  public void onFailedToPerformValidation(final String message, final Throwable error) {
    exitClient();
  }

  private void exitClient() {
    System.exit(FATAL_EXIT_CODE);
  }
}
