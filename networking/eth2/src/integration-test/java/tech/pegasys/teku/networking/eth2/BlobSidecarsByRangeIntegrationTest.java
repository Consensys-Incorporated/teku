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
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.NoOpKZG;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;

@TestSpecContext(milestone = {CAPELLA, DENEB, ELECTRA, FULU})
public class BlobSidecarsByRangeIntegrationTest extends AbstractRpcMethodIntegrationTest {

  private Eth2Peer peer;
  private Spec spec;
  private SpecMilestone specMilestone;

  @BeforeEach
  public void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    specContext
        .getSpec()
        .reinitializeForTesting(
            AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
            AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
            NoOpKZG.INSTANCE);
    peer = createPeer(specContext.getSpec());
    spec = specContext.getSpec();
    specMilestone = specContext.getSpecMilestone();
  }

  @TestTemplate
  public void requestBlobSidecars_shouldFailBeforeDenebMilestone() {
    assumeThat(specMilestone).isLessThan(SpecMilestone.DENEB);
    assertThatThrownBy(() -> requestBlobSidecarsByRange(peer, UInt64.ONE, UInt64.valueOf(10)))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("BlobSidecarsByRange method is not supported");
  }

  @TestTemplate
  public void requestBlobSidecars_shouldReturnEmptyBlobSidecarsAfterDenebMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    final List<BlobSidecar> blobSidecars =
        requestBlobSidecarsByRange(peer, UInt64.ONE, UInt64.valueOf(10));
    assertThat(blobSidecars).isEmpty();
  }

  @TestTemplate
  public void requestBlobSidecars_shouldReturnEmptyBlobSidecarsWhenCountIsZero()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);

    // finalize chain 2 blobs per block
    finalizeChainWithBlobs(2);

    final List<BlobSidecar> blobSidecars =
        requestBlobSidecarsByRange(peer, UInt64.ONE, UInt64.ZERO);

    assertThat(blobSidecars).isEmpty();
  }

  @TestTemplate
  public void requestBlobSidecars_shouldCompleteAfterFuluDeprecation()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isEqualTo(FULU);

    final List<BlobSidecar> blobSidecars = new ArrayList<>();
    final UInt64 startSlot = spec.blobSidecarsDeprecationSlot().increment();
    final SafeFuture<Void> requestFuture =
        peer.requestBlobSidecarsByRange(
            startSlot, UInt64.ONE, RpcResponseListener.from(blobSidecars::add));

    waitFor(requestFuture);
    assertThat(blobSidecars).isEmpty();
    waitFor(() -> assertThat(peer.getOutstandingRequests()).isEqualTo(0));

    assertThat(peer.requestMetadata().get(10, TimeUnit.SECONDS)).isNotNull();
  }

  @TestTemplate
  public void requestBlobSidecars_shouldReturnCanonicalBlobSidecarsOnDenebMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(DENEB);
    assumeThat(specMilestone).isLessThan(FULU);

    // finalize chain 2 blobs per block
    finalizeChainWithBlobs(2);

    final ChainBuilder fork = peerStorage.chainBuilder().fork();

    final Checkpoint finalizedCheckpoint =
        peerStorage.recentChainData().getFinalizedCheckpoint().orElseThrow();
    final UInt64 finalizedSlot =
        finalizedCheckpoint.getEpochStartSlot(peerStorage.recentChainData().getSpec());

    // add 5 extra blocks that will be canonical
    final UInt64 targetSlot = peerStorage.getChainHead().getSlot().plus(5);
    final SignedBlockAndState canonicalHead =
        peerStorage.chainUpdater().advanceChainUntil(targetSlot);

    // generate non canonical blocks and blobs up to the same target slot
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobsCount(Optional.of(4));
    final List<SignedBlockAndState> nonCanonicalBlocksAndStates =
        fork.generateBlocksUpToSlot(targetSlot.intValue(), peerStorage.chainUpdater().blockOptions);

    final List<BlobSidecar> nonCanonicalBlobSidecars = new ArrayList<>();
    nonCanonicalBlocksAndStates.forEach(
        signedBlockAndState -> {
          final List<BlobSidecar> blobSidecars =
              fork.getBlobSidecars(signedBlockAndState.getRoot());
          nonCanonicalBlobSidecars.addAll(blobSidecars);
          peerStorage.chainUpdater().saveBlock(signedBlockAndState, blobSidecars);
        });

    // make sure canonical head is the canonical head
    peerStorage.chainUpdater().updateBestBlock(canonicalHead);

    // make sure we have 2 heads
    assertThat(peerStorage.recentChainData().getChainHeads().size()).isEqualTo(2);

    // lets get blobs starting from 5 slots prior to finalized slot
    final UInt64 startSlot = finalizedSlot.minus(5);

    // grab expected blobs from storage
    final List<BlobSidecar> expectedCanonicalBlobSidecars =
        retrieveCanonicalBlobSidecarsFromPeerStorage(UInt64.rangeClosed(startSlot, targetSlot));

    final UInt64 slotCount = targetSlot.minus(startSlot).increment();
    // call and check
    final List<BlobSidecar> blobSidecars = requestBlobSidecarsByRange(peer, startSlot, slotCount);
    assertThat(blobSidecars).containsExactlyInAnyOrderElementsOf(expectedCanonicalBlobSidecars);
    assertThat(blobSidecars).doesNotContainAnyElementsOf(nonCanonicalBlobSidecars);
  }

  private void finalizeChainWithBlobs(final int blobsPerBlock) {
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobs(true);
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobsCount(Optional.of(blobsPerBlock));

    final List<SignedBlockAndState> finalizedBlocksAndStates =
        peerStorage
            .chainBuilder()
            .finalizeCurrentChain(Optional.of(peerStorage.chainUpdater().blockOptions));
    finalizedBlocksAndStates.forEach(
        blockAndState -> {
          final List<BlobSidecar> blobSidecars =
              peerStorage.chainBuilder().getBlobSidecars(blockAndState.getRoot());
          peerStorage.chainUpdater().saveBlock(blockAndState, blobSidecars);
          peerStorage.chainUpdater().updateBestBlock(blockAndState);
        });
  }

  private List<BlobSidecar> requestBlobSidecarsByRange(
      final Eth2Peer peer, final UInt64 from, final UInt64 count)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<BlobSidecar> blobSidecars = new ArrayList<>();
    waitFor(
        peer.requestBlobSidecarsByRange(from, count, RpcResponseListener.from(blobSidecars::add)));
    waitFor(() -> assertThat(peer.getOutstandingRequests()).isEqualTo(0));
    return blobSidecars;
  }
}
