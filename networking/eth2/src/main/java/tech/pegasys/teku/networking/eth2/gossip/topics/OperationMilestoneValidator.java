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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import java.util.function.Function;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Fork;

public class OperationMilestoneValidator<T> implements OperationValidator<T> {

  private final Spec spec;
  private final Fork expectedFork;
  private final Function<T, UInt64> getEpochForMessage;

  public OperationMilestoneValidator(
      final Spec spec, final Fork expectedFork, final Function<T, UInt64> getEpochForMessage) {
    this.spec = spec;
    this.expectedFork = expectedFork;
    this.getEpochForMessage = getEpochForMessage;
  }

  @Override
  public boolean isValid(final T message) {
    final UInt64 messageEpoch = getEpochForMessage.apply(message);
    final Fork actualFork = spec.getForkSchedule().getFork(messageEpoch);
    return expectedFork.equals(actualFork);
  }
}
