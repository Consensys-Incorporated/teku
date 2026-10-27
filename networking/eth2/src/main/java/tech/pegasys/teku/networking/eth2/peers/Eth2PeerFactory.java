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

package tech.pegasys.teku.networking.eth2.peers;

import static tech.pegasys.teku.spec.constants.NetworkConstants.MAX_REQUEST_LIGHT_CLIENT_UPDATES;

import java.util.Optional;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethods;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.MetadataMessagesFactory;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.StatusMessageFactory;
import tech.pegasys.teku.networking.p2p.peer.Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;

public class Eth2PeerFactory {

  private static final long TIME_OUT = 60;
  private final Spec spec;
  private final StatusMessageFactory statusMessageFactory;
  private final MetadataMessagesFactory metadataMessagesFactory;
  private final MetricsSystem metricsSystem;
  private final CombinedChainDataClient chainDataClient;
  private final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider;
  private final TimeProvider timeProvider;
  private final Optional<Checkpoint> requiredCheckpoint;
  private final int peerBlocksRateLimit;
  private final int peerBlobSidecarsRateLimit;
  private final int peerRequestLimit;
  private final DiscoveryNodeIdExtractor discoveryNodeIdExtractor;
  private final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator;

  public Eth2PeerFactory(
      final Spec spec,
      final MetricsSystem metricsSystem,
      final CombinedChainDataClient chainDataClient,
      final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider,
      final StatusMessageFactory statusMessageFactory,
      final MetadataMessagesFactory metadataMessagesFactory,
      final TimeProvider timeProvider,
      final Optional<Checkpoint> requiredCheckpoint,
      final int peerBlocksRateLimit,
      final int peerBlobSidecarsRateLimit,
      final int peerRequestLimit,
      final DiscoveryNodeIdExtractor discoveryNodeIdExtractor) {
    this.spec = spec;
    this.metricsSystem = metricsSystem;
    this.chainDataClient = chainDataClient;
    this.blobKzgCommitmentsProvider = blobKzgCommitmentsProvider;
    this.timeProvider = timeProvider;
    this.statusMessageFactory = statusMessageFactory;
    this.metadataMessagesFactory = metadataMessagesFactory;
    this.requiredCheckpoint = requiredCheckpoint;
    this.peerBlocksRateLimit = peerBlocksRateLimit;
    this.peerBlobSidecarsRateLimit = peerBlobSidecarsRateLimit;
    this.peerRequestLimit = peerRequestLimit;
    this.discoveryNodeIdExtractor = discoveryNodeIdExtractor;
    this.dataColumnSidecarSignatureValidator =
        new DataColumnSidecarSignatureValidator(spec, chainDataClient);
  }

  public Eth2Peer create(final Peer peer, final BeaconChainMethods rpcMethods) {
    return Eth2Peer.create(
        spec,
        peer,
        discoveryNodeIdExtractor.calculateDiscoveryNodeId(peer),
        rpcMethods,
        statusMessageFactory,
        metadataMessagesFactory,
        PeerChainValidator.create(spec, metricsSystem, chainDataClient, requiredCheckpoint),
        dataColumnSidecarSignatureValidator,
        RateTracker.create(peerBlocksRateLimit, TIME_OUT, timeProvider, "blocks"),
        RateTracker.create(peerBlobSidecarsRateLimit, TIME_OUT, timeProvider, "blob_sidecars"),
        RateTracker.create(
            peerBlocksRateLimit * spec.getNumberOfDataColumns().orElse(1),
            TIME_OUT,
            timeProvider,
            "data_column_sidecars"),
        RateTracker.create(
            peerBlocksRateLimit, TIME_OUT, timeProvider, "execution_payload_envelopes"),
        RateTracker.create(
            MAX_REQUEST_LIGHT_CLIENT_UPDATES, TIME_OUT, timeProvider, "light_client_updates"),
        RateTracker.create(peerRequestLimit, TIME_OUT, timeProvider, "request_tracker"),
        metricsSystem,
        timeProvider,
        blobKzgCommitmentsProvider);
  }
}
