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

package tech.pegasys.teku.beacon.sync.fetch;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class DefaultFetchTaskFactory implements FetchTaskFactory {

  private final P2PNetwork<Eth2Peer> eth2Network;

  public DefaultFetchTaskFactory(final P2PNetwork<Eth2Peer> eth2Network) {
    this.eth2Network = eth2Network;
  }

  @Override
  public FetchBlockTask createFetchBlockTask(
      final Bytes32 blockRoot, final Optional<Eth2Peer> preferredPeer) {
    return new FetchBlockTask(eth2Network, preferredPeer, blockRoot);
  }

  @Override
  public FetchBlobSidecarTask createFetchBlobSidecarTask(
      final BlobIdentifier blobIdentifier, final Optional<Eth2Peer> preferredPeer) {
    return new FetchBlobSidecarTask(eth2Network, preferredPeer, blobIdentifier);
  }

  @Override
  public FetchExecutionPayloadTask createFetchExecutionPayloadTask(
      final Bytes32 beaconBlockRoot, final Optional<Eth2Peer> preferredPeer) {
    return new FetchExecutionPayloadTask(eth2Network, preferredPeer, beaconBlockRoot);
  }
}
