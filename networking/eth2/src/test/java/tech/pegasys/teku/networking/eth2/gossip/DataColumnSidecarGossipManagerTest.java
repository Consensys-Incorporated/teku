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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import com.google.common.base.Supplier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.DataColumnSidecarSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.networking.p2p.gossip.TopicChannel;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.log.gossip.DasGossipLogger;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(milestone = {SpecMilestone.FULU, SpecMilestone.GLOAS})
public class DataColumnSidecarGossipManagerTest {
  private static final Pattern DATA_COLUMN_SIDECAR_TOPIC_PATTERN =
      Pattern.compile("data_column_sidecar_(\\d+)");

  @SuppressWarnings("unchecked")
  private final OperationProcessor<DataColumnSidecar> processor = mock(OperationProcessor.class);

  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final Map<Integer, TopicChannel> topicChannels = new HashMap<>();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final DasGossipLogger dasGossipLogger = mock(DasGossipLogger.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private DataColumnSidecarSubnetSubscriptions subnetSubscriptions;
  private DataColumnSidecarGossipManager dataColumnSidecarGossipManager;
  private SpecMilestone specMilestone;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    specMilestone = specContext.getSpecMilestone();
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    // return TopicChannel mock for each data_column_sidecar_<subnet_id> topic
    doAnswer(
            i -> {
              final String topicName = i.getArgument(0);
              final TopicChannel topicChannel = mock(TopicChannel.class);
              final Matcher matcher = DATA_COLUMN_SIDECAR_TOPIC_PATTERN.matcher(topicName);
              if (!matcher.find()) {
                Assertions.fail(
                    DATA_COLUMN_SIDECAR_TOPIC_PATTERN
                        + " regex does not match the topic: "
                        + topicName);
              }
              final int subnetId = Integer.parseInt(matcher.group(1));
              topicChannels.put(subnetId, topicChannel);
              return topicChannel;
            })
        .when(gossipNetwork)
        .subscribe(any(), any());
    when(processor.process(any(), any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    when(gossipNetwork.gossip(any(), any())).thenReturn(SafeFuture.completedFuture(null));

    final ForkInfo forkInfo =
        new ForkInfo(spec.fork(UInt64.ZERO), dataStructureUtil.randomBytes32());
    final Bytes4 forkDigest = dataStructureUtil.randomBytes4();

    subnetSubscriptions =
        new DataColumnSidecarSubnetSubscriptions(
            spec,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            storageSystem.recentChainData(),
            processor,
            DebugDataDumper.NOOP,
            forkInfo,
            forkDigest);

    dataColumnSidecarGossipManager =
        new DataColumnSidecarGossipManager(subnetSubscriptions, dasGossipLogger, () -> false);
    IntStream.range(0, spec.getNumberOfDataColumnSubnets().orElseThrow())
        .forEach(dataColumnSidecarGossipManager::subscribeToSubnetId);
  }

  @TestTemplate
  public void testGossipingDataColumnSidecarPublishesToCorrectSubnet() {
    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        dataStructureUtil.randomSignedBeaconBlockHeader();
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecar(signedBeaconBlockHeader, UInt64.ONE);
    final Bytes serialized = gossipEncoding.encode(dataColumnSidecar);

    dataColumnSidecarGossipManager.publish(dataColumnSidecar);

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
  public void testGossipingDataColumnSidecarWithLargeIndexGossipToCorrectSubnet() {
    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        dataStructureUtil.randomSignedBeaconBlockHeader();
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecar(signedBeaconBlockHeader, UInt64.valueOf(200));
    final Bytes serialized = gossipEncoding.encode(dataColumnSidecar);

    dataColumnSidecarGossipManager.publish(dataColumnSidecar);
    final int dataColumnSidecarSubnetCount =
        SpecConfigFulu.required(spec.forMilestone(specMilestone).getConfig())
            .getDataColumnSidecarSubnetCount();

    topicChannels.forEach(
        (subnetId, channel) -> {
          if (subnetId == 200 % dataColumnSidecarSubnetCount) {
            verify(channel).gossip(serialized);
          } else {
            verifyNoInteractions(channel);
          }
        });
  }

  @TestTemplate
  public void testUnsubscribingClosesAllChannels() {
    dataColumnSidecarGossipManager.unsubscribe();

    topicChannels
        .values()
        .forEach(
            channel -> {
              verify(channel).close();
            });
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  public void testAcceptingSidecarGossipIfOnTheCorrectTopic() {
    // topic handler for data column sidecars with subnet_id 1
    subnetSubscriptions.subscribeToSubnetId(1);
    final Eth2TopicHandler<DataColumnSidecar> topicHandler =
        (Eth2TopicHandler<DataColumnSidecar>) subnetSubscriptions.getSubscribedTopicHandler(1);

    // processing blob sidecar with subnet_id 1 should be accepted
    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        dataStructureUtil.randomSignedBeaconBlockHeader();
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecar(signedBeaconBlockHeader, UInt64.ONE);

    final InternalValidationResult validationResult =
        safeJoin(topicHandler.getProcessor().process(dataColumnSidecar, Optional.empty()));

    assertThat(validationResult).isEqualTo(InternalValidationResult.ACCEPT);
  }

  @TestTemplate
  @SuppressWarnings("unchecked")
  public void testRejectingSidecarGossipIfNotOnTheCorrectTopic() {
    // topic handler for data column sidecars with subnet_id 1
    subnetSubscriptions.subscribeToSubnetId(1);
    final Eth2TopicHandler<DataColumnSidecar> topicHandler =
        (Eth2TopicHandler<DataColumnSidecar>) subnetSubscriptions.getSubscribedTopicHandler(1);

    // processing blob sidecar with subnet_id 2 should be rejected
    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        dataStructureUtil.randomSignedBeaconBlockHeader();
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecar(signedBeaconBlockHeader, UInt64.valueOf(2));

    final InternalValidationResult validationResult =
        safeJoin(topicHandler.getProcessor().process(dataColumnSidecar, Optional.empty()));

    assertThat(validationResult.isReject()).isTrue();
    assertThat(validationResult.getDescription())
        .hasValue("DataColumnSidecar with subnet_id 2 does not match the topic subnet_id 1");
  }

  @TestTemplate
  public void testMultipleSubnetSubscriptions() {
    reset(dasGossipLogger);
    dataColumnSidecarGossipManager.unsubscribe();
    dataColumnSidecarGossipManager.subscribeToSubnetId(1);
    dataColumnSidecarGossipManager.subscribeToSubnetId(2);
    dataColumnSidecarGossipManager.subscribeToSubnetId(3);

    verify(dasGossipLogger).onDataColumnSubnetSubscribe(1);
    verify(dasGossipLogger).onDataColumnSubnetSubscribe(2);
    verify(dasGossipLogger).onDataColumnSubnetSubscribe(3);
  }

  @TestTemplate
  public void testSubnetUnsubscriptions() {
    // Subscribe first
    dataColumnSidecarGossipManager.subscribeToSubnetId(1);
    // Then unsubscribe
    dataColumnSidecarGossipManager.unsubscribeFromSubnetId(1);

    verify(dasGossipLogger).onDataColumnSubnetUnsubscribe(1);
  }

  @TestTemplate
  public void testPublishNotLinkedToSubscription() {
    dataColumnSidecarGossipManager.unsubscribe();
    final DataColumnSidecar dataColumnSidecar = dataStructureUtil.randomDataColumnSidecar();
    dataColumnSidecarGossipManager.publish(dataColumnSidecar);

    // Ensure async runner executes the tasks
    asyncRunner.executeDueActions();
    assertThat(asyncRunner.hasDelayedActions()).isFalse();

    verify(dasGossipLogger).onPublish(any(), any());
    verify(gossipNetwork).gossip(any(), any(Bytes.class));
  }

  @TestTemplate
  public void testIsEnabledDuringOptimisticSync_whenSuperNodeSupplierReturnsTrue() {
    final DataColumnSidecarGossipManager managerWithSuperNode =
        new DataColumnSidecarGossipManager(subnetSubscriptions, dasGossipLogger, () -> true);

    assertThat(managerWithSuperNode.isEnabledDuringOptimisticSync())
        .as(
            "isEnabledDuringOptimisticSync should return true when isSuperNodeSupplier returns true")
        .isTrue();
  }

  @TestTemplate
  public void testIsDisabledDuringOptimisticSync_whenSuperNodeSupplierReturnsFalse() {
    final DataColumnSidecarGossipManager managerWithoutSuperNode =
        new DataColumnSidecarGossipManager(subnetSubscriptions, dasGossipLogger, () -> false);

    assertThat(managerWithoutSuperNode.isEnabledDuringOptimisticSync())
        .as(
            "isEnabledDuringOptimisticSync should return false when isSuperNodeSupplier returns false")
        .isFalse();
  }

  @TestTemplate
  public void testIsEnabledDuringOptimisticSync_respectsDynamicSupplierChanges() {
    // Create a mutable supplier that we can change
    final MutableSupplier<Boolean> mutableSupplier = new MutableSupplier<>(false);
    final DataColumnSidecarGossipManager managerWithDynamicSupplier =
        new DataColumnSidecarGossipManager(subnetSubscriptions, dasGossipLogger, mutableSupplier);

    // Initially false
    assertThat(managerWithDynamicSupplier.isEnabledDuringOptimisticSync())
        .as("Should initially return false")
        .isFalse();

    // Change supplier to return true
    mutableSupplier.setValue(true);
    assertThat(managerWithDynamicSupplier.isEnabledDuringOptimisticSync())
        .as("Should return true after supplier changes to true")
        .isTrue();

    // Change supplier back to false
    mutableSupplier.setValue(false);
    assertThat(managerWithDynamicSupplier.isEnabledDuringOptimisticSync())
        .as("Should return false after supplier changes back to false")
        .isFalse();
  }

  // Helper class for testing dynamic supplier behavior
  private static class MutableSupplier<T> implements Supplier<T> {
    private T value;

    MutableSupplier(final T initialValue) {
      this.value = initialValue;
    }

    void setValue(final T value) {
      this.value = value;
    }

    @Override
    public T get() {
      return value;
    }
  }
}
