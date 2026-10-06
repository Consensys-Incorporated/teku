/*
 * Copyright Consensys Software Inc., 2026
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.util.ForkAndSpecMilestone;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

/**
 * Verifies that the DataColumnSidecar gossip schema is resolved from the fork the subscription
 * belongs to and not from the highest supported milestone. Every milestone supporting data column
 * sidecars is scheduled at a distinct epoch, so each fork must keep using its own schema even when
 * later forks are scheduled on the same network.
 */
public class DataColumnSidecarSubnetSubscriptionsTest {

  private static final SpecMilestone FIRST_DATA_COLUMN_SIDECAR_MILESTONE = SpecMilestone.FULU;
  private static final int SUBNET_ID = 1;

  private static final Spec SPEC =
      TestSpecFactory.createMinimalWithCapellaDenebElectraFuluGloasAndHezeForkEpoch(
          UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, UInt64.ZERO, UInt64.valueOf(2), UInt64.valueOf(4));

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(SPEC);
  private final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(SPEC);
  private final RecentChainData recentChainData = storageSystem.recentChainData();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  @SuppressWarnings("unchecked")
  private final OperationProcessor<DataColumnSidecar> processor = mock(OperationProcessor.class);

  static Stream<Arguments> dataColumnSidecarForks() {
    return SPEC.getForkSchedule().getActiveMilestones().stream()
        .filter(
            forkAndMilestone ->
                forkAndMilestone
                    .getSpecMilestone()
                    .isGreaterThanOrEqualTo(FIRST_DATA_COLUMN_SIDECAR_MILESTONE))
        .map(
            forkAndMilestone ->
                Arguments.of(
                    forkAndMilestone.getSpecMilestone(), forkAndMilestone.getFork().getEpoch()));
  }

  @BeforeEach
  void setUp() {
    storageSystem.chainUpdater().initializeGenesis();
    when(gossipNetwork.subscribe(any(), any())).thenReturn(mock(TopicChannel.class));
  }

  @Test
  void shouldCoverEveryMilestoneSupportingDataColumnSidecars() {
    assertThat(
            SPEC.getForkSchedule().getActiveMilestones().stream()
                .map(ForkAndSpecMilestone::getSpecMilestone)
                .filter(
                    milestone ->
                        milestone.isGreaterThanOrEqualTo(FIRST_DATA_COLUMN_SIDECAR_MILESTONE)))
        .containsExactlyElementsOf(
            SpecMilestone.getAllMilestonesFrom(FIRST_DATA_COLUMN_SIDECAR_MILESTONE));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dataColumnSidecarForks")
  void shouldUseSchemaFromTopicFork(final SpecMilestone milestone, final UInt64 forkEpoch) {
    final DataColumnSidecarSchema<DataColumnSidecar> expectedSchema =
        SchemaDefinitionsFulu.required(SPEC.forMilestone(milestone).getSchemaDefinitions())
            .getDataColumnSidecarSchema();

    assertThat(createSubnetSubscriptions(forkEpoch).getDataColumnSidecarSchema())
        .isSameAs(expectedSchema);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("dataColumnSidecarForks")
  void shouldDecodeSidecarFromTopicFork(final SpecMilestone milestone, final UInt64 forkEpoch) {
    final DataColumnSidecar sidecar = randomDataColumnSidecar(forkEpoch);
    assertThat(SPEC.atEpoch(forkEpoch).getMilestone()).isEqualTo(milestone);

    assertThat(handleMessage(createSubnetSubscriptions(forkEpoch), sidecar))
        .isCompletedWithValue(ValidationResult.Valid);
    verify(processor).process(eq(sidecar), any());
  }

  private DataColumnSidecarSubnetSubscriptions createSubnetSubscriptions(final UInt64 forkEpoch) {
    final ForkInfo forkInfo = recentChainData.getForkInfo(forkEpoch).orElseThrow();
    final Bytes4 forkDigest = recentChainData.getForkDigest(forkEpoch);
    final DataColumnSidecarSubnetSubscriptions subnetSubscriptions =
        new DataColumnSidecarSubnetSubscriptions(
            SPEC,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            recentChainData,
            processor,
            DebugDataDumper.NOOP,
            forkInfo,
            forkDigest);
    subnetSubscriptions.subscribe();
    return subnetSubscriptions;
  }

  private DataColumnSidecar randomDataColumnSidecar(final UInt64 forkEpoch) {
    final SignedBeaconBlockHeader blockHeader =
        dataStructureUtil.randomSignedBeaconBlockHeader(SPEC.computeStartSlotAtEpoch(forkEpoch));
    return dataStructureUtil.randomDataColumnSidecar(blockHeader, UInt64.valueOf(SUBNET_ID));
  }

  @SuppressWarnings("unchecked")
  private SafeFuture<ValidationResult> handleMessage(
      final DataColumnSidecarSubnetSubscriptions subnetSubscriptions,
      final DataColumnSidecar sidecar) {
    when(processor.process(any(), any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    subnetSubscriptions.subscribeToSubnetId(SUBNET_ID);
    final Eth2TopicHandler<DataColumnSidecar> topicHandler =
        (Eth2TopicHandler<DataColumnSidecar>)
            subnetSubscriptions.getSubscribedTopicHandler(SUBNET_ID);
    final Bytes serialized = gossipEncoding.encode(sidecar);

    final SafeFuture<ValidationResult> result =
        topicHandler.handleMessage(topicHandler.prepareMessage(serialized, Optional.empty()));
    asyncRunner.executeQueuedActions();
    return result;
  }
}
