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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.pow.api.DepositTreeSnapshot;
import tech.pegasys.teku.ethereum.pow.api.DepositsFromBlockEvent;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.Eth1Data;
import tech.pegasys.teku.spec.datastructures.operations.Deposit;
import tech.pegasys.teku.spec.datastructures.operations.DepositWithIndex;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.util.DepositUtil;
import tech.pegasys.teku.spec.datastructures.util.MerkleTree;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.api.Eth1DepositStorageChannel;
import tech.pegasys.teku.storage.api.StorageUpdateChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class DepositProviderTest {

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private BeaconState state;
  private final Eth1DataCache eth1DataCache = mock(Eth1DataCache.class);
  private final StorageUpdateChannel storageUpdateChannel = mock(StorageUpdateChannel.class);
  private final Eth1DepositStorageChannel eth1DepositStorageChannel =
      mock(Eth1DepositStorageChannel.class);
  private List<tech.pegasys.teku.ethereum.pow.api.Deposit> allSeenDepositsList;
  private DepositProvider depositProvider;
  private Eth1Data randomEth1Data;

  private MerkleTree depositMerkleTree;
  private DepositUtil depositUtil;

  void setup(final int maxDeposits) {
    setup(maxDeposits, SpecMilestone.PHASE0);
  }

  void setup(final int maxDeposits, final SpecMilestone milestone) {
    spec = TestSpecFactory.create(milestone, Eth2Network.MINIMAL, b -> b.maxDeposits(maxDeposits));
    depositUtil = new DepositUtil(spec);
    dataStructureUtil = new DataStructureUtil(spec);
    state =
        dataStructureUtil
            .randomBeaconState(UInt64.valueOf(1234))
            .updated(
                mutableState ->
                    // no votes
                    mutableState.setEth1DataVotes(
                        SszListSchema.create(Eth1Data.SSZ_SCHEMA, 32).of()));
    depositProvider =
        new DepositProvider(
            new StubMetricsSystem(),
            recentChainData,
            eth1DataCache,
            storageUpdateChannel,
            eth1DepositStorageChannel,
            spec);
    depositMerkleTree = new MerkleTree(spec.getGenesisSpecConfig().getDepositContractTreeDepth());
    createDepositEvents(40);
    randomEth1Data = dataStructureUtil.randomEth1Data();
  }

  @Test
  void stateEth1DepositIndexIsEqualToEth1DataDepositCount_NoDepositReturned() {
    setup(5);
    updateStateEth1DepositIndex(2);
    updateStateEth1DataDepositCount(2);
    mockDepositsFromEth1Block(0, 10);
    SszList<Deposit> deposits = depositProvider.getDeposits(state, randomEth1Data);
    assertThat(deposits).isEmpty();
  }

  @Test
  void numberOfDepositsThatCanBeIncludedLessThanMaxDeposits() {
    setup(16);
    updateStateEth1DepositIndex(5);
    updateStateEth1DataDepositCount(20);

    mockDepositsFromEth1Block(0, 10);
    mockDepositsFromEth1Block(10, 20);

    List<DepositWithIndex> deposits = depositProvider.getDepositsWithIndex(state, randomEth1Data);
    assertThat(deposits).hasSize(15);
    checkThatDepositProofIsValid(deposits);
  }

  @Test
  void numberOfDepositsGetsAdjustedAccordingToOurEth1DataVote() {
    setup(30);
    updateStateEth1DepositIndex(5);
    updateStateEth1DataDepositCount(20);

    mockDepositsFromEth1Block(0, 10);
    mockDepositsFromEth1Block(10, 30);

    int enoughVoteCount =
        spec.getGenesisSpecConfig().getEpochsPerEth1VotingPeriod()
            * spec.slotsPerEpoch(SpecConfig.GENESIS_EPOCH);
    UInt64 newDepositCount = UInt64.valueOf(30);
    Eth1Data newEth1Data = new Eth1Data(Bytes32.ZERO, newDepositCount, Bytes32.ZERO);
    SszList<Eth1Data> et1hDataVotes =
        Stream.generate(() -> newEth1Data)
            .limit(enoughVoteCount)
            .collect(SszListSchema.create(Eth1Data.SSZ_SCHEMA, 32).collector());
    state = state.updated(mutableState -> mutableState.setEth1DataVotes(et1hDataVotes));

    List<DepositWithIndex> deposits = depositProvider.getDepositsWithIndex(state, newEth1Data);
    assertThat(deposits).hasSize(25);
    checkThatDepositProofIsValid(deposits);
    assertThat(deposits.stream().map(DepositWithIndex::deposit))
        .containsExactlyElementsOf(depositProvider.getDeposits(state, newEth1Data));
  }

  @Test
  void numberOfDepositsThatCanBeIncludedMoreThanMaxDeposits() {
    setup(10);
    updateStateEth1DepositIndex(5);
    updateStateEth1DataDepositCount(20);

    mockDepositsFromEth1Block(0, 10);
    mockDepositsFromEth1Block(10, 20);

    List<DepositWithIndex> deposits = depositProvider.getDepositsWithIndex(state, randomEth1Data);
    assertThat(deposits).hasSize(10);
    checkThatDepositProofIsValid(deposits);
    assertThat(deposits.stream().map(DepositWithIndex::deposit))
        .containsExactlyElementsOf(depositProvider.getDeposits(state, randomEth1Data));
  }

  @Test
  void noDepositsIncludedIfFormerDepositMechanismHasBeenDisabled() {
    setup(16, SpecMilestone.ELECTRA);
    updateStateEth1DepositIndex(5);
    updateStateDepositRequestsStartIndex(5);

    final SszList<Deposit> deposits = depositProvider.getDeposits(state, randomEth1Data);

    assertThat(deposits).isEmpty();
  }

  @Test
  void getsRemainingEth1PendingDepositsIfElectraIsEnabled() {
    setup(16, SpecMilestone.ELECTRA);
    updateStateEth1DepositIndex(5);
    updateStateEth1DataDepositCount(20);
    // 16th deposit is using the new mechanism
    updateStateDepositRequestsStartIndex(16);

    mockDepositsFromEth1Block(0, 10);
    mockDepositsFromEth1Block(10, 20);

    final List<DepositWithIndex> deposits =
        depositProvider.getDepositsWithIndex(state, randomEth1Data);

    // the pending Eth1 deposits (deposit_requests_start_index - eth1_deposit_index)
    // we need to process eth1_deposit_index deposit (5) up to 16 (exclusive) so 11 is the
    // expected size
    assertThat(deposits).hasSize(11);
    checkThatDepositProofIsValid(deposits);
    assertThat(deposits.stream().map(DepositWithIndex::deposit))
        .containsExactlyElementsOf(depositProvider.getDeposits(state, randomEth1Data));
  }

  @Test
  void depositsWithFinalizedIndicesGetPrunedFromMap() {
    setup(16);
    Bytes32 finalizedBlockRoot = Bytes32.fromHexString("0x01");
    updateStateEth1DepositIndex(10);
    updateStateEth1DataDepositCount(10);
    mockDepositsFromEth1Block(0, 20);
    final AnchorPoint anchorPoint = mock(AnchorPoint.class);
    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getLatestFinalized()).thenReturn(anchorPoint);
    when(anchorPoint.getState()).thenAnswer(__ -> state);

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(20);

    depositProvider.onNewFinalizedCheckpoint(new Checkpoint(UInt64.ONE, finalizedBlockRoot), false);

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(10);
  }

  @Test
  void shouldDelegateOnEth1BlockToEth1DataCache() {
    setup(16);
    final Bytes32 blockHash = dataStructureUtil.randomBytes32();
    final UInt64 blockNumber = dataStructureUtil.randomUInt64();
    final UInt64 blockTimestamp = dataStructureUtil.randomUInt64();
    depositProvider.onEth1Block(blockNumber, blockHash, blockTimestamp);
    verify(eth1DataCache).onEth1Block(blockNumber, blockHash, blockTimestamp);
  }

  @Test
  void shouldNotifyEth1DataCacheOfDepositBlocks() {
    setup(16);
    final tech.pegasys.teku.ethereum.pow.api.Deposit deposit =
        dataStructureUtil.randomDepositEvent(UInt64.ZERO);
    final DepositsFromBlockEvent event =
        DepositsFromBlockEvent.create(
            dataStructureUtil.randomUInt64(),
            dataStructureUtil.randomBytes32(),
            dataStructureUtil.randomUInt64(),
            Stream.of(deposit));
    depositProvider.onDepositsFromBlock(event);

    depositMerkleTree.add(
        depositUtil
            .convertDepositEventToOperationDeposit(deposit)
            .deposit()
            .getData()
            .hashTreeRoot());
    verify(eth1DataCache)
        .onBlockWithDeposit(
            event.getBlockNumber(),
            new Eth1Data(depositMerkleTree.getRoot(), UInt64.ONE, event.getBlockHash()),
            event.getBlockTimestamp());
  }

  @Test
  void shouldNotThrowMissingDepositsExceptionWhenAllKnownDepositsHaveBeenIncluded() {
    setup(16);
    updateStateEth1DepositIndex(5);
    updateStateEth1DataDepositCount(5);
    mockDepositsFromEth1Block(0, 5);
    assertThat(depositProvider.getDeposits(state, randomEth1Data)).isEmpty();
  }

  @Test
  void shouldThrowMissingDepositsExceptionWhenRequiredDepositsAreNotAvailable() {
    setup(16);
    updateStateEth1DepositIndex(5);
    updateStateEth1DataDepositCount(10);
    assertThatThrownBy(() -> depositProvider.getDeposits(state, randomEth1Data))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("6 to 10");
  }

  @Test
  void shouldThrowMissingDepositsExceptionWhenAllDepositsRequiredForStateNotAvailable() {
    setup(1);
    // To generate a valid proof we need the deposits up to state deposit count
    // So fail even if we could have filled MAX_DEPOSITS
    mockDepositsFromEth1Block(0, 8);
    updateStateEth1DepositIndex(5);
    updateStateEth1DataDepositCount(10);

    assertThatThrownBy(() -> depositProvider.getDeposits(state, randomEth1Data))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("9 to 10");
  }

  @Test
  void shouldThrowWhenAllDepositsRequiredForStateNotAvailable_skippedDeposit() {
    setup(5);
    mockDepositsFromEth1Block(0, 7);
    // Deposit 7 is missing
    mockDepositsFromEth1Block(8, 10);
    updateStateEth1DepositIndex(5);
    updateStateEth1DataDepositCount(10);

    assertThatThrownBy(() -> depositProvider.getDeposits(state, randomEth1Data))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("7 to 8");
  }

  @Test
  void shouldThrowWhenAllDepositsRequiredForStateNotAvailable_skippedDeposits() {
    setup(5);
    mockDepositsFromEth1Block(0, 7);
    // Deposits 7,8 are missing
    mockDepositsFromEth1Block(9, 10);
    updateStateEth1DepositIndex(5);
    updateStateEth1DataDepositCount(10);

    assertThatThrownBy(() -> depositProvider.getDeposits(state, randomEth1Data))
        .isInstanceOf(MissingDepositsException.class)
        .hasMessageContaining("7 to 9");
  }

  @Test
  void whenCallingAvailableDeposits_AllDepositReturned() {
    setup(5);
    updateStateEth1DepositIndex(10);
    mockDepositsFromEth1Block(0, 10);
    List<DepositWithIndex> deposits = depositProvider.getAvailableDeposits();
    assertThat(deposits.size()).isEqualTo(10);
  }

  @Test
  void whenCallingAvailableDepositsAndSomeDepositsAlreadyInState_AllDepositsReturned() {
    setup(10);
    updateStateEth1DepositIndex(2);
    mockDepositsFromEth1Block(0, 10);
    updateStateEth1DataDepositCount(10);
    SszList<Deposit> deposits = depositProvider.getDeposits(state, randomEth1Data);
    assertThat(deposits.size()).isEqualTo(8);
    List<DepositWithIndex> availableDeposits = depositProvider.getAvailableDeposits();
    assertThat(availableDeposits.size()).isEqualTo(10);
  }

  @Test
  void whenCallingAvailableDepositsAndSomeDepositsPruned_AllNotPrunedDepositsReturned() {
    setup(16);
    Bytes32 finalizedBlockRoot = Bytes32.fromHexString("0x01");
    updateStateEth1DepositIndex(10);
    updateStateEth1DataDepositCount(10);
    mockDepositsFromEth1Block(0, 20);
    final AnchorPoint anchorPoint = mock(AnchorPoint.class);
    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getLatestFinalized()).thenReturn(anchorPoint);
    when(anchorPoint.getState()).thenAnswer(__ -> state);

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(20);

    depositProvider.onNewFinalizedCheckpoint(new Checkpoint(UInt64.ONE, finalizedBlockRoot), false);

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(10);
    List<DepositWithIndex> availableDeposits = depositProvider.getAvailableDeposits();
    assertThat(availableDeposits.size()).isEqualTo(10);
  }

  @Test
  void whenCallingForFinalizedSnapshotAndNoFinalizedSnapshotAvailable_EmptyOptionalReturned() {
    setup(16);
    assertThat(depositProvider.getFinalizedDepositTreeSnapshot()).isEmpty();
  }

  @Test
  void whenCallingForFinalizedSnapshotAndSnapshotAvailable_SnapshotReturned() {
    setup(16);
    final Eth1Data eth1Data1 =
        new Eth1Data(
            dataStructureUtil.randomBytes32(),
            UInt64.valueOf(10),
            dataStructureUtil.randomBytes32());
    updateStateEth1Data(eth1Data1);
    Bytes32 finalizedBlockRoot = Bytes32.fromHexString("0x01");
    updateStateEth1DepositIndex(10);
    mockDepositsFromEth1Block(0, 20);
    final AnchorPoint anchorPoint = mock(AnchorPoint.class);
    final UpdatableStore store = mock(UpdatableStore.class);
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getLatestFinalized()).thenReturn(anchorPoint);
    when(anchorPoint.getState()).thenAnswer(__ -> state);

    assertThat(depositProvider.getDepositMapSize()).isEqualTo(20);

    when(eth1DataCache.getEth1DataAndHeight(eq(eth1Data1)))
        .thenReturn(
            Optional.of(new Eth1DataCache.Eth1DataAndHeight(eth1Data1, UInt64.valueOf(20))));
    when(storageUpdateChannel.onFinalizedDepositSnapshot(any())).thenReturn(SafeFuture.COMPLETE);
    depositProvider.onNewFinalizedCheckpoint(new Checkpoint(UInt64.ONE, finalizedBlockRoot), false);

    verify(eth1DataCache, times(1)).getEth1DataAndHeight(eq(eth1Data1));
    verify(eth1DepositStorageChannel, atLeastOnce()).removeDepositEvents();
    assertThat(depositProvider.getDepositMapSize()).isEqualTo(10);
    Optional<DepositTreeSnapshot> finalizedDepositTreeSnapshot =
        depositProvider.getFinalizedDepositTreeSnapshot();
    assertThat(finalizedDepositTreeSnapshot).isNotEmpty();
    assertThat(finalizedDepositTreeSnapshot.get().getDepositCount()).isEqualTo(10);
    assertThat(finalizedDepositTreeSnapshot.get().getExecutionBlockHash())
        .isEqualTo(eth1Data1.getBlockHash());
    assertThat(finalizedDepositTreeSnapshot.get().getExecutionBlockHeight())
        .isEqualTo(UInt64.valueOf(20));

    // when calling next time with the same Eth1Data, shouldn't finalize again
    depositProvider.onNewFinalizedCheckpoint(
        new Checkpoint(UInt64.valueOf(2), dataStructureUtil.randomBytes32()), false);
    verify(eth1DataCache, times(1)).getEth1DataAndHeight(any());
    Optional<DepositTreeSnapshot> finalizedDepositTreeSnapshot2 =
        depositProvider.getFinalizedDepositTreeSnapshot();
    assertThat(finalizedDepositTreeSnapshot2).isEqualTo(finalizedDepositTreeSnapshot);

    // when we have new Eth1Data and all deposits are added to the blocks, cache is called again
    final Eth1Data eth1Data2 =
        new Eth1Data(
            dataStructureUtil.randomBytes32(),
            UInt64.valueOf(20),
            dataStructureUtil.randomBytes32());
    updateStateEth1DepositIndex(20);
    updateStateEth1Data(eth1Data2);
    when(eth1DataCache.getEth1DataAndHeight(eq(eth1Data2)))
        .thenReturn(
            Optional.of(new Eth1DataCache.Eth1DataAndHeight(eth1Data2, UInt64.valueOf(30))));
    depositProvider.onNewFinalizedCheckpoint(
        new Checkpoint(UInt64.valueOf(3), dataStructureUtil.randomBytes32()), false);
    verify(eth1DataCache).getEth1DataAndHeight(eq(eth1Data2));
    Optional<DepositTreeSnapshot> finalizedDepositTreeSnapshot3 =
        depositProvider.getFinalizedDepositTreeSnapshot();
    assertThat(finalizedDepositTreeSnapshot3).isNotEmpty();
    assertThat(finalizedDepositTreeSnapshot3.get().getDepositCount()).isEqualTo(20);
    assertThat(finalizedDepositTreeSnapshot3.get().getExecutionBlockHash())
        .isEqualTo(eth1Data2.getBlockHash());
    assertThat(finalizedDepositTreeSnapshot3.get().getExecutionBlockHeight())
        .isEqualTo(UInt64.valueOf(30));
  }

  private void checkThatDepositProofIsValid(final List<DepositWithIndex> depositsWithIndex) {
    final SpecVersion genesisSpec = spec.getGenesisSpec();
    depositsWithIndex.forEach(
        depositWithIndex ->
            assertThat(
                    genesisSpec
                        .predicates()
                        .isValidMerkleBranch(
                            depositWithIndex.deposit().getData().hashTreeRoot(),
                            depositWithIndex.deposit().getProof(),
                            genesisSpec.getConfig().getDepositContractTreeDepth() + 1,
                            depositWithIndex.index().intValue(),
                            depositMerkleTree.getRoot()))
                .withFailMessage("Expected proof to be valid but was not")
                .isTrue());
  }

  private void createDepositEvents(final int n) {
    allSeenDepositsList =
        IntStream.range(0, n)
            .mapToObj(i -> dataStructureUtil.randomDepositEvent(UInt64.valueOf(i)))
            .collect(Collectors.toList());
  }

  private void mockDepositsFromEth1Block(final int startIndex, final int n) {
    allSeenDepositsList.subList(startIndex, n).stream()
        .map(depositUtil::convertDepositEventToOperationDeposit)
        .map(depositWithIndex -> depositWithIndex.deposit().getData().hashTreeRoot())
        .forEachOrdered(depositMerkleTree::add);

    DepositsFromBlockEvent depositsFromBlockEvent = mock(DepositsFromBlockEvent.class);
    when(depositsFromBlockEvent.getDeposits())
        .thenReturn(allSeenDepositsList.subList(startIndex, startIndex + n));
    when(depositsFromBlockEvent.getBlockHash()).thenReturn(Bytes32.ZERO);
    depositProvider.onDepositsFromBlock(depositsFromBlockEvent);
  }

  private void updateStateEth1Data(final Eth1Data eth1Data) {
    state = state.updated(mutableState -> mutableState.setEth1Data(eth1Data));
  }

  private void updateStateEth1DataDepositCount(final int n) {
    final Eth1Data eth1Data =
        new Eth1Data(
            dataStructureUtil.randomBytes32(),
            UInt64.valueOf(n),
            dataStructureUtil.randomBytes32());
    updateStateEth1Data(eth1Data);
  }

  private void updateStateEth1DepositIndex(final int n) {
    state = state.updated(mutableState -> mutableState.setEth1DepositIndex(UInt64.valueOf(n)));
  }

  private void updateStateDepositRequestsStartIndex(final int n) {
    state =
        state.updated(
            mutableState ->
                MutableBeaconStateElectra.required(mutableState)
                    .setDepositRequestsStartIndex(UInt64.valueOf(n)));
  }
}
