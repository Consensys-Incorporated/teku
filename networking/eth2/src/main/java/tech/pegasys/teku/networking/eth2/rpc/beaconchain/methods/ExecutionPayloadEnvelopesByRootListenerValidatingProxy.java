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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.ExecutionPayloadEnvelopesByRootResponseInvalidResponseException.InvalidResponseType;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

public class ExecutionPayloadEnvelopesByRootListenerValidatingProxy
    implements RpcResponseListener<SignedExecutionPayloadEnvelope> {

  private final Peer peer;
  private final RpcResponseListener<SignedExecutionPayloadEnvelope> listener;
  private final Set<Bytes32> expectedBeaconBlockRoots;

  public ExecutionPayloadEnvelopesByRootListenerValidatingProxy(
      final Peer peer,
      final RpcResponseListener<SignedExecutionPayloadEnvelope> listener,
      final List<Bytes32> expectedBeaconBlockRoots) {
    this.peer = peer;
    this.listener = listener;
    this.expectedBeaconBlockRoots = ConcurrentHashMap.newKeySet();
    this.expectedBeaconBlockRoots.addAll(expectedBeaconBlockRoots);
  }

  @Override
  public SafeFuture<?> onResponse(final SignedExecutionPayloadEnvelope envelope) {
    return SafeFuture.of(
        () -> {
          if (!expectedBeaconBlockRoots.remove(envelope.getBeaconBlockRoot())) {
            throw new ExecutionPayloadEnvelopesByRootResponseInvalidResponseException(
                peer, InvalidResponseType.ENVELOPE_UNEXPECTED_BEACON_BLOCK_ROOT);
          }
          return listener.onResponse(envelope);
        });
  }
}
