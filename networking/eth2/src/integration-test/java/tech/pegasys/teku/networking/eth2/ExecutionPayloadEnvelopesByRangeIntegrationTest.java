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

package tech.pegasys.teku.networking.eth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.generator.ChainBuilder;

@TestSpecContext(milestone = {FULU, GLOAS})
public class ExecutionPayloadEnvelopesByRangeIntegrationTest
    extends AbstractRpcMethodIntegrationTest {

  private Eth2Peer peer;
  private SpecMilestone specMilestone;

  @BeforeEach
  public void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    peer = createPeer(specContext.getSpec());
    specMilestone = specContext.getSpecMilestone();
  }

  @TestTemplate
  public void requestExecutionPayloadEnvelopes_shouldFailBeforeGloasMilestone() {
    assumeThat(specMilestone).isLessThan(GLOAS);
    assertThatThrownBy(
            () -> requestExecutionPayloadEnvelopesByRange(peer, UInt64.ONE, UInt64.valueOf(10)))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("ExecutionPayloadEnvelopesByRange method is not supported");
  }

  @TestTemplate
  public void requestExecutionPayloadEnvelopes_shouldReturnEmptyWhenNoBlocksInRange()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);
    final List<SignedExecutionPayloadEnvelope> envelopes =
        requestExecutionPayloadEnvelopesByRange(peer, UInt64.ONE, UInt64.valueOf(10));
    assertThat(envelopes).isEmpty();
  }

  @TestTemplate
  public void requestExecutionPayloadEnvelopes_shouldReturnEmptyWhenCountIsZero()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);

    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState block = peerStorage.chainUpdater().advanceChainUntil(targetSlot);
    peerStorage.chainUpdater().updateBestBlock(block);

    final List<SignedExecutionPayloadEnvelope> envelopes =
        requestExecutionPayloadEnvelopesByRange(peer, UInt64.ONE, UInt64.ZERO);
    assertThat(envelopes).isEmpty();
  }

  @TestTemplate
  public void requestExecutionPayloadEnvelopes_shouldReturnExecutionPayloadEnvelopes()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);

    // up to slot 3
    final UInt64 targetSlot = UInt64.valueOf(3);
    final SignedBlockAndState block = peerStorage.chainUpdater().advanceChainUntil(targetSlot);
    peerStorage.chainUpdater().updateBestBlock(block);
    // fork choice selected the FULL variant of the head, so the head payload is part of the chain
    peerStorage
        .recentChainData()
        .updateHead(ForkChoiceNode.createFull(block.getRoot()), block.getSlot());

    // grab expected execution payload envelopes from storage
    final List<SignedExecutionPayloadEnvelope> expectedExecutionPayloadEnvelopes =
        peerStorage.chainBuilder().streamExecutionPayloads(UInt64.ZERO).toList();

    assertThat(expectedExecutionPayloadEnvelopes).hasSize(3);

    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes =
        requestExecutionPayloadEnvelopesByRange(peer, UInt64.ONE, UInt64.valueOf(3));

    assertThat(executionPayloadEnvelopes)
        .containsExactlyInAnyOrderElementsOf(expectedExecutionPayloadEnvelopes);
  }

  @TestTemplate
  public void requestExecutionPayloadEnvelopes_shouldReturnOnlyCanonicalEnvelopes()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);

    // build initial canonical chain to slot 3
    final UInt64 initialSlot = UInt64.valueOf(3);
    peerStorage.chainUpdater().advanceChainUntil(initialSlot);

    // create a fork from the current canonical chain head
    final ChainBuilder fork = peerStorage.chainBuilder().fork();

    // add 3 more canonical blocks (slots 4, 5, 6)
    final UInt64 targetSlot = initialSlot.plus(3);
    final SignedBlockAndState canonicalHead =
        peerStorage.chainUpdater().advanceChainUntil(targetSlot);

    // generate non-canonical fork blocks at the same slots (4, 5, 6)
    final List<SignedBlockAndState> nonCanonicalBlocks =
        fork.generateBlocksUpToSlot(targetSlot.intValue(), peerStorage.chainUpdater().blockOptions);

    // save non-canonical blocks and their execution payloads to storage
    final List<SignedExecutionPayloadEnvelope> nonCanonicalEnvelopes = new ArrayList<>();
    nonCanonicalBlocks.forEach(
        blockAndState -> {
          peerStorage.chainUpdater().saveBlock(blockAndState);
          fork.getExecutionPayloadAtSlot(blockAndState.getSlot())
              .ifPresent(
                  ep -> {
                    nonCanonicalEnvelopes.add(ep);
                    peerStorage.chainUpdater().saveExecutionPayload(ep);
                  });
        });

    // ensure canonical head is set as the best block, with its payload part of the chain
    peerStorage.chainUpdater().updateBestBlock(canonicalHead);
    peerStorage
        .recentChainData()
        .updateHead(ForkChoiceNode.createFull(canonicalHead.getRoot()), canonicalHead.getSlot());

    // verify non-canonical envelopes exist and are different from canonical ones
    assertThat(nonCanonicalEnvelopes).hasSize(3);

    // grab expected canonical execution payloads (slots 1-6)
    final List<SignedExecutionPayloadEnvelope> expectedEnvelopes =
        peerStorage.chainBuilder().streamExecutionPayloads(UInt64.ONE).toList();
    assertThat(expectedEnvelopes).hasSize(6);
    assertThat(expectedEnvelopes).doesNotContainAnyElementsOf(nonCanonicalEnvelopes);

    // request all slots 1-6
    final List<SignedExecutionPayloadEnvelope> envelopes =
        requestExecutionPayloadEnvelopesByRange(peer, UInt64.ONE, UInt64.valueOf(6));

    // only canonical envelopes should be returned, not the non-canonical fork ones
    assertThat(envelopes).containsExactlyInAnyOrderElementsOf(expectedEnvelopes);
    assertThat(envelopes).doesNotContainAnyElementsOf(nonCanonicalEnvelopes);
  }

  @TestTemplate
  public void
      requestExecutionPayloadEnvelopes_shouldNotReturnEnvelopesOfPayloadsTheChainDidNotBuildOn()
          throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(GLOAS);

    // canonical chain to slot 3, every payload built on
    peerStorage.chainUpdater().advanceChainUntil(UInt64.valueOf(3));

    // the block at slot 4 builds on the EMPTY variant of the block at slot 3: the payload of slot 3
    // is not part of the chain even though its envelope is stored
    final UInt64 emptyPayloadSlot = UInt64.valueOf(3);
    final UInt64 headSlot = UInt64.valueOf(4);
    final SignedBlockAndState head =
        peerStorage
            .chainUpdater()
            .advanceChain(
                headSlot,
                peerStorage.chainBuilder().createBlockOptionsBuildingOnEmptyParent(headSlot));
    // the head payload has been stored but fork choice has selected the EMPTY variant
    peerStorage.chainUpdater().updateBestBlock(head);
    peerStorage
        .recentChainData()
        .updateHead(ForkChoiceNode.createEmpty(head.getRoot()), head.getSlot());

    final SignedExecutionPayloadEnvelope emptyPayloadEnvelope =
        peerStorage.chainBuilder().getExecutionPayloadAtSlot(emptyPayloadSlot).orElseThrow();
    final SignedExecutionPayloadEnvelope headEnvelope =
        peerStorage.chainBuilder().getExecutionPayloadAtSlot(headSlot).orElseThrow();
    assertThat(peerStorage.recentChainData().containsExecutionPayload(head.getRoot())).isTrue();

    final List<SignedExecutionPayloadEnvelope> envelopes =
        requestExecutionPayloadEnvelopesByRange(peer, UInt64.ONE, UInt64.valueOf(4));

    assertThat(envelopes)
        .containsExactlyInAnyOrderElementsOf(
            peerStorage
                .chainBuilder()
                .streamExecutionPayloads(UInt64.ONE, UInt64.valueOf(2))
                .toList())
        .doesNotContain(emptyPayloadEnvelope, headEnvelope);

    // once fork choice selects the FULL variant of the head, its envelope is served
    peerStorage
        .recentChainData()
        .updateHead(ForkChoiceNode.createFull(head.getRoot()), head.getSlot());

    final List<SignedExecutionPayloadEnvelope> envelopesWithFullHead =
        requestExecutionPayloadEnvelopesByRange(peer, UInt64.ONE, UInt64.valueOf(4));

    assertThat(envelopesWithFullHead)
        .hasSize(3)
        .contains(headEnvelope)
        .doesNotContain(emptyPayloadEnvelope);
  }

  private List<SignedExecutionPayloadEnvelope> requestExecutionPayloadEnvelopesByRange(
      final Eth2Peer peer, final UInt64 startSlot, final UInt64 count)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<SignedExecutionPayloadEnvelope> executionPayloadEnvelopes = new ArrayList<>();
    waitFor(
        peer.requestExecutionPayloadEnvelopesByRange(
            startSlot, count, RpcResponseListener.from(executionPayloadEnvelopes::add)));
    assertThat(peer.getOutstandingRequests()).isEqualTo(0);
    return executionPayloadEnvelopes;
  }
}
