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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.beacon.sync.fetch.FetchResult.Status;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;

public class FetchBlobSidecarTask extends AbstractFetchTask<BlobIdentifier, BlobSidecar> {

  private static final Logger LOG = LogManager.getLogger();

  private final BlobIdentifier blobIdentifier;

  FetchBlobSidecarTask(
      final P2PNetwork<Eth2Peer> eth2Network, final BlobIdentifier blobIdentifier) {
    super(eth2Network, Optional.empty());
    this.blobIdentifier = blobIdentifier;
  }

  public FetchBlobSidecarTask(
      final P2PNetwork<Eth2Peer> eth2Network,
      final Optional<Eth2Peer> preferredPeer,
      final BlobIdentifier blobIdentifier) {
    super(eth2Network, preferredPeer);
    this.blobIdentifier = blobIdentifier;
  }

  @Override
  public BlobIdentifier getKey() {
    return blobIdentifier;
  }

  @Override
  SafeFuture<FetchResult<BlobSidecar>> fetch(final Eth2Peer peer) {
    return peer.requestBlobSidecarByRoot(blobIdentifier)
        .thenApply(
            maybeBlobSidecar ->
                maybeBlobSidecar
                    .map(blobSidecar -> FetchResult.createSuccessful(peer, blobSidecar))
                    .orElseGet(() -> FetchResult.createFailed(peer, Status.FETCH_FAILED)))
        .exceptionally(
            err -> {
              LOG.debug(
                  "Failed to fetch blob sidecar by identifier {} from peer {}",
                  blobIdentifier,
                  peer.getId(),
                  err);
              return FetchResult.createFailed(peer, Status.FETCH_FAILED);
            });
  }
}
