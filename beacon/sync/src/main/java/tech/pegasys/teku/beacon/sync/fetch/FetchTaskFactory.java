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
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public interface FetchTaskFactory {

  default FetchBlockTask createFetchBlockTask(final Bytes32 blockRoot) {
    return createFetchBlockTask(blockRoot, Optional.empty());
  }

  FetchBlockTask createFetchBlockTask(Bytes32 blockRoot, Optional<Eth2Peer> preferredPeer);

  default FetchBlobSidecarTask createFetchBlobSidecarTask(final BlobIdentifier blobIdentifier) {
    return createFetchBlobSidecarTask(blobIdentifier, Optional.empty());
  }

  FetchBlobSidecarTask createFetchBlobSidecarTask(
      BlobIdentifier blobIdentifier, Optional<Eth2Peer> preferredPeer);

  default FetchExecutionPayloadTask createFetchExecutionPayloadTask(final Bytes32 beaconBlockRoot) {
    return createFetchExecutionPayloadTask(beaconBlockRoot, Optional.empty());
  }

  FetchExecutionPayloadTask createFetchExecutionPayloadTask(
      Bytes32 beaconBlockRoot, Optional<Eth2Peer> preferredPeer);
}
