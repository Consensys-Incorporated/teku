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
import static org.junit.jupiter.api.Assumptions.assumeTrue;
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
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.performance.trackers.BlockProductionPerformance;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.versions.gloas.BuilderConfig;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.heze.ExecutionPayloadBidHeze;
import tech.pegasys.teku.spec.datastructures.execution.GetPayloadResponse;
import tech.pegasys.teku.spec.datastructures.forkchoice.InclusionListStore;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.BidForBlock;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBid;
import tech.pegasys.teku.statetransition.execution.ExecutionPayloadBidManager.RemoteBidOrigin;
import tech.pegasys.teku.statetransition.util.PendingPool;
import tech.pegasys.teku.statetransition.util.PoolFactory;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;

@TestSpecContext(milestone = {SpecMilestone.GLOAS, SpecMilestone.HEZE})
public class DefaultExecutionPayloadBidManagerTest {

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private final InclusionListStore inclusionListStore = new InclusionListStore(16);
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
      mock(ReadOnlyForkChoiceStrategy.class);
  private final Bytes32 dependentRoot = Bytes32.fromHexStringLenient("0x1234");

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
  private PendingPool<SignedExecutionPayloadBid> pendingExecutionPayloadBids;

  @SuppressWarnings("unchecked")
  private final OperationAddedSubscriber<SignedExecutionPayloadBid> operationAddedSubscriber =
      mock(OperationAddedSubscriber.class);

  private DefaultExecutionPayloadBidManager executionPayloadBidManager;

  @BeforeEach
  public void setup(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    pendingExecutionPayloadBids =
        new PoolFactory(new StubMetricsSystem()).createPendingPoolForExecutionPayloadBids(spec);
    executionPayloadBidManager =
        new DefaultExecutionPayloadBidManager(
            spec,
            executionPayloadBidGossipValidator,
            executionPayloadBidCircuitBreaker,
            receivedExecutionPayloadBidEventsChannelPublisher,
            pendingExecutionPayloadBids,
            builderBidFetcher,
            bidSelector,
            inclusionListStore,
            recentChainData);
    when(recentChainData.getForkChoiceStrategy()).thenReturn(Optional.of(forkChoiceStrategy));
    when(forkChoiceStrategy.getAncestor(any(), any())).thenReturn(Optional.of(dependentRoot));
    when(executionPayloadBidCircuitBreaker.isEngaged(any(), any())).thenReturn(false);
    when(builderBidFetcher.getBuilderBids(any(), any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(Collections.emptyList()));
    executionPayloadBidManager.subscribeOperationAdded(operationAddedSubscriber);
  }

  @TestTemplate
  void selfBuiltBidIncludesLateListsObservedWhileWaitingForPayload() {
    assumeTrue(spec.isMilestoneSupported(SpecMilestone.HEZE));
    final UInt64 slot = UInt64.valueOf(64);
    final BeaconStateGloas state = stateAtSlot(slot);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(47)))
        .thenReturn(Optional.of(dependentRoot));
    when(forkChoiceStrategy.getAncestor(parentRoot, UInt64.valueOf(55)))
        .thenReturn(Optional.of(Bytes32.ZERO));
    final SafeFuture<GetPayloadResponse> payloadFuture = new SafeFuture<>();
    final DefaultExecutionPayloadBidManager manager =
        new DefaultExecutionPayloadBidManager(
            spec,
            executionPayloadBidGossipValidator,
            executionPayloadBidCircuitBreaker,
            receivedExecutionPayloadBidEventsChannelPublisher,
            pendingExecutionPayloadBids,
            builderBidFetcher,
            new ExecutionPayloadBidSelector(false, executionPayloadBidCircuitBreaker),
            inclusionListStore,
            recentChainData);
    final SafeFuture<BidForBlock> result =
        manager.getBidForBlock(
            parentRoot,
            parentHash,
            state,
            payloadFuture,
            BuilderConfig.NO_OP,
            blockProductionPerformance);
    final int validatorIndex =
        spec.atSlot(slot)
            .getInclusionListUtil()
            .orElseThrow()
            .getInclusionListCommittee(state, slot.decrement())
            .getInt(0);
    final SchemaDefinitionsHeze schemas =
        SchemaDefinitionsHeze.required(spec.atSlot(slot).getSchemaDefinitions());
    inclusionListStore.processInclusionList(
        schemas
            .getSignedInclusionListSchema()
            .create(
                schemas
                    .getInclusionListSchema()
                    .create(
                        slot.decrement(),
                        UInt64.valueOf(validatorIndex),
                        dependentRoot,
                        List.of(
                            schemas
                                .getInclusionListSchema()
                                .getTransactionSchema()
                                .fromBytes(Bytes.of(1)))),
                BLSSignature.empty()),
        false);
    payloadFuture.complete(randomGetPayloadResponse(slot, parentHash));

    final BidForBlock selected = SafeFutureAssert.safeJoin(result);
    assertThat(
            ((ExecutionPayloadBidHeze) selected.bid().getMessage())
                .getInclusionListBits()
                .getBit(0))
        .isTrue();
    verify(forkChoiceStrategy).getAncestor(parentRoot, UInt64.valueOf(47));
  }

  @TestTemplate
  void filtersNonInclusiveRemoteBidsBeforeSelectingHighestBid() {
    assumeTrue(spec.isMilestoneSupported(SpecMilestone.HEZE));
    final UInt64 slot = UInt64.valueOf(64);
    final BeaconStateGloas state = stateAtSlot(slot);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid missingBits =
        createBid(slot, parentRoot, parentHash, UInt64.valueOf(200));
    final SignedExecutionPayloadBid inclusiveBid =
        withAllInclusionListBits(createBid(slot, parentRoot, parentHash, UInt64.valueOf(100)));
    when(executionPayloadBidCircuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);
    when(builderBidFetcher.getBuilderBids(any(), any(), any(), any(), any(), any()))
        .thenReturn(SafeFuture.completedFuture(List.of(toRemoteBid(missingBits))));
    addLateInclusionList(state);
    final DefaultExecutionPayloadBidManager manager = realSelectorManager();
    addAcceptedBid(manager, missingBits);
    addAcceptedBid(manager, inclusiveBid);

    final BidForBlock selected =
        SafeFutureAssert.safeJoin(
            manager.getBidForBlock(
                parentRoot,
                parentHash,
                state,
                SafeFuture.completedFuture(
                    getPayloadResponse(slot, parentHash, UInt256.ZERO, false)),
                BuilderConfig.NO_OP,
                blockProductionPerformance));

    assertThat(selected.bid()).isEqualTo(inclusiveBid);
  }

  @TestTemplate
  void fallsBackToSelfBuildWhenAllRemoteBidsOmitLateLists() {
    assumeTrue(spec.isMilestoneSupported(SpecMilestone.HEZE));
    final UInt64 slot = UInt64.valueOf(64);
    final BeaconStateGloas state = stateAtSlot(slot);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid missingBits =
        createBid(slot, parentRoot, parentHash, UInt64.valueOf(200));
    when(executionPayloadBidCircuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);
    final SafeFuture<List<RemoteBid>> builderFuture = new SafeFuture<>();
    when(builderBidFetcher.getBuilderBids(any(), any(), any(), any(), any(), any()))
        .thenReturn(builderFuture);
    final DefaultExecutionPayloadBidManager manager = realSelectorManager();
    addAcceptedBid(manager, missingBits);
    final SafeFuture<BidForBlock> result =
        manager.getBidForBlock(
            parentRoot,
            parentHash,
            state,
            SafeFuture.completedFuture(getPayloadResponse(slot, parentHash, UInt256.ZERO, false)),
            BuilderConfig.NO_OP,
            blockProductionPerformance);
    addLateInclusionList(state);
    builderFuture.complete(List.of(toRemoteBid(missingBits)));

    final ExecutionPayloadBidHeze selected =
        (ExecutionPayloadBidHeze) SafeFutureAssert.safeJoin(result).bid().getMessage();
    assertThat(selected.getBuilderIndex()).isEqualTo(SpecConfigGloas.BUILDER_INDEX_SELF_BUILD);
    assertThat(selected.getInclusionListBits().getBit(0)).isTrue();
  }

  @TestTemplate
  void selectsInclusiveBuilderBidWhenLocalPayloadIsUnavailable() {
    assumeTrue(spec.isMilestoneSupported(SpecMilestone.HEZE));
    final UInt64 slot = UInt64.valueOf(64);
    final BeaconStateGloas state = stateAtSlot(slot);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid missingBits =
        createBid(slot, parentRoot, parentHash, UInt64.valueOf(200));
    final SignedExecutionPayloadBid inclusiveBid =
        withAllInclusionListBits(createBid(slot, parentRoot, parentHash, UInt64.valueOf(100)));
    when(executionPayloadBidCircuitBreaker.isBuilderAllowed(any(), any())).thenReturn(true);
    when(builderBidFetcher.getBuilderBids(any(), any(), any(), any(), any(), any()))
        .thenReturn(
            SafeFuture.completedFuture(
                List.of(toRemoteBid(missingBits), toRemoteBid(inclusiveBid))));
    addLateInclusionList(state);

    final BidForBlock selected =
        SafeFutureAssert.safeJoin(
            realSelectorManager()
                .getBidForBlock(
                    parentRoot,
                    parentHash,
                    state,
                    SafeFuture.failedFuture(new IllegalStateException("EL unavailable")),
                    BuilderConfig.NO_OP,
                    blockProductionPerformance));

    assertThat(selected.bid()).isEqualTo(inclusiveBid);
  }

  @TestTemplate
  void buildsHezeBidUsingHistoricalRootWhenAncestorIsFinalized() {
    assumeTrue(spec.isMilestoneSupported(SpecMilestone.HEZE));
    final UInt64 slot = UInt64.valueOf(64);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    when(forkChoiceStrategy.getAncestor(any(), any())).thenReturn(Optional.empty());
    final BeaconStateGloas state =
        BeaconStateGloas.required(
            stateAtSlot(slot)
                .updated(
                    mutableState -> mutableState.getBlockRoots().setElement(47, dependentRoot)));
    addLateInclusionList(state);

    final SafeFuture<BidForBlock> result =
        realSelectorManager()
            .getBidForBlock(
                parentRoot,
                parentHash,
                state,
                SafeFuture.completedFuture(randomGetPayloadResponse(slot, parentHash)),
                BuilderConfig.NO_OP,
                blockProductionPerformance);

    assertThat(
            ((ExecutionPayloadBidHeze) SafeFutureAssert.safeJoin(result).bid().getMessage())
                .getInclusionListBits()
                .getBit(0))
        .isTrue();
  }

  @TestTemplate
  void buildsBidWithRealFinalizedAnchorAndHistoricalDependentRoot() {
    assumeTrue(spec.isMilestoneSupported(SpecMilestone.HEZE));
    final var storage = InMemoryStorageSystemBuilder.buildDefault(spec);
    final var parent = dataStructureUtil.createAnchorFromState(stateAtSlot(UInt64.valueOf(48)));
    storage.recentChainData().initializeFromAnchorPoint(parent, UInt64.ZERO);
    final ReadOnlyForkChoiceStrategy strategy =
        storage.recentChainData().getForkChoiceStrategy().orElseThrow();
    assertThat(strategy.getAncestor(parent.getRoot(), UInt64.valueOf(47))).isEmpty();
    final BeaconStateGloas state =
        BeaconStateGloas.required(
            parent.getState().updated(mutableState -> mutableState.setSlot(UInt64.valueOf(64))));
    final Bytes32 historicalRoot = spec.getBlockRootAtSlot(state, UInt64.valueOf(47));
    final int validatorIndex =
        spec.atSlot(state.getSlot())
            .getInclusionListUtil()
            .orElseThrow()
            .getInclusionListCommittee(state, UInt64.valueOf(63))
            .getInt(0);
    final SchemaDefinitionsHeze schemas =
        SchemaDefinitionsHeze.required(spec.atSlot(state.getSlot()).getSchemaDefinitions());
    inclusionListStore.processInclusionList(
        schemas
            .getSignedInclusionListSchema()
            .create(
                schemas
                    .getInclusionListSchema()
                    .create(
                        UInt64.valueOf(63),
                        UInt64.valueOf(validatorIndex),
                        historicalRoot,
                        List.of(
                            schemas
                                .getInclusionListSchema()
                                .getTransactionSchema()
                                .fromBytes(Bytes.of(1)))),
                BLSSignature.empty()),
        true);
    final DefaultExecutionPayloadBidManager manager =
        new DefaultExecutionPayloadBidManager(
            spec,
            executionPayloadBidGossipValidator,
            executionPayloadBidCircuitBreaker,
            receivedExecutionPayloadBidEventsChannelPublisher,
            pendingExecutionPayloadBids,
            builderBidFetcher,
            new ExecutionPayloadBidSelector(false, executionPayloadBidCircuitBreaker),
            inclusionListStore,
            storage.recentChainData());
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();

    final BidForBlock result =
        SafeFutureAssert.safeJoin(
            manager.getBidForBlock(
                parent.getRoot(),
                parentHash,
                state,
                SafeFuture.completedFuture(randomGetPayloadResponse(state.getSlot(), parentHash)),
                BuilderConfig.NO_OP,
                blockProductionPerformance));

    assertThat(
            ((ExecutionPayloadBidHeze) result.bid().getMessage()).getInclusionListBits().getBit(0))
        .isTrue();
  }

  private DefaultExecutionPayloadBidManager realSelectorManager() {
    return new DefaultExecutionPayloadBidManager(
        spec,
        executionPayloadBidGossipValidator,
        executionPayloadBidCircuitBreaker,
        receivedExecutionPayloadBidEventsChannelPublisher,
        pendingExecutionPayloadBids,
        builderBidFetcher,
        new ExecutionPayloadBidSelector(false, executionPayloadBidCircuitBreaker),
        inclusionListStore,
        recentChainData);
  }

  @TestTemplate
  void buildsFirstHezeBidWhenPreviousSlotUsesGloas() {
    assumeTrue(spec.isMilestoneSupported(SpecMilestone.HEZE));
    spec = TestSpecFactory.createMinimalWithHezeForkEpoch(UInt64.ONE);
    dataStructureUtil = new DataStructureUtil(spec);
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.ONE);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final BeaconStateGloas state = stateAtSlot(slot);
    assertThat(spec.atSlot(slot.decrement()).getInclusionListUtil()).isEmpty();

    final BidForBlock result =
        SafeFutureAssert.safeJoin(
            realSelectorManager()
                .getBidForBlock(
                    parentRoot,
                    parentHash,
                    state,
                    SafeFuture.completedFuture(randomGetPayloadResponse(slot, parentHash)),
                    BuilderConfig.NO_OP,
                    blockProductionPerformance));

    assertThat(result.bid().getMessage()).isInstanceOf(ExecutionPayloadBidHeze.class);
    assertThat(
            ((ExecutionPayloadBidHeze) result.bid().getMessage())
                .getInclusionListBits()
                .getBitCount())
        .isZero();
  }

  private void addLateInclusionList(final BeaconStateGloas state) {
    final UInt64 slot = state.getSlot();
    final int validatorIndex =
        spec.atSlot(slot)
            .getInclusionListUtil()
            .orElseThrow()
            .getInclusionListCommittee(state, slot.decrement())
            .getInt(0);
    final SchemaDefinitionsHeze schemas =
        SchemaDefinitionsHeze.required(spec.atSlot(slot).getSchemaDefinitions());
    inclusionListStore.processInclusionList(
        schemas
            .getSignedInclusionListSchema()
            .create(
                schemas
                    .getInclusionListSchema()
                    .create(
                        slot.decrement(),
                        UInt64.valueOf(validatorIndex),
                        dependentRoot,
                        List.of(
                            schemas
                                .getInclusionListSchema()
                                .getTransactionSchema()
                                .fromBytes(Bytes.of(1)))),
                BLSSignature.empty()),
        false);
  }

  private SignedExecutionPayloadBid withAllInclusionListBits(
      final SignedExecutionPayloadBid signedBid) {
    final ExecutionPayloadBid bid = signedBid.getMessage();
    final SchemaDefinitionsHeze schemas =
        SchemaDefinitionsHeze.required(spec.atSlot(bid.getSlot()).getSchemaDefinitions());
    final var schema = schemas.getExecutionPayloadBidSchema();
    final var bits = schema.getInclusionListBitsSchema();
    return schemas
        .getSignedExecutionPayloadBidSchema()
        .create(
            schema.create(
                bid.getParentBlockHash(),
                bid.getParentBlockRoot(),
                bid.getBlockHash(),
                bid.getPrevRandao(),
                bid.getFeeRecipient(),
                bid.getGasLimit(),
                bid.getBuilderIndex(),
                bid.getSlot(),
                bid.getValue(),
                bid.getExecutionPayment(),
                bid.getBlobKzgCommitments(),
                bid.getExecutionRequestsRoot(),
                bits.ofBits(IntStream.range(0, bits.getLength()).toArray())),
            signedBid.getSignature());
  }

  @TestTemplate
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

  @TestTemplate
  public void fallsBackToLocalSelfBuiltBidWhenNoRemoteBidIsReturned() {
    final BeaconStateGloas state = stateAtSlot(UInt64.valueOf(10));
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

  @TestTemplate
  public void fallsBackToLocalSelfBuiltBidWhenRemoteBidFutureFails() {
    final BeaconStateGloas state = stateAtSlot(UInt64.valueOf(10));
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
        .thenThrow(new IllegalStateException("oopsy, bad builder"));
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

  @TestTemplate
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

    when(builderBidFetcher.getBuilderBids(any(), any(), any(), any(), any(), any()))
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
  void selectsRemoteBidWhenLocalPayloadCannotBeConvertedToBid() {
    final UInt64 slot = UInt64.valueOf(10);
    final Bytes32 parentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 parentHash = dataStructureUtil.randomBytes32();
    final SignedExecutionPayloadBid remoteBidRaw =
        createBid(slot, parentRoot, parentHash, UInt64.valueOf(100));
    final RemoteBid remoteBid = toRemoteBid(remoteBidRaw);
    final BidForBlock remoteBidForBlock =
        new BidForBlock(remoteBidRaw, UInt256.ONE, Optional.empty());
    addAcceptedBid(remoteBidRaw);
    when(bidSelector.selectBestRemoteBid(any(), any(), any(), any(), any(), any()))
        .thenReturn(Optional.of(remoteBid));
    when(bidSelector.selectBestBidForBlock(
            eq(Optional.empty()), eq(Optional.of(remoteBid)), any(), eq(slot)))
        .thenReturn(remoteBidForBlock);
    final GetPayloadResponse payloadWithoutBlobs =
        new GetPayloadResponse(
            dataStructureUtil.randomExecutionPayload(
                slot, builder -> builder.parentHash(parentHash)),
            UInt256.ONE);

    final SafeFuture<BidForBlock> result =
        executionPayloadBidManager.getBidForBlock(
            parentRoot,
            parentHash,
            stateAtSlot(slot),
            SafeFuture.completedFuture(payloadWithoutBlobs),
            BuilderConfig.NO_OP,
            blockProductionPerformance);

    assertThat(result).isCompletedWithValue(remoteBidForBlock);
  }

  @TestTemplate
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

  @TestTemplate
  public void acceptedBuilderBidNotifiesSubscriberAsLocal() {
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.BUILDER));

    verify(operationAddedSubscriber).onOperationAdded(signedBid, ACCEPT, false);
  }

  @TestTemplate
  public void acceptedP2pBidNotifiesSubscriberAsFromNetwork() {
    final SignedExecutionPayloadBid signedBid =
        createBid(UInt64.valueOf(10), dataStructureUtil.randomBytes32(), UInt64.valueOf(100));
    when(executionPayloadBidGossipValidator.validate(signedBid))
        .thenReturn(SafeFuture.completedFuture(ACCEPT));

    SafeFutureAssert.safeJoin(
        executionPayloadBidManager.validateAndAddBid(signedBid, RemoteBidOrigin.P2P));

    verify(operationAddedSubscriber).onOperationAdded(signedBid, ACCEPT, true);
  }

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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

  @TestTemplate
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
    final ExecutionPayloadBidSchema<? extends ExecutionPayloadBid> schema =
        schemaDefinitions.getExecutionPayloadBidSchema();
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
        dataStructureUtil.randomBeaconStateWithActiveValidators(128, slot));
  }
}
