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

package tech.pegasys.teku.statetransition.forkchoice;

import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class PreparedProposerInfo extends ExpiringInfo {
  private final Eth1Address feeRecipient;

  public PreparedProposerInfo(final UInt64 expirySlot, final Eth1Address feeRecipient) {
    super(expirySlot);
    this.feeRecipient = feeRecipient;
  }

  public Eth1Address getFeeRecipient() {
    return feeRecipient;
  }
}
