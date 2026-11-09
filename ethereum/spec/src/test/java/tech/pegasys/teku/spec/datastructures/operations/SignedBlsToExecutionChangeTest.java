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
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SignedBlsToExecutionChangeTest {
  private final Spec spec = TestSpecFactory.createMinimal(SpecMilestone.CAPELLA);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final SignedBlsToExecutionChangeSchema signedBlsToExecutionChangeSchema =
      spec.getGenesisSchemaDefinitions()
          .toVersionCapella()
          .orElseThrow()
          .getSignedBlsToExecutionChangeSchema();

  private final BlsToExecutionChange message = dataStructureUtil.randomBlsToExecutionChange();

  private final BLSSignature signature = dataStructureUtil.randomSignature();

  @Test
  public void objectEquality() {
    final SignedBlsToExecutionChange signedBlsToExecutionChange1 =
        signedBlsToExecutionChangeSchema.create(message, signature);
    final SignedBlsToExecutionChange signedBlsToExecutionChange2 =
        signedBlsToExecutionChangeSchema.create(message, signature);

    assertThat(signedBlsToExecutionChange1).isEqualTo(signedBlsToExecutionChange2);
  }

  @Test
  public void objectAccessorMethods() {
    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        signedBlsToExecutionChangeSchema.create(message, signature);

    assertThat(signedBlsToExecutionChange.getMessage()).isEqualTo(message);
    assertThat(signedBlsToExecutionChange.getSignature()).isEqualTo(signature);
  }

  @Test
  public void roundTripSSZ() {
    final SignedBlsToExecutionChange signedBlsToExecutionChange =
        signedBlsToExecutionChangeSchema.create(message, signature);

    final Bytes sszWithdrawalBytes = signedBlsToExecutionChange.sszSerialize();
    final SignedBlsToExecutionChange deserializedObject =
        signedBlsToExecutionChangeSchema.sszDeserialize(sszWithdrawalBytes);

    assertThat(signedBlsToExecutionChange).isEqualTo(deserializedObject);
  }
}
