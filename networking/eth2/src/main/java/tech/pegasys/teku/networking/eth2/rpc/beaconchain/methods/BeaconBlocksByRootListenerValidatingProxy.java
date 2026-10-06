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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlocksByRootResponseInvalidResponseException.InvalidResponseType;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;

public class BeaconBlocksByRootListenerValidatingProxy
    implements RpcResponseListener<SignedBeaconBlock> {

  private final Peer peer;
  private final RpcResponseListener<SignedBeaconBlock> listener;
  private final Set<Bytes32> expectedBlockRoots;

  public BeaconBlocksByRootListenerValidatingProxy(
      final Peer peer,
      final RpcResponseListener<SignedBeaconBlock> listener,
      final List<Bytes32> expectedBlockRoots) {
    this.peer = peer;
    this.listener = listener;
    this.expectedBlockRoots = ConcurrentHashMap.newKeySet();
    this.expectedBlockRoots.addAll(expectedBlockRoots);
  }

  @Override
  public SafeFuture<?> onResponse(final SignedBeaconBlock block) {
    return SafeFuture.of(
        () -> {
          if (!expectedBlockRoots.remove(block.getRoot())) {
            throw new BlocksByRootResponseInvalidResponseException(
                peer, InvalidResponseType.BLOCK_UNEXPECTED_ROOT);
          }
          return listener.onResponse(block);
        });
  }
}
