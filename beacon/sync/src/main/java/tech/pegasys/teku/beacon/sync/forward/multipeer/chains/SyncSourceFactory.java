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

package tech.pegasys.teku.beacon.sync.forward.multipeer.chains;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.SyncSource;
import tech.pegasys.teku.spec.Spec;

public class SyncSourceFactory {

  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final int batchSize;
  private final int maxBlocksPerMinute;
  private final int maxBlobSidecarsPerMinute;

  private final Map<Eth2Peer, SyncSource> syncSourcesByPeer = new HashMap<>();

  public SyncSourceFactory(
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final int batchSize,
      final int maxBlocksPerMinute,
      final int maxBlobSidecarsPerMinute) {
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
    this.batchSize = batchSize;
    this.maxBlocksPerMinute = maxBlocksPerMinute;
    this.maxBlobSidecarsPerMinute = maxBlobSidecarsPerMinute;
  }

  public SyncSource getOrCreateSyncSource(final Eth2Peer peer, final Spec spec) {
    // Limit request rates for blocks/blobs/execution payloads to just a little under what we'd
    // accept (see Eth2PeerFactory)
    final int maxBlocksPerMinute = this.maxBlocksPerMinute - batchSize - 1;
    final Optional<Integer> maybeMaxBlobsPerBlock = spec.getMaxBlobsPerBlockForHighestMilestone();
    final Optional<Integer> maybeMaxBlobSidecarsPerMinute =
        maybeMaxBlobsPerBlock.map(
            maxBlobsPerBlock -> this.maxBlobSidecarsPerMinute - (batchSize * maxBlobsPerBlock) - 1);
    final Optional<Integer> maybeMaxDataColumnSidecarsPerMinute =
        spec.getNumberOfDataColumns()
            .map(dataColumnsPerBlock -> maxBlocksPerMinute * dataColumnsPerBlock);
    final Optional<Integer> maybeMaxExecutionPayloadEnvelopesPerMinute =
        spec.getNetworkingConfigGloas().map(__ -> maxBlocksPerMinute);
    return syncSourcesByPeer.computeIfAbsent(
        peer,
        source ->
            new ThrottlingSyncSource(
                asyncRunner,
                timeProvider,
                source,
                maxBlocksPerMinute,
                maybeMaxBlobsPerBlock,
                maybeMaxBlobSidecarsPerMinute,
                maybeMaxDataColumnSidecarsPerMinute,
                maybeMaxExecutionPayloadEnvelopesPerMinute));
  }

  public void onPeerDisconnected(final Eth2Peer peer) {
    syncSourcesByPeer.remove(peer);
  }
}
