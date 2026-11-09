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
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.network.P2PNetwork;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;

public class FetchExecutionPayloadTask
    extends AbstractFetchTask<Bytes32, SignedExecutionPayloadEnvelope> {

  private static final Logger LOG = LogManager.getLogger();

  private final Bytes32 beaconBlockRoot;

  protected FetchExecutionPayloadTask(
      final P2PNetwork<Eth2Peer> eth2Network, final Bytes32 beaconBlockRoot) {
    super(eth2Network, Optional.empty());
    this.beaconBlockRoot = beaconBlockRoot;
  }

  public FetchExecutionPayloadTask(
      final P2PNetwork<Eth2Peer> eth2Network,
      final Optional<Eth2Peer> preferredPeer,
      final Bytes32 beaconBlockRoot) {
    super(eth2Network, preferredPeer);
    this.beaconBlockRoot = beaconBlockRoot;
  }

  @Override
  public Bytes32 getKey() {
    return beaconBlockRoot;
  }

  @Override
  SafeFuture<FetchResult<SignedExecutionPayloadEnvelope>> fetch(final Eth2Peer peer) {
    return peer.requestExecutionPayloadEnvelopeByRoot(beaconBlockRoot)
        .thenApply(
            maybeExecutionPayload ->
                maybeExecutionPayload
                    .map(executionPayload -> FetchResult.createSuccessful(peer, executionPayload))
                    .orElseGet(
                        () -> FetchResult.createFailed(peer, FetchResult.Status.FETCH_FAILED)))
        .exceptionally(
            err -> {
              LOG.debug(
                  "Failed to fetch execution payload by beacon block root {} from peer {}",
                  beaconBlockRoot,
                  peer.getId(),
                  err);
              return FetchResult.createFailed(peer, FetchResult.Status.FETCH_FAILED);
            });
  }
}
