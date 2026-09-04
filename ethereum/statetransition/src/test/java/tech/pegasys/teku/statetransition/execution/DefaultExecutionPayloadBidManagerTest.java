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

package tech.pegasys.teku.statetransition.execution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.BidForBlock;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBid;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBidOrigin;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class DefaultExecutionPayloadBidManagerTest {

  private final Spec spec = TestSpecFactory.createMainnetGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private final BlockProductionPerformance blockProductionPerformance =
      mock(BlockProductionPerformance.class);

  private final ExecutionPayloadBidGossipValidator executionPayloadBidGossipValidator =
      mock(ExecutionPayloadBidGossipValidator.class);
  private final ExecutionPayloadBidCircuitBreaker executionPayloadBidCircuitBreaker =
      mock(ExecutionPayloadBidCircuitBreaker.class);
  private final BuilderBidFetcher builderBidFetcher = mock(BuilderBidFetcher.class);
  private final ExecutionPayloadBidSelector bidSelector = mock(ExecutionPayloadBidSelector.class);

  private final ReceivedExecutionPayloadBidEventsChannel
      receivedExecutionPayloadBidEventsChannelPublisher =
          mock(ReceivedExecutionPayloadBidEventsChannel.class);
  private final PendingPool<SignedExecutionPayloadBid> pendingExecutionPayloadBids =
      new PoolFactory(new StubMetricsSystem()).createPendingPoolForExecutionPayloadBids(spec);

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<SignedExecutionPayloadBid> operationAddedSubscriber =
      mock(OperationAddedSubscriber.class);

  private final DefaultExecutionPayloadBidManager executionPayloadBidManager =
      new DefaultExecutionPayloadBidManager(
          spec,
          executionPayloadBidGossipValidator,
          executionPayloadBidCircuitBreaker,
          receivedExecutionPayloadBidEventsChannelPublisher,
          pendingExecutionPayloadBids,
          builderBidFetcher,
          bidSelector);

  @BeforeEach
  public void setup() {
    when(executionPayloadBidCircuitBreaker.isEngaged(any(), any())).thenReturn(false);
    when(builderBidFetcher.getBuilderBids(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    executionPayloadBidManager.subscribeOperationAdded(operationAddedSubscriber);
  }

  @Test
  public void fallsBackToLocalSelfBuiltBidWhenCircuitBreakerIsEngaged() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconStateGloas state = stateAtSlot(slot);
    final SignedExecutionPayloadBid localBid =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    final BidForBlock localBidForBlock = new BidForBlock(localBid, UInt256.ONE, Optional.empty());

    when(executionPayloadBidCircuitBreaker.isEngaged(parentRoot, state)).thenReturn(true);
    when(bidSelector.selectBestBidForBlock(
            argThat(Optional::isPresent), eq(Optional.empty()), any(), eq(slot)))
        .thenReturn(localBidForBlock);

    final BidForBlock result =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                state,
                SafeFuture.completedFuture(randomGetPayloadResponse(slot, parentBlockHash)),
                BuilderConfig.NO_OP,
                blockProductionPerformance));

    assertThat(result).isEqualTo(localBidForBlock);
    verify(bidSelector, never()).selectBestRemoteBid(any(), any(), any(), any(), any(), any());
  }

  @Test
  public void fallsBackToLocalSelfBuiltBidWhenNoRemoteBidIsReturned() {
    final BeaconStateGloas state = BeaconStateGloas.required(dataStructureUtil.randomBeaconState());
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();

    final SignedExecutionPayloadBid localBid =
        createBid(state.getSlot(), parentRoot, parentBlockHash, UInt64.valueOf(100));
    final BidForBlock localBidForBlock = new BidForBlock(localBid, UInt256.ONE, Optional.empty());
    final SignedExecutionPayloadBid remoteBid =
        createBid(state.getSlot(), parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBid);

    when(bidSelector.selectBestRemoteBid(
            eq(Set.of(toRemoteBid(remoteBid))),
            eq(Collections.emptyList()),
            eq(parentRoot),
            eq(parentBlockHash),
            any(),
            any()))
        .thenReturn(Optional.empty());
    when(bidSelector.selectBestBidForBlock(
            argThat(Optional::isPresent), eq(Optional.empty()), any(), eq(state.getSlot())))
        .thenReturn(localBidForBlock);

    final BidForBlock result =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                state,
                SafeFuture.completedFuture(
                    randomGetPayloadResponse(state.getSlot(), parentBlockHash)),
                BuilderConfig.NO_OP,
                blockProductionPerformance));

    assertThat(result).isEqualTo(localBidForBlock);
  }

  @Test
  public void retrievedBuilderBidsArePassedToBidSelector() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final BeaconStateGloas state = stateAtSlot(slot);
    final SignedExecutionPayloadBid builderBidRaw =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(200));
    final RemoteBid builderBid = toRemoteBid(builderBidRaw);
    final List<RemoteBid> builderBids = List.of(builderBid);
    final BidForBlock builderBidForBlock =
        new BidForBlock(builderBidRaw, UInt256.ONE, Optional.empty());

    when(builderBidFetcher.getBuilderBids(any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(builderBids));
    when(bidSelector.selectBestRemoteBid(any(), eq(builderBids), any(), any(), any(), any()))
        .thenReturn(Optional.of(builderBid));
    when(bidSelector.selectBestBidForBlock(any(), eq(Optional.of(builderBid)), any(), eq(slot)))
        .thenReturn(builderBidForBlock);

    final BidForBlock selectedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                state,
                SafeFuture.completedFuture(randomGetPayloadResponse(slot, parentBlockHash)),
                BuilderConfig.NO_OP,
                blockProductionPerformance));

    assertThat(selectedBid).isEqualTo(builderBidForBlock);
    verify(bidSelector).selectBestRemoteBid(any(), eq(builderBids), any(), any(), any(), any());
  }

  @Test
  public void selectsRemoteBidWhenLocalPayloadHasWrongParentHash() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBidRaw =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBidRaw);
    final RemoteBid remoteBid = toRemoteBid(remoteBidRaw);
    final BidForBlock remoteBidForBlock =
        new BidForBlock(remoteBidRaw, UInt256.ONE, Optional.empty());

    when(bidSelector.selectBestRemoteBid(
            eq(Set.of(remoteBid)),
            eq(Collections.emptyList()),
            eq(parentRoot),
            eq(parentBlockHash),
            any(),
            any()))
        .thenReturn(Optional.of(remoteBid));
    when(bidSelector.selectBestBidForBlock(
            eq(Optional.empty()), eq(Optional.of(remoteBid)), any(), eq(slot)))
        .thenReturn(remoteBidForBlock);

    final BidForBlock selectedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(slot),
                SafeFuture.completedFuture(
                    randomGetPayloadResponse(slot, dataStructureUtil.randomBytes32())),
                BuilderConfig.NO_OP,
                blockProductionPerformance));

    assertThat(selectedBid).isEqualTo(remoteBidForBlock);
  }

  @Test
  void selectsRemoteBidWhenLocalPayloadFutureFails() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBidRaw =
        createBid(slot, parentRoot, parentBlockHash, UInt64.valueOf(100));
    addAcceptedBid(remoteBidRaw);
    final RemoteBid remoteBid = toRemoteBid(remoteBidRaw);
    final BidForBlock remoteBidForBlock =
        new BidForBlock(remoteBidRaw, UInt256.ONE, Optional.empty());

    when(bidSelector.selectBestRemoteBid(
            eq(Set.of(remoteBid)),
            eq(Collections.emptyList()),
            eq(parentRoot),
            eq(parentBlockHash),
            any(),
            any()))
        .thenReturn(Optional.of(remoteBid));
    when(bidSelector.selectBestBidForBlock(
            eq(Optional.empty()), eq(Optional.of(remoteBid)), any(), eq(slot)))
        .thenReturn(remoteBidForBlock);

    final BidForBlock selectedBid =
        SafeFutureAssert.safeJoin(
            executionPayloadBidManager.getBidForBlock(
                parentRoot,
                parentBlockHash,
                stateAtSlot(slot),
                SafeFuture.failedFuture(new RuntimeException("engine unavailable")),
                BuilderConfig.NO_OP,
                blockProductionPerformance));

    assertThat(selectedBid).isEqualTo(remoteBidForBlock);
  }

  @Test
  public void doesNotStoreBidWhenValidationDoesNotAccept() {
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), dataStructureUtil.randomBytes32(), UInt64.valueOf(100));

    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("nope")));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));

    verify(receivedExecutionPayloadBidEventsChannelPublisher, never())
        .onExecutionPayloadBidValidated(signedBid);
    verifyNoInteractions(operationAddedSubscriber);
  }

  @Test
  public void acceptedBuilderBidNotifiesSubscriberAsLocal() {
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.BUILDER));

    verify(operationAddedSubscriber).onOperationAdded(signedBid, ACCEPT, false);
  }

  @Test
  public void acceptedP2pBidNotifiesSubscriberAsFromNetwork() {
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));

    verify(operationAddedSubscriber).onOperationAdded(signedBid, ACCEPT, true);
  }

  @Test
  public void validationFutureCompletesAfterAcceptanceIsProcessed() throws InterruptedException {
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    final SafeFuture<InternalValidationResult> validationFuture = new SafeFuture<>();
    final CountDownLatch processingStarted = new CountDownLatch(1);
    final CountDownLatch continueProcessing = new CountDownLatch(1);
    executionPayloadBidManager.subscribeOperationAdded(
        (operation, validationStatus, fromNetwork) -> {
          processingStarted.countDown();
          try {
            continueProcessing.await();
          } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
          }
        });
    when(executionPayloadBidGossipValidator.validate(signedBid)).thenReturn(validationFuture);

    final SafeFuture<InternalValidationResult> result =
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.BUILDER);
    final Thread validationThread = new Thread(() -> validationFuture.complete(ACCEPT));
    validationThread.start();

    try {
      assertThat(processingStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(result).isNotDone();
    } finally {
      continueProcessing.countDown();
      validationThread.join();
    }
    assertThat(result).isCompletedWithValue(ACCEPT);
  }

  @Test
  public void onSlotRetriesBidSavedForFuture() {
    final UInt64 slot = UInt64.valueOf(10);
    final SignedExecutionPayloadBid signedBid =
        createBid(slot, dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(slot);
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.BUILDER));
    assertThat(pendingExecutionPayloadBids.get(signedBid.hashTreeRoot())).contains(signedBid);
    verify(receivedExecutionPayloadBidEventsChannelPublisher, never())
        .onExecutionPayloadBidValidated(signedBid);

    executionPayloadBidManager.onSlot(slot);

    verify(executionPayloadBidGossipValidator, times(2)).validate(signedBid);
    verify(receivedExecutionPayloadBidEventsChannelPublisher)
        .onExecutionPayloadBidValidated(signedBid);
    verify(operationAddedSubscriber).onOperationAdded(signedBid, ACCEPT, false);
  }

  @Test
  public void onSlotKeepsBidPendingWhenRetryIsStillForFuture() {
    final UInt64 slot = UInt64.valueOf(10);
    final SignedExecutionPayloadBid signedBid =
        createBid(slot, dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(slot);
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
    executionPayloadBidManager.onSlot(slot);
    executionPayloadBidManager.onSlot(slot);

    verify(executionPayloadBidGossipValidator, times(3)).validate(signedBid);
    verify(receivedExecutionPayloadBidEventsChannelPublisher)
        .onExecutionPayloadBidValidated(signedBid);
    verify(operationAddedSubscriber).onOperationAdded(signedBid, ACCEPT, false);
    verify(operationAddedSubscriber, never()).onOperationAdded(signedBid, ACCEPT, true);
  }

  @Test
  public void builderSubmittedPendingP2pBidIsPublishedWhenAccepted() {
    final UInt64 slot = UInt64.valueOf(10);
    final SignedExecutionPayloadBid signedBid =
        createBid(slot, dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(slot);
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.BUILDER));
    executionPayloadBidManager.onSlot(slot);

    verify(operationAddedSubscriber).onOperationAdded(signedBid, ACCEPT, false);
    verify(operationAddedSubscriber, never()).onOperationAdded(signedBid, ACCEPT, true);
  }

  @Test
  public void onSlotDropsPendingBidsForPriorSlots() {
    final UInt64 slot = UInt64.valueOf(10);
    final SignedExecutionPayloadBid signedBid =
        createBid(slot, dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(slot);
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
    executionPayloadBidManager.onSlot(slot.plus(1));

    verify(executionPayloadBidGossipValidator).validate(signedBid);
    verify(receivedExecutionPayloadBidEventsChannelPublisher, never())
        .onExecutionPayloadBidValidated(signedBid);
  }

  @Test
  public void ignoredRetryIsNotRetained() {
    final UInt64 slot = UInt64.valueOf(10);
    final SignedExecutionPayloadBid signedBid =
        createBid(slot, dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(slot);
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.IGNORE));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
    executionPayloadBidManager.onSlot(slot);
    executionPayloadBidManager.onSlot(slot);

    verify(executionPayloadBidGossipValidator, times(2)).validate(signedBid);
  }

  @Test
  public void rejectedRetryIsNotRetained() {
    final UInt64 slot = UInt64.valueOf(10);
    final SignedExecutionPayloadBid signedBid =
        createBid(slot, dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(slot);
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.reject("invalid")));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.BUILDER));
    executionPayloadBidManager.onSlot(slot);
    executionPayloadBidManager.onSlot(slot);

    verify(executionPayloadBidGossipValidator, times(2)).validate(signedBid);
  }

  @Test
  public void proposerPreferencesRetryBidsForMatchingSlot() {
    final SignedProposerPreferences preferences =
        dataStructureUtil.randomSignedProposerPreferences();
    final UInt64 slot = preferences.getMessage().getProposalSlot();
    final SignedExecutionPayloadBid signedBid =
        createBid(slot, dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(slot);
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.BUILDER));
    executionPayloadBidManager.onOperationAdded(preferences, ACCEPT, true);

    verify(executionPayloadBidGossipValidator, times(2)).validate(signedBid);
    verify(receivedExecutionPayloadBidEventsChannelPublisher)
        .onExecutionPayloadBidValidated(signedBid);
  }

  @Test
  public void importedParentBlockRetriesMatchingBid() {
    final SignedBeaconBlock parentBlock = dataStructureUtil.randomSignedBeaconBlock(9);
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), parentBlock.getRoot(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(signedBid.getMessage().getSlot());
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
    executionPayloadBidManager.onBlockImported(parentBlock, false);

    verify(executionPayloadBidGossipValidator, times(2)).validate(signedBid);
    verify(executionPayloadBidCircuitBreaker).observeImportedBlock(parentBlock);
    verify(receivedExecutionPayloadBidEventsChannelPublisher)
        .onExecutionPayloadBidValidated(signedBid);
  }

  @Test
  public void importedParentExecutionPayloadRetriesMatchingBid() {
    final SignedBeaconBlock parentBlock = dataStructureUtil.randomSignedBeaconBlock(9);
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(parentBlock);
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), parentBlock.getRoot(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(signedBid.getMessage().getSlot());
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
    executionPayloadBidManager.onExecutionPayloadImported(executionPayload, false);

    verify(executionPayloadBidGossipValidator, times(2)).validate(signedBid);
    verify(receivedExecutionPayloadBidEventsChannelPublisher)
        .onExecutionPayloadBidValidated(signedBid);
  }

  @Test
  public void overlappingDependencyEventsDoNotRetryOnePendingBidTwice() {
    final SignedBeaconBlock parentBlock = dataStructureUtil.randomSignedBeaconBlock(9);
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelopeForBlock(parentBlock);
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), parentBlock.getRoot(), UInt64.valueOf(100));
    executionPayloadBidManager.onSlot(signedBid.getMessage().getSlot());
    final SafeFuture<InternalValidationResult> retryResult = new SafeFuture<>();
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(SAVE_FOR_FUTURE))
        .thenReturn(retryResult);

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
    executionPayloadBidManager.onBlockImported(parentBlock, false);
    executionPayloadBidManager.onExecutionPayloadImported(executionPayload, false);

    verify(executionPayloadBidGossipValidator, times(2)).validate(signedBid);
    retryResult.complete(ACCEPT);
    verify(receivedExecutionPayloadBidEventsChannelPublisher)
        .onExecutionPayloadBidValidated(signedBid);
  }

  @Test
  public void onSlotPrunesP2PBidsForPriorSlots() {
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentBlockHash = dataStructureUtil.randomBytes32();
    final UInt64 currentSlot = UInt64.valueOf(10);

    final SignedExecutionPayloadBid staleBid =
        createBid(currentSlot.minus(1), parentRoot, parentBlockHash, UInt64.valueOf(500));
    final SignedExecutionPayloadBid currentSlotBid =
        createBid(currentSlot, parentRoot, parentBlockHash, UInt64.valueOf(200));
    final SignedExecutionPayloadBid nextSlotBid =
        createBid(currentSlot.plus(1), parentRoot, parentBlockHash, UInt64.valueOf(300));

    addAcceptedBid(staleBid);
    addAcceptedBid(currentSlotBid);
    addAcceptedBid(nextSlotBid);

    assertThat(executionPayloadBidManager.getP2PBidsForSlot(staleBid.getMessage().getSlot()))
        .containsExactly(toRemoteBid(staleBid));

    executionPayloadBidManager.onSlot(currentSlot);

    assertThat(executionPayloadBidManager.getP2PBidsForSlot(staleBid.getMessage().getSlot()))
        .isEmpty();
    assertThat(executionPayloadBidManager.getP2PBidsForSlot(currentSlotBid.getMessage().getSlot()))
        .containsExactly(toRemoteBid(currentSlotBid));
    assertThat(executionPayloadBidManager.getP2PBidsForSlot(nextSlotBid.getMessage().getSlot()))
        .containsExactly(toRemoteBid(nextSlotBid));
  }

  @Test
  public void observeImportedBlocksForCircuitBreakerBuilderTracking() {
    final SignedBeaconBlock block = dataStructureUtil.randomSignedBeaconBlock(10);

    executionPayloadBidManager.onBlockImported(block, false);

    verify(executionPayloadBidCircuitBreaker).observeImportedBlock(block);
  }

  private GetPayloadResponse randomGetPayloadResponse(
      final UInt64 slot, final Bytes32 parentBlockHash) {
    return getPayloadResponse(slot, parentBlockHash, UInt256.valueOf(1_000_000_000_000L), false);
  }

  private GetPayloadResponse getPayloadResponse(
      final UInt64 slot,
      final Bytes32 parentBlockHash,
      final UInt256 value,
      final boolean shouldOverrideBuilder) {
    return new GetPayloadResponse(
        dataStructureUtil.randomExecutionPayload(
            slot, builder -> builder.parentHash(parentBlockHash)),
        value,
        dataStructureUtil.randomBlobsBundle(3),
        shouldOverrideBuilder,
        dataStructureUtil.randomExecutionRequests(slot));
  }

  private SignedExecutionPayloadBid createBid(
      final UInt64 slot, final Bytes32 parentBlockRoot, final UInt64 value) {
    return createBid(slot, parentBlockRoot, dataStructureUtil.randomBytes32(), value);
  }

  private SignedExecutionPayloadBid createBid(
      final UInt64 slot,
      final Bytes32 parentBlockRoot,
      final Bytes32 parentBlockHash,
      final UInt64 value) {
    return createBid(
        slot, parentBlockRoot, parentBlockHash, value, dataStructureUtil.randomUInt64());
  }

  private SignedExecutionPayloadBid createBid(
      final UInt64 slot,
      final Bytes32 parentBlockRoot,
      final Bytes32 parentBlockHash,
      final UInt64 value,
      final UInt64 builderIndex) {
    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(slot).getSchemaDefinitions());
    final ExecutionPayloadBidSchema schema = schemaDefinitions.getExecutionPayloadBidSchema();
    final ExecutionPayloadBid bid =
        schema.create(
            parentBlockHash,
            parentBlockRoot,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomEth1Address(),
            dataStructureUtil.randomUInt64(),
            builderIndex,
            slot,
            value,
            UInt64.ZERO,
            schema
                .getBlobKzgCommitmentsSchema()
                .createFromElements(dataStructureUtil.randomBlobKzgCommitments().asList()),
            dataStructureUtil.randomBytes32());
    return schemaDefinitions
        .getSignedExecutionPayloadBidSchema()
        .create(bid, dataStructureUtil.randomSignature());
  }

  private RemoteBid toRemoteBid(final SignedExecutionPayloadBid bid) {
    return new RemoteBid(bid, bid.getMessage().getValue(), Optional.empty());
  }

  private void addAcceptedBid(final SignedExecutionPayloadBid signedBid) {
    addAcceptedBid(executionPayloadBidManager, signedBid);
  }

  private void addAcceptedBid(
      final DefaultExecutionPayloadBidManager manager, final SignedExecutionPayloadBid signedBid) {
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));
    SafeFutureAssert.safeJoin(manager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));
  }

  private BeaconStateGloas stateAtSlot(final UInt64 slot) {
    return BeaconStateGloas.required(
        dataStructureUtil.randomBeaconState().updated(state -> state.setSlot(slot)));
  }
}
