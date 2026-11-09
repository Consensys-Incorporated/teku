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

package tech.pegasys.teku.spec.datastructures.operations;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequest;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.WithdrawalRequestSchema;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class WithdrawalRequestTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimal(SpecMilestone.ELECTRA));
  private final WithdrawalRequestSchema withdrawalRequestSchema = new WithdrawalRequestSchema();
  private final Bytes20 sourceAddress = dataStructureUtil.randomBytes20();
  private final BLSPublicKey validatorPubkey = dataStructureUtil.randomPublicKey();
  private final UInt64 amount = dataStructureUtil.randomUInt64();

  @Test
  public void objectEquality() {
    final WithdrawalRequest withdrawalRequest1 =
        withdrawalRequestSchema.create(sourceAddress, validatorPubkey, amount);
    final WithdrawalRequest withdrawalRequest2 =
        withdrawalRequestSchema.create(sourceAddress, validatorPubkey, amount);

    assertThat(withdrawalRequest1).isEqualTo(withdrawalRequest2);
  }

  @Test
  public void objectAccessorMethods() {
    final WithdrawalRequest withdrawalRequest =
        withdrawalRequestSchema.create(sourceAddress, validatorPubkey, amount);

    assertThat(withdrawalRequest.getSourceAddress()).isEqualTo(sourceAddress);
    assertThat(withdrawalRequest.getValidatorPubkey()).isEqualTo(validatorPubkey);
  }

  @Test
  public void roundTripSSZ() {
    final WithdrawalRequest withdrawalRequest =
        withdrawalRequestSchema.create(sourceAddress, validatorPubkey, amount);

    final Bytes sszBytes = withdrawalRequest.sszSerialize();
    final WithdrawalRequest deserializedObject = withdrawalRequestSchema.sszDeserialize(sszBytes);

    assertThat(withdrawalRequest).isEqualTo(deserializedObject);
  }
}
