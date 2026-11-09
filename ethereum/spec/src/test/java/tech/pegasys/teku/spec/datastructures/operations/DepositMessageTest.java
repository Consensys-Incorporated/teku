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
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class DepositMessageTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createDefault());
  private final BLSPublicKey pubkey = dataStructureUtil.randomPublicKey();
  private final Bytes32 withdrawalCredentials = dataStructureUtil.randomBytes32();
  private final UInt64 amount = dataStructureUtil.randomUInt64();

  @Test
  public void shouldRoundTripViaSsz() {
    final DepositMessage message = new DepositMessage(pubkey, withdrawalCredentials, amount);
    final Bytes ssz = message.sszSerialize();
    final DepositMessage result = DepositMessage.SSZ_SCHEMA.sszDeserialize(ssz);

    assertThat(result).isEqualTo(message);
  }
}
