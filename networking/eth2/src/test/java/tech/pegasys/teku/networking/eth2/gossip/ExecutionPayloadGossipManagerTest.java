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

package tech.pegasys.teku.networking.eth2.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.networking.eth2.gossip.ExecutionPayloadGossipManager.MAX_IN_FLIGHT_MESSAGES;

import io.libp2p.core.pubsub.ValidationResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.eth2.gossip.topics.topichandlers.Eth2TopicHandler;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(milestone = {SpecMilestone.GLOAS})
public class ExecutionPayloadGossipManagerTest {

  @SuppressWarnings("unchecked")
  private final OperationProcessor<SignedExecutionPayloadEnvelope> processor =
      mock(OperationProcessor.class);

  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();

  private DataStructureUtil dataStructureUtil;
  private Eth2TopicHandler<?> topicHandler;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    final Spec spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    final RecentChainData recentChainData = storageSystem.recentChainData();

    topicHandler =
        new ExecutionPayloadGossipManager(
                spec,
                recentChainData,
                metricsSystem,
                asyncRunner,
                mock(GossipNetwork.class),
                gossipEncoding,
                new ForkInfo(spec.fork(UInt64.ZERO), dataStructureUtil.randomBytes32()),
                recentChainData.getForkDigestByMilestone(SpecMilestone.GLOAS).orElseThrow(),
                processor,
                spec.getNetworkingConfig(),
                DebugDataDumper.NOOP)
            .getTopicHandler();
  }

  @TestTemplate
  void shouldIgnoreMessagesOverInFlightLimit() {
    when(processor.process(any(), any())).thenAnswer(__ -> new SafeFuture<>());
    final Bytes message =
        gossipEncoding.encode(dataStructureUtil.randomSignedExecutionPayloadEnvelope(1));

    final List<SafeFuture<ValidationResult>> results = new ArrayList<>();
    for (int i = 0; i <= MAX_IN_FLIGHT_MESSAGES; i++) {
      results.add(
          topicHandler.handleMessage(topicHandler.prepareMessage(message, Optional.empty())));
    }
    asyncRunner.executeQueuedActions(MAX_IN_FLIGHT_MESSAGES);

    verify(processor, times(MAX_IN_FLIGHT_MESSAGES)).process(any(), any());
    assertThat(results.subList(0, MAX_IN_FLIGHT_MESSAGES)).allMatch(result -> !result.isDone());
    assertThatSafeFuture(results.getLast()).isCompletedWithValue(ValidationResult.Ignore);
    assertThat(
            metricsSystem.getLabelledCounterValue(
                TekuMetricCategory.NETWORK,
                "gossip_messages_in_flight_limit_discarded_total",
                "execution_payload"))
        .isEqualTo(1);
  }

  @TestTemplate
  void shouldExposeInFlightCountGauge() {
    when(processor.process(any(), any())).thenAnswer(__ -> new SafeFuture<>());
    final Bytes message =
        gossipEncoding.encode(dataStructureUtil.randomSignedExecutionPayloadEnvelope(1));
    final Supplier<OptionalDouble> gauge =
        () ->
            metricsSystem
                .getLabelledGauge(TekuMetricCategory.NETWORK, "gossip_messages_in_flight")
                .getValue("execution_payload");
    assertThat(gauge.get()).hasValue(0);

    topicHandler
        .handleMessage(topicHandler.prepareMessage(message, Optional.empty()))
        .finishStackTrace();
    topicHandler
        .handleMessage(topicHandler.prepareMessage(message, Optional.empty()))
        .finishStackTrace();

    assertThat(gauge.get()).hasValue(2);
  }
}
