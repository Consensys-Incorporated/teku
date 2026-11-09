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
import static tech.pegasys.teku.spec.constants.NetworkConstants.MAX_REQUEST_LIGHT_CLIENT_UPDATES;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

class LightClientUpdatesByRangeRequestMessageTest {

  private static final Bytes EXPECTED_SSZ =
      Bytes.fromHexString("0x03000000000000000500000000000000");
  private static final LightClientUpdatesByRangeRequestMessage MESSAGE =
      new LightClientUpdatesByRangeRequestMessage(UInt64.valueOf(3), UInt64.valueOf(5));

  @Test
  public void shouldSerializeToSsz() {
    final Bytes result = MESSAGE.sszSerialize();
    assertThat(result).isEqualTo(EXPECTED_SSZ);
  }

  @Test
  public void shouldDeserializeFromSsz() {
    final LightClientUpdatesByRangeRequestMessage result =
        LightClientUpdatesByRangeRequestMessage.SSZ_SCHEMA.sszDeserialize(EXPECTED_SSZ);
    assertThatSszData(result).isEqualByAllMeansTo(MESSAGE);
    assertThat(result.getStartPeriod()).isEqualTo(UInt64.valueOf(3));
    assertThat(result.getCount()).isEqualTo(UInt64.valueOf(5));
  }

  @Test
  public void shouldCapMaximumResponseChunksAtMaxRequestLightClientUpdates() {
    assertThat(MESSAGE.getMaximumResponseChunks()).isEqualTo(5);
    assertThat(
            new LightClientUpdatesByRangeRequestMessage(UInt64.ZERO, UInt64.MAX_VALUE)
                .getMaximumResponseChunks())
        .isEqualTo(MAX_REQUEST_LIGHT_CLIENT_UPDATES);
  }
}
