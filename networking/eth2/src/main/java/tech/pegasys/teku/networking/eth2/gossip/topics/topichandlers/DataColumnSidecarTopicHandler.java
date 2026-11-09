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

package tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class DataColumnSidecarTopicHandler {

  public static Eth2TopicHandler<DataColumnSidecar> createHandler(
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final OperationProcessor<DataColumnSidecar> operationProcessor,
      final GossipEncoding gossipEncoding,
      final DebugDataDumper debugDataDumper,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final String topicName,
      final DataColumnSidecarSchema<DataColumnSidecar> dataColumnSidecarSchema,
      final int subnetId) {
    final Spec spec = recentChainData.getSpec();
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        new TopicSubnetIdAwareOperationProcessor(spec, subnetId, operationProcessor),
        gossipEncoding,
        forkDigest,
        topicName,
        new OperationMilestoneValidator<>(
            recentChainData.getSpec(),
            forkInfo.getFork(),
            message -> spec.computeEpochAtSlot(message.getSlot())),
        dataColumnSidecarSchema,
        spec.getNetworkingConfig(),
        debugDataDumper);
  }

  private static class TopicSubnetIdAwareOperationProcessor
      implements OperationProcessor<DataColumnSidecar> {
    private final int subnetId;
    private final OperationProcessor<DataColumnSidecar> delegate;
    private final MiscHelpersFulu miscHelpersFulu;

    TopicSubnetIdAwareOperationProcessor(
        final Spec spec, final int subnetId, final OperationProcessor<DataColumnSidecar> delegate) {
      this.subnetId = subnetId;
      this.delegate = delegate;
      this.miscHelpersFulu =
          MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
    }

    @Override
    public SafeFuture<InternalValidationResult> process(
        final DataColumnSidecar dataColumnSidecar, final Optional<UInt64> arrivalTimestamp) {
      final int dataColumnSidecarSubnet =
          miscHelpersFulu
              .computeSubnetForDataColumnSidecar(dataColumnSidecar.getIndex())
              .intValue();
      if (dataColumnSidecarSubnet != subnetId) {
        return SafeFuture.completedFuture(
            InternalValidationResult.reject(
                "DataColumnSidecar with subnet_id %s does not match the topic subnet_id %d",
                dataColumnSidecarSubnet, subnetId));
      }
      return delegate.process(dataColumnSidecar, arrivalTimestamp);
    }
  }
}
