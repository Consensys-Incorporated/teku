/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.networking.eth2.rpc.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.context.RpcContextCodec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessageSchemaPhase0;

final class RpcResponseEncoderTest {

  private static final Bytes RECORDED_STATUS_RESPONSE_BYTES =
      Bytes.fromHexString(
          "0x0054ff060000734e61507059003300007e8c1ea2540000aa01009c30a903798306695d21d1faa76363a0070677130835e503760b0e84479b7819e60000000000000000");
  private static final StatusMessagePhase0 RECORDED_STATUS_MESSAGE_DATA =
      new StatusMessagePhase0(
          new Bytes4(Bytes.of(0, 0, 0, 0)),
          Bytes32.ZERO,
          UInt64.ZERO,
          Bytes32.fromHexString(
              "0x30A903798306695D21D1FAA76363A0070677130835E503760B0E84479B7819E6"),
          UInt64.ZERO);

  private final RpcContextCodec<?, StatusMessagePhase0> contextCodec =
      RpcContextCodec.noop(new StatusMessageSchemaPhase0());
  private final RpcResponseEncoder<StatusMessagePhase0, ?> responseEncoder =
      new RpcResponseEncoder<>(
          RpcEncoding.createSszSnappyEncoding(
              TestSpecFactory.createDefault().getNetworkingConfig().getMaxPayloadSize()),
          contextCodec);

  @Test
  public void shouldEncodeSuccessfulResponse() {
    final Bytes actual = responseEncoder.encodeSuccessfulResponse(RECORDED_STATUS_MESSAGE_DATA);
    assertThat(actual).isEqualTo(RECORDED_STATUS_RESPONSE_BYTES);
  }
}
