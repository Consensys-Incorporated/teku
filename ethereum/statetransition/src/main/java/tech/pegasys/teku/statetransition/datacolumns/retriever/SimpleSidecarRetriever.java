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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.annotations.VisibleForTesting;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.units.bigints.UInt256;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.cache.Cache;
import tech.pegasys.teku.infrastructure.collections.cache.LRUCache;
import tech.pegasys.teku.infrastructure.exceptions.ExceptionUtil;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.config.SpecConfigFulu;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.util.DataColumnSlotAndIdentifier;
import tech.pegasys.teku.spec.logic.versions.fulu.helpers.MiscHelpersFulu;
import tech.pegasys.teku.statetransition.blobs.RemoteOrigin;

public class SimpleSidecarRetriever
    implements DataColumnSidecarRetriever, DataColumnPeerManager.PeerListener {
  private static final Logger LOG = LogManager.getLogger();

  /** Cadence at which {@link #nextRound()} runs to match pending requests to peers. */
  public static final Duration DEFAULT_ROUND_PERIOD = Duration.ofSeconds(1);

  /**
   * Default time a request must have been continuously in-flight before it becomes eligible for a
   * hedged second attempt. Chosen to be shorter than the RPC response-chunk timeout so a slow peer
   * is hedged well before its request would otherwise time out, but long enough that healthy peers
   * (which respond in well under a second) are never hedged.
   */
  public static final Duration DEFAULT_HEDGE_DELAY = Duration.ofSeconds(3);

  private final Spec spec;
  private final MiscHelpersFulu miscHelpersFulu;
  private final DasPeerCustodyCountSupplier custodyCountSupplier;
  private final DataColumnReqResp reqResp;
  private final AsyncRunner asyncRunner;
  private final TimeProvider timeProvider;
  private final Duration roundPeriod;
  private final int maxRequestCount;

  /**
   * Fraction of the currently pending requests that may have a redundant ("hedged") second
   * in-flight attempt at any time. Hedging lets a column stuck on a slow/unresponsive peer be
   * re-dispatched to an alternate custody peer without waiting for the original RPC to time out.
   * {@code 0} disables hedging entirely; any positive value always permits at least one hedged
   * request, however few are pending.
   */
  private final double overlapFraction;

  /**
   * How long a request must have been continuously in-flight before it becomes eligible for a
   * hedged second attempt. This targets genuinely slow peers rather than blindly duplicating every
   * request. Measured against wall-clock time (not round counts): {@link #nextRound()} can be
   * triggered off its fixed cadence by {@link #flush()}, so round counts are not a reliable measure
   * of elapsed time.
   */
  private final Duration hedgeDelay;

  private final Map<DataColumnSlotAndIdentifier, RetrieveRequest> pendingRequests =
      new ConcurrentHashMap<>();
  private final Map<UInt256, ConnectedPeer> connectedPeers = new ConcurrentHashMap<>();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final AtomicLong retrieveCounter = new AtomicLong();
  private final AtomicLong errorCounter = new AtomicLong();
  private final AtomicLong hedgeCounter = new AtomicLong();
  private final DataColumnPeerManager peerManager;

  public SimpleSidecarRetriever(
      final Spec spec,
      final DataColumnPeerManager peerManager,
      final DasPeerCustodyCountSupplier custodyCountSupplier,
      final DataColumnReqResp reqResp,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final double overlapFraction) {
    this(
        spec,
        peerManager,
        custodyCountSupplier,
        reqResp,
        asyncRunner,
        timeProvider,
        DEFAULT_ROUND_PERIOD,
        overlapFraction,
        DEFAULT_HEDGE_DELAY);
  }

  public SimpleSidecarRetriever(
      final Spec spec,
      final DataColumnPeerManager peerManager,
      final DasPeerCustodyCountSupplier custodyCountSupplier,
      final DataColumnReqResp reqResp,
      final AsyncRunner asyncRunner,
      final TimeProvider timeProvider,
      final Duration roundPeriod,
      final double overlapFraction,
      final Duration hedgeDelay) {
    checkArgument(
        overlapFraction >= 0.0 && overlapFraction <= 1.0,
        "overlapFraction must be within [0, 1] but was %s",
        overlapFraction);
    this.spec = spec;
    this.miscHelpersFulu =
        MiscHelpersFulu.required(spec.forMilestone(SpecMilestone.FULU).miscHelpers());
    this.custodyCountSupplier = custodyCountSupplier;
    this.asyncRunner = asyncRunner;
    this.timeProvider = timeProvider;
    this.roundPeriod = roundPeriod;
    this.reqResp = reqResp;
    this.peerManager = peerManager;
    this.peerManager.addPeerListener(this);
    this.maxRequestCount =
        SpecConfigFulu.required(spec.forMilestone(SpecMilestone.FULU).getConfig())
            .getMaxRequestDataColumnSidecars();
    this.overlapFraction = overlapFraction;
    this.hedgeDelay = hedgeDelay;
  }

  private void startIfNecessary() {
    if (started.compareAndSet(false, true)) {
      asyncRunner.runWithFixedDelay(
          this::nextRound, roundPeriod, err -> LOG.debug("Unexpected error", err));
    }
  }

  @Override
  public SafeFuture<DataColumnSidecar> retrieve(final DataColumnSlotAndIdentifier columnId) {
    final RetrieveRequest request =
        pendingRequests.computeIfAbsent(columnId, __ -> new RetrieveRequest(columnId));
    startIfNecessary();
    return request.result;
  }

  @Override
  public void flush() {
    asyncRunner.runAsync(this::nextRound).finishStackTrace();
  }

  @Override
  public void onNewValidatedSidecar(
      final DataColumnSidecar sidecar, final RemoteOrigin remoteOrigin) {
    final DataColumnSlotAndIdentifier dataColumnSlotAndIdentifier =
        DataColumnSlotAndIdentifier.fromDataColumn(sidecar);

    Optional.ofNullable(pendingRequests.get(dataColumnSlotAndIdentifier))
        .filter(request -> !request.result.isDone())
        .ifPresent(request -> reqRespSucceeded(request, sidecar));
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  /**
   * Produces the peer/request pairs to activate this round: first the primary matches (requests
   * with no in-flight attempt), then any hedged matches for slow in-flight requests, bounded by
   * {@link #overlapFraction}.
   */
  private List<RequestMatch> matchRequestsAndPeers() {
    final RequestTracker ongoingRequestsTracker = createFromCurrentPendingRequests();
    final List<RequestMatch> matches = new ArrayList<>();

    // Primary: requests that currently have no in-flight attempt.
    pendingRequests.entrySet().stream()
        .filter(entry -> entry.getValue().attemptingPeers.isEmpty())
        .sorted(Comparator.comparing(entry -> entry.getKey().slot()))
        .forEach(
            entry ->
                findBestMatchingPeer(entry.getValue(), ongoingRequestsTracker, Set.of())
                    .ifPresent(
                        peer -> {
                          ongoingRequestsTracker.decreaseAvailableRequests(peer.nodeId);
                          matches.add(new RequestMatch(peer, entry.getValue(), false));
                        }));

    addHedgeMatches(ongoingRequestsTracker, matches);
    return matches;
  }

  /**
   * Adds redundant attempts for requests that have been in-flight for too long, re-dispatching them
   * to an alternate custody peer. Bounded by the overlap budget so at most {@code overlapFraction}
   * of the pending requests carry a duplicate attempt at once - or one request, if that fraction
   * rounds down to zero (see the budget computation below).
   *
   * <p>A request typically carries at most two concurrent attempts; concurrent rounds may
   * transiently produce a third (see the best-effort note in {@link #nextRound()}: two overlapping
   * rounds can both observe the same request as hedge-eligible, or both as having no attempt at
   * all, and dispatch to different peers). Escalation past two is otherwise implicit, driven by the
   * RPC first-chunk timeout ({@code Eth2OutgoingRequestHandler.RESPONSE_CHUNK_ARRIVAL_TIMEOUT},
   * ~10s) freeing a slot to re-hedge a still-stuck request to a fresh peer. Example, in the
   * non-overlapping case (hedgeDelay=3s, RPC timeout=10s):
   *
   * <ul>
   *   <li>t=0 — primary attempt → peer A. attemptingPeers={A}.
   *   <li>t=3s (hedgeDelay) — size==1 → hedge → peer B. {A,B}.
   *   <li>t=6s — size==2, not eligible; both attempts still outstanding (nothing new).
   *   <li>t≈10s (RPC timeout) — A times out → removed, {B}; B has by now been in flight longer than
   *       hedgeDelay, so the request is hedge-eligible again → hedge to peer C. {B,C}.
   *   <li>t=13s — B times out → hedge to D. {C,D} … rotating a peer out ~every RPC timeout until
   *       the column arrives or all attempts drain (then primary re-dispatches).
   * </ul>
   *
   * <p>"Peer C" above is not necessarily a peer that has not been tried: a failed attempt releases
   * its peer immediately (unless the peer disconnected), so A remains selectable and can win
   * re-selection until enough failures have decayed its {@link ConnectedPeer#getResponseScore()}.
   * Rotation is driven by that score, not by any exclusion of previously-failed peers.
   */
  private void addHedgeMatches(
      final RequestTracker ongoingRequestsTracker, final List<RequestMatch> matches) {
    if (overlapFraction <= 0.0) {
      return;
    }
    // The hedge budget is a fraction of the number of pending column retrievals, not of the peer
    // request limits (maxRequestCount / getCurrentRequestLimit). Those limits still cap what any
    // single peer will accept and are enforced in findMatchingPeers; here we instead want the
    // redundancy to scale with the amount of outstanding work, matching how the rest of nextRound
    // reasons over the whole pending set. This keeps overlap proportional and self-scaling (5% of
    // in-flight work) rather than coupling it to per-peer rate limiting, which is a separate
    // concern.
    //
    // Floored at one whenever hedging is enabled at all: with the default fraction of 0.05,
    // floor(0.05 * N) is 0 for every N < 20, which would switch hedging off precisely in the case
    // it exists for - the tail of a block's columns, where one straggler stuck on a slow peer gates
    // data availability. One redundant request is negligible traffic, and the proportional cap
    // still governs once there is enough outstanding work for it to bind.
    final int maxOverlap = Math.max(1, (int) Math.floor(overlapFraction * pendingRequests.size()));
    // Within a single round a request is hedged at most once: it only becomes eligible below while
    // it has exactly one attempt (size() == 1), and a hedge takes it to two. Counting requests with
    // size() > 1 therefore counts each hedged request once against the budget.
    //
    // This accounting is best-effort and approximate: rounds are not synchronized and completion
    // callbacks run concurrently, so attemptingPeers can change while we compute the budget and
    // scan for eligible requests. A request may be counted with a stale size, picked while
    // transitioning 2 -> 1 attempts, or picked by two overlapping rounds at once - in which case it
    // briefly carries a third attempt while still costing the budget only one. The effect is
    // slightly over- or under-eager hedging that self-corrects next round; correctness never
    // depends on an exact count.
    final long currentOverlap =
        pendingRequests.values().stream().filter(r -> r.attemptingPeers.size() > 1).count();
    int budget = maxOverlap - (int) currentOverlap;
    if (budget <= 0) {
      return;
    }

    final UInt64 nowMillis = timeProvider.getTimeInMillis();
    // Best-effort selection: iterate lazily and stop as soon as the budget is spent, so we don't
    // walk the whole pending set when many requests are eligible. We intentionally don't sort by
    // slot here - sorting is a stateful barrier that would force a full walk every round, and it is
    // unnecessary: the primary loop above is slot-ordered, so lower-slot requests are dispatched
    // (and reach hedge-eligibility) first anyway.
    final Iterator<RetrieveRequest> eligible =
        pendingRequests.values().stream()
            .filter(request -> request.attemptingPeers.size() == 1)
            .filter(request -> request.inFlightForAtLeast(nowMillis, hedgeDelay))
            .iterator();

    while (budget > 0 && eligible.hasNext()) {
      final RetrieveRequest request = eligible.next();
      final Optional<ConnectedPeer> alternatePeer =
          findBestMatchingPeer(request, ongoingRequestsTracker, request.attemptingPeers);
      if (alternatePeer.isPresent()) {
        ongoingRequestsTracker.decreaseAvailableRequests(alternatePeer.get().nodeId);
        matches.add(new RequestMatch(alternatePeer.get(), request, true));
        // hedgeCounter is bumped by activateMatchedRequest once the attempt is actually dispatched
        // - a match here is only a proposal and may still be dropped. The budget is spent either
        // way: it is a per-round, best-effort allowance (see above), not a count of live hedges.
        budget--;
      }
    }
  }

  private boolean activateMatchedRequest(final RequestMatch match) {
    // The request may have completed after it was matched to a peer.
    if (isStaleRequest(match.request)) {
      return false;
    }
    if (!match.request.attemptingPeers.add(match.peer.nodeId)) {
      // already attempting this column via this peer - a concurrent round can have claimed this
      // peer between the match being proposed and now, in which case no RPC fires here
      return false;
    }
    if (match.hedge()) {
      // Counted here rather than where the match is proposed: only attempts that get past the
      // claim above actually put a redundant request on the wire.
      hedgeCounter.incrementAndGet();
    }
    // Timestamp for this attempt, used to decide hedging eligibility (see inFlightForAtLeast).
    final long dispatchedAtMillis = timeProvider.getTimeInMillis().longValue();

    final SafeFuture<DataColumnSidecar> reqRespPromise =
        reqResp.requestDataColumnSidecar(match.peer.nodeId, match.request.columnId);
    match.peer.countSidecarRequest();

    final SafeFuture<Void> activeRpcRequest =
        reqRespPromise.handle(
            (sidecar, err) -> {
              reqRespCompleted(match.request, match.peer, sidecar);
              if (err == null) {
                match.peer.countSidecarReceived();
              } else if (ExceptionUtil.hasCause(err, CancellationException.class)) {
                // the request was cancelled by us because the sidecar was no longer needed (for
                // example it arrived via gossip), so the peer must not be penalised for it
                match.peer.discardSidecarRequest();
                LOG.trace(
                    "SimpleSidecarRetriever.Request cancelled for {}",
                    () -> match.request.columnId);
              } else {
                LOG.debug(
                    "SimpleSidecarRetriever.Request failed for {} due to: {}",
                    () -> match.request.columnId,
                    () -> ExceptionUtil.getMessageOrSimpleName(err));
              }

              return null;
            });

    // here we make sure that if something goes wrong in the handle call we
    // log all the info to fix the bug
    activeRpcRequest.ignoreCancelException().finishStackTrace();

    final ActiveRequest activeRequest =
        new ActiveRequest(activeRpcRequest, reqRespPromise, match.peer, dispatchedAtMillis);
    match.request.activeRequests.put(match.peer.nodeId, activeRequest);

    // This attempt can already be finished by the time we get here, so the registration above may
    // be resurrecting an entry a completion path has just cleaned up. Two ways in:
    //  - reqResp buffers the request until flush(), but the buffer is shared and rounds are not
    //    synchronized, so a concurrent round's flush() can dispatch and complete (typically fail)
    //    this promise before this assignment;
    //  - the gossip completion path (reqRespSucceeded -> cancelActiveRequests) is not gated on
    //    flush at all.
    // Both completion paths do their cleanup before this point, so re-check them and undo the
    // registration if this attempt is already dead. Leaving a completed ActiveRequest behind would
    // keep consuming this peer's request budget in createFromCurrentPendingRequests for as long as
    // the column stays pending, with no later timeout to clear it - and with a small per-peer
    // limit that can lock the peer out of serving the request that would have replaced the entry.
    if (isStaleRequest(match.request)
        // reqRespCompleted removes from attemptingPeers *before* activeRequests, so observing the
        // peer gone here means our put came after (or raced with) that cleanup.
        || !match.request.attemptingPeers.contains(match.peer.nodeId)) {
      // Remove by identity, so a re-dispatch that has already installed a newer attempt for this
      // peer is left untouched.
      match.request.activeRequests.remove(match.peer.nodeId, activeRequest);
      // If the RPC was still buffered, flush() will drop it and it never reaches the network;
      // cancel() also gives the peer back the request we charged it above.
      activeRequest.cancel();
      return false;
    }
    return true;
  }

  @SuppressWarnings({"ReferenceEquality", "ReferenceComparison"})
  private boolean isStaleRequest(final RetrieveRequest request) {
    return pendingRequests.get(request.columnId) != request;
  }

  private Optional<ConnectedPeer> findBestMatchingPeer(
      final RetrieveRequest request,
      final RequestTracker ongoingRequestsTracker,
      final Set<UInt256> excludedPeers) {
    final Stream<ConnectedPeer> matchingPeers =
        findMatchingPeers(request, ongoingRequestsTracker)
            .filter(peer -> !excludedPeers.contains(peer.nodeId));

    // Preferring peers with the best response rate, then preferring less busy peers among equals
    final Comparator<ConnectedPeer> comparator =
        Comparator.comparing(ConnectedPeer::getResponseScore)
            .thenComparing(
                (ConnectedPeer peer) ->
                    ongoingRequestsTracker.getAvailableRequestCount(peer.nodeId));
    return matchingPeers.max(comparator);
  }

  private Stream<ConnectedPeer> findMatchingPeers(
      final RetrieveRequest request, final RequestTracker ongoingRequestsTracker) {
    return connectedPeers.values().stream()
        .filter(peer -> peer.isCustodyFor(request.columnId))
        .filter(peer -> peer.hasSlotAvailable(request.columnId.slot()))
        .filter(peer -> ongoingRequestsTracker.hasAvailableRequests(peer.nodeId));
  }

  private void disposeCompletedRequests() {
    pendingRequests
        .entrySet()
        .removeIf(
            pendingEntry -> {
              final RetrieveRequest pendingRequest = pendingEntry.getValue();
              if (pendingRequest.result.isDone()) {
                pendingRequest.cancelActiveRequests();
                return true;
              }
              return false;
            });
  }

  private void nextRound() {
    // Intentionally unsynchronized. nextRound runs on the fixed-delay schedule and from flush() on
    // a multi-threaded runner, so two rounds may briefly overlap. The activation guard
    // (attemptingPeers.add) is atomic, so the same (request, peer) is never dispatched twice; the
    // only effect of overlap is that a request might get a second primary attempt or the overlap
    // budget might be spent slightly over target - extra redundant traffic that self-corrects,
    // never a lost or stalled request. We accept that rather than serialize rounds. (See also the
    // best-effort notes on the overlap budget and inFlightForAtLeast.)
    disposeCompletedRequests();

    final long activatedMatches =
        matchRequestsAndPeers().stream()
            .map(this::activateMatchedRequest)
            .filter(activated -> activated)
            .count();

    if (LOG.isTraceEnabled()) {
      final long activeRequestCount =
          pendingRequests.values().stream().filter(r -> !r.attemptingPeers.isEmpty()).count();
      LOG.trace(
          "SimpleSidecarRetriever.nextRound: completed: {}, errored: {}, hedged: {}, total pending: {}, active pending: {}, new active: {}, number of custody peers: {}",
          retrieveCounter,
          errorCounter,
          hedgeCounter,
          pendingRequests.size(),
          activeRequestCount,
          activatedMatches,
          gatherAvailableCustodiesInfo());
    }

    reqResp.flush();
  }

  private void reqRespSucceeded(final RetrieveRequest request, final DataColumnSidecar sidecar) {
    // Removed by value: this request may already be gone from the map and the column re-requested
    // since (a concurrent attempt can still deliver a sidecar between the removal here and
    // cancelActiveRequests below). Removing by key alone would evict that newer RetrieveRequest
    // while completing this one, leaving its caller waiting for a retrieval no round will ever
    // dispatch again.
    if (pendingRequests.remove(request.columnId, request)) {
      request.result.completeAsync(sidecar, asyncRunner);
      retrieveCounter.incrementAndGet();
      // cancel any redundant/hedged in-flight attempts for this column
      request.cancelActiveRequests();
    }
  }

  private void reqRespCompleted(
      final RetrieveRequest request,
      final ConnectedPeer peer,
      final DataColumnSidecar maybeResult) {
    if (maybeResult != null) {
      // Same completion handling as the gossip path. If another (hedged) attempt already won,
      // reqRespSucceeded is a no-op and that winner's cancelActiveRequests() releases this peer's
      // attempt, so no per-peer cleanup is needed here.
      reqRespSucceeded(request, maybeResult);
      return;
    }
    // This attempt failed. Release just this peer's attempt so the request can be re-dispatched
    // next round if still pending. Removing the activeRequests entry also drops this attempt's
    // dispatch timestamp, so the in-flight clock (see inFlightForAtLeast) tracks only live attempts
    // with no field to reset.
    //
    // Order matters: attemptingPeers is cleared first so that an activateMatchedRequest racing this
    // callback (completion can happen before it registers the attempt) reliably sees the peer gone
    // and drops the entry it just installed.
    request.attemptingPeers.remove(peer.nodeId);
    request.activeRequests.remove(peer.nodeId);
    errorCounter.incrementAndGet();
  }

  private String gatherAvailableCustodiesInfo() {
    final SpecVersion specVersion = spec.forMilestone(SpecMilestone.FULU);
    final Map<UInt64, Long> colIndexToCount =
        connectedPeers.values().stream()
            .flatMap(p -> p.getNodeCustodyIndices(specVersion).stream())
            .collect(Collectors.groupingBy(i -> i, Collectors.counting()));
    final int numberOfColumns =
        SpecConfigFulu.required(specVersion.getConfig()).getNumberOfColumns();
    IntStream.range(0, numberOfColumns)
        .mapToObj(UInt64::valueOf)
        .forEach(idx -> colIndexToCount.putIfAbsent(idx, 0L));
    colIndexToCount.replaceAll((colIdx, count) -> Long.min(3, count));
    final Map<Long, Long> custodyCountToPeerCount =
        colIndexToCount.entrySet().stream()
            .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.counting()));
    return new TreeMap<>(custodyCountToPeerCount)
        .entrySet().stream()
            .map(
                entry -> {
                  String peerCnt = entry.getKey() == 3 ? "3+" : "" + entry.getKey();
                  return entry.getValue() + " cols: " + peerCnt + " peers";
                })
            .collect(Collectors.joining(","));
  }

  @Override
  public void peerConnected(
      final UInt256 nodeId, final Supplier<Optional<UInt64>> maybeEarliestAvailableSlot) {
    LOG.trace(
        "SimpleSidecarRetriever.peerConnected: 0x...{}", () -> nodeId.toHexString().substring(58));
    connectedPeers.computeIfAbsent(
        nodeId,
        __ ->
            new ConnectedPeer(
                nodeId,
                maybeEarliestAvailableSlot,
                miscHelpersFulu,
                spec,
                () -> custodyCountSupplier.getCustodyGroupCountForPeer(nodeId)));
  }

  @Override
  public void peerDisconnected(final UInt256 nodeId) {
    LOG.trace(
        "SimpleSidecarRetriever.peerDisconnected: 0x...{}",
        () -> nodeId.toHexString().substring(58));
    connectedPeers.remove(nodeId);
  }

  @VisibleForTesting
  Map<UInt256, ConnectedPeer> getConnectedPeers() {
    return connectedPeers;
  }

  @VisibleForTesting
  long getHedgeCount() {
    return hedgeCounter.get();
  }

  @VisibleForTesting
  int getPendingRequestCount() {
    return pendingRequests.size();
  }

  /**
   * A dispatched attempt. {@code promise} is the completion-handling stage; {@code rpcPromise} is
   * the underlying {@link DataColumnReqResp} promise, kept so cancellation can reach the request
   * while it is still buffered.
   */
  private record ActiveRequest(
      SafeFuture<Void> promise,
      SafeFuture<DataColumnSidecar> rpcPromise,
      ConnectedPeer peer,
      long dispatchedAtMillis) {

    /**
     * Abandons this attempt. If the RPC had not completed yet it was still sitting in the reqResp
     * buffer and {@link DataColumnReqResp#flush()} will now drop it instead of putting it on the
     * wire.
     *
     * <p>Cancelling {@code promise} alone is not enough: it is a dependent stage, so cancelling it
     * leaves {@code rpcPromise} pending and still queued for the next flush, and the request goes
     * out regardless - with its response discarded, since the cancelled stage never runs. The
     * handling stage is cancelled first so that cancelling the source below does not fire the
     * completion callback and book this as a failed attempt.
     */
    void cancel() {
      promise().cancel(true);
      if (rpcPromise().cancel(true)) {
        // Give the peer back the request charged at dispatch: we withdrew it, so scoring the peer
        // down for a response we stopped waiting for would push a healthy peer down the selection
        // order. Done here because cancelling the handling stage above means the completion
        // callback - which compensates when the reqResp layer cancels on its own - never runs.
        peer().discardSidecarRequest();
      }
    }
  }

  private static class RetrieveRequest {
    final DataColumnSlotAndIdentifier columnId;
    final SafeFuture<DataColumnSidecar> result = new SafeFuture<>();
    // Peers with a reserved or in-flight attempt for this column. Also acts as the activation
    // guard.
    final Set<UInt256> attemptingPeers = ConcurrentHashMap.newKeySet();
    final Map<UInt256, ActiveRequest> activeRequests = new ConcurrentHashMap<>();

    private RetrieveRequest(final DataColumnSlotAndIdentifier columnId) {
      this.columnId = columnId;
    }

    /**
     * True if some in-flight attempt was dispatched at least {@code delay} ago. The "in-flight
     * clock" is derived from the immutable per-attempt {@link ActiveRequest#dispatchedAtMillis}
     * timestamps rather than a shared mutable field, so there is nothing to reset and nothing to
     * synchronize: when attempts drain the entries simply disappear, and a re-dispatch carries a
     * fresh timestamp. Reading it concurrently with a drain/dispatch can only make a request look
     * eligible a cycle early or late - never stalls it.
     */
    boolean inFlightForAtLeast(final UInt64 nowMillis, final Duration delay) {
      final long cutoffMillis = nowMillis.longValue() - delay.toMillis();
      return activeRequests.values().stream()
          .anyMatch(activeRequest -> activeRequest.dispatchedAtMillis() <= cutoffMillis);
    }

    void cancelActiveRequests() {
      // Only ever called on a request that the caller has already removed (or is removing) from
      // pendingRequests, so this RetrieveRequest - together with attemptingPeers/activeRequests -
      // is discarded right after. We therefore only cancel the in-flight futures and deliberately
      // do NOT clear the two collections: clearing them in sequence is not atomic and could briefly
      // expose an inconsistent (attemptingPeers, activeRequests) view to a concurrent round.
      //
      // ActiveRequest.cancel also cancels the underlying reqResp promise, so an attempt dispatched
      // this round but not yet flushed is dropped from the buffer rather than sent for a column we
      // already have.
      activeRequests.values().forEach(ActiveRequest::cancel);
    }
  }

  static class ConnectedPeer {
    private final UInt256 nodeId;
    private final Supplier<Optional<UInt64>> maybeEarliestAvailableSlot;
    private final Cache<CacheKey, Set<UInt64>> custodyIndicesCache = LRUCache.create(2);
    private final MiscHelpersFulu miscHelpersFulu;
    private final Spec spec;
    private final Supplier<Integer> custodyCountSupplier;
    // just to avoid starting with non-divisible 0/0
    private final AtomicInteger sidecarsRequested = new AtomicInteger(1);
    private final AtomicInteger sidecarsReceived = new AtomicInteger(1);

    private record CacheKey(SpecVersion specVersion, int custodyCount) {}

    public ConnectedPeer(
        final UInt256 nodeId,
        final Supplier<Optional<UInt64>> maybeEarliestAvailableSlot,
        final MiscHelpersFulu miscHelpersFulu,
        final Spec spec,
        final Supplier<Integer> custodyCountSupplier) {
      this.nodeId = nodeId;
      this.maybeEarliestAvailableSlot = maybeEarliestAvailableSlot;
      this.miscHelpersFulu = miscHelpersFulu;
      this.spec = spec;
      this.custodyCountSupplier = custodyCountSupplier;
    }

    private Set<UInt64> calcNodeCustodyIndices(final CacheKey cacheKey) {
      return miscHelpersFulu.computeCustodyColumnIndices(nodeId, cacheKey.custodyCount());
    }

    private Set<UInt64> getNodeCustodyIndices(final SpecVersion specVersion) {
      return custodyIndicesCache.get(
          new CacheKey(specVersion, custodyCountSupplier.get()), this::calcNodeCustodyIndices);
    }

    public boolean isCustodyFor(final DataColumnSlotAndIdentifier columnId) {
      return getNodeCustodyIndices(spec.atSlot(columnId.slot())).contains(columnId.columnIndex());
    }

    public boolean hasSlotAvailable(final UInt64 slot) {
      // if we don't have information, we consider it optimistically
      return maybeEarliestAvailableSlot.get().map(slot::isGreaterThanOrEqualTo).orElse(true);
    }

    /**
     * Score is 0 to 10, where 10 is peer which always response well on all queries. Score is
     * bucketed to 11 values, so another comparator could use different criteria to score several
     * peers from one bucket.
     */
    public int getResponseScore() {
      return (int) (sidecarsReceived.get() * 10.0F / sidecarsRequested.get());
    }

    public void countSidecarRequest() {
      final int current = sidecarsRequested.incrementAndGet();
      if (current == Integer.MAX_VALUE) {
        resetCounters();
      }
    }

    /**
     * Reverts a {@link #countSidecarRequest()} for a request which was cancelled on our side, so
     * that the peer's response score is not affected by a response we stopped waiting for.
     */
    public void discardSidecarRequest() {
      // counters could have been reset in between, so never go below the initial value
      sidecarsRequested.updateAndGet(current -> Math.max(1, current - 1));
    }

    private void resetCounters() {
      sidecarsRequested.set(1);
      sidecarsReceived.set(1);
    }

    public void countSidecarReceived() {
      final int current = sidecarsReceived.incrementAndGet();
      if (current == Integer.MAX_VALUE) {
        resetCounters();
      }
    }

    @VisibleForTesting
    AtomicInteger getSidecarsRequested() {
      return sidecarsRequested;
    }

    @VisibleForTesting
    AtomicInteger getSidecarsReceived() {
      return sidecarsReceived;
    }
  }

  /**
   * A proposed (peer, request) pairing for this round. {@code hedge} distinguishes a redundant
   * second attempt from a primary one; a match is only a proposal, and activation may still drop
   * it.
   */
  private record RequestMatch(ConnectedPeer peer, RetrieveRequest request, boolean hedge) {}

  private RequestTracker createFromCurrentPendingRequests() {
    final Map<UInt256, Integer> pendingRequestsCount = new HashMap<>();
    pendingRequests
        .values()
        .forEach(
            request ->
                request
                    .activeRequests
                    .values()
                    .forEach(
                        activeRequest ->
                            pendingRequestsCount.merge(
                                activeRequest.peer().nodeId, 1, Integer::sum)));
    return new RequestTracker(pendingRequestsCount);
  }

  private class RequestTracker {
    private final Map<UInt256, Integer> pendingRequestsCount;

    private RequestTracker(final Map<UInt256, Integer> pendingRequestsCount) {
      this.pendingRequestsCount = pendingRequestsCount;
    }

    int getAvailableRequestCount(final UInt256 nodeId) {
      return Integer.min(maxRequestCount, reqResp.getCurrentRequestLimit(nodeId))
          - pendingRequestsCount.getOrDefault(nodeId, 0);
    }

    boolean hasAvailableRequests(final UInt256 nodeId) {
      return getAvailableRequestCount(nodeId) > 0;
    }

    void decreaseAvailableRequests(final UInt256 nodeId) {
      pendingRequestsCount.compute(nodeId, (__, cnt) -> cnt == null ? 1 : cnt + 1);
    }
  }
}
