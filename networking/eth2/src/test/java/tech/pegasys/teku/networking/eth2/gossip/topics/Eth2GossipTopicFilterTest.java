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

package tech.pegasys.teku.networking.eth2.gossip.topics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static tech.pegasys.teku.networking.eth2.gossip.encoding.GossipEncoding.SSZ_SNAPPY;
import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getAttestationSubnetTopicName;
import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getBlobSidecarSubnetTopicName;
import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getExecutionProofSubnetTopicName;
import static tech.pegasys.teku.networking.eth2.gossip.topics.GossipTopicName.getSyncCommitteeSubnetTopicName;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;
import static tech.pegasys.teku.spec.SpecMilestone.HEZE;
import static tech.pegasys.teku.spec.constants.NetworkConstants.SYNC_COMMITTEE_SUBNET_COUNT;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.P2PConfig;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.BlobScheduleEntry;
import tech.pegasys.teku.spec.config.Constants;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.builder.FuluBuilder;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.BlobParameters;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(milestone = {DENEB, ELECTRA, FULU, GLOAS, HEZE})
class Eth2GossipTopicFilterTest {
  private final UInt64 nextMilestoneForkEpoch = UInt64.valueOf(10);
  private final BlobParameters bpoFork = new BlobParameters(UInt64.valueOf(11), 64);
  private RecentChainData recentChainData;
  private Spec spec;
  private SpecMilestone currentSpecMilestone;
  private SpecMilestone nextSpecMilestone;
  private Bytes4 currentForkDigest;
  private Eth2GossipTopicFilter filter;
  private Bytes4 nextForkDigest;
  private P2PConfig p2pConfig;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    // we set up a spec that will be transitioning to specContext.getSpecMilestone() in
    // currentForkEpoch epochs
    // current milestone is actually the previous milestone
    currentSpecMilestone = specContext.getSpecMilestone().getPreviousMilestone();
    nextSpecMilestone = specContext.getSpecMilestone();

    final Consumer<FuluBuilder> fuluBuilder =
        fb ->
            fb.blobSchedule(
                List.of(new BlobScheduleEntry(bpoFork.epoch(), bpoFork.maxBlobsPerBlock())));
    spec =
        switch (nextSpecMilestone) {
          case PHASE0, ALTAIR, BELLATRIX, CAPELLA ->
              throw new IllegalArgumentException(nextSpecMilestone + " is a unsupported milestone");
          case DENEB -> TestSpecFactory.createMinimalWithDenebForkEpoch(nextMilestoneForkEpoch);
          case ELECTRA -> TestSpecFactory.createMinimalWithElectraForkEpoch(nextMilestoneForkEpoch);
          case FULU ->
              TestSpecFactory.createMinimalFulu(
                  b -> b.fuluBuilder(fuluBuilder).fuluForkEpoch(nextMilestoneForkEpoch));
          case GLOAS ->
              TestSpecFactory.createMinimalGloas(
                  b -> b.fuluBuilder(fuluBuilder).gloasForkEpoch(nextMilestoneForkEpoch));
          case HEZE ->
              TestSpecFactory.createMinimalHeze(
                  b -> b.fuluBuilder(fuluBuilder).hezeForkEpoch(nextMilestoneForkEpoch));
        };
    p2pConfig = P2PConfig.builder().specProvider(spec).build();
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();

    recentChainData = spy(storageSystem.recentChainData());
    filter = new Eth2GossipTopicFilter(recentChainData, SSZ_SNAPPY, spec, p2pConfig);

    currentForkDigest = recentChainData.getCurrentForkDigest().orElseThrow();
    nextForkDigest =
        recentChainData
            .getNextForkDigest(recentChainData.getCurrentEpoch().orElseThrow())
            .orElseThrow();
  }

  @TestTemplate
  void shouldNotAllowIrrelevantTopics() {
    assertThat(filter.isRelevantTopic("abc")).isFalse();
  }

  @TestTemplate
  void shouldNotRequireNextForkToBePresent() {
    doAnswer(invocation -> Optional.empty()).when(recentChainData).getNextFork(any());
    assertThat(filter.isRelevantTopic(getTopicName(GossipTopicName.BEACON_BLOCK))).isTrue();
  }

  @TestTemplate
  void shouldConsiderTopicsForNextForkRelevant() {
    assertThat(filter.isRelevantTopic(getNextForkTopicName(GossipTopicName.BEACON_BLOCK))).isTrue();
  }

  @TestTemplate
  void shouldConsiderTopicsForSignedContributionAndProofRelevant() {
    assertThat(
            filter.isRelevantTopic(
                getNextForkTopicName(GossipTopicName.SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF)))
        .isTrue();
  }

  @TestTemplate
  void shouldConsiderAllAttestationSubnetsRelevant() {
    for (int i = 0; i < spec.getNetworkingConfig().getAttestationSubnetCount(); i++) {
      assertThat(filter.isRelevantTopic(getTopicName(getAttestationSubnetTopicName(i)))).isTrue();
      assertThat(filter.isRelevantTopic(getNextForkTopicName(getAttestationSubnetTopicName(i))))
          .isTrue();
    }
  }

  @TestTemplate
  void shouldConsiderAllSyncCommitteeSubnetsRelevant() {
    for (int i = 0; i < SYNC_COMMITTEE_SUBNET_COUNT; i++) {
      assertThat(filter.isRelevantTopic(getTopicName(getSyncCommitteeSubnetTopicName(i)))).isTrue();
    }
  }

  @TestTemplate
  void shouldConsiderAllBlobSidecarSubnetsRelevantForCurrentMilestone() {
    final SpecConfig config = spec.forMilestone(currentSpecMilestone).getConfig();
    assumeThat(config.toVersionDeneb()).isPresent();
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(config);
    for (int i = 0; i < specConfigDeneb.getBlobSidecarSubnetCount(); i++) {
      assertThat(filter.isRelevantTopic(getTopicName(getBlobSidecarSubnetTopicName(i)))).isTrue();
    }
  }

  @TestTemplate
  void shouldConsiderAllBlobSidecarSubnetsRelevantForNextMilestone() {
    final SpecConfig config = spec.forMilestone(nextSpecMilestone).getConfig();
    assumeThat(config.toVersionDeneb()).isPresent();
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(config);
    for (int i = 0; i < specConfigDeneb.getBlobSidecarSubnetCount(); i++) {
      assertThat(filter.isRelevantTopic(getNextForkTopicName(getBlobSidecarSubnetTopicName(i))))
          .isTrue();
    }
  }

  @TestTemplate
  void shouldNotConsiderBlobSidecarWithIncorrectSubnetIdRelevant() {
    final int blobSidecarSubnetCount =
        spec.forMilestone(nextSpecMilestone)
            .getConfig()
            .toVersionDeneb()
            .orElseThrow()
            .getBlobSidecarSubnetCount();
    assertThat(
            filter.isRelevantTopic(
                getTopicName(getBlobSidecarSubnetTopicName(blobSidecarSubnetCount + 1))))
        .isFalse();
  }

  @TestTemplate
  void shouldNotConsiderBlobSidecarWithoutIndexTopicRelevant() {
    assertThat(filter.isRelevantTopic(getTopicName("blob_sidecar"))).isFalse();
  }

  @TestTemplate
  void shouldNotAllowTopicsWithUnknownForkDigest() {
    final String irrelevantTopic =
        GossipTopics.getTopic(
            Bytes4.fromHexString("0x11223344"), GossipTopicName.BEACON_BLOCK, SSZ_SNAPPY);
    assertThat(filter.isRelevantTopic(irrelevantTopic)).isFalse();
  }

  @TestTemplate
  void shouldAllowTopicsForBpoFork() {
    assumeThat(nextSpecMilestone).isEqualTo(FULU);
    final Bytes4 bpoForkDigest = recentChainData.getForkDigestByBpoFork(bpoFork).orElseThrow();
    final String bpoTopic =
        GossipTopics.getTopic(bpoForkDigest, GossipTopicName.BEACON_BLOCK, SSZ_SNAPPY);
    assertThat(filter.isRelevantTopic(bpoTopic)).isTrue();
  }

  @TestTemplate
  void shouldAllowTopicsForBpoForkSameEpochAsFulu() {
    assumeThat(nextSpecMilestone).isEqualTo(FULU);
    final BlobParameters bpoForkCustom = new BlobParameters(nextMilestoneForkEpoch, 64);
    spec =
        TestSpecFactory.createMinimalFulu(
            b ->
                b.fuluForkEpoch(nextMilestoneForkEpoch)
                    .fuluBuilder(
                        fb ->
                            fb.blobSchedule(
                                List.of(
                                    new BlobScheduleEntry(
                                        bpoForkCustom.epoch(), bpoFork.maxBlobsPerBlock())))));
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();

    recentChainData = spy(storageSystem.recentChainData());
    filter = new Eth2GossipTopicFilter(recentChainData, SSZ_SNAPPY, spec, p2pConfig);

    currentForkDigest = recentChainData.getCurrentForkDigest().orElseThrow();
    nextForkDigest =
        recentChainData
            .getNextForkDigest(recentChainData.getCurrentEpoch().orElseThrow())
            .orElseThrow();

    final Bytes4 bpoForkDigest =
        recentChainData.getForkDigestByBpoFork(bpoForkCustom).orElseThrow();
    final String bpoTopic =
        GossipTopics.getTopic(bpoForkDigest, GossipTopicName.BEACON_BLOCK, SSZ_SNAPPY);
    assertThat(filter.isRelevantTopic(bpoTopic)).isTrue();
  }

  @TestTemplate
  void shouldNotConsiderExecutionProofSubnetsRelevantByDefault() {
    assumeThat(nextSpecMilestone).isEqualTo(ELECTRA);
    for (int i = 0; i < Constants.MAX_EXECUTION_PROOF_SUBNETS; i++) {
      assertThat(filter.isRelevantTopic(getTopicName(getExecutionProofSubnetTopicName(i))))
          .isFalse();
    }
  }

  @TestTemplate
  void shouldConsiderExecutionProofSubnetsRelevantWhenEnabled() {
    P2PConfig p2pConfigOverwritten =
        P2PConfig.builder().specProvider(spec).executionProofTopicEnabled(true).build();
    filter = new Eth2GossipTopicFilter(recentChainData, SSZ_SNAPPY, spec, p2pConfigOverwritten);
    assumeThat(nextSpecMilestone).isEqualTo(ELECTRA);
    for (int i = 0; i < Constants.MAX_EXECUTION_PROOF_SUBNETS; i++) {
      assertThat(filter.isRelevantTopic(getTopicName(getExecutionProofSubnetTopicName(i))))
          .isTrue();
    }
  }

  private String getTopicName(final GossipTopicName name) {
    return GossipTopics.getTopic(currentForkDigest, name, SSZ_SNAPPY);
  }

  private String getTopicName(final String name) {
    return GossipTopics.getTopic(currentForkDigest, name, SSZ_SNAPPY);
  }

  private String getNextForkTopicName(final GossipTopicName name) {
    return GossipTopics.getTopic(nextForkDigest, name, SSZ_SNAPPY);
  }

  private String getNextForkTopicName(final String name) {
    return GossipTopics.getTopic(nextForkDigest, name, SSZ_SNAPPY);
  }
}
