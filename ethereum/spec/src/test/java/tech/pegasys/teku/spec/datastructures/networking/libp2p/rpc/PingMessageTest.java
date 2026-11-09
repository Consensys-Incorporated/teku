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

package tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class PingMessageTest {

  private static final Bytes EXPECTED_SSZ = Bytes.fromHexString("0x0100000000000000");
  private static final PingMessage MESSAGE = new PingMessage(UInt64.ONE);

  @Test
  public void shouldSerializeToSsz() {
    final Bytes result = MESSAGE.sszSerialize();
    assertThat(result).isEqualTo(EXPECTED_SSZ);
  }

  @Test
  public void shouldDeserializeFromSsz() {
    PingMessage result = PingMessage.SSZ_SCHEMA.sszDeserialize(EXPECTED_SSZ);
    assertThatSszData(result).isEqualByAllMeansTo(MESSAGE);
  }
}
