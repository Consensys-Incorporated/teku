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

package tech.pegasys.teku.networking.eth2.gossip;

import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getBlobSidecarSubnetTopicName;

import com.google.common.annotations.VisibleForTesting;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.Optional;
import java.util.stream.IntStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecarSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsDeneb;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BlobSidecarGossipManager implements GossipManager {
  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final GossipNetwork gossipNetwork;
  private final GossipEncoding gossipEncoding;
  private final Int2ObjectMap<Eth2TopicHandler<BlobSidecar>> subnetIdToTopicHandler;
  private final GossipFailureLogger gossipFailureLogger;

  private final Int2ObjectMap<TopicChannel> subnetIdToChannel = new Int2ObjectOpenHashMap<>();

  public static BlobSidecarGossipManager create(
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final OperationProcessor<BlobSidecar> processor,
      final DebugDataDumper debugDataDumper) {
    final SpecVersion forkSpecVersion = spec.atEpoch(forkInfo.getFork().getEpoch());
    final BlobSidecarSchema gossipType =
        SchemaDefinitionsDeneb.required(forkSpecVersion.getSchemaDefinitions())
            .getBlobSidecarSchema();
    final Int2ObjectMap<Eth2TopicHandler<BlobSidecar>> subnetIdToTopicHandler =
        new Int2ObjectOpenHashMap<>();
    final int blobSidecarSubnetCount =
        SpecConfigDeneb.required(spec.atEpoch(forkInfo.getFork().getEpoch()).getConfig())
            .getBlobSidecarSubnetCount();

    IntStream.range(0, blobSidecarSubnetCount)
        .forEach(
            subnetId -> {
              final Eth2TopicHandler<BlobSidecar> topicHandler =
                  createBlobSidecarTopicHandler(
                      subnetId,
                      recentChainData,
                      spec,
                      asyncRunner,
                      processor,
                      gossipEncoding,
                      forkInfo,
                      forkDigest,
                      gossipType,
                      debugDataDumper);
              subnetIdToTopicHandler.put(subnetId, topicHandler);
            });
    return new BlobSidecarGossipManager(
        spec,
        gossipNetwork,
        gossipEncoding,
        subnetIdToTopicHandler,
        GossipFailureLogger.createNonSuppressing("blob_sidecar"));
  }

  private BlobSidecarGossipManager(
      final Spec spec,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final Int2ObjectMap<Eth2TopicHandler<BlobSidecar>> subnetIdToTopicHandler,
      final GossipFailureLogger gossipFailureLogger) {
    this.spec = spec;
    this.gossipNetwork = gossipNetwork;
    this.gossipEncoding = gossipEncoding;
    this.subnetIdToTopicHandler = subnetIdToTopicHandler;
    this.gossipFailureLogger = gossipFailureLogger;
  }

  /**
   * This method is designed to return a future that only completes successfully whenever the gossip
   * was succeeded (sent to at least one peer) or failed.
   */
  public SafeFuture<Void> publishBlobSidecar(final BlobSidecar message) {
    final int subnetId = spec.computeSubnetForBlobSidecar(message).intValue();
    return Optional.ofNullable(subnetIdToChannel.get(subnetId))
        .map(channel -> channel.gossip(gossipEncoding.encode(message)))
        .orElse(SafeFuture.failedFuture(new IllegalStateException("Gossip channel not available")))
        .handle(
            (__, err) -> {
              if (err != null) {
                gossipFailureLogger.log(err, Optional.of(message.getSlot()));
              } else {
                LOG.trace(
                    "Successfully gossiped blob sidecar {} on {}",
                    () -> message.getSlotAndBlockRoot().toLogString(),
                    () -> subnetIdToTopicHandler.get(subnetId).getTopic());
              }
              return null;
            });
  }

  @VisibleForTesting
  Eth2TopicHandler<BlobSidecar> getTopicHandler(final int subnetId) {
    return subnetIdToTopicHandler.get(subnetId);
  }

  @Override
  public void subscribe() {
    subnetIdToTopicHandler
        .int2ObjectEntrySet()
        .forEach(
            entry -> {
              final Eth2TopicHandler<BlobSidecar> topicHandler = entry.getValue();
              final TopicChannel channel =
                  gossipNetwork.subscribe(topicHandler.getTopic(), topicHandler);
              subnetIdToChannel.put(entry.getIntKey(), channel);
            });
  }

  @Override
  public void unsubscribe() {
    subnetIdToChannel.values().forEach(TopicChannel::close);
    subnetIdToChannel.clear();
  }

  @Override
  public boolean isEnabledDuringOptimisticSync() {
    return true;
  }

  private static Eth2TopicHandler<BlobSidecar> createBlobSidecarTopicHandler(
      final int subnetId,
      final RecentChainData recentChainData,
      final Spec spec,
      final AsyncRunner asyncRunner,
      final OperationProcessor<BlobSidecar> processor,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final BlobSidecarSchema gossipType,
      final DebugDataDumper debugDataDumper) {
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        new TopicSubnetIdAwareOperationProcessor(spec, subnetId, processor),
        gossipEncoding,
        forkDigest,
        getBlobSidecarSubnetTopicName(subnetId),
        new OperationMilestoneValidator<>(
            spec,
            forkInfo.getFork(),
            blobSidecar -> spec.computeEpochAtSlot(blobSidecar.getSlot())),
        gossipType,
        spec.getNetworkingConfig(),
        debugDataDumper);
  }

  @VisibleForTesting
  public record TopicSubnetIdAwareOperationProcessor(
      Spec spec, int subnetId, OperationProcessor<BlobSidecar> delegate)
      implements OperationProcessor<BlobSidecar> {

    @Override
    public SafeFuture<InternalValidationResult> process(
        final BlobSidecar blobSidecar, final Optional<UInt64> arrivalTimestamp) {
      final int blobSidecarSubnet = spec.computeSubnetForBlobSidecar(blobSidecar).intValue();
      if (blobSidecarSubnet != subnetId) {
        return SafeFuture.completedFuture(
            InternalValidationResult.reject(
                "blob sidecar with subnet_id %s does not match the topic subnet_id %d",
                blobSidecarSubnet, subnetId));
      }
      return delegate.process(blobSidecar, arrivalTimestamp);
    }
  }
}
