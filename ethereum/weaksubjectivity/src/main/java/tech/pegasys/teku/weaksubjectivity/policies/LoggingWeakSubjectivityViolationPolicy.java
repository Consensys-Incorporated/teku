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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.logging.WeakSubjectivityLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;

class LoggingWeakSubjectivityViolationPolicy implements WeakSubjectivityViolationPolicy {
  private static final Logger LOG = LogManager.getLogger();

  private final Level level;
  private final WeakSubjectivityLogger wsLogger;

  public LoggingWeakSubjectivityViolationPolicy(
      final WeakSubjectivityLogger wsLogger, final Level level) {
    this.level = level;
    this.wsLogger = wsLogger;
  }

  @Override
  public void onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
      final UInt64 currentEpoch,
      final CheckpointState latestFinalizedCheckpoint,
      final UInt64 wsPeriod) {
    LOG.log(
        level,
        "The latest finalized checkpoint at epoch {} fell outside of the weak subjectivity period after epoch {}, which is prior to the current epoch {}.",
        latestFinalizedCheckpoint.getEpoch(),
        latestFinalizedCheckpoint.getEpoch().plus(wsPeriod),
        currentEpoch);
    wsLogger.finalizedCheckpointOutsideOfWeakSubjectivityPeriod(
        level, latestFinalizedCheckpoint.getEpoch());
  }

  @Override
  public void onChainInconsistentWithWeakSubjectivityCheckpoint(
      final Checkpoint wsCheckpoint, final Bytes32 blockRoot, final UInt64 blockSlot) {
    wsLogger.chainInconsistentWithWeakSubjectivityCheckpoint(
        level, blockRoot, blockSlot, wsCheckpoint.getRoot(), wsCheckpoint.getEpoch());
  }

  @Override
  public void onFailedToPerformValidation(final String message, final Throwable error) {
    wsLogger.failedToPerformWeakSubjectivityValidation(level, message, error);
  }
}
