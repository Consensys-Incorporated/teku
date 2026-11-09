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

package tech.pegasys.teku.spec.datastructures.util;

import static com.google.common.base.Preconditions.checkArgument;

import tech.pegasys.teku.ethereum.pow.api.Deposit;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.DepositData;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;

public class DepositUtil {
  private final Spec spec;

  public DepositUtil(final Spec spec) {
    this.spec = spec;
  }

  public DepositWithIndex convertDepositEventToOperationDeposit(final Deposit event) {
    checkArgument(
        event.getAmount().isGreaterThanOrEqualTo(spec.getGenesisSpecConfig().getMinDepositAmount()),
        "Deposit amount too low");
    DepositData data =
        new DepositData(
            event.getPubkey(),
            event.getWithdrawal_credentials(),
            event.getAmount(),
            event.getSignature());
    return new DepositWithIndex(
        new tech.pegasys.teku.spec.datastructures.operations.Deposit(data),
        event.getMerkle_tree_index());
  }
}
