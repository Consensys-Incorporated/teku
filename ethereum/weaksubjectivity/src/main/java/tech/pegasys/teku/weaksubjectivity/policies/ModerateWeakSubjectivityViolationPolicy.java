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

import java.util.List;
import org.apache.logging.log4j.Level;
import tech.pegasys.teku.infrastructure.logging.WeakSubjectivityLogger;
import tech.pegasys.teku.infrastructure.time.Throttler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;

class ModerateWeakSubjectivityViolationPolicy extends CompoundWeakSubjectivityViolationPolicy {
  private final Throttler<WeakSubjectivityViolationPolicy> warningPolicy =
      new Throttler<>(
          new LoggingWeakSubjectivityViolationPolicy(
              WeakSubjectivityLogger.createFileLogger(), Level.INFO),
          UInt64.valueOf(2));

  public ModerateWeakSubjectivityViolationPolicy() {
    super(List.of(WeakSubjectivityViolationPolicy.strict()));
  }

  @Override
  public void onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
      final UInt64 currentEpoch,
      final CheckpointState latestFinalizedCheckpoint,
      final UInt64 wsPeriod) {
    // Warn periodically
    warningPolicy.invoke(
        currentEpoch,
        p ->
            p.onFinalizedCheckpointOutsideOfWeakSubjectivityPeriod(
                currentEpoch, latestFinalizedCheckpoint, wsPeriod));
  }
}
