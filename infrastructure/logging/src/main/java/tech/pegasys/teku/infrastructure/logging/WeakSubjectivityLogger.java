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

package tech.pegasys.teku.infrastructure.logging;

import static tech.pegasys.teku.infrastructure.logging.ColorConsolePrinter.print;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class WeakSubjectivityLogger {
  private static final Logger FILE_LOGGER = LogManager.getLogger();

  @SuppressWarnings("PrivateStaticFinalLoggers")
  private final Logger log;

  private final boolean allowColor;

  private WeakSubjectivityLogger(final Logger log, final boolean allowColor) {
    this.log = log;
    this.allowColor = allowColor;
  }

  public static WeakSubjectivityLogger createConsoleLogger() {
    return new WeakSubjectivityLogger(StatusLogger.STATUS_LOG.log, true);
  }

  public static WeakSubjectivityLogger createFileLogger() {
    return new WeakSubjectivityLogger(FILE_LOGGER, false);
  }

  public void warnWeakSubjectivityChecksSuppressed(final UInt64 untilEpoch) {
    final String msg = "Suppressing weak subjectivity errors until epoch " + untilEpoch;
    logWithColorIfLevelGreaterThanInfo(Level.WARN, msg, ColorConsolePrinter.Color.YELLOW);
  }

  public void warnWeakSubjectivityFinalizedCheckpointValidationDeferred(
      final UInt64 finalizedEpoch, final UInt64 wsCheckpointEpoch) {
    final String msg =
        String.format(
            "Deferring weak subjectivity checks for finalized checkpoint at epoch %s.  Checks will resume once weak subjectivity checkpoint at epoch %s is reached.",
            finalizedEpoch, wsCheckpointEpoch);
    logWithColorIfLevelGreaterThanInfo(Level.WARN, msg, ColorConsolePrinter.Color.YELLOW);
  }

  public void finalizedCheckpointOutsideOfWeakSubjectivityPeriod(
      final Level level, final UInt64 latestFinalizedCheckpointEpoch) {
    final String msg =
        String.format(
            "The latest finalized checkpoint at epoch %s is outside of the weak subjectivity period.  Please supply a recent weak subjectivity checkpoint using --ws-checkpoint=<BLOCK_ROOT>:<EPOCH>.",
            latestFinalizedCheckpointEpoch);
    logWithColorIfLevelGreaterThanInfo(level, msg, ColorConsolePrinter.Color.RED);
  }

  public void chainInconsistentWithWeakSubjectivityCheckpoint(
      final Level level,
      final Bytes32 blockRoot,
      final UInt64 blockSlot,
      final Bytes32 wsCheckpointRoot,
      final UInt64 wsCheckpointEpoch) {
    final String msg =
        String.format(
            "Block %s at slot %s is inconsistent with weak subjectivity checkpoint (root=%s, epoch=%s)",
            blockRoot, blockSlot, wsCheckpointRoot, wsCheckpointEpoch);
    logWithColorIfLevelGreaterThanInfo(level, msg, ColorConsolePrinter.Color.RED);
  }

  public void failedToPerformWeakSubjectivityValidation(
      final Level level, final String message, final Throwable error) {
    log.log(level, "Failed to perform weak subjectivity validation: " + message, error);
  }

  private void logWithColorIfLevelGreaterThanInfo(
      final Level level, final String msg, final ColorConsolePrinter.Color color) {
    final boolean useColor = allowColor && level.compareTo(Level.INFO) < 0;
    log.log(level, useColor ? print(msg, color) : msg);
  }
}
