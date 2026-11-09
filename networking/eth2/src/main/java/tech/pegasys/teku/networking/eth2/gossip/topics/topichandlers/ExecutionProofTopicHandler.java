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

import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationMilestoneValidator;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionProofTopicHandler {

  public static Eth2TopicHandler<?> createHandler(
      final RecentChainData recentChainData,
      final AsyncRunner asyncRunner,
      final OperationProcessor<ExecutionProof> operationProcessor,
      final GossipEncoding gossipEncoding,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest,
      final String topicName,
      final ExecutionProofSchema executionProofSchema,
      final DebugDataDumper debugDataDumper) {
    final Spec spec = recentChainData.getSpec();
    return new Eth2TopicHandler<>(
        recentChainData,
        asyncRunner,
        operationProcessor,
        gossipEncoding,
        forkDigest,
        topicName,
        new OperationMilestoneValidator<>(
            recentChainData.getSpec(),
            forkInfo.getFork(),
            __ -> spec.computeEpochAtSlot(recentChainData.getHeadSlot())),
        executionProofSchema,
        recentChainData.getSpec().getNetworkingConfig(),
        debugDataDumper);
  }
}
