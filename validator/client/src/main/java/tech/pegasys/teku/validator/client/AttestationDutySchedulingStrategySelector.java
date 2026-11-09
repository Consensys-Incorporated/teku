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

package tech.pegasys.teku.validator.client;

public class AttestationDutySchedulingStrategySelector {

  private final int minSizeToBatchBySlot;
  private final boolean useDvtEndpoint;
  private final AttestationDutyDefaultSchedulingStrategy defaultStrategy;
  private final AttestationDutyBatchSchedulingStrategy batchStrategy;

  public AttestationDutySchedulingStrategySelector(
      final int minSizeToBatchBySlot,
      final boolean useDvtEndpoint,
      final AttestationDutyDefaultSchedulingStrategy defaultStrategy,
      final AttestationDutyBatchSchedulingStrategy batchStrategy) {
    this.minSizeToBatchBySlot = minSizeToBatchBySlot;
    this.useDvtEndpoint = useDvtEndpoint;
    this.defaultStrategy = defaultStrategy;
    this.batchStrategy = batchStrategy;
  }

  public AttestationDutySchedulingStrategy selectStrategy(final int expectedDutiesCount) {
    // disabling batch flow when DVT is enabled
    if (expectedDutiesCount < minSizeToBatchBySlot || useDvtEndpoint) {
      return defaultStrategy;
    }
    return batchStrategy;
  }
}
