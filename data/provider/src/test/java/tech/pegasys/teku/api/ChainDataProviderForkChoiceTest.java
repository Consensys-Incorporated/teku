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

package tech.pegasys.teku.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_EMPTY;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL;
import static tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING;

import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.storage.client.BlobReconstructionProvider;
import tech.pegasys.teku.storage.client.BlobSidecarReconstructionProvider;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.protoarray.ForkChoiceStrategy;
import tech.pegasys.teku.storage.protoarray.ProtoArray;

@TestSpecContext(milestone = {SpecMilestone.PHASE0, SpecMilestone.FULU, SpecMilestone.GLOAS})
class ChainDataProviderForkChoiceTest {
  private final Bytes32 parentRoot = Bytes32.fromHexString("0x1111");
  private final Bytes32 blockRoot = Bytes32.fromHexString("0x2222");
  private final Bytes32 parentHash = Bytes32.fromHexString("0x3333");
  private final Bytes32 payloadHash = Bytes32.fromHexString("0x4444");
  private final Checkpoint checkpoint = new Checkpoint(UInt64.ZERO, parentRoot);
  private final BlockCheckpoints checkpoints =
      new BlockCheckpoints(checkpoint, checkpoint, checkpoint, checkpoint);
  private ProtoArray protoArray;
  private Spec spec;
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private ForkChoiceStrategy strategy;
  private ChainDataProvider provider;
  private boolean gloas;

  @BeforeEach
  void setUp(final SpecContext context) {
    spec = context.getSpec();
    gloas = context.getSpecMilestone().isGreaterThanOrEqualTo(SpecMilestone.GLOAS);
    protoArray =
        ProtoArray.builder()
            .spec(spec)
            .currentEpoch(UInt64.ZERO)
            .justifiedCheckpoint(checkpoint)
            .finalizedCheckpoint(checkpoint)
            .pruneThreshold(0)
            .build();
    addNode(
        ForkChoiceNode.createBase(parentRoot),
        UInt64.ZERO,
        Bytes32.ZERO,
        Optional.empty(),
        parentHash,
        false);
    addNode(
        ForkChoiceNode.createBase(blockRoot),
        UInt64.ONE,
        parentRoot,
        Optional.of(ForkChoiceNode.createBase(parentRoot)),
        parentHash,
        false);
    if (gloas) {
      addNode(
          ForkChoiceNode.createEmpty(blockRoot),
          UInt64.ONE,
          parentRoot,
          Optional.of(ForkChoiceNode.createBase(blockRoot)),
          parentHash,
          false);
      addNode(
          ForkChoiceNode.createFull(blockRoot),
          UInt64.ONE,
          parentRoot,
          Optional.of(ForkChoiceNode.createBase(blockRoot)),
          payloadHash,
          true);
    }
    strategy = ForkChoiceStrategy.initialize(spec, protoArray);
    final CombinedChainDataClient client = mock(CombinedChainDataClient.class);
    when(client.isStoreAvailable()).thenReturn(true);
    when(recentChainData.getJustifiedCheckpoint()).thenReturn(Optional.of(checkpoint));
    when(recentChainData.getFinalizedCheckpoint()).thenReturn(Optional.of(checkpoint));
    when(recentChainData.getForkChoiceStrategy()).thenReturn(Optional.of(strategy));
    provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            client,
            mock(RewardCalculator.class),
            mock(BlobSidecarReconstructionProvider.class),
            mock(BlobReconstructionProvider.class),
            new LightClientUpdateStore(spec));
  }

  @TestTemplate
  void shouldExposeNodeTopologyAndExecutionData() {
    final List<ForkChoiceNodeDataV2> nodes = provider.getForkChoiceDataV2().getNodes();
    assertThat(nodes).hasSize(gloas ? 4 : 2);
    assertThat(nodes.getFirst().getParentPayloadStatus()).isEmpty();
    final ForkChoiceNodeDataV2 base = nodes.get(1);
    assertThat(base.getPayloadStatus())
        .isEqualTo(gloas ? PAYLOAD_STATUS_PENDING : PAYLOAD_STATUS_FULL);
    assertThat(base.getParentRoot()).isEqualTo(parentRoot);
    assertThat(base.getParentPayloadStatus())
        .contains(gloas ? PAYLOAD_STATUS_PENDING : PAYLOAD_STATUS_FULL);
    assertThat(base.getNode().getExecutionBlockHash()).isEqualTo(parentHash);
    assertThat(base.getNode().getValidationStatus()).isEqualTo(ProtoNodeValidationStatus.VALID);
    if (gloas) {
      final ForkChoiceNodeDataV2 empty = nodes.get(2);
      final ForkChoiceNodeDataV2 full = nodes.get(3);
      assertThat(empty.getPayloadStatus()).isEqualTo(PAYLOAD_STATUS_EMPTY);
      assertThat(full.getPayloadStatus()).isEqualTo(PAYLOAD_STATUS_FULL);
      for (final ForkChoiceNodeDataV2 child : List.of(empty, full)) {
        assertThat(child.getParentRoot()).isEqualTo(blockRoot);
        assertThat(child.getParentPayloadStatus()).contains(PAYLOAD_STATUS_PENDING);
      }
      assertThat(empty.getNode().getExecutionBlockHash()).isEqualTo(parentHash);
      assertThat(full.getNode().getExecutionBlockHash()).isEqualTo(payloadHash);
      assertThat(full.getNode().getValidationStatus())
          .isEqualTo(ProtoNodeValidationStatus.OPTIMISTIC);
    }
  }

  @TestTemplate
  void shouldReturnNullParentStatusAfterPruning() {
    protoArray.maybePrune(ForkChoiceNode.createBase(blockRoot));
    final List<ForkChoiceNodeDataV2> nodes = provider.getForkChoiceDataV2().getNodes();
    assertThat(nodes.getFirst().getParentRoot()).isEqualTo(parentRoot);
    assertThat(nodes.getFirst().getParentPayloadStatus()).isEmpty();
    if (gloas) {
      assertThat(nodes.get(1).getParentPayloadStatus()).contains(PAYLOAD_STATUS_PENDING);
      assertThat(nodes.get(2).getParentPayloadStatus()).contains(PAYLOAD_STATUS_PENDING);
    }
  }

  @TestTemplate
  void shouldExposeRawWeightsAndPositionBasedPtcCounts() {
    strategy.onPayloadTimelinessCommitteeVote(blockRoot, IntSet.of(0, 1), true, true);
    strategy.onPayloadTimelinessCommitteeVote(blockRoot, IntSet.of(2), false, true);
    strategy.onPayloadTimelinessCommitteeVote(blockRoot, IntSet.of(1), true, false);
    protoArray.getNode(ForkChoiceNode.createBase(blockRoot)).orElseThrow().adjustWeight(123);
    final List<ForkChoiceNodeDataV2> nodes = provider.getForkChoiceDataV2().getNodes();
    assertThat(nodes.get(1).getNode().getWeight()).isEqualTo(UInt64.valueOf(123));
    assertThat(nodes.stream().filter(node -> node.getNode().getRoot().equals(blockRoot)))
        .allSatisfy(
            node -> {
              assertThat(node.getPayloadAttesterCount()).isEqualTo(UInt64.valueOf(gloas ? 3 : 0));
              assertThat(node.getPayloadAvailabilityYesCount())
                  .isEqualTo(UInt64.valueOf(gloas ? 2 : 0));
              assertThat(node.getPayloadDataAvailabilityYesCount())
                  .isEqualTo(UInt64.valueOf(gloas ? 2 : 0));
            });
  }

  @TestTemplate
  void shouldLinkPendingNodeToSelectedParentVariant() {
    for (final ForkChoicePayloadStatus status :
        gloas
            ? List.of(PAYLOAD_STATUS_EMPTY, PAYLOAD_STATUS_FULL)
            : List.of(PAYLOAD_STATUS_PENDING)) {
      final Bytes32 root =
          status == PAYLOAD_STATUS_FULL
              ? Bytes32.fromHexString("0x5555")
              : Bytes32.fromHexString("0x6666");
      addNode(
          ForkChoiceNode.createBase(root),
          UInt64.valueOf(2),
          blockRoot,
          Optional.of(new ForkChoiceNode(blockRoot, status)),
          status == PAYLOAD_STATUS_FULL ? payloadHash : parentHash,
          false);
      strategy = ForkChoiceStrategy.initialize(spec, protoArray);
      when(recentChainData.getForkChoiceStrategy()).thenReturn(Optional.of(strategy));
      final ForkChoiceNodeDataV2 child = provider.getForkChoiceDataV2().getNodes().getLast();
      assertThat(child.getParentRoot()).isEqualTo(blockRoot);
      assertThat(child.getParentPayloadStatus()).contains(gloas ? status : PAYLOAD_STATUS_FULL);
    }
  }

  private void addNode(
      final ForkChoiceNode node,
      final UInt64 slot,
      final Bytes32 beaconParent,
      final Optional<ForkChoiceNode> parent,
      final Bytes32 hash,
      final boolean optimistic) {
    protoArray.addNode(
        node, slot, beaconParent, parent, Bytes32.ZERO, checkpoints, UInt64.ONE, hash, optimistic);
  }
}
