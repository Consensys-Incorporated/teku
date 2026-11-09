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

package tech.pegasys.teku.ethereum.execution.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.ethereum.execution.types.Eth1Address.ETH1ADDRESS_TYPE;
import static tech.pegasys.teku.infrastructure.json.DeserializableTypeUtil.assertRoundTrip;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class Eth1AddressTest {

  @Test
  void eth1Address_shouldRoundTrip() throws Exception {
    assertRoundTrip(
        Eth1Address.fromHexString("0x1Db3439a222C519ab44bb1144fC28167b4Fa6EE6"), ETH1ADDRESS_TYPE);
  }

  @Test
  void eth1Address_acceptAddressWithoutPrefix() throws Exception {
    Eth1Address.fromHexString("1Db3439a222C519ab44bb1144fC28167b4Fa6EE6");
  }

  @Test
  void eth1Address_checksumValidAllUpper() throws Exception {
    Eth1Address.fromHexString("0x52908400098527886E0F7030069857D2E4169EE7");
    Eth1Address.fromHexString("0x8617E340B3D01FA5F11F306F4090FD50E238070D");
  }

  @Test
  void eth1Address_shouldIgnoreInvalidAllUpper() throws Exception {
    Eth1Address.fromHexString("0x5AAEB6053F3E94C9B9A09F33669435E7EF1BEAED");
  }

  @Test
  void eth1Address_checksumValidAllLower() throws Exception {
    Eth1Address.fromHexString("0xde709f2102306220921060314715629080e2fb77");
    Eth1Address.fromHexString("0x27b1fdb04752bbc536007a920d24acb045561c26");
  }

  @Test
  void eth1Address_shouldIgnoreInvalidAllLower() throws Exception {
    Eth1Address.fromHexString("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed");
  }

  @Test
  void eth1Address_checksumNormal() throws Exception {
    Eth1Address.fromHexString("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");
    Eth1Address.fromHexString("0xfB6916095ca1df60bB79Ce92cE3Ea74c37c5d359");
    Eth1Address.fromHexString("0xdbF03B407c01E7cD3CBea99509d93f8DDDC8C6FB");
    Eth1Address.fromHexString("0xD1220A0cf47c7B9Be7A2E6BA89F429762e7b9aDb");
  }

  @Test
  void eth1Address_shouldChecksumFromBytes() throws Exception {
    assertThat(
            Eth1Address.fromBytes(Bytes.fromHexString("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed"))
                .toHexString())
        .isEqualTo("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");
  }

  @Test
  void eth1Address_shouldThrowIfChecksumFails() throws Exception {
    assertThatThrownBy(
            // The first "normal" address with the last character made uppercase.
            () -> Eth1Address.fromHexString("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAeD"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");
  }

  @Test
  void eth1Address_allLowerShouldPrintChecksum() throws Exception {
    assertThat(Eth1Address.fromHexString("0x5aaeb6053f3e94c9b9a09f33669435e7ef1beaed").toString())
        .isEqualTo("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");
  }

  @Test
  void eth1Address_allUpperShouldPrintChecksum() throws Exception {
    assertThat(Eth1Address.fromHexString("0x5AAEB6053F3E94C9B9A09F33669435E7EF1BEAED").toString())
        .isEqualTo("0x5aAeb6053F3E94C9b9A09f33669435E7Ef1BeAed");
  }
}
