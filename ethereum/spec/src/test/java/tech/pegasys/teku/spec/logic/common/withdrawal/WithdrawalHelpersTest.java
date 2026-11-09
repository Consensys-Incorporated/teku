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

package tech.pegasys.teku.spec.logic.common.withdrawal;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.logic.common.withdrawals.WithdrawalsHelpers;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class WithdrawalHelpersTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private static final UInt64 ONE_ETH = UInt64.valueOf(1_000_000_000L);

  @Test
  void getTotalWithdrawn_returnsZeroIfNoWithdrawals() {
    final List<Withdrawal> withdrawalList = List.of();
    assertThat(WithdrawalsHelpers.getTotalWithdrawn(withdrawalList, ZERO)).isEqualTo(ZERO);
  }

  @Test
  void getTotalWithdrawn_returnsSummedValue() {
    final List<Withdrawal> withdrawalList =
        List.of(
            dataStructureUtil.randomWithdrawal(ZERO, ONE_ETH),
            dataStructureUtil.randomWithdrawal(ZERO, ONE_ETH));
    assertThat(WithdrawalsHelpers.getTotalWithdrawn(withdrawalList, ZERO))
        .isEqualTo(ONE_ETH.times(2));
  }

  @Test
  void getTotalWithdrawn_returnsValueSpecificToValidator() {
    final List<Withdrawal> withdrawalList =
        List.of(
            dataStructureUtil.randomWithdrawal(ZERO, ONE_ETH),
            dataStructureUtil.randomWithdrawal(ONE, ONE_ETH));
    assertThat(WithdrawalsHelpers.getTotalWithdrawn(withdrawalList, ZERO)).isEqualTo(ONE_ETH);
  }

  @Test
  void getEthAddressFromWithdrawalCredentials_extracts_address() {
    final Eth1Address address = dataStructureUtil.randomEth1Address();
    final Validator validator =
        dataStructureUtil
            .validatorBuilder()
            .withdrawalCredentials(
                Bytes32.fromHexString("0x01000000000000" + address.toUnprefixedHexString()))
            .build();
    assertThat(
            WithdrawalsHelpers.getEthAddressFromWithdrawalCredentials(validator)
                .toUnprefixedHexString())
        .isEqualToIgnoringCase(address.toUnprefixedHexString());
  }
}
