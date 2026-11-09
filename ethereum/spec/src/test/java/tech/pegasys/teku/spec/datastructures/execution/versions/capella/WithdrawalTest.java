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

package tech.pegasys.teku.spec.datastructures.execution.versions.capella;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class WithdrawalTest {
  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.CAPELLA);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final WithdrawalSchema withdrawalSchema =
      spec.getGenesisSchemaDefinitions().toVersionCapella().orElseThrow().getWithdrawalSchema();

  private final UInt64 index = dataStructureUtil.randomUInt64();
  private final UInt64 validatorIndex = dataStructureUtil.randomUInt64();
  private final Bytes20 address = dataStructureUtil.randomBytes20();
  private final UInt64 amount = dataStructureUtil.randomUInt64();

  @Test
  public void objectEquality() {
    final Withdrawal withdrawal1 = withdrawalSchema.create(index, validatorIndex, address, amount);
    final Withdrawal withdrawal2 = withdrawalSchema.create(index, validatorIndex, address, amount);

    assertThat(withdrawal1).isEqualTo(withdrawal2);
  }

  @Test
  public void objectAccessorMethods() {
    final Withdrawal withdrawal = withdrawalSchema.create(index, validatorIndex, address, amount);

    assertThat(withdrawal.getIndex()).isEqualTo(index);
    assertThat(withdrawal.getValidatorIndex()).isEqualTo(validatorIndex);
    assertThat(withdrawal.getAddress()).isEqualTo(address);
    assertThat(withdrawal.getAmount()).isEqualTo(amount);
  }

  @Test
  public void roundTripSSZ() {
    final Withdrawal withdrawal = withdrawalSchema.create(index, validatorIndex, address, amount);

    final Bytes sszWithdrawalBytes = withdrawal.sszSerialize();
    final Withdrawal deserializedObject = withdrawalSchema.sszDeserialize(sszWithdrawalBytes);

    assertThat(withdrawal).isEqualTo(deserializedObject);
  }
}
