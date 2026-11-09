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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.async.Waiter.waitFor;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;
import static tech.pegasys.teku.spec.SpecMilestone.FULU;
import static tech.pegasys.teku.spec.SpecMilestone.GLOAS;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.NoOpKZG;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;

@TestSpecContext(milestone = {ELECTRA, FULU, GLOAS})
public class DataColumnSidecarsByRangeIntegrationTest extends AbstractRpcMethodIntegrationTest {

  private Eth2Peer peer;
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
    specMilestone = specContext.getSpecMilestone();
  }

  @TestTemplate
  public void requestDataColumnSidecars_shouldFailBeforeFuluMilestone() {
    assumeThat(specMilestone).isLessThan(FULU);
    assertThatThrownBy(
            () -> requestDataColumnSidecarsByRange(peer, UInt64.ONE, UInt64.valueOf(10), List.of()))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("DataColumnSidecarsByRange method is not supported");
  }

  @TestTemplate
  public void requestDataColumnSidecars_shouldReturnEmptyDataColumnSidecarsAfterFuluMilestone()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(FULU);
    final List<DataColumnSidecar> dataColumnSidecars =
        requestDataColumnSidecarsByRange(
            peer, UInt64.ONE, UInt64.valueOf(10), List.of(UInt64.ONE, UInt64.valueOf(2)));
    assertThat(dataColumnSidecars).isEmpty();
  }

  @TestTemplate
  public void requestDataColumnSidecars_shouldReturnEmptyDataColumnSidecarsWhenCountIsZero()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(FULU);

    // finalize chain 2 blobs per block
    finalizeChainWithBlobs(2);

    final List<DataColumnSidecar> dataColumnSidecars =
        requestDataColumnSidecarsByRange(
            peer, UInt64.ONE, UInt64.ZERO, List.of(UInt64.ONE, UInt64.valueOf(2)));

    assertThat(dataColumnSidecars).isEmpty();
  }

  @TestTemplate
  public void requestDataColumnSidecars_shouldReturnOnlyCanonicalDataColumnSidecars()
      throws ExecutionException, InterruptedException, TimeoutException {
    assumeThat(specMilestone).isGreaterThanOrEqualTo(FULU);

    // finalize chain 2 blobs per block
    finalizeChainWithBlobs(2);

    final ChainBuilder fork = peerStorage.chainBuilder().fork();

    final Checkpoint finalizedCheckpoint =
        peerStorage.recentChainData().getFinalizedCheckpoint().orElseThrow();
    final UInt64 finalizedSlot =
        finalizedCheckpoint.getEpochStartSlot(peerStorage.recentChainData().getSpec());

    // add 2 extra blocks that will be canonical
    final UInt64 targetSlot = peerStorage.getChainHead().getSlot().plus(2);
    final SignedBlockAndState canonicalHead =
        peerStorage.chainUpdater().advanceChainUntil(targetSlot);

    // generate non canonical blocks and data columns up to the same target slot
    peerStorage.chainUpdater().blockOptions.setGenerateRandomBlobsCount(Optional.of(2));
    final List<SignedBlockAndState> nonCanonicalBlocksAndStates =
        fork.generateBlocksUpToSlot(targetSlot.intValue(), peerStorage.chainUpdater().blockOptions);

    final List<DataColumnSidecar> nonCanonicalDataColumnSidecars = new ArrayList<>();
    nonCanonicalBlocksAndStates.forEach(
        signedBlockAndState -> {
          final List<DataColumnSidecar> dataColumnSidecars =
              fork.getDataColumnSidecars(signedBlockAndState.getRoot());
          nonCanonicalDataColumnSidecars.addAll(dataColumnSidecars);
          peerStorage.chainUpdater().saveBlock(signedBlockAndState);
          dataColumnSidecars.forEach(
              sidecar -> peerStorage.database().addNonCanonicalSidecar(sidecar));
        });

    // make sure canonical head is the canonical head
    peerStorage.chainUpdater().updateBestBlock(canonicalHead);

    // save canonical blocks to local storage, simulating local node having received the canonical
    // beacon blocks via gossip/sync before requesting data column sidecars
    peerStorage
        .chainBuilder()
        .streamBlocksAndStates()
        .forEach(blockAndState -> localPeerStorage.chainUpdater().saveBlock(blockAndState));

    // make sure we have 2 or more heads (post-Gloas, we may have more heads)
    assertThat(peerStorage.recentChainData().getChainHeads().size()).isGreaterThanOrEqualTo(2);

    // lets get data columns starting from 5 slots prior to finalized slot
    final UInt64 startSlot = finalizedSlot.minus(5);

    final List<UInt64> columns = List.of(UInt64.ZERO, UInt64.ONE);

    // grab expected data columns from storage
    final List<DataColumnSidecar> expectedCanonicalDataColumnSidecars =
        retrieveCanonicalDataColumnSidecarsFromPeerStorage(
            UInt64.rangeClosed(startSlot, targetSlot), columns);
    assertThat(expectedCanonicalDataColumnSidecars).isNotEmpty();

    final UInt64 slotCount = targetSlot.minus(startSlot).increment();

    final List<DataColumnSidecar> dataColumnSidecars =
        requestDataColumnSidecarsByRange(peer, startSlot, slotCount, columns);

    assertThat(dataColumnSidecars)
        .containsExactlyInAnyOrderElementsOf(expectedCanonicalDataColumnSidecars);
    assertThat(dataColumnSidecars).doesNotContainAnyElementsOf(nonCanonicalDataColumnSidecars);
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
          peerStorage.chainUpdater().updateBestBlock(blockAndState);
          peerStorage
              .chainBuilder()
              .getDataColumnSidecars(blockAndState.getRoot())
              .forEach(sidecar -> safeJoin(peerStorage.chainStorage().onNewSidecar(sidecar)));
        });
  }

  private List<DataColumnSidecar> requestDataColumnSidecarsByRange(
      final Eth2Peer peer, final UInt64 from, final UInt64 count, final List<UInt64> columns)
      throws InterruptedException, ExecutionException, TimeoutException {
    final List<DataColumnSidecar> dataColumnSidecars = new ArrayList<>();
    waitFor(
        peer.requestDataColumnSidecarsByRange(
            from, count, columns, RpcResponseListener.from(dataColumnSidecars::add)));
    waitFor(() -> assertThat(peer.getOutstandingRequests()).isEqualTo(0));
    return dataColumnSidecars;
  }
}
