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

package tech.pegasys.teku.networking.eth2;

import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.Waiter;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.LengthOutOfBoundsException;
import tech.pegasys.teku.networking.eth2.rpc.core.encodings.RpcEncoding;
import tech.pegasys.teku.networking.eth2.rpc.core.methods.Eth2RpcMethod;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.StatusMessage;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.status.versions.phase0.StatusMessagePhase0;

public class ErrorConditionsIntegrationTest {

  private final Spec spec = TestSpecFactory.createDefault();
  private final RpcEncoding rpcEncoding =
      RpcEncoding.createSszSnappyEncoding(spec.getNetworkingConfig().getMaxPayloadSize());
  private final Eth2P2PNetworkFactory networkFactory = new Eth2P2PNetworkFactory();

  @AfterEach
  public void tearDown() throws Exception {
    networkFactory.stopAll();
  }

  @Test
  public void shouldRejectInvalidRequests() throws Exception {
    final Eth2P2PNetwork network1 =
        networkFactory.builder().rpcEncoding(rpcEncoding).startNetwork();
    final Eth2P2PNetwork network2 =
        networkFactory.builder().rpcEncoding(rpcEncoding).peer(network1).startNetwork();

    final Eth2Peer peer = network1.getPeer(network2.getNodeId()).orElseThrow();

    final Eth2RpcMethod<StatusMessage, StatusMessage> status =
        ((ActiveEth2P2PNetwork) network1).getBeaconChainMethods().status();
    final SafeFuture<StatusMessage> response =
        peer.requestSingleItem(
            status, new InvalidStatusMessage(spec.getGenesisSpecConfig().getGenesisForkVersion()));

    final RpcException expected = new LengthOutOfBoundsException();

    Assertions.assertThatThrownBy(() -> Waiter.waitFor(response))
        .isInstanceOf(ExecutionException.class)
        .extracting(Throwable::getCause)
        .isInstanceOf(RpcException.class)
        .is(
            new Condition<>(
                error -> {
                  final RpcException rpcException = (RpcException) error;
                  return rpcException
                          .getErrorMessageString()
                          .equals(expected.getErrorMessageString())
                      && rpcException.getResponseCode() == expected.getResponseCode();
                },
                "Exception did not match expected exception %s",
                expected));
  }

  // Deliberately doesn't serialize to a valid STATUS message.
  private static class InvalidStatusMessage extends StatusMessagePhase0 {

    public InvalidStatusMessage(final Bytes4 forkVersion) {
      super(forkVersion, Bytes32.ZERO, UInt64.ZERO, Bytes32.ZERO, UInt64.ZERO);
    }

    @Override
    public Bytes sszSerialize() {
      return Bytes.fromHexString("0xABCDEF");
    }
  }
}
