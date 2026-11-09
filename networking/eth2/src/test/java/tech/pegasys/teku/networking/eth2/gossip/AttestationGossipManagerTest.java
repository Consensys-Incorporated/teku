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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding;
import tech.pegasys.teku.networking.eth2.gossip.subnets.AttestationSubnetSubscriptions;
import tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopics;
import tech.pegasys.teku.networking.eth2.gossip.topics.OperationProcessor;
import tech.pegasys.teku.networking.p2p.gossip.GossipNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.MemoryOnlyRecentChainData;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(milestone = {PHASE0, ELECTRA})
public class AttestationGossipManagerTest {

  private Spec spec;
  private SpecMilestone specMilestone;
  private DataStructureUtil dataStructureUtil;
  private RecentChainData recentChainData;
  private ForkInfo forkInfo;
  private Bytes4 forkDigest;
  private AttestationGossipManager attestationGossipManager;

  @SuppressWarnings("unchecked")
  private final OperationProcessor<ValidatableAttestation> gossipedAttestationProcessor =
      mock(OperationProcessor.class);

  private final GossipNetwork gossipNetwork = mock(GossipNetwork.class);
  private final GossipEncoding gossipEncoding = GossipEncoding.SSZ_SNAPPY;

  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final StubAsyncRunner asyncRunner = new StubAsyncRunner();

  private final Int2IntMap committeeSizes = new Int2IntOpenHashMap();

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
    dataStructureUtil = specContext.getDataStructureUtil();
    recentChainData = MemoryOnlyRecentChainData.create(spec);
    forkInfo = new ForkInfo(spec.fork(UInt64.ZERO), dataStructureUtil.randomBytes32());
    forkDigest = dataStructureUtil.randomBytes4();

    final AttestationSubnetSubscriptions attestationSubnetSubscriptions =
        new AttestationSubnetSubscriptions(
            spec,
            asyncRunner,
            gossipNetwork,
            gossipEncoding,
            recentChainData,
            gossipedAttestationProcessor,
            forkInfo,
            forkDigest,
            DebugDataDumper.NOOP);

    ChainBuilder chainBuilder = ChainBuilder.create(spec);
    ChainUpdater chainUpdater = new ChainUpdater(recentChainData, chainBuilder, spec);
    chainUpdater.initializeGenesis();

    attestationGossipManager =
        new AttestationGossipManager(metricsSystem, attestationSubnetSubscriptions);
  }

  @TestTemplate
  public void onNewAttestation_afterMatchingAssignment(final SpecContext specContext) {
    specContext.assumeIsOneOf(PHASE0);
    // Create a new DataStructureUtil so that generated attestations are not subject to change
    // when access to the global DataStructureUtil changes
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
    final UInt64 slot = UInt64.valueOf(3);
    final Attestation attestation = dataStructureUtil.randomAttestation(slot);
    final Attestation attestation2 =
        spec.getGenesisSchemaDefinitions()
            .getAttestationSchema()
            .create(
                dataStructureUtil.randomBitlist(slot),
                dataStructureUtil.randomAttestationData(UInt64.valueOf(13)),
                dataStructureUtil.randomSignature());
    final int subnetId = computeSubnetId(attestation);
    // Sanity check the attestations are for the same subnet
    assertThat(computeSubnetId(attestation2)).isEqualTo(subnetId);
    // Setup committee assignment
    attestationGossipManager.subscribeToSubnetId(subnetId);

    // Post new attestation
    final Bytes serialized = gossipEncoding.encode(attestation);
    attestationGossipManager.onNewAttestation(ValidatableAttestation.from(spec, attestation));

    verify(gossipNetwork).gossip(getSubnetTopic(subnetId), serialized);

    // We should process attestations for different committees on the same subnet
    final Bytes serialized2 = gossipEncoding.encode(attestation2);
    attestationGossipManager.onNewAttestation(ValidatableAttestation.from(spec, attestation2));

    verify(gossipNetwork).gossip(getSubnetTopic(subnetId), serialized2);
  }

  @TestTemplate
  public void onNewAttestation_noMatchingAssignment() {
    final ValidatableAttestation validatableAttestation = createAttestation(2);
    final Attestation attestation =
        getExpectedAttestationFromValidatableAttestation(validatableAttestation);

    final int subnetId = computeSubnetId(attestation);
    // Subscribed to different subnet
    attestationGossipManager.subscribeToSubnetId(subnetId + 1);

    // Post new attestation
    attestationGossipManager.onNewAttestation(validatableAttestation);

    verify(gossipNetwork).gossip(getSubnetTopic(subnetId), gossipEncoding.encode(attestation));
  }

  @TestTemplate
  public void onNewAttestation_afterDismissal() {
    final ValidatableAttestation validatableAttestation = createAttestation(1);
    final ValidatableAttestation validatableAttestation2 = createAttestation(1);
    final Attestation attestation =
        getExpectedAttestationFromValidatableAttestation(validatableAttestation);
    final Attestation attestation2 =
        getExpectedAttestationFromValidatableAttestation(validatableAttestation2);
    // Setup committee assignment
    final int subnetId = computeSubnetId(attestation2);
    final int dismissedSubnetId = computeSubnetId(attestation);
    attestationGossipManager.subscribeToSubnetId(subnetId);

    // Unassign
    attestationGossipManager.unsubscribeFromSubnetId(dismissedSubnetId);

    // Attestation for dismissed assignment should be ignored
    final Bytes serialized = gossipEncoding.encode(attestation);
    attestationGossipManager.onNewAttestation(validatableAttestation);

    verify(gossipNetwork).gossip(getSubnetTopic(dismissedSubnetId), serialized);

    // Attestation for remaining assignment should be processed
    final Bytes serialized2 = gossipEncoding.encode(attestation2);
    attestationGossipManager.onNewAttestation(validatableAttestation2);

    verify(gossipNetwork).gossip(getSubnetTopic(subnetId), serialized2);
  }

  @TestTemplate
  void onNewAttestation_incrementSuccessCount() {
    final ValidatableAttestation validatableAttestation = createAttestation(3);

    when(gossipNetwork.gossip(any(), any())).thenReturn(SafeFuture.completedFuture(null));

    // Attestation for dismissed assignment should be ignored
    attestationGossipManager.onNewAttestation(validatableAttestation);

    assertThat(getPublishSuccessCounterValue()).isEqualTo(1);
    assertThat(getPublishFailureCounterValue()).isZero();
  }

  @TestTemplate
  void onNewAttestation_incrementFailureCount() {
    final ValidatableAttestation validatableAttestation = createAttestation(3);
    when(gossipNetwork.gossip(any(), any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Ooops")));

    // Attestation for dismissed assignment should be ignored
    attestationGossipManager.onNewAttestation(validatableAttestation);

    assertThat(getPublishSuccessCounterValue()).isZero();
    assertThat(getPublishFailureCounterValue()).isEqualTo(1);
  }

  private ValidatableAttestation createAttestation(final int slot) {
    final Attestation attestationFromValidators;

    if (specMilestone.isGreaterThanOrEqualTo(ELECTRA)) {
      attestationFromValidators = dataStructureUtil.randomSingleAttestation(UInt64.valueOf(slot));
    } else {
      attestationFromValidators = dataStructureUtil.randomAttestation(slot);
    }

    final ValidatableAttestation validatableAttestation =
        ValidatableAttestation.from(spec, attestationFromValidators, committeeSizes);

    if (attestationFromValidators.isSingleAttestation()) {
      validatableAttestation.convertToAggregatedFormatFromSingleAttestation(
          dataStructureUtil.randomAttestation(slot));
    }

    return validatableAttestation;
  }

  private Attestation getExpectedAttestationFromValidatableAttestation(
      final ValidatableAttestation validatableAttestation) {
    if (specMilestone.isGreaterThanOrEqualTo(ELECTRA)) {
      return validatableAttestation.getUnconvertedAttestation();
    } else {
      return validatableAttestation.getAttestation();
    }
  }

  private long getPublishSuccessCounterValue() {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.BEACON, "published_attestation_total", "success");
  }

  private long getPublishFailureCounterValue() {
    return metricsSystem.getLabelledCounterValue(
        TekuMetricCategory.BEACON, "published_attestation_total", "failure");
  }

  private Integer computeSubnetId(final Attestation attestation) {
    return spec.computeSubnetForAttestation(
        safeJoin(recentChainData.getBestState().orElseThrow()), attestation);
  }

  private String getSubnetTopic(final int subnetId) {
    return GossipTopics.getAttestationSubnetTopic(forkDigest, subnetId, gossipEncoding);
  }
}
