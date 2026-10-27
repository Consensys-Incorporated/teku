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
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;

class LightClientBootstrapRequestMessageTest {

  private static final Bytes EXPECTED_SSZ =
      Bytes.fromHexString("0x0102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f20");
  private static final LightClientBootstrapRequestMessage MESSAGE =
      new LightClientBootstrapRequestMessage(Bytes32.wrap(EXPECTED_SSZ));

  @Test
  public void shouldSerializeToSsz() {
    final Bytes result = MESSAGE.sszSerialize();
    assertThat(result).isEqualTo(EXPECTED_SSZ);
  }

  @Test
  public void shouldDeserializeFromSsz() {
    final LightClientBootstrapRequestMessage result =
        LightClientBootstrapRequestMessage.SSZ_SCHEMA.sszDeserialize(EXPECTED_SSZ);
    assertThatSszData(result).isEqualByAllMeansTo(MESSAGE);
    assertThat(result.getBlockRoot()).isEqualTo(Bytes32.wrap(EXPECTED_SSZ));
  }

  @Test
  public void shouldRequestSingleResponseChunk() {
    assertThat(MESSAGE.getMaximumResponseChunks()).isEqualTo(1);
  }
}
