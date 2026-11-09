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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(milestone = {SpecMilestone.DENEB, SpecMilestone.ELECTRA})
public class BlobSidecarGossipManagerTest {

  private static final Pattern BLOB_SIDECAR_TOPIC_PATTERN = Pattern.compile("blob_sidecar_(\\d+)");

  @SuppressWarnings("unchecked")
  private final OperationProcessor<BlobSidecar> processor = mock(OperationProcessor.class);

  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final Map<Integer, TopicChannel> topicChannels = new HashMap<>();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private BlobSidecarGossipManager blobSidecarGossipManager;
  private SpecMilestone specMilestone;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    specMilestone = specContext.getSpecMilestone();
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    // return TopicChannel mock for each blob_sidecar_<subnet_id> topic
    doAnswer(
            i -> {
              final String topicName = i.getArgument(0);
              final TopicChannel topicChannel = mock(TopicChannel.class);
              final Matcher matcher = BLOB_SIDECAR_TOPIC_PATTERN.matcher(topicName);
              if (!matcher.find()) {
                Assertions.fail(
                    BLOB_SIDECAR_TOPIC_PATTERN + " regex does not match the topic: " + topicName);
              }
              final int subnetId = Integer.parseInt(matcher.group(1));
              topicChannels.put(subnetId, topicChannel);
              return topicChannel;
            })
        .when(gossipNetwork)
        .subscribe(any(), any());
    when(processor.process(any(), any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final ForkInfo forkInfo =
        new ForkInfo(spec.fork(UInt64.ZERO), dataStructureUtil.randomBytes32());
    final Bytes4 forkDigest = dataStructureUtil.randomBytes4();
    blobSidecarGossipManager =
        BlobSidecarGossipManager.create(
            storageSystem.recentChainData(),
            spec,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            forkInfo,
            forkDigest,
            processor,
            DebugDataDumper.NOOP);
    blobSidecarGossipManager.subscribe();
  }

  @TestTemplate
  public void testGossipingBlobSidecarPublishesToCorrectSubnet() {
    final BlobSidecar blobSidecar =
        dataStructureUtil.createRandomBlobSidecarBuilder().index(UInt64.ONE).build();
    final Bytes serialized = gossipEncoding.encode(blobSidecar);

    safeJoin(blobSidecarGossipManager.publishBlobSidecar(blobSidecar));

    topicChannels.forEach(
        (subnetId, channel) -> {
          if (subnetId == 1) {
            verify(channel).gossip(serialized);
          } else {
            verifyNoInteractions(channel);
          }
        });
  }

  @TestTemplate
  public void testGossipingBlobSidecarWithLargeIndexGossipToCorrectSubnet() {
    final BlobSidecar blobSidecar =
        dataStructureUtil.createRandomBlobSidecarBuilder().index(UInt64.valueOf(10)).build();
    final Bytes serialized = gossipEncoding.encode(blobSidecar);

    safeJoin(blobSidecarGossipManager.publishBlobSidecar(blobSidecar));
    final int blobSidecarSubnetCount =
        SpecConfigDeneb.required(spec.forMilestone(specMilestone).getConfig())
            .getBlobSidecarSubnetCount();

    topicChannels.forEach(
        (subnetId, channel) -> {
          if (subnetId == 10 % blobSidecarSubnetCount) {
            verify(channel).gossip(serialized);
          } else {
            verifyNoInteractions(channel);
          }
        });
  }

  @TestTemplate
  public void testUnsubscribingClosesAllChannels() {
    blobSidecarGossipManager.unsubscribe();

    topicChannels
        .values()
        .forEach(
            channel -> {
              verify(channel).close();
            });
  }

  @TestTemplate
  public void testAcceptingSidecarGossipIfOnTheCorrectTopic() {
    // topic handler for blob sidecars with subnet_id 1
    final Eth2TopicHandler<BlobSidecar> topicHandler = blobSidecarGossipManager.getTopicHandler(1);

    // processing blob sidecar with subnet_id 1 should be accepted
    final BlobSidecar blobSidecar =
        dataStructureUtil.createRandomBlobSidecarBuilder().index(UInt64.ONE).build();

    System.out.println(blobSidecar);
    final InternalValidationResult validationResult =
        safeJoin(topicHandler.getProcessor().process(blobSidecar, Optional.empty()));

    assertThat(validationResult).isEqualTo(InternalValidationResult.ACCEPT);
  }

  @TestTemplate
  public void testRejectingSidecarGossipIfNotOnTheCorrectTopic() {
    // topic handler for blob sidecars with subnet_id 1
    final Eth2TopicHandler<BlobSidecar> topicHandler = blobSidecarGossipManager.getTopicHandler(1);

    // processing blob sidecar with subnet_id 2 should be rejected
    final BlobSidecar blobSidecar =
        dataStructureUtil.createRandomBlobSidecarBuilder().index(UInt64.valueOf(2)).build();
    final InternalValidationResult validationResult =
        safeJoin(topicHandler.getProcessor().process(blobSidecar, Optional.empty()));

    assertThat(validationResult.isReject()).isTrue();
    assertThat(validationResult.getDescription())
        .hasValue("blob sidecar with subnet_id 2 does not match the topic subnet_id 1");
  }
}
