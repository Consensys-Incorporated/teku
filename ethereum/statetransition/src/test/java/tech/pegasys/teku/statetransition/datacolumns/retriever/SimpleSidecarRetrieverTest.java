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

package tech.pegasys.teku.statetransition.datacolumns.retriever;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.kzg.trusted_setups.TrustedSetupLoader;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

/**
 * Known coverage gap: these tests drive the retriever through {@link StubAsyncRunner}, which runs
 * every scheduled action on the calling thread. Rounds are therefore strictly serialized, while
 * production runs {@link SimpleSidecarRetriever#nextRound()} on a multi-threaded das runner from
 * both the fixed-delay schedule and {@link SimpleSidecarRetriever#flush()}. Nothing here exercises
 * two rounds overlapping, so the interleavings the implementation is explicitly written to tolerate
 * are untested: a completion callback racing the registration of the attempt it belongs to, an
 * attempt being claimed by another round between being matched and being activated, and a request
 * transiently carrying more than two attempts. Those paths are unreachable single-threaded (within
 * a round the primary and hedge passes select disjoint requests, and hedges exclude peers already
 * in attemptingPeers), so reaching them would need a threaded stress test with no deterministic
 * failure condition.
 */
@SuppressWarnings({"JavaCase"})
public class SimpleSidecarRetrieverTest {
  private static final Logger LOG = LogManager.getLogger();
  final StubTimeProvider stubTimeProvider = StubTimeProvider.withTimeInSeconds(0);
  final StubAsyncRunner stubAsyncRunner = new StubAsyncRunner(stubTimeProvider);

  final Spec spec = TestSpecFactory.createMinimalFulu();
  final TestPeerManager testPeerManager = new TestPeerManager();
  final SpecConfigFulu config =
      SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig());
  final MiscHelpersFulu miscHelpers =
      MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
  final int columnCount = config.getNumberOfColumns();
  final KZG kzg = KZG.getInstance(false);

  final DasPeerCustodyCountSupplierStub custodyCountSupplier =
      new DasPeerCustodyCountSupplierStub(config.getCustodyRequirement());

  final Duration retrieverRound = Duration.ofSeconds(1);
  final Duration hedgeDelay = Duration.ofSeconds(2);
  // hedging disabled (overlapFraction == 0)
  final SimpleSidecarRetriever simpleSidecarRetriever =
      new SimpleSidecarRetriever(
          spec,
          testPeerManager,
          custodyCountSupplier,
          testPeerManager,
          stubAsyncRunner,
          stubTimeProvider,
          retrieverRound,
          0.0,
          hedgeDelay);

  // Retriever with hedging enabled: up to 100% of pending requests may be hedged, after being
  // in-flight for hedgeDelay. Uses the same peer manager so tests can share the peer setup helpers.
  final SimpleSidecarRetriever hedgingRetriever =
      new SimpleSidecarRetriever(
          spec,
          testPeerManager,
          custodyCountSupplier,
          testPeerManager,
          stubAsyncRunner,
          stubTimeProvider,
          retrieverRound,
          1.0,
          hedgeDelay);

  final UInt64 columnIndex = UInt64.valueOf(1);

  final Iterator<UInt256> custodyNodeIds = craftNodeIdsCustodyOf(columnIndex).iterator();
  final Iterator<UInt256> nonCustodyNodeIds = craftNodeIdsNotCustodyOf(columnIndex).iterator();

  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(0, spec);

  public SimpleSidecarRetrieverTest() {
    TrustedSetupLoader.loadTrustedSetupForTests(kzg);
  }

  Set<UInt64> nodeCustodyColumns(final UInt256 nodeId) {
    return miscHelpers.computeCustodyColumnIndices(
        nodeId, custodyCountSupplier.getCustodyGroupCountForPeer(nodeId));
  }

  Stream<UInt256> craftNodeIds() {
    return IntStream.iterate(0, i -> i + 1).mapToObj(UInt256::valueOf);
  }

  Stream<UInt256> craftNodeIdsCustodyOf(final UInt64 custodyColumn) {
    return craftNodeIds().filter(nodeId -> nodeCustodyColumns(nodeId).contains(custodyColumn));
  }

  Stream<UInt256> craftNodeIdsNotCustodyOf(final UInt64 custodyColumn) {
    return craftNodeIds().filter(nodeId -> !nodeCustodyColumns(nodeId).contains(custodyColumn));
  }

  private void advanceTimeGradually(final Duration delta) {
    for (long i = 0; i < delta.toMillis(); i++) {
      stubTimeProvider.advanceTimeBy(Duration.ofMillis(1));
      stubAsyncRunner.executeDueActionsRepeatedly();
    }
  }

  @Test
  void constructorRejectsOutOfRangeOverlapFraction() {
    assertThatThrownBy(
            () ->
                new SimpleSidecarRetriever(
                    spec,
                    testPeerManager,
                    custodyCountSupplier,
                    testPeerManager,
                    stubAsyncRunner,
                    stubTimeProvider,
                    retrieverRound,
                    1.5,
                    hedgeDelay))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sanityTest() {
    final TestPeer custodyPeerMissingData = createCustodyPeer();
    final TestPeer custodyPeerHavingData = createCustodyPeer();
    final TestPeer nonCustodyPeer = createNonCustodyPeer();

    final DataColumnSidecar sidecar0 = createSidecarAndAddToAllPeers(10, custodyPeerHavingData);
    final DataColumnSlotAndIdentifier id0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    testPeerManager.connectPeer(custodyPeerMissingData);
    testPeerManager.connectPeer(nonCustodyPeer);
    assertThat(
            simpleSidecarRetriever
                .getConnectedPeers()
                .get(custodyPeerMissingData.getNodeId())
                .getResponseScore())
        .isEqualTo(10);

    final SafeFuture<DataColumnSidecar> resp0 = simpleSidecarRetriever.retrieve(id0);

    advanceTimeGradually(retrieverRound.multipliedBy(2));

    assertThat(resp0).isNotDone();
    assertThat(custodyPeerMissingData.getRequests()).hasSize(2);
    assertThat(
            simpleSidecarRetriever
                .getConnectedPeers()
                .get(custodyPeerMissingData.getNodeId())
                .getResponseScore())
        .isEqualTo(3);

    testPeerManager.connectPeer(custodyPeerHavingData);
    assertThat(
            simpleSidecarRetriever
                .getConnectedPeers()
                .get(custodyPeerHavingData.getNodeId())
                .getResponseScore())
        .isEqualTo(10);

    advanceTimeGradually(retrieverRound.multipliedBy(2));

    assertThat(resp0).isCompletedWithValue(sidecar0);
    assertThat(nonCustodyPeer.getRequests()).isEmpty();
    assertThat(custodyPeerHavingData.getRequests()).hasSize(1);
    assertThat(custodyPeerMissingData.getRequests()).hasSize(2);
  }

  @Test
  void selectingBestPeerShouldRespectRequestLimits() {
    final TestPeer nonCustodyPeer = createNonCustodyPeer();
    final TestPeer overloadedCustodyPeer = createCustodyPeer(0);
    final TestPeer busyCustodyPeer = createCustodyPeer(10);
    final TestPeer freeCustodyPeer = createCustodyPeer();

    Supplier<List<Integer>> allRequestCountsFunc =
        connectPeers(nonCustodyPeer, overloadedCustodyPeer, busyCustodyPeer, freeCustodyPeer);

    final DataColumnSlotAndIdentifier id0 =
        new DataColumnSlotAndIdentifier(UInt64.ONE, Bytes32.ZERO, columnIndex);
    simpleSidecarRetriever.retrieve(id0).finish(err -> LOG.error("Error retrieving sidecar", err));

    advanceTimeGradually(retrieverRound);
    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 0, 0, 1));

    advanceTimeGradually(retrieverRound);
    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 0, 1, 1));

    advanceTimeGradually(retrieverRound);
    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 0, 1, 2));

    advanceTimeGradually(retrieverRound);
    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 0, 2, 2));

    overloadedCustodyPeer.currentRequestLimit(1);
    advanceTimeGradually(retrieverRound);
    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 1, 2, 2));
  }

  @Test
  void selectingBestPeerShouldRespectAdvertisedEarliestSlotAvailable() {
    final TestPeer peerWithEarliestSlotAvailableSetToLargeSlot =
        createCustodyPeer(UInt64.MAX_VALUE);
    final TestPeer peerWithEarliestSlotAvailableZero = createCustodyPeer(UInt64.ZERO);
    final TestPeer peerWithEarliestSlotAvailableOne = createCustodyPeer(UInt64.ONE);
    final TestPeer peerWithEarliestSlotAvailableTwo = createCustodyPeer(UInt64.valueOf(2));

    final Supplier<List<Integer>> allRequestCountsFunc =
        connectPeers(
            peerWithEarliestSlotAvailableSetToLargeSlot,
            peerWithEarliestSlotAvailableZero,
            peerWithEarliestSlotAvailableOne,
            peerWithEarliestSlotAvailableTwo);

    final DataColumnSidecar sidecar0 =
        createSidecarAndAddToAllPeers(0, peerWithEarliestSlotAvailableZero);
    simpleSidecarRetriever
        .retrieve(DataColumnSlotAndIdentifier.fromDataColumn(sidecar0))
        .finish(err -> LOG.error("Error retrieving sidecar", err));
    advanceTimeGradually(retrieverRound);
    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 1, 0, 0));

    final DataColumnSidecar sidecar1 =
        createSidecarAndAddToAllPeers(
            1, peerWithEarliestSlotAvailableZero, peerWithEarliestSlotAvailableOne);
    simpleSidecarRetriever
        .retrieve(DataColumnSlotAndIdentifier.fromDataColumn(sidecar1))
        .finish(err -> LOG.error("Error retrieving sidecar", err));
    advanceTimeGradually(retrieverRound);
    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 1, 1, 0));

    final DataColumnSidecar sidecar2 =
        createSidecarAndAddToAllPeers(
            2,
            peerWithEarliestSlotAvailableZero,
            peerWithEarliestSlotAvailableOne,
            peerWithEarliestSlotAvailableTwo);
    simpleSidecarRetriever
        .retrieve(DataColumnSlotAndIdentifier.fromDataColumn(sidecar2))
        .finish(err -> LOG.error("Error retrieving sidecar", err));
    advanceTimeGradually(retrieverRound);
    assertThat(allRequestCountsFunc.get()).isEqualTo(List.of(0, 1, 1, 1));
  }

  @Test
  void selectingBestPeerShouldRespectResponseScore() {
    final TestPeer nonCustodyPeer = createNonCustodyPeer(1000);
    final TestPeer goodScoreCustodyPeer = createCustodyPeer(1000);
    final TestPeer medianScoreCustodyPeer = createCustodyPeer(1000);
    final TestPeer badScoreCustodyPeer = createCustodyPeer(1000);

    connectPeers(nonCustodyPeer, goodScoreCustodyPeer, medianScoreCustodyPeer, badScoreCustodyPeer);

    setRequestScore(goodScoreCustodyPeer, 10);
    setRequestScore(medianScoreCustodyPeer, 5);
    setRequestScore(badScoreCustodyPeer, 1);

    // verifying all requests within request limit goes to goodScoreCustodyPeer,
    assertThat(goodScoreCustodyPeer.getAvailableRequestCount()).isEqualTo(1000);
    for (int i = 0; i < 1000; ++i) {
      final DataColumnSidecar sidecar =
          createSidecarAndAddToAllPeers(
              i, goodScoreCustodyPeer, medianScoreCustodyPeer, badScoreCustodyPeer);
      simpleSidecarRetriever
          .retrieve(DataColumnSlotAndIdentifier.fromDataColumn(sidecar))
          .finish(err -> LOG.error("Error retrieving sidecar", err));
    }

    advanceTimeGradually(retrieverRound.multipliedBy(2));
    assertThat(goodScoreCustodyPeer.getAvailableRequestCount()).isEqualTo(0);
  }

  @Test
  void cancellingRequestShouldRemoveItFromPending() {
    final TestPeer custodyPeer = createCustodyPeer();
    testPeerManager.connectPeer(custodyPeer);

    final DataColumnSlotAndIdentifier id0 =
        new DataColumnSlotAndIdentifier(UInt64.ONE, Bytes32.ZERO, columnIndex);
    SafeFuture<DataColumnSidecar> resp0_0 = simpleSidecarRetriever.retrieve(id0);

    advanceTimeGradually(retrieverRound);
    assertThat(custodyPeer.getRequests()).hasSize(1);
    advanceTimeGradually(retrieverRound);
    assertThat(custodyPeer.getRequests()).hasSize(2);

    resp0_0.cancel(true);

    advanceTimeGradually(retrieverRound);
    // after original request is cancelled the retriever should stop requesting peer
    assertThat(custodyPeer.getRequests()).hasSize(2);
    advanceTimeGradually(retrieverRound);
    assertThat(custodyPeer.getRequests()).hasSize(2);
  }

  @Test
  void sidecarReceivedFromGossipShouldCancelActiveRequest() {
    final TestPeer custodyPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1));
    testPeerManager.connectPeer(custodyPeer);
    final DataColumnSidecar sidecar = createSidecarAndAddToAllPeers(1);
    final SafeFuture<DataColumnSidecar> result =
        simpleSidecarRetriever.retrieve(DataColumnSlotAndIdentifier.fromDataColumn(sidecar));

    advanceTimeGradually(retrieverRound);
    assertThat(custodyPeer.getRequests()).hasSize(1);
    assertThat(custodyPeer.getRequests().getFirst().response()).isNotDone();

    simpleSidecarRetriever.onNewValidatedSidecar(sidecar, RemoteOrigin.GOSSIP);
    advanceTimeGradually(Duration.ofMillis(1));

    assertThat(result).isCompletedWithValue(sidecar);
    assertThat(custodyPeer.getRequests().getFirst().response()).isCancelled();
  }

  @Test
  void requestCancelledOnOurSideShouldNotAffectPeerScore() {
    final TestPeer custodyPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1));
    testPeerManager.connectPeer(custodyPeer);
    final DataColumnSidecar sidecar = createSidecarAndAddToAllPeers(1, custodyPeer);
    final SafeFuture<DataColumnSidecar> result =
        simpleSidecarRetriever.retrieve(DataColumnSlotAndIdentifier.fromDataColumn(sidecar));

    advanceTimeGradually(retrieverRound);
    final SimpleSidecarRetriever.ConnectedPeer connectedPeer =
        simpleSidecarRetriever.getConnectedPeers().get(custodyPeer.getNodeId());
    assertThat(custodyPeer.getRequests()).hasSize(1);
    assertThat(connectedPeer.getSidecarsRequested()).hasValue(2);

    // the sidecar arrives via gossip while the request is still in flight
    simpleSidecarRetriever.onNewValidatedSidecar(sidecar, RemoteOrigin.GOSSIP);
    advanceTimeGradually(Duration.ofMillis(1));

    assertThat(result).isCompletedWithValue(sidecar);
    assertThat(custodyPeer.getRequests().getFirst().response()).isCancelled();
    // we cancelled the request, so the peer shouldn't be penalised for not responding
    assertThat(connectedPeer.getSidecarsRequested()).hasValue(1);
    assertThat(connectedPeer.getResponseScore()).isEqualTo(10);
  }

  @Test
  void failedRequestShouldAffectPeerScore() {
    final TestPeer custodyPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1));
    testPeerManager.connectPeer(custodyPeer);
    final DataColumnSidecar sidecar = createSidecarAndAddToAllPeers(1, custodyPeer);
    simpleSidecarRetriever
        .retrieve(DataColumnSlotAndIdentifier.fromDataColumn(sidecar))
        .finish(err -> LOG.trace("Error retrieving sidecar", err));

    advanceTimeGradually(retrieverRound);
    final SimpleSidecarRetriever.ConnectedPeer connectedPeer =
        simpleSidecarRetriever.getConnectedPeers().get(custodyPeer.getNodeId());
    assertThat(custodyPeer.getRequests()).hasSize(1);

    custodyPeer
        .getRequests()
        .getFirst()
        .response()
        .completeExceptionally(new RuntimeException("Peer error"));
    advanceTimeGradually(Duration.ofMillis(1));

    // the peer failed to respond, so the request must still count against its score
    assertThat(connectedPeer.getSidecarsRequested()).hasValue(2);
    assertThat(connectedPeer.getResponseScore()).isEqualTo(5);
  }

  @Test
  void hedgingShouldReDispatchToAlternatePeerWhenOriginalIsSlow() {
    final TestPeer slowPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1))
            .currentRequestLimit(1000);
    final TestPeer fastAlternatePeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(1000);

    final DataColumnSidecar sidecar0 = createSidecarAndAddToAllPeers(10, fastAlternatePeer);
    final DataColumnSlotAndIdentifier id0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    // Only the slow peer is available when the request starts, so the primary attempt lands on it.
    testPeerManager.connectPeer(slowPeer);
    final SafeFuture<DataColumnSidecar> resp = hedgingRetriever.retrieve(id0);

    advanceTimeGradually(retrieverRound);
    assertThat(resp).isNotDone();
    assertThat(slowPeer.getRequests()).hasSize(1);

    // A capable alternate appears; after the hedge delay the request is re-dispatched to it and
    // completes even though the original peer never responds.
    testPeerManager.connectPeer(fastAlternatePeer);
    advanceTimeGradually(retrieverRound.multipliedBy(3));

    assertThat(resp).isCompletedWithValue(sidecar0);
    assertThat(fastAlternatePeer.getRequests()).hasSize(1);
    assertThat(slowPeer.getRequests()).hasSize(1);
    assertThat(hedgingRetriever.getHedgeCount()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void withoutHedgingRequestStaysStuckOnSlowPeer() {
    final TestPeer slowPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1))
            .currentRequestLimit(1000);
    final TestPeer fastAlternatePeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(1000);

    final DataColumnSidecar sidecar0 = createSidecarAndAddToAllPeers(10, fastAlternatePeer);
    final DataColumnSlotAndIdentifier id0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    testPeerManager.connectPeer(slowPeer);
    // default retriever has hedging disabled (overlapFraction == 0)
    final SafeFuture<DataColumnSidecar> resp = simpleSidecarRetriever.retrieve(id0);

    advanceTimeGradually(retrieverRound);
    testPeerManager.connectPeer(fastAlternatePeer);
    advanceTimeGradually(retrieverRound.multipliedBy(5));

    // Without hedging the request is never re-dispatched to the available alternate.
    assertThat(resp).isNotDone();
    assertThat(fastAlternatePeer.getRequests()).isEmpty();
    assertThat(slowPeer.getRequests()).hasSize(1);
    assertThat(simpleSidecarRetriever.getHedgeCount()).isZero();
  }

  @Test
  void zeroOverlapFractionShouldDisableHedgingAtEveryPendingCount() {
    // The budget floors at one when hedging is enabled, so guard the off switch explicitly: at
    // overlapFraction 0 no request is ever hedged, whether the pending set is below the point where
    // the proportional cap would round down to zero or well above it.
    final TestPeer slowPeer = createSupernodePeer(Duration.ofDays(1));
    final TestPeer alternatePeer = createSupernodePeer(Duration.ofMillis(100));

    // 40 pending requests: floor(fraction * N) would be non-zero for any fraction >= 0.025.
    final List<DataColumnSlotAndIdentifier> ids =
        IntStream.range(0, 40)
            .mapToObj(
                i -> new DataColumnSlotAndIdentifier(UInt64.ONE, Bytes32.ZERO, UInt64.valueOf(i)))
            .toList();

    testPeerManager.connectPeer(slowPeer);
    // simpleSidecarRetriever is built with overlapFraction == 0
    ids.forEach(id -> simpleSidecarRetriever.retrieve(id).finish(err -> LOG.debug("err", err)));

    advanceTimeGradually(retrieverRound);
    assertThat(slowPeer.getRequests()).hasSize(40);

    // An idle alternate is available and every primary attempt is long past the hedge delay, yet
    // nothing is ever re-dispatched.
    testPeerManager.connectPeer(alternatePeer);
    advanceTimeGradually(retrieverRound.multipliedBy(6));

    assertThat(alternatePeer.getRequests()).isEmpty();
    assertThat(simpleSidecarRetriever.getHedgeCount()).isZero();
    assertThat(slowPeer.getRequests()).hasSize(40);
  }

  @Test
  void hedgingShouldApplyToASingleStuckRequestAtTheDefaultOverlapFraction() {
    // floor(0.05 * N) is 0 for every N < 20, so a naive budget would disable hedging exactly in the
    // scenario it exists for: a lone column stuck on a slow peer. The budget floors at one whenever
    // hedging is enabled. 0.05 mirrors P2PConfig.DEFAULT_SIDECAR_RETRIEVAL_OVERLAP_FRACTION, which
    // this module cannot reference (networking sits above ethereum in the module layering).
    final double defaultOverlapFraction = 0.05;
    final SimpleSidecarRetriever retriever =
        new SimpleSidecarRetriever(
            spec,
            testPeerManager,
            custodyCountSupplier,
            testPeerManager,
            stubAsyncRunner,
            stubTimeProvider,
            retrieverRound,
            defaultOverlapFraction,
            hedgeDelay);

    final TestPeer slowPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1))
            .currentRequestLimit(1000);
    final TestPeer fastAlternatePeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(1000);

    final DataColumnSidecar sidecar0 = createSidecarAndAddToAllPeers(10, fastAlternatePeer);
    final DataColumnSlotAndIdentifier id0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    testPeerManager.connectPeer(slowPeer);
    final SafeFuture<DataColumnSidecar> resp = retriever.retrieve(id0);

    advanceTimeGradually(retrieverRound);
    assertThat(resp).isNotDone();

    testPeerManager.connectPeer(fastAlternatePeer);
    advanceTimeGradually(retrieverRound.multipliedBy(3));

    assertThat(resp).isCompletedWithValue(sidecar0);
    assertThat(retriever.getHedgeCount()).isEqualTo(1);
  }

  @Test
  void winningHedgeShouldNotPenalisePeerWhoseAttemptWasCancelled() {
    final TestPeer slowPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1))
            .currentRequestLimit(1000);
    final TestPeer fastAlternatePeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(1000);

    final DataColumnSidecar sidecar0 = createSidecarAndAddToAllPeers(10, fastAlternatePeer);
    final DataColumnSlotAndIdentifier id0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    testPeerManager.connectPeer(slowPeer);
    final SafeFuture<DataColumnSidecar> resp = hedgingRetriever.retrieve(id0);

    advanceTimeGradually(retrieverRound);
    assertThat(resp).isNotDone();

    testPeerManager.connectPeer(fastAlternatePeer);
    advanceTimeGradually(retrieverRound.multipliedBy(3));

    // The hedge won and the slow peer's still in-flight attempt was cancelled by us
    assertThat(resp).isCompletedWithValue(sidecar0);
    assertThat(hedgingRetriever.getHedgeCount()).isEqualTo(1);
    assertThat(slowPeer.getRequests().getFirst().response()).isCancelled();

    // So the slow peer must not be scored down for a response we stopped waiting for
    final SimpleSidecarRetriever.ConnectedPeer slowConnectedPeer =
        hedgingRetriever.getConnectedPeers().get(slowPeer.getNodeId());
    assertThat(slowConnectedPeer.getSidecarsRequested()).hasValue(1);
    assertThat(slowConnectedPeer.getResponseScore()).isEqualTo(10);
  }

  @Test
  void hedgingShouldRespectOverlapBudget() {
    // 4 pending requests, 25% overlap -> at most 1 concurrent hedged attempt.
    final SimpleSidecarRetriever retriever =
        new SimpleSidecarRetriever(
            spec,
            testPeerManager,
            custodyCountSupplier,
            testPeerManager,
            stubAsyncRunner,
            stubTimeProvider,
            retrieverRound,
            0.25,
            hedgeDelay);

    final TestPeer slowPeer = createSupernodePeer(Duration.ofDays(1));
    final TestPeer slowAlternatePeer = createSupernodePeer(Duration.ofDays(1));

    final List<DataColumnSlotAndIdentifier> ids =
        IntStream.range(0, 4)
            .mapToObj(
                i -> new DataColumnSlotAndIdentifier(UInt64.ONE, Bytes32.ZERO, UInt64.valueOf(i)))
            .toList();

    // Only the slow peer present initially, so all 4 primaries land on it.
    testPeerManager.connectPeer(slowPeer);
    ids.forEach(id -> retriever.retrieve(id).finish(err -> LOG.error("err", err)));

    advanceTimeGradually(retrieverRound);
    assertThat(slowPeer.getRequests()).hasSize(4);

    // The alternate never responds either, so a hedged attempt keeps the overlap budget consumed
    // and no further hedges are issued regardless of how many rounds elapse.
    testPeerManager.connectPeer(slowAlternatePeer);
    advanceTimeGradually(retrieverRound.multipliedBy(6));

    assertThat(slowAlternatePeer.getRequests()).hasSize(1);
    assertThat(retriever.getHedgeCount()).isEqualTo(1);
    assertThat(slowPeer.getRequests()).hasSize(4);
  }

  @Test
  void shouldReHedgeToThirdPeerWhenPrimaryDropsWhileHedgeInFlight() {
    // Primary attempt (highest score) stays in-flight past hedgeDelay and picks up a hedge; the
    // hedge peer keeps hanging; the primary then disconnects, and the slot it frees must be
    // re-hedged to a third (data-holding) peer.
    //
    // The primary is removed by disconnecting it rather than by exhausting a request limit: a
    // TestPeer's limit is a monotonic lifetime quota, whereas the production limit
    // (DataColumnPeerManagerImpl -> RateTracker) is a decaying time window whose charge is adjusted
    // down to the number of objects actually returned, so a failed or empty response frees the
    // peer's budget almost immediately and never retires it. Driving the exclusion off that quota
    // would test a stub-only mechanism.
    //
    // Note this makes the test slightly optimistic about peer freshness: with no disconnect,
    // nothing stops a merely-failing peer from winning re-selection until its response score has
    // decayed (see the caveat on the re-hedge example in addHedgeMatches).
    final TestPeer primaryPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1))
            .currentRequestLimit(1000);
    final TestPeer hangingHedgePeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1))
            .currentRequestLimit(1000);
    final TestPeer thirdPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(1000);

    // Only the third peer has the data.
    final DataColumnSidecar sidecar0 = createSidecarAndAddToAllPeers(10, thirdPeer);
    final DataColumnSlotAndIdentifier id0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    connectPeers(primaryPeer, hangingHedgePeer, thirdPeer);
    // Scores drive selection order: primary first, hanging peer as the first hedge, third peer
    // last.
    setRequestScore(hedgingRetriever, primaryPeer, 10);
    setRequestScore(hedgingRetriever, hangingHedgePeer, 5);
    setRequestScore(hedgingRetriever, thirdPeer, 1);

    final SafeFuture<DataColumnSidecar> resp = hedgingRetriever.retrieve(id0);

    // Primary attempt, then one hedge to the hanging peer once hedgeDelay has passed. The overlap
    // budget is now spent (the request carries two attempts), so nothing else is dispatched.
    advanceTimeGradually(retrieverRound.multipliedBy(4));
    assertThat(resp).isNotDone();
    assertThat(hedgingRetriever.getHedgeCount()).isEqualTo(1);
    assertThat(primaryPeer.getRequests()).hasSize(1);
    assertThat(hangingHedgePeer.getRequests()).hasSize(1);
    assertThat(thirdPeer.getRequests()).isEmpty();

    // The primary drops: its in-flight attempt fails and it leaves the custody peer set, so the
    // request is back to a single (still hanging) attempt and becomes hedge-eligible again.
    testPeerManager.disconnectPeer(primaryPeer);
    advanceTimeGradually(retrieverRound.multipliedBy(3));

    assertThat(resp).isCompletedWithValue(sidecar0);
    assertThat(hedgingRetriever.getHedgeCount()).isEqualTo(2);
    assertThat(primaryPeer.getRequests()).hasSize(1);
    assertThat(hangingHedgePeer.getRequests()).hasSize(1);
    assertThat(thirdPeer.getRequests()).hasSize(1);
  }

  @Test
  void requestIsEventuallyClearedRegardlessOfAttemptOrdering() {
    // No peer can serve the column initially: one attempt hangs forever, another keeps failing, so
    // attempts rotate/hedge in whatever order across rounds. The request must stay pending - never
    // lost, never wrongly completed - and once any peer can serve it, it is eventually completed
    // and removed from the pending set.
    final TestPeer hangingPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofDays(1))
            .currentRequestLimit(1000);
    final TestPeer failingPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(1000);

    // Build the sidecar but don't hand it to any connected peer yet.
    final DataColumnSidecar sidecar0 = createSidecarAndAddToAllPeers(10);
    final DataColumnSlotAndIdentifier id0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    connectPeers(hangingPeer, failingPeer);
    final SafeFuture<DataColumnSidecar> resp = hedgingRetriever.retrieve(id0);

    advanceTimeGradually(retrieverRound.multipliedBy(10));
    assertThat(resp).isNotDone();
    assertThat(hedgingRetriever.getPendingRequestCount()).isEqualTo(1);

    // A capable peer appears; regardless of the earlier attempt ordering the request clears.
    final TestPeer dataPeer =
        new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
            .currentRequestLimit(1000);
    dataPeer.addSidecar(sidecar0);
    testPeerManager.connectPeer(dataPeer);

    advanceTimeGradually(retrieverRound.multipliedBy(10));
    assertThat(resp).isCompletedWithValue(sidecar0);
    assertThat(hedgingRetriever.getPendingRequestCount()).isZero();
  }

  @Test
  void attemptFailingBeforeRegistrationShouldNotLeakPeerRequestBudget() {
    // An attempt can complete before activateMatchedRequest registers it: the reqResp buffer is
    // shared across rounds, so a concurrent round's flush() may dispatch and fail this request
    // first. Modelled here by a reqResp that fails the peer's request synchronously. The failed
    // attempt must not leave a completed entry behind in activeRequests - such an entry would
    // permanently consume the peer's request budget and, with a limit of 1, lock the only custody
    // peer out of ever retrying the still-pending column.
    final TestPeer custodyPeer = createCustodyPeer(1);
    final SynchronouslyFailingReqResp failingReqResp =
        new SynchronouslyFailingReqResp(testPeerManager, custodyPeer.getNodeId());
    final SimpleSidecarRetriever retriever =
        new SimpleSidecarRetriever(
            spec,
            testPeerManager,
            custodyCountSupplier,
            failingReqResp,
            stubAsyncRunner,
            stubTimeProvider,
            retrieverRound,
            0.0,
            hedgeDelay);

    testPeerManager.connectPeer(custodyPeer);
    final DataColumnSlotAndIdentifier id0 =
        new DataColumnSlotAndIdentifier(UInt64.ONE, Bytes32.ZERO, columnIndex);
    retriever.retrieve(id0).finish(err -> LOG.debug("Error retrieving sidecar", err));

    advanceTimeGradually(retrieverRound.multipliedBy(3));

    // The request is still pending and the peer is retried every round rather than being reserved
    // by a stale in-flight entry.
    assertThat(retriever.getPendingRequestCount()).isEqualTo(1);
    assertThat(failingReqResp.getRequestCount()).isEqualTo(3);
  }

  @Test
  void gossipWinningMidDispatchShouldWithdrawTheBufferedRequest() {
    // Gossip can deliver the column while activateMatchedRequest is between claiming the peer and
    // registering the attempt. The attempt is then abandoned, and the reqResp promise must be
    // cancelled so the still-buffered request is dropped at flush instead of going to the network
    // for a column we already have - and the peer must not be scored down for it.
    final DataColumnSidecar sidecar0 = createSidecarAndAddToAllPeers(10);
    final DataColumnSlotAndIdentifier id0 = DataColumnSlotAndIdentifier.fromDataColumn(sidecar0);

    // Delivers the sidecar by gossip from inside the dispatch call itself, reproducing the race
    // deterministically, and hands back a promise that stays pending the way a buffered request
    // does.
    final GossipRacingReqResp racingReqResp = new GossipRacingReqResp();
    final SimpleSidecarRetriever retriever =
        new SimpleSidecarRetriever(
            spec,
            testPeerManager,
            custodyCountSupplier,
            racingReqResp,
            stubAsyncRunner,
            stubTimeProvider,
            retrieverRound,
            0.0,
            hedgeDelay);
    racingReqResp.deliverByGossipOnRequest(retriever, sidecar0);

    // Connect only now: peerConnected is delivered to listeners at connect time.
    final TestPeer custodyPeer = createCustodyPeer();
    testPeerManager.connectPeer(custodyPeer);

    final int requestedBefore =
        retriever.getConnectedPeers().get(custodyPeer.getNodeId()).getSidecarsRequested().get();

    final SafeFuture<DataColumnSidecar> resp = retriever.retrieve(id0);
    advanceTimeGradually(retrieverRound);

    assertThat(resp).isCompletedWithValue(sidecar0);
    // The buffered request was withdrawn rather than left pending for flush to send.
    assertThat(racingReqResp.getIssuedPromise()).isCancelled();
    // ... and the peer is not charged for a request that never reached the wire.
    assertThat(retriever.getConnectedPeers().get(custodyPeer.getNodeId()).getSidecarsRequested())
        .hasValue(requestedBefore);
    assertThat(retriever.getPendingRequestCount()).isZero();
  }

  /**
   * Delivers a sidecar via the retriever's gossip entry point from within {@code
   * requestDataColumnSidecar}, i.e. while the caller is still mid-dispatch, and returns a promise
   * that never completes on its own - as a request buffered until the next flush would.
   */
  private static class GossipRacingReqResp implements DataColumnReqResp {
    private final SafeFuture<DataColumnSidecar> issuedPromise = new SafeFuture<>();
    private Optional<Runnable> onRequest = Optional.empty();

    void deliverByGossipOnRequest(
        final SimpleSidecarRetriever retriever, final DataColumnSidecar sidecar) {
      this.onRequest =
          Optional.of(() -> retriever.onNewValidatedSidecar(sidecar, RemoteOrigin.GOSSIP));
    }

    SafeFuture<DataColumnSidecar> getIssuedPromise() {
      return issuedPromise;
    }

    @Override
    public SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
        final UInt256 nodeId, final DataColumnSlotAndIdentifier columnIdentifier) {
      onRequest.ifPresent(Runnable::run);
      return issuedPromise;
    }

    @Override
    public void flush() {}

    @Override
    public int getCurrentRequestLimit(final UInt256 nodeId) {
      return 1000;
    }
  }

  /** Fails requests to a given peer synchronously, i.e. before the caller sees the promise. */
  private static class SynchronouslyFailingReqResp implements DataColumnReqResp {
    private final DataColumnReqResp delegate;
    private final UInt256 failingNodeId;
    private final AtomicInteger requestCount = new AtomicInteger();

    private SynchronouslyFailingReqResp(
        final DataColumnReqResp delegate, final UInt256 failingNodeId) {
      this.delegate = delegate;
      this.failingNodeId = failingNodeId;
    }

    int getRequestCount() {
      return requestCount.get();
    }

    @Override
    public SafeFuture<DataColumnSidecar> requestDataColumnSidecar(
        final UInt256 nodeId, final DataColumnSlotAndIdentifier columnIdentifier) {
      if (!nodeId.equals(failingNodeId)) {
        return delegate.requestDataColumnSidecar(nodeId, columnIdentifier);
      }
      requestCount.incrementAndGet();
      return SafeFuture.failedFuture(new DataColumnReqRespException());
    }

    @Override
    public void flush() {
      delegate.flush();
    }

    @Override
    public int getCurrentRequestLimit(final UInt256 nodeId) {
      return delegate.getCurrentRequestLimit(nodeId);
    }
  }

  @Test
  @SuppressWarnings("unused")
  void performanceTest() {
    final List<TestPeer> testNodes =
        craftNodeIds()
            .map(
                nodeId ->
                    new TestPeer(stubAsyncRunner, nodeId, Duration.ofMillis(100))
                        .currentRequestLimit(1000))
            .limit(128)
            .peek(node -> custodyCountSupplier.setCustomCount(node.getNodeId(), columnCount))
            .peek(testPeerManager::connectPeer)
            .toList();

    final List<DataColumnSlotAndIdentifier> columnIds =
        IntStream.range(0, Integer.MAX_VALUE)
            .mapToObj(UInt64::valueOf)
            .flatMap(
                slot ->
                    IntStream.range(0, columnCount)
                        .mapToObj(UInt64::valueOf)
                        .map(colIdx -> new DataColumnSlotAndIdentifier(slot, Bytes32.ZERO, colIdx)))
            .limit(20_000)
            .toList();

    columnIds.forEach(columnId -> simpleSidecarRetriever.retrieve(columnId).finishDebug(LOG));

    Assertions.assertTimeout(Duration.ofSeconds(10), () -> advanceTimeGradually(retrieverRound));
  }

  @Test
  @SuppressWarnings("FutureReturnValueIgnored")
  void shouldTrackCustodyCountChangesForPeers() {
    final Duration responseLatency = Duration.ofDays(1); // complete responses manually
    final TestPeer peer =
        new TestPeer(
                stubAsyncRunner, custodyNodeIds.next(), responseLatency, Optional.of(UInt64.ZERO))
            .currentRequestLimit(1000);
    testPeerManager.connectPeer(peer);

    final List<DataColumnSlotAndIdentifier> colIds =
        IntStream.range(0, columnCount)
            .mapToObj(UInt64::valueOf)
            .map(colIdx -> new DataColumnSlotAndIdentifier(UInt64.ONE, Bytes32.ZERO, colIdx))
            .toList();

    colIds.forEach(simpleSidecarRetriever::retrieve);

    final int peerCustodyCount = custodyCountSupplier.getCustodyGroupCountForPeer(peer.getNodeId());

    advanceTimeGradually(retrieverRound);
    assertThat(peer.getRequests()).hasSize(peerCustodyCount);

    final int newPeerCustodyCount = peerCustodyCount + 1;
    custodyCountSupplier.setCustomCount(peer.getNodeId(), newPeerCustodyCount);

    advanceTimeGradually(retrieverRound);
    assertThat(peer.getRequests()).hasSize(newPeerCustodyCount);

    custodyCountSupplier.setCustomCount(peer.getNodeId(), columnCount);

    advanceTimeGradually(retrieverRound);
    assertThat(peer.getRequests()).hasSize(columnCount);
  }

  @ParameterizedTest
  @MethodSource("sidecarsRequestedReceivedToScore")
  public void connectedPeerGetResponseScore(
      final int sidecarsRequestedIncrement,
      final int sidecarsReceivedIncrement,
      final int expectedScore) {
    final SimpleSidecarRetriever.ConnectedPeer connectedPeer =
        new SimpleSidecarRetriever.ConnectedPeer(
            dataStructureUtil.randomUInt256(), Optional::empty, miscHelpers, spec, () -> 4);

    // Base score
    assertThat(connectedPeer.getResponseScore()).isEqualTo(10);

    // Apply and verify input
    connectedPeer.getSidecarsRequested().addAndGet(sidecarsRequestedIncrement);
    connectedPeer.getSidecarsReceived().addAndGet(sidecarsReceivedIncrement);
    assertThat(connectedPeer.getResponseScore()).isEqualTo(expectedScore);
  }

  // with 1000 requests limit
  private TestPeer createCustodyPeer() {
    return createCustodyPeer(1000);
  }

  private TestPeer createCustodyPeer(final int requestLimit) {
    return new TestPeer(stubAsyncRunner, custodyNodeIds.next(), Duration.ofMillis(100))
        .currentRequestLimit(requestLimit);
  }

  // with 1000 limit and configured earliest slot
  private TestPeer createCustodyPeer(final UInt64 earliestSlot) {
    return new TestPeer(
            stubAsyncRunner,
            custodyNodeIds.next(),
            Duration.ofMillis(100),
            Optional.of(earliestSlot))
        .currentRequestLimit(1000);
  }

  // custodies every column (like a supernode), with the given response latency
  private TestPeer createSupernodePeer(final Duration latency) {
    final UInt256 nodeId = dataStructureUtil.randomUInt256();
    custodyCountSupplier.setCustomCount(nodeId, columnCount);
    return new TestPeer(stubAsyncRunner, nodeId, latency).currentRequestLimit(1000);
  }

  // with 1000 requests limit
  private TestPeer createNonCustodyPeer() {
    return createNonCustodyPeer(1000);
  }

  private TestPeer createNonCustodyPeer(final int requestLimit) {
    return new TestPeer(stubAsyncRunner, nonCustodyNodeIds.next(), Duration.ofMillis(100))
        .currentRequestLimit(requestLimit);
  }

  /**
   * Connects peer manager to all peers
   *
   * @param peers test peers
   * @return requests stat for all peers
   */
  private Supplier<List<Integer>> connectPeers(final TestPeer... peers) {
    for (final TestPeer peer : peers) {
      testPeerManager.connectPeer(peer);
    }

    return () -> Arrays.stream(peers).map(peer -> peer.getRequests().size()).toList();
  }

  /**
   * Sets peer score within range with a very high requested/receiverd numbers, so it will not
   * change after several requests.
   *
   * @param peer test peer
   * @param score in 0 to 10 range
   */
  private void setRequestScore(final TestPeer peer, final int score) {
    setRequestScore(simpleSidecarRetriever, peer, score);
  }

  private void setRequestScore(
      final SimpleSidecarRetriever retriever, final TestPeer peer, final int score) {
    assertThat(score >= 0).isTrue();
    assertThat(score <= 10).isTrue();

    retriever.getConnectedPeers().get(peer.getNodeId()).getSidecarsRequested().set(10_000_000);
    retriever
        .getConnectedPeers()
        .get(peer.getNodeId())
        .getSidecarsReceived()
        .set(score * 1_000_000 + 10_000);
  }

  private DataColumnSidecar createSidecarAndAddToAllPeers(final int slot, final TestPeer... peers) {
    final SignedBeaconBlockHeader signedBeaconBlockHeader =
        dataStructureUtil.randomSignedBeaconBlockHeader(UInt64.valueOf(slot));
    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecar(signedBeaconBlockHeader, columnIndex);
    for (final TestPeer peer : peers) {
      peer.addSidecar(dataColumnSidecar);
    }

    return dataColumnSidecar;
  }

  private static Stream<Arguments> sidecarsRequestedReceivedToScore() {
    return Stream.of(
        Arguments.of(1, 0, 5),
        Arguments.of(2, 0, 3),
        Arguments.of(3, 0, 2),
        Arguments.of(4, 0, 2),
        Arguments.of(5, 0, 1),
        Arguments.of(1_000_000, 0, 0),
        Arguments.of(1, 1, 10),
        Arguments.of(2, 1, 6),
        Arguments.of(3, 2, 7),
        Arguments.of(4, 1, 4),
        Arguments.of(1_000_000, 1_000_000, 10));
  }
}
