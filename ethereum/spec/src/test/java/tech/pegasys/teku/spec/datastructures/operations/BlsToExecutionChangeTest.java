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
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlsToExecutionChangeTest {
  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.CAPELLA);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BlsToExecutionChangeSchema blsToExecutionChangeSchema =
      spec.getGenesisSchemaDefinitions()
          .toVersionCapella()
          .orElseThrow()
          .getBlsToExecutionChangeSchema();

  private final UInt64 validatorIndex = dataStructureUtil.randomUInt64();
  private final BLSPublicKey fromBlsPubkey = dataStructureUtil.randomPublicKey();
  private final Bytes20 toExecutionAddress = dataStructureUtil.randomBytes20();

  @Test
  public void objectEquality() {
    final BlsToExecutionChange blsToExecutionChange1 =
        blsToExecutionChangeSchema.create(validatorIndex, fromBlsPubkey, toExecutionAddress);
    final BlsToExecutionChange blsToExecutionChange2 =
        blsToExecutionChangeSchema.create(validatorIndex, fromBlsPubkey, toExecutionAddress);

    assertThat(blsToExecutionChange1).isEqualTo(blsToExecutionChange2);
  }

  @Test
  public void objectAccessorMethods() {
    final BlsToExecutionChange blsToExecutionChange =
        blsToExecutionChangeSchema.create(validatorIndex, fromBlsPubkey, toExecutionAddress);

    assertThat(blsToExecutionChange.getValidatorIndex()).isEqualTo(validatorIndex);
    assertThat(blsToExecutionChange.getFromBlsPubkey()).isEqualTo(fromBlsPubkey);
    assertThat(blsToExecutionChange.getToExecutionAddress()).isEqualTo(toExecutionAddress);
  }

  @Test
  public void roundTripSSZ() {
    final BlsToExecutionChange blsToExecutionChange =
        blsToExecutionChangeSchema.create(validatorIndex, fromBlsPubkey, toExecutionAddress);

    final Bytes sszWithdrawalBytes = blsToExecutionChange.sszSerialize();
    final BlsToExecutionChange deserializedObject =
        blsToExecutionChangeSchema.sszDeserialize(sszWithdrawalBytes);

    assertThat(blsToExecutionChange).isEqualTo(deserializedObject);
  }
}
