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

package tech.pegasys.teku.beacon.pow.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.pow.api.Deposit;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.ethereum.pow.api.InvalidDepositEventsException;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DepositsFromBlockEventTest {
  final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());

  @Test
  public void create_withOutOfOrderDeposits() {
    final Deposit deposit1 = dataStructureUtil.randomDepositEvent(1);
    final Deposit deposit2 = dataStructureUtil.randomDepositEvent(2);
    final Deposit deposit3 = dataStructureUtil.randomDepositEvent(3);
    final Stream<Deposit> depositStream = Stream.of(deposit3, deposit1, deposit2);

    final DepositsFromBlockEvent event =
        DepositsFromBlockEvent.create(
            UInt64.ONE, dataStructureUtil.randomBytes32(), UInt64.ONE, depositStream);
    assertThat(event.getDeposits()).containsExactly(deposit1, deposit2, deposit3);
    assertThat(event.getFirstDepositIndex()).isEqualTo(deposit1.getMerkle_tree_index());
    assertThat(event.getLastDepositIndex()).isEqualTo(deposit3.getMerkle_tree_index());
  }

  @Test
  public void create_withMissingDeposit() {
    final Deposit deposit1 = dataStructureUtil.randomDepositEvent(1);
    final Deposit deposit3 = dataStructureUtil.randomDepositEvent(3);
    final Stream<Deposit> depositStream = Stream.of(deposit3, deposit1);

    assertThatThrownBy(
            () ->
                DepositsFromBlockEvent.create(
                    UInt64.ONE, dataStructureUtil.randomBytes32(), UInt64.ONE, depositStream))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining(
            "Deposits must be ordered and contiguous. Deposit at index 3 does not follow prior deposit at index 1");
  }

  @Test
  public void create_withMissingDepositAtEndOfList() {
    final Stream<Deposit> depositStream =
        Stream.of(
            dataStructureUtil.randomDepositEvent(4),
            dataStructureUtil.randomDepositEvent(3),
            dataStructureUtil.randomDepositEvent(5),
            dataStructureUtil.randomDepositEvent(7));

    assertThatThrownBy(
            () ->
                DepositsFromBlockEvent.create(
                    UInt64.ONE, dataStructureUtil.randomBytes32(), UInt64.ONE, depositStream))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining(
            "Deposits must be ordered and contiguous. Deposit at index 7 does not follow prior deposit at index 5");
  }

  @Test
  public void create_withDuplicateDeposit() {
    final Deposit deposit1 = dataStructureUtil.randomDepositEvent(1);
    final Stream<Deposit> depositStream = Stream.of(deposit1, deposit1);

    assertThatThrownBy(
            () ->
                DepositsFromBlockEvent.create(
                    UInt64.ONE, dataStructureUtil.randomBytes32(), UInt64.ONE, depositStream))
        .isInstanceOf(InvalidDepositEventsException.class)
        .hasMessageContaining(
            "Deposits must be ordered and contiguous. Deposit at index 1 does not follow prior deposit at index 1");
  }
}
