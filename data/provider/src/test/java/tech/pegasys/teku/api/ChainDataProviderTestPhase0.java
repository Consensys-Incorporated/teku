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

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.migrated.BlockHeadersResponse;
import tech.pegasys.teku.api.migrated.StateSyncCommitteesData;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.api.provider.GenesisData;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.util.BlockRewardCalculatorUtil;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.RecentChainData;

public class ChainDataProviderTestPhase0 extends AbstractChainDataProviderTest {

  @Override
  protected Spec getSpec() {
    return TestSpecFactory.createMinimalPhase0();
  }

  @Test
  public void getChainHeads_shouldReturnChainHeads() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final List<ProtoNodeData> chainHeads = provider.getChainHeads();
    assertThat(chainHeads)
        .containsExactly(
            new ProtoNodeData(
                bestBlock.getSlot(),
                blockRoot,
                bestBlock.getParentRoot(),
                bestBlock.getStateRoot(),
                bestBlock.getExecutionBlockNumber().orElse(UInt64.ZERO),
                bestBlock.getExecutionBlockHash().orElse(Bytes32.ZERO),
                ZERO,
                ProtoNodeValidationStatus.VALID,
                spec.calculateBlockCheckpoints(bestBlock.getState()),
                ZERO,
                ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING));
  }

  @Test
  public void getForkChoiceDataV2_shouldReturnFullPayloadStatusBeforeGloas() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    final ForkChoiceDataV2 forkChoiceData = provider.getForkChoiceDataV2();

    assertThat(forkChoiceData.getNodes()).isNotEmpty();
    assertThat(forkChoiceData.getNodes())
        .allSatisfy(
            node ->
                assertThat(node.getPayloadStatus())
                    .isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL));
  }

  @Test
  public void getGenesisTime_shouldThrowIfStoreNotAvailable() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            null,
            mockCombinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);
    assertThatThrownBy(provider::getGenesisTime).isInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getGenesisTime_shouldReturnValueIfStoreAvailable() {
    final UInt64 genesis = beaconStateInternal.getGenesisTime();
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    final UInt64 result = provider.getGenesisTime();
    assertEquals(genesis, result);
  }

  @Test
  public void getGenesisData_shouldThrowIfStoreNotAvailable() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            null,
            mockCombinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    when(mockCombinedChainDataClient.isStoreAvailable()).thenReturn(false);
    assertThatThrownBy(provider::getGenesisData).isInstanceOf(ChainDataUnavailableException.class);
  }

  @Test
  public void getGenesisData_shouldReturnValueIfStoreAvailable() {
    final UInt64 genesisTime = beaconStateInternal.getGenesisTime();
    final Bytes32 genesisValidatorsRoot = beaconStateInternal.getGenesisValidatorsRoot();
    final Bytes4 genesisForkVersion = spec.atEpoch(ZERO).getConfig().getGenesisForkVersion();

    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    final GenesisData result = provider.getGenesisData();
    assertThat(result)
        .isEqualTo(new GenesisData(genesisTime, genesisValidatorsRoot, genesisForkVersion));
  }

  @Test
  public void getBlockAndMetaDataByBlockId_shouldGetHeadBlock()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final SignedBeaconBlock block =
        storageSystem.getChainHead().getSignedBeaconBlock().orElseThrow();
    BlockAndMetaData result = provider.getBlockAndMetaData("head").get().orElseThrow();

    assertThat(result.getData()).isEqualTo(block);
    assertThat(result.isCanonical()).isTrue();
  }

  @Test
  public void getBlockHeaders_shouldGetHeadBlockIfNoParameters() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final SignedBeaconBlock block =
        storageSystem.getChainHead().getSignedBeaconBlock().orElseThrow();
    BlockHeadersResponse results =
        safeJoin(provider.getBlockHeaders(Optional.empty(), Optional.empty()));
    assertThat(results.getData().get(0).getData().getRoot()).isEqualTo(block.getRoot());
  }

  @Test
  public void getBlockHeaders_shouldGetBlockGivenSlot() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final UInt64 slot = combinedChainDataClient.getCurrentSlot();
    BlockHeadersResponse results =
        safeJoin(provider.getBlockHeaders(Optional.empty(), Optional.of(slot)));
    assertThat(results.getData().get(0).getData().getSlot()).isEqualTo(slot);
  }

  @Test
  public void shouldGetBlockHeadersOnEmptyChainHeadSlot() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    final UInt64 headSlot = recentChainData.getHeadSlot();
    storageSystem.chainUpdater().advanceChain(headSlot.plus(1));

    final SafeFuture<BlockHeadersResponse> future =
        provider.getBlockHeaders(Optional.empty(), Optional.empty());
    final BlockAndMetaData header = safeJoin(future).getData().get(0);
    assertThat(header.getData().getSlot()).isEqualTo(headSlot);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorIndex() {

    final BeaconState internalState = data.randomBeaconState(1024);
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    List<Integer> indices =
        provider.getFilteredValidatorList(internalState, List.of("1", "33"), emptySet()).stream()
            .map(v -> v.getIndex().intValue())
            .collect(toList());
    assertThat(indices).containsExactly(1, 33);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorPubkey() {
    final BeaconState internalState = data.randomBeaconState(1024);
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final Bytes48 key = internalState.getValidators().get(12).getPubkeyBytes();
    final String missingKey = data.randomPublicKey().toString();
    List<Bytes48> pubkeys =
        provider
            .getFilteredValidatorList(
                internalState, List.of(key.toHexString(), missingKey), emptySet())
            .stream()
            .map(v -> v.getValidator().getPublicKey().toBytesCompressed())
            .collect(toList());
    assertThat(pubkeys).containsExactly(key);
  }

  @Test
  public void filteredValidatorsList_shouldFilterByValidatorStatus() {
    final BeaconState internalState = data.randomBeaconState(11);
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    assertThat(
            provider.getFilteredValidatorList(
                internalState, emptyList(), Set.of(ValidatorStatus.pending_initialized)))
        .hasSize(11);
    assertThat(
            provider.getFilteredValidatorList(
                internalState, emptyList(), Set.of(ValidatorStatus.active_ongoing)))
        .hasSize(0);
  }

  @Test
  public void getStateCommittees_shouldReturnEmptyIfStateNotFound()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    assertThat(
            provider
                .getStateCommittees(
                    data.randomBytes32().toHexString(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty())
                .get())
        .isEmpty();
  }

  @Test
  public void getCommitteesFromState_shouldNotRequireFilters() {
    final BeaconState internalState = data.randomBeaconState(64);
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    assertThat(
            provider
                .getCommitteesFromState(
                    internalState, Optional.empty(), Optional.empty(), Optional.empty())
                .size())
        .isEqualTo(specConfig.getSlotsPerEpoch());
  }

  @Test
  public void getCommitteesFromState_shouldFilterOnSlot() {
    final BeaconState internalState = data.randomBeaconState(64);
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    assertThat(
            provider
                .getCommitteesFromState(
                    internalState,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(internalState.getSlot()))
                .size())
        .isEqualTo(1);
  }

  @Test
  public void getStateSyncCommittees_shouldReturnEmptyListBeforeAltair() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final BeaconState internalState = data.randomBeaconState();
    when(mockCombinedChainDataClient.getBestState())
        .thenReturn(Optional.of(completedFuture(internalState)));

    final SafeFuture<Optional<ObjectAndMetaData<StateSyncCommitteesData>>> future =
        provider.getStateSyncCommittees("head", Optional.empty());
    assertThatSafeFuture(future)
        .isCompletedWithOptionalContaining(
            addMetaData(new StateSyncCommitteesData(List.of(), List.of()), ZERO));
  }

  @Test
  public void getSyncCommitteeRewardsFromBlockId_slotIsPreAltair() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            new RewardCalculator(spec, new BlockRewardCalculatorUtil(spec)),
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final SafeFuture<Optional<SyncCommitteeRewardData>> future =
        provider.getSyncCommitteeRewardsFromBlockId("head", Set.of());
    assertThat(future).isCompletedExceptionally();
    assertThatThrownBy(future::get).hasCauseInstanceOf(BadRequestException.class);
    assertThatThrownBy(future::get)
        .hasMessageMatching(
            "tech.pegasys.teku.api.exceptions.BadRequestException: "
                + "Slot [0-9]+ is pre altair, and no sync committee information is available");
  }

  @Test
  public void getLightClientBootstrap_shouldReturnEmptyBeforeAltair() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final BeaconState internalState = data.randomBeaconState();

    BeaconBlockHeader expectedBlockHeader = BeaconBlockHeader.fromState(internalState);

    when(mockCombinedChainDataClient.getStateByBlockRoot(eq(expectedBlockHeader.getRoot())))
        .thenReturn(completedFuture(Optional.of(internalState)));

    final SafeFuture<Optional<ObjectAndMetaData<LightClientBootstrap>>> future =
        provider.getLightClientBoostrap(expectedBlockHeader.getRoot());
    assertThatSafeFuture(future).isCompletedWithEmptyOptional();
  }

  @Test
  public void getValidatorBalancesFromState_shouldGetBalances() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final BeaconState internalState = data.randomBeaconState(1024);
    assertThat(provider.getValidatorBalancesFromState(internalState, emptyList()).size())
        .isEqualTo(1024);

    assertThat(
            provider
                .getValidatorBalancesFromState(
                    internalState, List.of("0", "100", "1023", "1024", "1024000"))
                .size())
        .isEqualTo(3);
  }

  @Test
  public void getBlockRoot_shouldReturnRootOfBlock() throws Exception {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final Optional<ObjectAndMetaData<Bytes32>> response = provider.getBlockRoot("head").get();
    assertThat(response).isPresent();
    assertThat(response.get().getData()).isEqualTo(bestBlock.getRoot());
  }

  @Test
  public void getBlockAttestations_shouldReturnAttestationsOfBlock() throws Exception {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    ChainBuilder chainBuilder = storageSystem.chainBuilder();

    ChainBuilder.BlockOptions blockOptions = ChainBuilder.BlockOptions.create();
    AttestationGenerator attestationGenerator =
        new AttestationGenerator(spec, chainBuilder.getValidatorKeys());
    Attestation attestation1 =
        attestationGenerator.validAttestation(bestBlock.toUnsigned(), bestBlock.getSlot());
    Attestation attestation2 =
        attestationGenerator.validAttestation(
            bestBlock.toUnsigned(), bestBlock.getSlot().increment());
    blockOptions.addAttestation(attestation1);
    blockOptions.addAttestation(attestation2);
    SignedBlockAndState newHead =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(bestBlock.getSlot().plus(10), blockOptions);
    storageSystem.chainUpdater().updateBestBlock(newHead);

    final Optional<ObjectAndMetaData<List<Attestation>>> response =
        provider.getBlockAttestations("head").get();
    assertThat(response).isPresent();
    assertThat(response.get().getData()).containsExactly(attestation1, attestation2);
  }

  @Test
  void pathParamMaySupportAltair_shouldBeFalseIfAltairNotSupported() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    assertThat(provider.stateParameterMaySupportAltair("genesis")).isFalse();
  }

  @Test
  void getValidatorInclusionAtEpoch_shouldThrowServiceUnavailableIfEpochNotFound() {
    final RecentChainData recentChainData1 = mock(RecentChainData.class);
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData1,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    when(recentChainData1.getCurrentEpoch()).thenReturn(Optional.empty());
    assertThatThrownBy(() -> provider.getValidatorInclusionAtEpoch(data.randomEpoch()))
        .isInstanceOf(ServiceUnavailableException.class);
  }

  @Test
  void getValidatorInclusionAtEpoch_shouldThrowIllegalArgForCurrentOrFutureEpoch() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    assertThatThrownBy(() -> provider.getValidatorInclusionAtEpoch(UInt64.valueOf(3)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> provider.getValidatorInclusionAtEpoch(UInt64.valueOf(4)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> provider.getValidatorInclusionAtEpoch(UInt64.MAX_VALUE))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void getValidatorInclusionAtEpoch_shouldReturnStateForPreviousEpoch()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            mockCombinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    // expect to see the last slot of epoch requested, so 3 * 8 - 1 (23)
    when(mockCombinedChainDataClient.getChainHead())
        .thenReturn(combinedChainDataClient.getChainHead());
    when(mockCombinedChainDataClient.getStateAtSlotExact(eq(UInt64.valueOf(23)), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    provider.getValidatorInclusionAtEpoch(UInt64.valueOf(2)).get();
    verify(mockCombinedChainDataClient).getStateAtSlotExact(eq(UInt64.valueOf(23)), any());
  }

  @Test
  void getFinalizedBlockRoot_shouldReturnBlockRootWhenFinalized()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    Optional<Bytes32> finalizedBlockRoot = provider.getFinalizedBlockRoot(UInt64.valueOf(24)).get();
    assertThat(finalizedBlockRoot).isPresent();
  }

  @Test
  void getFinalizedBlockRoot_shouldReturnEmptyWhenBlockNotFinalized()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    Optional<Bytes32> finalizedBlockRoot = provider.getFinalizedBlockRoot(UInt64.valueOf(1)).get();
    assertThat(finalizedBlockRoot).isEmpty();
  }

  @Test
  void getExpectedWithdrawalsFailsPreCapella() {
    final ChainDataProvider chainDataProvider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    assertThatThrownBy(
            () ->
                chainDataProvider.getExpectedWithdrawalsFromState(
                    data.randomBeaconState(ONE), Optional.empty()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("pre-capella");
  }

  @ParameterizedTest(name = "given slot={0} when epoch={1} then randao={2}")
  @MethodSource("getRandaoIndexCases")
  void getRandaoIndex(
      final int stateSlot, final int queryEpoch, final Optional<Bytes32> maybeRandao) {
    final BeaconState state = data.randomBeaconState(UInt64.valueOf(stateSlot));
    final UInt64 epoch = UInt64.valueOf(queryEpoch);
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    assertThat(provider.getRandaoAtEpochFromState(state, Optional.of(epoch)))
        .isEqualTo(maybeRandao);
  }

  public static Stream<Arguments> getRandaoIndexCases() {
    final int epochsPerHistoricalVector = 64;
    final int slotsPerEpoch = 8;
    final int currentEpoch = 2048;
    final int stateSlot = currentEpoch * slotsPerEpoch; // first slot of epoch 100
    ArrayList<Arguments> args = new ArrayList<>();
    args.add(Arguments.of(stateSlot, currentEpoch - epochsPerHistoricalVector, Optional.empty()));
    args.add(Arguments.of(stateSlot, currentEpoch + 1, Optional.empty()));
    final Optional<Bytes32> randao =
        Optional.of(
            Bytes32.fromHexString(
                "0xcc9b03923c85d9ce8eba32968977d98d668788d1c2f0e4f3a79facfcd8247794"));
    final Optional<Bytes32> randao2 =
        Optional.of(
            Bytes32.fromHexString(
                "0x933815925ca23b00aef1fc130edd9b26547e8b1b9f966ac51e1ce2a3480251f4"));
    args.add(Arguments.of(stateSlot, currentEpoch - epochsPerHistoricalVector + 1, randao2));
    args.add(Arguments.of(stateSlot, currentEpoch, randao));
    args.add(Arguments.of(7, 0, randao));
    args.add(Arguments.of(9, 0, randao));
    args.add(Arguments.of(9, 1, randao2));
    return args.stream();
  }

  private <T> ObjectAndMetaData<T> addMetaData(final T expected, final UInt64 slot) {
    return new ObjectAndMetaData<>(
        expected,
        spec.atSlot(slot).getMilestone(),
        false,
        true,
        combinedChainDataClient.isFinalized(slot));
  }
}
