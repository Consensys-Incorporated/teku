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

package tech.pegasys.teku.statetransition.lightclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.ReorgContext;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientServerServiceTest {

  /** Blocks are on the canonical chain unless a test says otherwise. */
  private static final BiPredicate<UInt64, Bytes32> CANONICAL = (slot, root) -> true;

  private final Map<Bytes32, SignedBeaconBlock> blocksByRoot = new HashMap<>();
  private final Map<Bytes32, BeaconState> statesByRoot = new HashMap<>();

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private LightClientUpdateStore store;
  private LightClientServerService service;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    store = new LightClientUpdateStore(spec);
    service =
        new LightClientServerService(spec, store, this::lookUpBlock, this::lookUpState, CANONICAL);
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateOrphanedWhileLookupsWereInFlight() {
    final Chain chain = generateChain();
    final SafeFuture<Optional<BeaconState>> pendingAttestedState = new SafeFuture<>();
    final AtomicBoolean canonical = new AtomicBoolean(true);
    final LightClientServerService racingService =
        new LightClientServerService(
            spec,
            store,
            this::lookUpBlock,
            root ->
                root.equals(chain.attested().getRoot()) ? pendingAttestedState : lookUpState(root),
            (slot, root) -> canonical.get());

    racingService.onBlockImported(chain.signature().getBlock(), false);

    canonical.set(false);
    pendingAttestedState.complete(Optional.of(chain.attested().getState()));

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldStoreUpdateForOptimisticallyImportedBlock() {
    final Chain chain = generateChain();

    service.onBlockImported(chain.signature().getBlock(), true);

    final LightClientUpdate update = onlyUpdateAtPeriod(0);
    assertThat(update.getSignatureSlot().get()).isEqualTo(chain.signature().getSlot());
    assertThat(store.getLatestOptimisticUpdate()).isPresent();
  }

  @TestTemplate
  public void onBlockImported_shouldIgnoreBlockWithInsufficientSyncCommitteeParticipants() {
    final int belowMinimum =
        SpecConfigAltair.required(spec.getGenesisSpecConfig()).getMinSyncCommitteeParticipants()
            - 1;
    final Chain chain =
        generateChain(
            BlockOptions.create()
                .setSyncAggregate(
                    dataStructureUtil.randomSyncAggregate(
                        IntStream.range(0, belowMinimum).toArray()))
                .setSkipStateTransition(true));

    rejectStateLookups().onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldIgnoreOrphanedBlocks() {
    final Chain chain = generateChain();
    final LightClientServerService orphanRejectingService =
        new LightClientServerService(
            spec,
            store,
            this::lookUpBlock,
            __ -> fail("should have returned before looking up state"),
            (slot, root) -> false);

    orphanRejectingService.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenPostStateIsUnavailable() {
    final Chain chain = generateChain();
    statesByRoot.remove(chain.signature().getRoot());

    service.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenAttestedBlockIsUnavailable() {
    final Chain chain = generateChain();
    blocksByRoot.remove(chain.attested().getRoot());

    service.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenAttestedStateIsUnavailable() {
    final Chain chain = generateChain();
    statesByRoot.remove(chain.attested().getRoot());

    service.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldStoreNonFinalityUpdateWhenFinalizedBlockIsUnavailable() {
    final Chain chain = generateChain();
    finalizeAttestedStateAt(chain, dataStructureUtil.randomBytes32());

    service.onBlockImported(chain.signature().getBlock(), false);

    assertThat(onlyUpdateAtPeriod(0).getFinalityBranch().isDefault()).isTrue();
    assertThat(store.getLatestOptimisticUpdate()).isPresent();
    assertThat(store.getLatestFinalityUpdate()).isEmpty();
  }

  @TestTemplate
  public void onBlockImported_shouldProveGenesisFinalityBeforeTheChainFinalizes() {
    final Chain chain = generateChain();
    assertThat(chain.attested().getState().getFinalizedCheckpoint().getRoot().isZero()).isTrue();

    service.onBlockImported(chain.signature().getBlock(), false);

    final LightClientUpdate update = onlyUpdateAtPeriod(0);
    assertThat(update.getFinalityBranch().isDefault()).isFalse();
    assertThat(update.getFinalizedHeader().getBeacon().getSlot()).isEqualTo(UInt64.ZERO);
    assertThat(update.getFinalizedHeader().getBeacon().getBodyRoot()).isEqualTo(Bytes32.ZERO);
    assertThat(store.getLatestFinalityUpdate()).isPresent();
    assertThat(store.getLatestOptimisticUpdate()).isPresent();
  }

  @TestTemplate
  public void onBlockImported_shouldStoreNonFinalityUpdateWhenGenesisBlockIsUnavailable() {
    final Chain chain = generateChain();
    blocksByRoot.remove(chain.genesis().getRoot());

    service.onBlockImported(chain.signature().getBlock(), false);

    assertThat(onlyUpdateAtPeriod(0).getFinalityBranch().isDefault()).isTrue();
    assertThat(store.getLatestFinalityUpdate()).isEmpty();
  }

  @TestTemplate
  public void onBlockImported_shouldNotPropagateFailureWhenLookupFails() {
    final Chain chain = generateChain();
    final LightClientServerService failingService =
        new LightClientServerService(
            spec,
            store,
            this::lookUpBlock,
            __ -> SafeFuture.failedFuture(new IllegalStateException("store is down")),
            CANONICAL);

    failingService.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldStoreUpdateFinalityUpdateAndOptimisticUpdate() {
    final Chain chain = generateChain();
    finalizeAttestedStateAt(chain, chain.attested().getRoot());

    service.onBlockImported(chain.signature().getBlock(), false);

    final LightClientUpdate update = onlyUpdateAtPeriod(0);
    assertThat(update.getAttestedHeader().getBeacon().getSlot())
        .isEqualTo(chain.attested().getSlot());
    assertThat(update.getSignatureSlot().get()).isEqualTo(chain.signature().getSlot());

    assertThat(store.getLatestFinalityUpdate()).isPresent();
    assertThat(store.getLatestOptimisticUpdate()).isPresent();
  }

  @TestTemplate
  public void onBlockImported_shouldNotBlockWhileLookupsArePending() {
    final Chain chain = generateChain();
    final SafeFuture<Optional<BeaconState>> pendingAttestedState = new SafeFuture<>();
    final LightClientServerService pendingService =
        new LightClientServerService(
            spec,
            store,
            this::lookUpBlock,
            root ->
                root.equals(chain.attested().getRoot()) ? pendingAttestedState : lookUpState(root),
            CANONICAL);

    pendingService.onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();

    pendingAttestedState.complete(Optional.of(chain.attested().getState()));

    assertThat(store.getLatestOptimisticUpdate()).isPresent();
  }

  @TestTemplate
  public void onNewFinalizedCheckpoint_shouldPruneUpdatesBeyondRetentionWindow() {
    addUpdateAtPeriod(1);
    final LightClientUpdate retained = addUpdateAtPeriod(200);

    service.onNewFinalizedCheckpoint(checkpointAtPeriod(200), false);

    assertThat(store.getBestUpdatesInRange(UInt64.ONE, 1)).isEmpty();
    assertThat(onlyUpdateAtPeriod(200)).isEqualTo(retained);
  }

  @TestTemplate
  public void onNewFinalizedCheckpoint_shouldKeepUpdateExactlyAtRetentionBoundary() {
    final int boundaryPeriod = 200 - retainedPeriodsBehindFinalized();
    addUpdateAtPeriod(boundaryPeriod - 1);
    final LightClientUpdate atBoundary = addUpdateAtPeriod(boundaryPeriod);

    service.onNewFinalizedCheckpoint(checkpointAtPeriod(200), false);

    assertThat(store.getBestUpdatesInRange(UInt64.valueOf(boundaryPeriod - 1), 1)).isEmpty();
    assertThat(onlyUpdateAtPeriod(boundaryPeriod)).isEqualTo(atBoundary);
  }

  @TestTemplate
  public void onNewFinalizedCheckpoint_shouldNotPruneWhenFinalizedPeriodIsBelowRetention() {
    final LightClientUpdate update = addUpdateAtPeriod(1);

    service.onNewFinalizedCheckpoint(checkpointAtPeriod(5), false);

    assertThat(onlyUpdateAtPeriod(1)).isEqualTo(update);
  }

  @TestTemplate
  public void chainHeadUpdated_shouldDropUpdatesOrphanedByAReorg() {
    final Chain chain = generateChain();
    service.onBlockImported(chain.signature().getBlock(), false);
    assertThat(store.getBestUpdatesInRange(UInt64.ZERO, 1)).hasSize(1);

    notifyChainHeadUpdated(orphanEverything(), reorg());

    assertThat(store.getBestUpdatesInRange(UInt64.ZERO, 1)).isEmpty();
  }

  @TestTemplate
  public void chainHeadUpdated_shouldKeepUpdatesStillOnTheCanonicalChain() {
    final Chain chain = generateChain();
    service.onBlockImported(chain.signature().getBlock(), false);

    notifyChainHeadUpdated(service, reorg());

    assertThat(store.getBestUpdatesInRange(UInt64.ZERO, 1)).hasSize(1);
  }

  @TestTemplate
  public void chainHeadUpdated_shouldIgnorePayloadReorgs() {
    final Chain chain = generateChain();
    service.onBlockImported(chain.signature().getBlock(), false);

    notifyChainHeadUpdated(orphanEverything(), payloadReorg());

    assertThat(store.getBestUpdatesInRange(UInt64.ZERO, 1)).hasSize(1);
  }

  @TestTemplate
  public void chainHeadUpdated_shouldIgnoreHeadAdvancesThatAreNotReorgs() {
    final Chain chain = generateChain();
    service.onBlockImported(chain.signature().getBlock(), false);

    notifyChainHeadUpdated(orphanEverything(), Optional.empty());

    assertThat(store.getBestUpdatesInRange(UInt64.ZERO, 1)).hasSize(1);
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenAnotherBlockIsCanonicalAtTheSignatureSlot() {
    final SignedBeaconBlock signatureBlock = generateChain().signature().getBlock();

    serviceBackedByForkChoice(
            signatureBlock.getSlot(), Optional.of(dataStructureUtil.randomBytes32()), false)
        .onBlockImported(signatureBlock, false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldStoreUpdateWhenTheSignatureBlockIsCanonicalAtItsSlot() {
    final SignedBeaconBlock signatureBlock = generateChain().signature().getBlock();

    serviceBackedByForkChoice(
            signatureBlock.getSlot(), Optional.of(signatureBlock.getRoot()), false)
        .onBlockImported(signatureBlock, false);

    assertThat(store.getLatestOptimisticUpdate()).isPresent();
  }

  @TestTemplate
  public void onBlockImported_shouldStoreUpdateWhenForkChoiceIsPrunedPastAFinalizedSlot() {
    final SignedBeaconBlock signatureBlock = generateChain().signature().getBlock();

    serviceBackedByForkChoice(signatureBlock.getSlot(), Optional.empty(), true)
        .onBlockImported(signatureBlock, false);

    assertThat(store.getLatestOptimisticUpdate()).isPresent();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenForkChoiceDoesNotCoverAnUnfinalizedSlot() {
    final SignedBeaconBlock signatureBlock = generateChain().signature().getBlock();

    serviceBackedByForkChoice(signatureBlock.getSlot(), Optional.empty(), false)
        .onBlockImported(signatureBlock, false);

    assertNothingStored();
  }

  @TestTemplate
  public void onBlockImported_shouldNotStoreUpdateWhenThereIsNoChainHead() {
    final Chain chain = generateChain();
    final CombinedChainDataClient combinedChainDataClient = mock(CombinedChainDataClient.class);
    when(combinedChainDataClient.getChainHead()).thenReturn(Optional.empty());

    new LightClientServerService(spec, store, combinedChainDataClient)
        .onBlockImported(chain.signature().getBlock(), false);

    assertNothingStored();
  }

  private record Chain(
      SignedBlockAndState genesis, SignedBlockAndState attested, SignedBlockAndState signature) {}

  private LightClientServerService orphanEverything() {
    return new LightClientServerService(
        spec, store, this::lookUpBlock, this::lookUpState, (slot, root) -> false);
  }

  private Optional<ReorgContext> payloadReorg() {
    return Optional.of(
        ReorgContext.payloadReorg(
            dataStructureUtil.randomBytes32(),
            UInt64.ONE,
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomBytes32()));
  }

  private Optional<ReorgContext> reorg() {
    return ReorgContext.of(
        dataStructureUtil.randomBytes32(),
        UInt64.ONE,
        dataStructureUtil.randomBytes32(),
        UInt64.ZERO,
        dataStructureUtil.randomBytes32());
  }

  private void notifyChainHeadUpdated(
      final LightClientServerService target, final Optional<ReorgContext> reorgContext) {
    target.chainHeadUpdated(
        UInt64.ONE,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        false,
        false,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32(),
        Optional.<ForkChoicePayloadStatus>empty(),
        reorgContext);
  }

  private SafeFuture<Optional<SignedBeaconBlock>> lookUpBlock(final Bytes32 root) {
    return SafeFuture.completedFuture(Optional.ofNullable(blocksByRoot.get(root)));
  }

  private SafeFuture<Optional<BeaconState>> lookUpState(final Bytes32 root) {
    return SafeFuture.completedFuture(Optional.ofNullable(statesByRoot.get(root)));
  }

  private LightClientServerService rejectStateLookups() {
    return new LightClientServerService(
        spec,
        store,
        this::lookUpBlock,
        __ -> fail("should have returned before looking up state"),
        CANONICAL);
  }

  private Chain generateChain() {
    final int syncCommitteeSize =
        SpecConfigAltair.required(spec.getGenesisSpecConfig()).getSyncCommitteeSize();
    return generateChain(
        BlockOptions.create()
            .setSyncAggregate(
                dataStructureUtil.randomSyncAggregate(
                    IntStream.range(0, syncCommitteeSize).toArray())));
  }

  private Chain generateChain(final BlockOptions signatureBlockOptions) {
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final Chain chain =
        new Chain(
            chainBuilder.generateGenesis(),
            chainBuilder.generateNextBlock(),
            chainBuilder.generateNextBlock(signatureBlockOptions));

    blocksByRoot.put(chain.genesis().getRoot(), chain.genesis().getBlock());
    blocksByRoot.put(chain.attested().getRoot(), chain.attested().getBlock());
    statesByRoot.put(chain.attested().getRoot(), chain.attested().getState());
    statesByRoot.put(chain.signature().getRoot(), chain.signature().getState());
    return chain;
  }

  private LightClientUpdate addUpdateAtPeriod(final long period) {
    final LightClientUpdate update =
        dataStructureUtil.createRandomLightClientUpdateBuilder(periodStartSlot(period)).build();
    store.addUpdate(update, dataStructureUtil.randomBytes32(), CANONICAL);
    return update;
  }

  private Checkpoint checkpointAtPeriod(final long period) {
    return new Checkpoint(
        spec.computeEpochAtSlot(periodStartSlot(period)), dataStructureUtil.randomBytes32());
  }

  private int retainedPeriodsBehindFinalized() {
    final int epochsPerPeriod =
        SpecConfigAltair.required(spec.getGenesisSpecConfig()).getEpochsPerSyncCommitteePeriod();
    return (spec.getNetworkingConfig().getMinEpochsForBlockRequests() + epochsPerPeriod - 1)
        / epochsPerPeriod;
  }

  private UInt64 periodStartSlot(final long period) {
    return spec.computeStartSlotAtEpoch(
            spec.getSyncCommitteeUtilRequired(UInt64.ZERO)
                .computeFirstEpochOfNextSyncCommitteePeriod(UInt64.ZERO))
        .times(period);
  }

  private LightClientUpdate onlyUpdateAtPeriod(final long period) {
    final var updates = store.getBestUpdatesInRange(UInt64.valueOf(period), 1);
    assertThat(updates).hasSize(1);
    return updates.getFirst();
  }

  private LightClientServerService serviceBackedByForkChoice(
      final UInt64 signatureSlot,
      final Optional<Bytes32> canonicalRootAtSignatureSlot,
      final boolean finalized) {
    final Bytes32 headRoot = dataStructureUtil.randomBytes32();

    final ChainHead chainHead = mock(ChainHead.class);
    when(chainHead.getRoot()).thenReturn(headRoot);

    final RecentChainData recentChainData = mock(RecentChainData.class);
    when(recentChainData.getBlockRootInEffectBySlot(signatureSlot, headRoot))
        .thenReturn(canonicalRootAtSignatureSlot);

    final CombinedChainDataClient combinedChainDataClient = mock(CombinedChainDataClient.class);
    when(combinedChainDataClient.getChainHead()).thenReturn(Optional.of(chainHead));
    when(combinedChainDataClient.getRecentChainData()).thenReturn(recentChainData);
    when(combinedChainDataClient.isFinalized(signatureSlot)).thenReturn(finalized);
    when(combinedChainDataClient.getBlockByBlockRoot(any()))
        .thenAnswer(invocation -> lookUpBlock(invocation.getArgument(0)));
    when(combinedChainDataClient.getStateByBlockRoot(any()))
        .thenAnswer(invocation -> lookUpState(invocation.getArgument(0)));

    return new LightClientServerService(spec, store, combinedChainDataClient);
  }

  private void finalizeAttestedStateAt(final Chain chain, final Bytes32 finalizedRoot) {
    final BeaconState attestedState = chain.attested().getState();
    final BeaconBlockHeader pinnedHeader = BeaconBlockHeader.fromState(attestedState);
    statesByRoot.put(
        chain.attested().getRoot(),
        attestedState.updated(
            state -> {
              state.setLatestBlockHeader(pinnedHeader);
              state.setFinalizedCheckpoint(
                  new Checkpoint(spec.computeEpochAtSlot(attestedState.getSlot()), finalizedRoot));
            }));
  }

  private void assertNothingStored() {
    assertThat(store.getBestUpdatesInRange(UInt64.ZERO, 1)).isEmpty();
    assertThat(store.getLatestFinalityUpdate()).isEmpty();
    assertThat(store.getLatestOptimisticUpdate()).isEmpty();
  }
}
