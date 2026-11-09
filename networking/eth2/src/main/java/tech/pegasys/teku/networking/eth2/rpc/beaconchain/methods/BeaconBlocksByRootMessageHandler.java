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

import static tech.pegasys.teku.networking.eth2.rpc.core.RpcResponseStatus.INVALID_REQUEST_CODE;

import com.google.common.annotations.VisibleForTesting;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.hyperledger.besu.plugin.services.metrics.Counter;
import org.hyperledger.besu.plugin.services.metrics.LabelledMetric;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.eth2.peers.RequestKey;
import tech.pegasys.teku.networking.eth2.rpc.beaconchain.BeaconChainMethodIds;
import tech.pegasys.teku.networking.eth2.rpc.core.PeerRequiredLocalMessageHandler;
import tech.pegasys.teku.networking.eth2.rpc.core.ResponseCallback;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException;
import tech.pegasys.teku.networking.eth2.rpc.core.RpcException.InvalidRpcMethodVersion;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BeaconBlocksByRootRequestMessage;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BeaconBlocksByRootMessageHandler
    extends PeerRequiredLocalMessageHandler<BeaconBlocksByRootRequestMessage, SignedBeaconBlock> {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final Counter totalBlocksRequestedCounter;
  private final LabelledMetric<Counter> requestCounter;

  public BeaconBlocksByRootMessageHandler(
      final Spec spec, final MetricsSystem metricsSystem, final RecentChainData recentChainData) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    requestCounter =
        metricsSystem.createLabelledCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blocks_by_root_requests_total",
            "Total number of blocks by root requests received",
            "status");
    totalBlocksRequestedCounter =
        metricsSystem.createCounter(
            TekuMetricCategory.NETWORK,
            "rpc_blocks_by_root_requested_blocks_total",
            "Total number of blocks requested in accepted blocks by root requests from peers");
  }

  @Override
  public Optional<RpcException> validateRequest(
      final String protocolId, final BeaconBlocksByRootRequestMessage request) {
    final UInt64 maxRequestBlocks = getMaxRequestBlocks();

    if (request.size() > maxRequestBlocks.intValue()) {
      requestCounter.labels("count_too_big").inc();
      return Optional.of(
          new RpcException(
              INVALID_REQUEST_CODE,
              "Only a maximum of " + maxRequestBlocks + " blocks can be requested per request"));
    }

    return Optional.empty();
  }

  @Override
  public void onIncomingMessage(
      final String protocolId,
      final Eth2Peer peer,
      final BeaconBlocksByRootRequestMessage message,
      final ResponseCallback<SignedBeaconBlock> callback) {
    LOG.trace(
        "Peer {} requested {} BeaconBlocks with roots: {}", peer.getId(), message.size(), message);

    final Optional<RequestKey> maybeRequestKey =
        peer.approveBlocksRequest(callback, message.size());

    if (!peer.approveRequest() || maybeRequestKey.isEmpty()) {
      requestCounter.labels("rate_limited").inc();
      return;
    }

    requestCounter.labels("ok").inc();
    totalBlocksRequestedCounter.inc(message.size());
    final AtomicInteger sentBlocks = new AtomicInteger(0);
    final UInt64 minServableEpoch = computeMinServableEpoch();

    SafeFuture<Void> future = SafeFuture.COMPLETE;

    for (final SszBytes32 blockRoot : message) {
      future =
          future.thenCompose(
              __ ->
                  retrieveBlock(blockRoot.get())
                      .thenCompose(
                          maybeBlock ->
                              processBlockRequest(
                                  protocolId, maybeBlock, callback, sentBlocks, minServableEpoch)));
    }

    future.finish(
        () -> {
          if (sentBlocks.get() != message.size()) {
            peer.adjustBlocksRequest(maybeRequestKey.get(), sentBlocks.get());
          }
          callback.completeSuccessfully();
        },
        err -> handleError(err, callback, "blocks by root"));
  }

  private SafeFuture<Void> processBlockRequest(
      final String protocolId,
      final Optional<SignedBeaconBlock> maybeBlock,
      final ResponseCallback<SignedBeaconBlock> callback,
      final AtomicInteger sentBlocks,
      final UInt64 minServableEpoch) {
    final Optional<SignedBeaconBlock> maybeBlockWithinRange =
        maybeBlock.filter(block -> isBlockWithinServableRange(block, minServableEpoch));
    if (maybeBlockWithinRange.isEmpty()) {
      return SafeFuture.COMPLETE;
    }

    final SignedBeaconBlock block = maybeBlockWithinRange.get();
    final Optional<RpcException> maybeError = validateResponse(protocolId, block);
    if (maybeError.isPresent()) {
      return SafeFuture.failedFuture(maybeError.get());
    }

    return callback.respond(block).thenRun(sentBlocks::incrementAndGet);
  }

  private SafeFuture<Optional<SignedBeaconBlock>> retrieveBlock(final Bytes32 blockRoot) {
    final Optional<SignedBeaconBlock> recentlyValidatedSignedBlock =
        recentChainData.getRecentlyValidatedSignedBlockByRoot(blockRoot);
    if (recentlyValidatedSignedBlock.isPresent()) {
      return SafeFuture.completedFuture(recentlyValidatedSignedBlock);
    }
    return recentChainData.retrieveSignedBlockByRoot(blockRoot);
  }

  private UInt64 getMaxRequestBlocks() {
    final UInt64 currentEpoch = recentChainData.getCurrentEpoch().orElse(UInt64.ZERO);
    final SpecMilestone milestone = spec.getForkSchedule().getSpecMilestoneAtEpoch(currentEpoch);
    return spec.forMilestone(milestone).miscHelpers().getMaxRequestBlocks();
  }

  private UInt64 computeMinServableEpoch() {
    final UInt64 currentEpoch = recentChainData.getCurrentEpoch().orElse(UInt64.ZERO);
    return currentEpoch.minusMinZero(spec.getNetworkingConfig().getMinEpochsForBlockRequests());
  }

  private boolean isBlockWithinServableRange(
      final SignedBeaconBlock block, final UInt64 minServableEpoch) {
    return spec.computeEpochAtSlot(block.getSlot()).isGreaterThanOrEqualTo(minServableEpoch);
  }

  @VisibleForTesting
  Optional<RpcException> validateResponse(
      final String protocolId, final SignedBeaconBlock response) {
    final int version = BeaconChainMethodIds.extractBeaconBlocksByRootVersion(protocolId);
    final SpecMilestone milestoneAtResponse =
        spec.getForkSchedule().getSpecMilestoneAtSlot(response.getSlot());
    final boolean isAltairActive = milestoneAtResponse.isGreaterThanOrEqualTo(SpecMilestone.ALTAIR);

    if (version == 1 && isAltairActive) {
      return Optional.of(
          new InvalidRpcMethodVersion("Must request altair blocks using v2 protocol"));
    }

    return Optional.empty();
  }
}
