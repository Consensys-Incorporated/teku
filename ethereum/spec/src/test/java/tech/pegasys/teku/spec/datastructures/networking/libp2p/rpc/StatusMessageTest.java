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

import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.fulu.StatusMessageFulu;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.fulu.StatusMessageSchemaFulu;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessageSchemaPhase0;

class StatusMessageTest {
  @Test
  public void shouldRoundTripViaSszPhase0() {
    final StatusMessageSchemaPhase0 schema = new StatusMessageSchemaPhase0();
    final StatusMessagePhase0 message =
        schema.create(
            Bytes4.leftPad(Bytes.EMPTY),
            Bytes32.fromHexStringLenient("0x01"),
            UInt64.valueOf(2),
            Bytes32.fromHexStringLenient("0x03"),
            UInt64.valueOf(4),
            Optional.empty());
    final Bytes data = message.sszSerialize();

    final StatusMessagePhase0 result = schema.sszDeserialize(data);
    assertThatSszData(result).isEqualByAllMeansTo(message);
  }

  @Test
  public void shouldRoundTripViaSszFulu() {
    final StatusMessageSchemaFulu schema = new StatusMessageSchemaFulu();
    final StatusMessageFulu message =
        schema.create(
            Bytes4.leftPad(Bytes.EMPTY),
            Bytes32.fromHexStringLenient("0x01"),
            UInt64.valueOf(2),
            Bytes32.fromHexStringLenient("0x03"),
            UInt64.valueOf(4),
            Optional.of(UInt64.valueOf(100)));
    final Bytes data = message.sszSerialize();

    final StatusMessageFulu result = schema.sszDeserialize(data);
    assertThatSszData(result).isEqualByAllMeansTo(message);
  }
}
