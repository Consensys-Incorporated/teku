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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.api.blockselector.BlockSelector;
import tech.pegasys.teku.api.exceptions.BadRequestException;
import tech.pegasys.teku.api.exceptions.ServiceUnavailableException;
import tech.pegasys.teku.api.fulu.ColumnCustodyAtSlot;
import tech.pegasys.teku.api.migrated.BlockHeadersResponse;
import tech.pegasys.teku.api.migrated.BlockRewardData;
import tech.pegasys.teku.api.migrated.BuilderStatus;
import tech.pegasys.teku.api.migrated.StateBuilderData;
import tech.pegasys.teku.api.migrated.StateSyncCommitteesData;
import tech.pegasys.teku.api.migrated.StateValidatorIdentity;
import tech.pegasys.teku.api.migrated.SyncCommitteeRewardData;
import tech.pegasys.teku.api.provider.GenesisData;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.bytes.Bytes20;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeValidationStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.metadata.BlockAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.DataColumnSidecarsAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.StateAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingConsolidation;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingDeposit;
import tech.pegasys.teku.spec.datastructures.state.versions.electra.PendingPartialWithdrawal;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.generator.AttestationGenerator;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.util.BeaconStateBuilderCapella;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.storage.client.ChainDataUnavailableException;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.protoarray.ProtoNode;

public class ChainDataProviderTest extends AbstractChainDataProviderTest {

  @Override
  protected Spec getSpec() {
    return TestSpecFactory.createMinimalCapella();
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
                bestBlock.getExecutionBlockNumber().orElse(ProtoNode.NO_EXECUTION_BLOCK_NUMBER),
                bestBlock.getExecutionBlockHash().orElse(ProtoNode.NO_EXECUTION_BLOCK_HASH),
                bestBlock.getExecutionGasLimit().orElse(ProtoNode.NO_EXECUTION_GAS_LIMIT),
                ProtoNodeValidationStatus.VALID,
                spec.calculateBlockCheckpoints(bestBlock.getState()),
                ZERO,
                ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING));
  }

  @Test
  public void getForkChoiceDataV2_shouldReturnGloasPayloadStatusAndPtcCounts() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final CombinedChainDataClient chainDataClient = mock(CombinedChainDataClient.class);
    final RecentChainData recentChainData = mock(RecentChainData.class);
    final ReadOnlyForkChoiceStrategy forkChoiceStrategy = mock(ReadOnlyForkChoiceStrategy.class);
    final Checkpoint justifiedCheckpoint =
        new Checkpoint(UInt64.valueOf(1), Bytes32.fromHexString("0x1111"));
    final Checkpoint finalizedCheckpoint =
        new Checkpoint(UInt64.ZERO, Bytes32.fromHexString("0x2222"));
    final Bytes32 blockRoot = Bytes32.fromHexString("0x3333");
    final ProtoNodeData node =
        new ProtoNodeData(
            UInt64.ONE,
            blockRoot,
            Bytes32.fromHexString("0x4444"),
            Bytes32.fromHexString("0x5555"),
            UInt64.valueOf(42),
            Bytes32.fromHexString("0x6666"),
            ZERO,
            ProtoNodeValidationStatus.OPTIMISTIC,
            new BlockCheckpoints(
                justifiedCheckpoint, finalizedCheckpoint, justifiedCheckpoint, finalizedCheckpoint),
            UInt64.valueOf(64),
            ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);
    final ChainDataProvider provider =
        new ChainDataProvider(
            gloasSpec,
            recentChainData,
            chainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    when(chainDataClient.isStoreAvailable()).thenReturn(true);
    when(recentChainData.getJustifiedCheckpoint()).thenReturn(Optional.of(justifiedCheckpoint));
    when(recentChainData.getFinalizedCheckpoint()).thenReturn(Optional.of(finalizedCheckpoint));
    when(recentChainData.getForkChoiceStrategy()).thenReturn(Optional.of(forkChoiceStrategy));
    when(forkChoiceStrategy.getBlockData()).thenReturn(List.of(node));
    when(forkChoiceStrategy.getPayloadAttesterCount(blockRoot)).thenReturn(UInt64.valueOf(4));
    when(forkChoiceStrategy.getPayloadAvailabilityYesCount(blockRoot))
        .thenReturn(UInt64.valueOf(2));
    when(forkChoiceStrategy.getPayloadDataAvailabilityYesCount(blockRoot))
        .thenReturn(UInt64.valueOf(3));

    final ForkChoiceDataV2 forkChoiceData = provider.getForkChoiceDataV2();

    assertThat(forkChoiceData.getJustifiedCheckpoint()).isEqualTo(justifiedCheckpoint);
    assertThat(forkChoiceData.getFinalizedCheckpoint()).isEqualTo(finalizedCheckpoint);
    assertThat(forkChoiceData.getNodes()).hasSize(1);
    final ForkChoiceNodeDataV2 forkChoiceNode = forkChoiceData.getNodes().get(0);
    assertThat(forkChoiceNode.getNode()).isEqualTo(node);
    assertThat(forkChoiceNode.getPayloadStatus())
        .isEqualTo(ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING);
    assertThat(forkChoiceNode.getPayloadAttesterCount()).isEqualTo(UInt64.valueOf(4));
    assertThat(forkChoiceNode.getPayloadAvailabilityYesCount()).isEqualTo(UInt64.valueOf(2));
    assertThat(forkChoiceNode.getPayloadDataAvailabilityYesCount()).isEqualTo(UInt64.valueOf(3));
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
  public void getStateSyncCommittees_shouldGetCommittees() {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    final List<UInt64> committeeIndices =
        List.of(UInt64.valueOf(6), UInt64.valueOf(9), UInt64.valueOf(0));

    final SafeFuture<Optional<ObjectAndMetaData<StateSyncCommitteesData>>> future =
        provider.getStateSyncCommittees("head", Optional.empty());
    assertThatSafeFuture(future)
        .isCompletedWithOptionalContaining(
            new ObjectAndMetaData<>(
                new StateSyncCommitteesData(committeeIndices, List.of(committeeIndices)),
                SpecMilestone.CAPELLA,
                false,
                true,
                false));
  }

  @Test
  public void getStateSyncCommittees_shouldRejectFarFutureEpoch() {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    final SafeFuture<Optional<ObjectAndMetaData<StateSyncCommitteesData>>> future =
        provider.getStateSyncCommittees("head", Optional.of(UInt64.valueOf("1024000")));
    SafeFutureAssert.assertThatSafeFuture(future)
        .isCompletedExceptionallyWith(IllegalArgumentException.class);
  }

  @Test
  public void getSyncCommitteeRewardsFromBlockId_noSpecifiedValidators() {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    final Set<String> validators = Set.of();
    when(rewardCalculatorMock.getSyncCommitteeRewardData(eq(validators), any(), any()))
        .thenReturn(mock(SyncCommitteeRewardData.class));
    final SafeFuture<Optional<SyncCommitteeRewardData>> future =
        provider.getSyncCommitteeRewardsFromBlockId("head", Set.of());
    verify(rewardCalculatorMock).getSyncCommitteeRewardData(eq(validators), any(), any());
    assertThat(future).isCompleted();
  }

  @Test
  public void getSyncCommitteeRewardsFromBlockId_specifyValidators() {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    final Set<String> validators = Set.of("0", "9");
    when(rewardCalculatorMock.getSyncCommitteeRewardData(eq(validators), any(), any()))
        .thenReturn(mock(SyncCommitteeRewardData.class));
    final SafeFuture<Optional<SyncCommitteeRewardData>> future =
        provider.getSyncCommitteeRewardsFromBlockId("head", validators);
    verify(rewardCalculatorMock).getSyncCommitteeRewardData(eq(validators), any(), any());
    assertThat(future).isCompleted();
  }

  @Test
  public void getSyncCommitteeRewardsFromBlockId_emptyBlockAndMetaData() {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    when(mockCombinedChainDataClient.getChainHead()).thenReturn(Optional.empty());
    final SafeFuture<Optional<SyncCommitteeRewardData>> future =
        provider.getSyncCommitteeRewardsFromBlockId("head", Set.of());
    SafeFutureAssert.assertThatSafeFuture(future).isCompletedWithEmptyOptional();
    verifyNoMoreInteractions(rewardCalculatorMock);
  }

  @Test
  public void getBlockRewardsFromBlockId_shouldCheckStateAndBlockAreHandled()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    when(mockCombinedChainDataClient.getStateAtSlotExact(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SafeFuture<Optional<ObjectAndMetaData<BlockRewardData>>> future =
        provider.getBlockRewardsFromBlockId("head");

    final Optional<BlockAndMetaData> blockAndMetadata = provider.getBlockAndMetaData("head").get();
    assertThat(blockAndMetadata).isNotEmpty();
    verify(mockCombinedChainDataClient, times(1))
        .getStateAtSlotExact(blockAndMetadata.get().getData().getSlot().decrement());
    SafeFutureAssert.assertThatSafeFuture(future).isCompletedWithEmptyOptional();
    verifyNoMoreInteractions(rewardCalculatorMock);
  }

  @Test
  public void getBlockRewardsFromBlockId_shouldCallThrough()
      throws ExecutionException, InterruptedException {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    when(mockCombinedChainDataClient.getStateAtSlotExact(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(beaconStateInternal)));

    final SafeFuture<Optional<ObjectAndMetaData<BlockRewardData>>> future =
        provider.getBlockRewardsFromBlockId("head");

    final Optional<BlockAndMetaData> blockAndMetadata = provider.getBlockAndMetaData("head").get();
    assertThat(blockAndMetadata).isNotEmpty();
    verify(mockCombinedChainDataClient, times(1))
        .getStateAtSlotExact(blockAndMetadata.get().getData().getSlot().decrement());
    verify(rewardCalculatorMock, times(1))
        .getBlockRewardDataAndMetaData(eq(blockAndMetadata.get()), any());
    assertThat(future).isCompleted();
  }

  @Test
  public void getBlockRewardsFromBlockId_shouldReturnEmptyIfBlockNotFound() {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    final String slot = "1122334455";
    when(mockCombinedChainDataClient.getBlockAtSlotExact(eq(UInt64.valueOf(slot)), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    final SafeFuture<Optional<ObjectAndMetaData<BlockRewardData>>> future =
        provider.getBlockRewardsFromBlockId(slot);
    SafeFutureAssert.assertThatSafeFuture(future).isCompletedWithEmptyOptional();
    verifyNoMoreInteractions(rewardCalculatorMock);
  }

  @Test
  public void getAttestationRewardsAtEpoch_shouldFailForPreAltair() {
    final Spec phase0Spec = TestSpecFactory.createMinimalPhase0();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(phase0Spec);
    final ChainDataProvider chainDataProvider = setupBySpec(phase0Spec, dataStructureUtil);

    assertThatThrownBy(() -> chainDataProvider.calculateAttestationRewardsAtEpoch(ONE, List.of()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Can't calculate attestation rewards for for epoch 1 pre Altair");
  }

  @Test
  public void getAttestationRewardsAtEpoch_shouldThrowBadRequestException() {
    final ChainDataProvider provider = setupBySpec(spec, data);
    mockBlockSelectorFactory();

    when(mockCombinedChainDataClient.getStateAtSlotExact(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThat(provider.calculateAttestationRewardsAtEpoch(ONE, List.of()))
        .isCompletedExceptionally()
        .isNotCancelled()
        .failsWithin(1, TimeUnit.SECONDS)
        .withThrowableOfType(ExecutionException.class)
        .withCauseInstanceOf(BadRequestException.class);
  }

  @Test
  public void getAttestationRewardsAtEpoch_shouldUseStateFromSlotAtEndOfNextEpoch() {
    final ChainDataProvider provider = setupBySpec(spec, data);
    mockBlockSelectorFactory();

    when(mockCombinedChainDataClient.getStateAtSlotExact(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(data.randomBeaconState())));

    final UInt64 queriedEpoch = ONE;
    final UInt64 expectedSlot = spec.computeStartSlotAtEpoch(queriedEpoch.plus(2L)).minus(1);
    assertThat(provider.calculateAttestationRewardsAtEpoch(queriedEpoch, List.of())).isCompleted();

    verify(stateSelectorFactory).slotSelector(expectedSlot);
  }

  @Test
  void getColumnCustodyAtSlot_withProofsPresent() {
    final Spec fulu = TestSpecFactory.createMinimalFulu();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(fulu);
    final ChainDataProvider chainDataProvider = setupBySpec(fulu, dataStructureUtil, 16);
    final BlockAndMetaData blockAndMetaData =
        new BlockAndMetaData(
            dataStructureUtil.randomSignedBeaconBlockWithCommitments(9),
            SpecMilestone.FULU,
            false,
            false,
            false);
    final DataColumnSidecarsAndMetaData dataColumnSidecarsAndMetaData =
        new DataColumnSidecarsAndMetaData(
            dataStructureUtil.randomDataColumnSidecars(ONE, 4),
            SpecMilestone.FULU,
            false,
            false,
            false);
    final SafeFuture<Optional<BlockAndMetaData>> futureMaybeBlockAndMetadata =
        SafeFuture.completedFuture(Optional.of(blockAndMetaData));
    final SafeFuture<Optional<DataColumnSidecarsAndMetaData>> futureMaybeDataColumnSidecars =
        SafeFuture.completedFuture(Optional.of(dataColumnSidecarsAndMetaData));

    final SafeFuture<Optional<ColumnCustodyAtSlot>> future =
        chainDataProvider.getColumnCustodyFromBlock(
            futureMaybeBlockAndMetadata, futureMaybeDataColumnSidecars);
    final Optional<ColumnCustodyAtSlot> maybeCustody = future.getImmediately();
    assertThat(maybeCustody).isPresent();
    assertThat(maybeCustody)
        .contains(
            new ColumnCustodyAtSlot(
                Optional.of(blockAndMetaData.getData().getRoot()),
                List.of(ZERO, ONE, UInt64.valueOf(2), UInt64.valueOf(3)),
                Optional.of(9)));
  }

  @Test
  public void getAttestationRewardsAtEpoch_shouldCallEpochAttestationRewardCalculator() {
    final UInt64 queriedEpoch = ONE;
    final UInt64 expectedSlot = spec.computeStartSlotAtEpoch(queriedEpoch.plus(2L)).minus(1);

    final Spec spySpec = spy(spec);
    final SpecVersion spySpecVersion = spy(spec.atSlot(expectedSlot));
    final EpochProcessor spyEpochProcessor = spy(spySpecVersion.getEpochProcessor());
    doReturn(spySpecVersion).when(spySpec).atSlot(eq(expectedSlot));
    doReturn(spyEpochProcessor).when(spySpecVersion).getEpochProcessor();
    final ChainDataProvider provider = setupBySpec(spySpec, data);
    mockBlockSelectorFactory();

    when(mockCombinedChainDataClient.getStateAtSlotExact(any(), any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(data.randomBeaconState())));

    assertThat(provider.calculateAttestationRewardsAtEpoch(queriedEpoch, List.of()))
        .isCompletedWithValueMatching(Optional::isPresent);

    /*
    Because we instantiate EpochAttestationRewardsCalculator within ChainDataProvider, we are using this "trick" to
    check that we are correctly doing the call-chain: ChainDataProvider -> EpochAttestationRewardsCalculator ->
    EpochProcessor#getRewardAndPenaltyDeltas()
     */
    verify(spyEpochProcessor).getRewardAndPenaltyDeltas(any(), any(), any());
  }

  private void mockBlockSelectorFactory() {
    final BlockAndMetaData blockAndMetaData = mock(BlockAndMetaData.class);
    when(blockAndMetaData.isExecutionOptimistic()).thenReturn(false);
    when(blockAndMetaData.isFinalized()).thenReturn(true);
    when(blockAndMetaData.getData()).thenReturn(data.randomSignedBeaconBlock());
    final BlockSelector blockSelector = mock(BlockSelector.class);
    when(blockSelector.getBlock())
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockAndMetaData)));
    doReturn(blockSelector).when(blockSelectorFactory).createSelectorForBlockId(any());
  }

  @Test
  public void getLightClientBootstrap_shouldGetBootstrap() {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);

    final SignedBeaconBlock candidate = data.randomSignedBeaconBlock(1);
    final BeaconState internalState =
        data.randomBeaconState(ONE)
            .updated(
                mutableState ->
                    mutableState.setLatestBlockHeader(
                        new BeaconBlockHeader(
                            candidate.getSlot(),
                            candidate.getMessage().getProposerIndex(),
                            candidate.getParentRoot(),
                            Bytes32.ZERO,
                            candidate.getMessage().getBodyRoot())));
    final SignedBeaconBlock block =
        SignedBeaconBlock.create(
            spec,
            candidate.getMessage().withStateRoot(internalState.hashTreeRoot()),
            candidate.getSignature());
    final BeaconBlockHeader expectedBlockHeader = BeaconBlockHeader.fromState(internalState);

    when(mockCombinedChainDataClient.getStateByBlockRoot(eq(block.getRoot())))
        .thenReturn(completedFuture(Optional.of(internalState)));
    when(mockCombinedChainDataClient.getBlockByBlockRoot(eq(block.getRoot())))
        .thenReturn(completedFuture(Optional.of(block)));

    final SafeFuture<Optional<ObjectAndMetaData<LightClientBootstrap>>> future =
        provider.getLightClientBoostrap(block.getRoot());

    LightClientBootstrap bootstrap = safeJoin(future).orElseThrow().getData();

    assertThat(bootstrap.getLightClientHeader().getBeacon()).isEqualTo(expectedBlockHeader);
    assertThat(bootstrap.getCurrentSyncCommittee())
        .isEqualTo(BeaconStateAltair.required(internalState).getCurrentSyncCommittee());
  }

  @Test
  public void getLightClientBootstrap_shouldReturnEmptyWhenBlockNotFound() {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    final BeaconState internalState = getHeadState();

    BeaconBlockHeader expectedBlockHeader = BeaconBlockHeader.fromState(internalState);

    when(mockCombinedChainDataClient.getStateByBlockRoot(any()))
        .thenReturn(completedFuture(Optional.empty()));
    when(mockCombinedChainDataClient.getBlockByBlockRoot(any()))
        .thenReturn(completedFuture(Optional.empty()));

    final SafeFuture<Optional<ObjectAndMetaData<LightClientBootstrap>>> future =
        provider.getLightClientBoostrap(expectedBlockHeader.getRoot());
    assertThatSafeFuture(future).isCompletedWithEmptyOptional();
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
  public void getValidatorIdentitiesFromState_shouldGetIdentities() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final BeaconState internalState = data.randomBeaconState(8);
    final Validator validator = internalState.getValidators().get(0);
    final StateValidatorIdentity identity =
        StateValidatorIdentity.create(
            ZERO, validator.getPublicKey(), validator.getActivationEpoch());

    assertThat(provider.getValidatorIdentitiesFromState(internalState, emptyList()).size())
        .isEqualTo(8);

    final SszList<StateValidatorIdentity> validatorIdentitiesFromState =
        provider.getValidatorIdentitiesFromState(internalState, List.of("0"));
    assertThat(validatorIdentitiesFromState.size()).isEqualTo(1);
    assertThat(validatorIdentitiesFromState.get(0)).isEqualTo(identity);
  }

  @Test
  public void getBuildersFromState_shouldReturnAllBuildersWithDerivedStatuses() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasData = new DataStructureUtil(gloasSpec);
    final ChainDataProvider provider =
        new ChainDataProvider(
            gloasSpec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    final Builder pendingBuilder =
        gloasData
            .builderBuilder()
            .depositEpoch(UInt64.valueOf(5))
            .withdrawableEpoch(SpecConfig.FAR_FUTURE_EPOCH)
            .build();
    final Builder activeBuilder =
        gloasData
            .builderBuilder()
            .depositEpoch(ONE)
            .withdrawableEpoch(SpecConfig.FAR_FUTURE_EPOCH)
            .build();
    final Builder exitedBuilder =
        gloasData.builderBuilder().depositEpoch(ONE).withdrawableEpoch(UInt64.valueOf(6)).build();
    final BeaconStateGloas state =
        createGloasStateWithBuilders(
            gloasData, UInt64.valueOf(3), pendingBuilder, activeBuilder, exitedBuilder);

    final List<StateBuilderData> builders =
        provider.getBuildersFromState(state, List.of(), List.of());

    assertThat(builders.stream().map(StateBuilderData::index).toList())
        .containsExactly(ZERO, ONE, UInt64.valueOf(2));
    assertThat(builders.stream().map(StateBuilderData::builder).toList())
        .containsExactly(pendingBuilder, activeBuilder, exitedBuilder);
  }

  @Test
  public void getBuildersFromState_shouldFilterByBuilderStatus() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasData = new DataStructureUtil(gloasSpec);
    final ChainDataProvider provider =
        new ChainDataProvider(
            gloasSpec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    final Builder pendingBuilder =
        gloasData
            .builderBuilder()
            .depositEpoch(UInt64.valueOf(5))
            .withdrawableEpoch(SpecConfig.FAR_FUTURE_EPOCH)
            .build();
    final Builder activeBuilder =
        gloasData
            .builderBuilder()
            .depositEpoch(ONE)
            .withdrawableEpoch(SpecConfig.FAR_FUTURE_EPOCH)
            .build();
    final Builder exitedBuilder =
        gloasData.builderBuilder().depositEpoch(ONE).withdrawableEpoch(UInt64.valueOf(6)).build();
    final BeaconStateGloas state =
        createGloasStateWithBuilders(
            gloasData, UInt64.valueOf(3), pendingBuilder, activeBuilder, exitedBuilder);

    final List<StateBuilderData> builders =
        provider.getBuildersFromState(
            state, List.of(), List.of(BuilderStatus.ACTIVE, BuilderStatus.EXITED));

    assertThat(builders.stream().map(StateBuilderData::index).toList())
        .containsExactly(ONE, UInt64.valueOf(2));
    assertThat(builders.stream().map(StateBuilderData::builderStatus).toList())
        .containsExactly(BuilderStatus.ACTIVE, BuilderStatus.EXITED);
    assertThat(builders.stream().map(StateBuilderData::builder).toList())
        .containsExactly(activeBuilder, exitedBuilder);
  }

  @Test
  public void getBuildersFromState_shouldFilterByBuilderIndexAndPubkey() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasData = new DataStructureUtil(gloasSpec);
    final ChainDataProvider provider =
        new ChainDataProvider(
            gloasSpec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final Builder builder0 = gloasData.randomBuilder();
    final Builder builder1 = gloasData.randomBuilder();
    final Builder builder2 = gloasData.randomBuilder();
    final BeaconStateGloas state =
        createGloasStateWithBuilders(gloasData, UInt64.valueOf(3), builder0, builder1, builder2);

    final List<StateBuilderData> builders =
        provider.getBuildersFromState(
            state, List.of("2", builder0.getPublicKey().toString(), "12345"), List.of());

    assertThat(builders.stream().map(StateBuilderData::index).toList())
        .containsExactly(UInt64.valueOf(2), ZERO);
    assertThat(builders.stream().map(StateBuilderData::builder).toList())
        .containsExactly(builder2, builder0);
  }

  @Test
  public void getBuildersFromState_shouldRejectInvalidBuilderId() {
    final Spec gloasSpec = TestSpecFactory.createMinimalGloas();
    final DataStructureUtil gloasData = new DataStructureUtil(gloasSpec);
    final ChainDataProvider provider =
        new ChainDataProvider(
            gloasSpec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final BeaconStateGloas state =
        createGloasStateWithBuilders(gloasData, UInt64.valueOf(3), gloasData.randomBuilder());

    assertThatThrownBy(
            () -> provider.getBuildersFromState(state, List.of("not-a-builder"), List.of()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("Invalid builder: not-a-builder");
  }

  @Test
  public void getBuildersFromState_shouldRejectPreGloasState() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));

    assertThatThrownBy(
            () -> provider.getBuildersFromState(data.randomBeaconState(), List.of(), List.of()))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("prior to GLOAS");
  }

  private BeaconStateGloas createGloasStateWithBuilders(
      final DataStructureUtil gloasData, final UInt64 finalizedEpoch, final Builder... builders) {
    return gloasData
        .stateBuilderGloas(0, 0, 0)
        .builders(
            BeaconStateSchemaGloas.required(gloasData.getBeaconStateSchema())
                .getBuildersSchema()
                .of(builders))
        .setFinalizedCheckpointToEpoch(finalizedEpoch)
        .build();
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
  void pathParamMaySupportAltair_shouldBeTrueIfAltairSupported() {
    final ChainDataProvider provider = setupBySpec(spec, data, 16);
    assertThat(provider.stateParameterMaySupportAltair("genesis")).isTrue();
    assertThat(provider.stateParameterMaySupportAltair("1")).isTrue();
    assertThat(provider.stateParameterMaySupportAltair("0x00")).isTrue();
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
  void checkMinimumMilestone_throwsCorrectMessage() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final Optional<StateAndMetaData> maybeState =
        Optional.of(
            new StateAndMetaData(
                data.randomBeaconState(), SpecMilestone.PHASE0, false, true, true));
    assertThatThrownBy(
            () -> provider.checkMinimumMilestone(maybeState, SpecMilestone.ELECTRA, "badbeef"))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("badbeef");
  }

  @Test
  void checkMinimumMilestone_checksMetadataMilestone() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    final Optional<StateAndMetaData> maybeState =
        Optional.of(
            new StateAndMetaData(
                data.randomBeaconState(), SpecMilestone.ELECTRA, false, true, true));
    assertDoesNotThrow(
        () -> provider.checkMinimumMilestone(maybeState, SpecMilestone.ELECTRA, "badbeef"));
  }

  @Test
  void checkMinimumMilestone_ignoresEmptyObjects() {
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            combinedChainDataClient,
            rewardCalculatorMock,
            mockBlobSidecarReconstructionProvider,
            mockBlobReconstructionProvider,
            new LightClientUpdateStore(spec));
    assertDoesNotThrow(
        () -> provider.checkMinimumMilestone(Optional.empty(), SpecMilestone.ELECTRA, "badbeef"));
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
  void getExpectedWithdrawalsFailsForHistoricRequest() {
    final Spec capella = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(capella);
    final ChainDataProvider chainDataProvider = setupBySpec(capella, dataStructureUtil, 16);
    assertThatThrownBy(
            () ->
                chainDataProvider.getExpectedWithdrawalsFromState(
                    dataStructureUtil.randomBeaconState(ONE), Optional.of(ONE)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("historic");
  }

  @Test
  void getPendingConsolidations_shouldReturnStatePendingConsolidations() {
    final Spec electra = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(electra);
    final ChainDataProvider chainDataProvider = setupBySpec(electra, dataStructureUtil, 16);

    final StateAndMetaData stateAndMetaData =
        getTestDataWithPartialConsolidations(dataStructureUtil, 1);

    final Optional<ObjectAndMetaData<SszList<PendingConsolidation>>> data =
        chainDataProvider.getPendingConsolidations(Optional.of(stateAndMetaData));

    assertThat(data).isPresent();
    assertThat(data.get().getData())
        .isEqualTo(
            stateAndMetaData.getData().toVersionElectra().orElseThrow().getPendingConsolidations());
  }

  @Test
  void getPendingPartialConsolidations_canBeEmpty() {
    final Spec electra = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(electra);
    final ChainDataProvider chainDataProvider = setupBySpec(electra, dataStructureUtil, 16);

    final StateAndMetaData stateAndMetaData = getTestDataWithDeposits(dataStructureUtil, 0);

    final Optional<ObjectAndMetaData<SszList<PendingConsolidation>>> data =
        chainDataProvider.getPendingConsolidations(Optional.of(stateAndMetaData));

    assertThat(data).isPresent();
    assertThat(data.get().getData())
        .isEqualTo(
            stateAndMetaData.getData().toVersionElectra().orElseThrow().getPendingConsolidations());
  }

  @Test
  void getStatePendingPartialWithdrawals_shouldReturnPartialWithdrawals() {
    final Spec electra = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(electra);
    final ChainDataProvider chainDataProvider = setupBySpec(electra, dataStructureUtil, 16);

    final StateAndMetaData stateAndMetaData =
        getTestDataWithPartialWithdrawals(dataStructureUtil, 1);

    final Optional<ObjectAndMetaData<SszList<PendingPartialWithdrawal>>> data =
        chainDataProvider.getPendingPartialWithdrawals(Optional.of(stateAndMetaData));

    assertThat(data).isPresent();
    assertThat(data.get().getData())
        .isEqualTo(
            stateAndMetaData
                .getData()
                .toVersionElectra()
                .orElseThrow()
                .getPendingPartialWithdrawals());
  }

  @Test
  void getStatePendingPartialWithdrawals_canBeEmpty() {
    final Spec electra = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(electra);
    final ChainDataProvider chainDataProvider = setupBySpec(electra, dataStructureUtil, 16);

    final StateAndMetaData stateAndMetaData =
        getTestDataWithPartialWithdrawals(dataStructureUtil, 0);

    final Optional<ObjectAndMetaData<SszList<PendingPartialWithdrawal>>> data =
        chainDataProvider.getPendingPartialWithdrawals(Optional.of(stateAndMetaData));

    assertThat(data).isPresent();
    assertThat(data.get().getData())
        .isEqualTo(
            stateAndMetaData
                .getData()
                .toVersionElectra()
                .orElseThrow()
                .getPendingPartialWithdrawals());
  }

  @Test
  void getPendingDeposits_shouldReturnPendingDeposits() {
    final Spec electra = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(electra);
    final ChainDataProvider chainDataProvider = setupBySpec(electra, dataStructureUtil, 16);

    final StateAndMetaData stateAndMetaData = getTestDataWithDeposits(dataStructureUtil, 1);

    final Optional<ObjectAndMetaData<SszList<PendingDeposit>>> data =
        chainDataProvider.getPendingDeposits(Optional.of(stateAndMetaData));

    assertThat(data).isPresent();
    assertThat(data.get().getData())
        .isEqualTo(
            stateAndMetaData.getData().toVersionElectra().orElseThrow().getPendingDeposits());
  }

  @Test
  void getPendingPartialDeposits_canBeEmpty() {
    final Spec electra = TestSpecFactory.createMinimalElectra();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(electra);
    final ChainDataProvider chainDataProvider = setupBySpec(electra, dataStructureUtil, 16);

    final StateAndMetaData stateAndMetaData = getTestDataWithDeposits(dataStructureUtil, 0);

    final Optional<ObjectAndMetaData<SszList<PendingDeposit>>> data =
        chainDataProvider.getPendingDeposits(Optional.of(stateAndMetaData));

    assertThat(data).isPresent();
    assertThat(data.get().getData())
        .isEqualTo(
            stateAndMetaData.getData().toVersionElectra().orElseThrow().getPendingDeposits());
  }

  private StateAndMetaData getTestDataWithDeposits(
      final DataStructureUtil dataStructureUtil, final int depositsCount) {
    final BeaconStateElectra state =
        dataStructureUtil.randomBeaconState().toVersionElectra().orElseThrow();
    final SszList<PendingDeposit> pendingDeposits =
        switch (depositsCount) {
          case 1 ->
              state.getPendingDeposits().getSchema().of(dataStructureUtil.randomPendingDeposit());
          case 0 -> state.getPendingDeposits().getSchema().of();
          default -> throw new IllegalArgumentException("Not implemented");
        };
    final BeaconStateElectra electraState =
        state.updatedElectra(data -> data.setPendingDeposits(pendingDeposits));

    return new StateAndMetaData(electraState, SpecMilestone.ELECTRA, false, true, true);
  }

  private StateAndMetaData getTestDataWithPartialWithdrawals(
      final DataStructureUtil dataStructureUtil, final int withdrawalCount) {
    final BeaconStateElectra state =
        dataStructureUtil.randomBeaconState().toVersionElectra().orElseThrow();
    final SszList<PendingPartialWithdrawal> withdrawalSszList =
        switch (withdrawalCount) {
          case 1 ->
              state
                  .getPendingPartialWithdrawals()
                  .getSchema()
                  .of(dataStructureUtil.randomPendingPartialWithdrawal());
          case 0 -> state.getPendingPartialWithdrawals().getSchema().of();
          default -> throw new IllegalArgumentException("Not implemented");
        };

    final BeaconStateElectra electraState =
        state.updatedElectra(data -> data.setPendingPartialWithdrawals(withdrawalSszList));

    return new StateAndMetaData(electraState, SpecMilestone.ELECTRA, false, true, true);
  }

  private StateAndMetaData getTestDataWithPartialConsolidations(
      final DataStructureUtil dataStructureUtil, final int consolidationCount) {
    final BeaconStateElectra state =
        dataStructureUtil.randomBeaconState().toVersionElectra().orElseThrow();
    final SszList<PendingConsolidation> pendingConsolidations =
        switch (consolidationCount) {
          case 1 ->
              state
                  .getPendingConsolidations()
                  .getSchema()
                  .of(dataStructureUtil.randomPendingConsolidation());
          case 0 -> state.getPendingConsolidations().getSchema().of();
          default -> throw new IllegalArgumentException("Not implemented");
        };

    final BeaconStateElectra electraState =
        state.updatedElectra(data -> data.setPendingConsolidations(pendingConsolidations));

    return new StateAndMetaData(electraState, SpecMilestone.ELECTRA, false, true, true);
  }

  @Test
  void getExpectedWithdrawalsFailsTooFarInFutureRequest() {
    final Spec capella = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(capella);
    final ChainDataProvider chainDataProvider = setupBySpec(capella, dataStructureUtil, 16);
    assertThatThrownBy(
            () ->
                chainDataProvider.getExpectedWithdrawalsFromState(
                    dataStructureUtil.randomBeaconState(UInt64.ZERO),
                    Optional.of(UInt64.valueOf(129))))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("more than 128 slots ahead");
  }

  @Test
  void getProposerLookahead_withEmptyInput() {
    final Spec capella = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(capella);
    final ChainDataProvider chainDataProvider = setupBySpec(capella, dataStructureUtil, 16);
    assertThat(chainDataProvider.getProposerLookahead(Optional.empty())).isEmpty();
  }

  @Test
  void getProposerLookahead_respondsSuccessfully() {
    final Spec capella = TestSpecFactory.createMinimalFulu();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(capella);
    final BeaconStateFulu fuluState =
        BeaconStateFulu.required(dataStructureUtil.randomBeaconState());
    final ChainDataProvider chainDataProvider = setupBySpec(capella, dataStructureUtil, 16);
    final StateAndMetaData stateAndMetaData =
        new StateAndMetaData(fuluState, SpecMilestone.FULU, true, true, true);
    final var expectedResult =
        new ObjectAndMetaData<>(
            fuluState.getProposerLookahead(), SpecMilestone.FULU, true, true, true);
    assertThat(chainDataProvider.getProposerLookahead(Optional.of(stateAndMetaData)))
        .isEqualTo(Optional.of(expectedResult));
  }

  @Test
  void getProposerLookahead_failsBeforeFulu() {
    final Spec capella = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(capella);
    final ChainDataProvider chainDataProvider = setupBySpec(capella, dataStructureUtil, 16);
    final StateAndMetaData stateAndMetaData =
        new StateAndMetaData(
            dataStructureUtil.randomBeaconState(), SpecMilestone.CAPELLA, true, true, true);
    assertThatThrownBy(() -> chainDataProvider.getProposerLookahead(Optional.of(stateAndMetaData)))
        .isInstanceOf(BadRequestException.class)
        .hasMessageContaining("does not contain proposer lookahead");
  }

  @Test
  void getExpectedWithdrawalsGeneratesList() {
    final Spec capella = TestSpecFactory.createMinimalCapella();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(capella);
    final ChainDataProvider chainDataProvider = setupBySpec(capella, dataStructureUtil, 16);
    final BeaconStateBuilderCapella capellaBuilder = dataStructureUtil.stateBuilderCapella();

    capellaBuilder.slot(UInt64.valueOf(1024_000));
    capellaBuilder.nextWithdrawalIndex(UInt64.valueOf(10));
    final List<Validator> validators = new ArrayList<>();
    while (validators.size() < 16) {
      validators.add(
          dataStructureUtil
              .validatorBuilder()
              .withRandomEth1WithdrawalCredentials()
              .effectiveBalance(specConfig.getMaxEffectiveBalance())
              .build());
    }
    final UInt64 eff = specConfig.getMaxEffectiveBalance();
    capellaBuilder.balances(
        eff.plus(1024_000),
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff,
        eff.plus(1),
        eff,
        eff,
        eff,
        eff);
    capellaBuilder.validators(
        dataStructureUtil
            .getBeaconStateSchema()
            .getValidatorsSchema()
            .createFromElements(validators));

    final List<Withdrawal> withdrawals =
        chainDataProvider.getExpectedWithdrawalsFromState(capellaBuilder.build(), Optional.empty());

    assertThat(withdrawals).hasSize(2);
    assertThat(withdrawals.get(0).getValidatorIndex()).isEqualTo(UInt64.valueOf(11));
    assertThat(withdrawals.get(0).getAddress())
        .isEqualTo(new Bytes20(validators.get(11).getWithdrawalCredentials().slice(12)));
    assertThat(withdrawals.get(0).getAmount()).isEqualTo(UInt64.valueOf(1));
    assertThat(withdrawals.get(1).getValidatorIndex()).isEqualTo(UInt64.valueOf(0));
    assertThat(withdrawals.get(1).getAddress())
        .isEqualTo(new Bytes20(validators.get(0).getWithdrawalCredentials().slice(12)));
    assertThat(withdrawals.get(1).getAmount()).isEqualTo(UInt64.valueOf(1024_000));
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

  private BeaconState getHeadState() {
    return safeJoin(mockCombinedChainDataClient.getChainHead().orElseThrow().getState());
  }
}
