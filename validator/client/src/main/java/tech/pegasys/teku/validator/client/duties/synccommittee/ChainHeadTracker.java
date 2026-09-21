/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.client.duties.synccommittee;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;

public class ChainHeadTracker implements ValidatorTimingChannel {
  public static final int HEAD_TOO_OLD_THRESHOLD = 32;

  private UInt64 headBlockSlot = UInt64.ZERO;
  private Optional<Bytes32> headBlockRoot = Optional.empty();

  public synchronized Optional<Bytes32> getCurrentChainHead(final UInt64 atSlot) {
    if (headBlockSlot.isGreaterThan(atSlot)) {
      // We've moved on and no longer have a reference to what the head block was at that slot
      throw new ChainHeadBeyondSlotException(atSlot);
    }
    if (headBlockRoot.isPresent()
        && atSlot.minusMinZero(headBlockSlot).isGreaterThan(HEAD_TOO_OLD_THRESHOLD)) {
      throw new ChainHeadTooOldException(headBlockSlot, atSlot);
    }
    return headBlockRoot;
  }

  @Override
  public synchronized void onHeadUpdate(
      final UInt64 slot,
      final Bytes32 previousDutyDependentRoot,
      final Bytes32 currentDutyDependentRoot,
      final Bytes32 headBlockRoot) {
    this.headBlockSlot = slot;
    this.headBlockRoot = Optional.of(headBlockRoot);
  }
}
