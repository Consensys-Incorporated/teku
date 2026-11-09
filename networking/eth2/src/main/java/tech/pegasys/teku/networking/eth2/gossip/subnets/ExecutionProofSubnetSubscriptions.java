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

package tech.pegasys.teku.networking.eth2.gossip.subnets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.ExecutionProofTopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProof;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionProofSchema;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ExecutionProofSubnetSubscriptions extends CommitteeSubnetSubscriptions {

  private final AsyncRunner asyncRunner;
  private final RecentChainData recentChainData;
  private final OperationProcessor<ExecutionProof> processor;
  private final ForkInfo forkInfo;
  private final Bytes4 forkDigest;
  private final ExecutionProofSchema executionProofSchema;
  private final DebugDataDumper debugDataDumper;

  private static final Logger LOG = LogManager.getLogger();

  public ExecutionProofSubnetSubscriptions(
      final Spec spec,
      final AsyncRunner asyncRunner,
      final GossipNetwork gossipNetwork,
      final GossipEncoding gossipEncoding,
      final RecentChainData recentChainData,
      final OperationProcessor<ExecutionProof> processor,
      final DebugDataDumper debugDataDumper,
      final ForkInfo forkInfo,
      final Bytes4 forkDigest) {
    super(gossipNetwork, gossipEncoding);
    this.asyncRunner = asyncRunner;
    this.recentChainData = recentChainData;
    this.processor = processor;
    this.debugDataDumper = debugDataDumper;
    this.forkInfo = forkInfo;
    this.forkDigest = forkDigest;
    final SpecVersion specVersion =
        spec.forMilestone(spec.getForkSchedule().getHighestSupportedMilestone());
    this.executionProofSchema =
        SchemaDefinitionsElectra.required(specVersion.getSchemaDefinitions())
            .getExecutionProofSchema();
  }

  public SafeFuture<?> gossip(final ExecutionProof executionProof) {
    int subnetId = executionProof.getSubnetId().get().intValue();
    final String topic =
        GossipTopics.getExecutionProofSubnetTopic(forkDigest, subnetId, gossipEncoding);
    return gossipNetwork.gossip(topic, gossipEncoding.encode(executionProof));
  }

  @Override
  protected Eth2TopicHandler<?> createTopicHandler(final int subnetId) {
    LOG.debug("Creating ExecutionProof topic handler for subnet {}", subnetId);
    final String topicName = GossipTopicName.getExecutionProofSubnetTopicName(subnetId);
    return ExecutionProofTopicHandler.createHandler(
        recentChainData,
        asyncRunner,
        processor,
        gossipEncoding,
        forkInfo,
        forkDigest,
        topicName,
        executionProofSchema,
        debugDataDumper);
  }
}
