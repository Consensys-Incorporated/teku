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

package tech.pegasys.teku.spec.logic.common.helpers;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class StateTooOldException extends RuntimeException {
  private final UInt64 slot;
  private final UInt64 oldestQueryableSlot;

  public StateTooOldException(final UInt64 slot, final UInt64 oldestQueryableSlot) {
    this.slot = slot;
    this.oldestQueryableSlot = oldestQueryableSlot;
  }

  @Override
  public String getMessage() {
    return String.format(
        "Committee information must be derived from a state no older than the previous epoch. State at slot %s is older than cutoff slot %s",
        slot, oldestQueryableSlot);
  }
}
