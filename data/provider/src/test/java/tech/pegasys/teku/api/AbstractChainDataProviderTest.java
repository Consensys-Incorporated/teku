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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import tech.pegasys.teku.api.blobselector.BlobSelectorFactory;
import tech.pegasys.teku.api.blobselector.BlobSidecarSelectorFactory;
import tech.pegasys.teku.api.blockselector.BlockSelectorFactory;
import tech.pegasys.teku.api.datacolumnselector.DataColumnSidecarSelectorFactory;
import tech.pegasys.teku.api.executionpayloadselector.ExecutionPayloadSelectorFactory;
import tech.pegasys.teku.api.stateselector.StateSelectorFactory;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.interop.GenesisStateBuilder;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.util.BeaconStateBuilderAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.storage.client.BlobReconstructionProvider;
import tech.pegasys.teku.storage.client.BlobSidecarReconstructionProvider;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

public abstract class AbstractChainDataProviderTest {

  protected Spec spec;

  protected DataStructureUtil data;
  protected StorageSystem storageSystem;
  protected SpecConfig specConfig;
  protected RecentChainData recentChainData;
  protected CombinedChainDataClient combinedChainDataClient;
  protected BlockSelectorFactory blockSelectorFactory;
  protected BlobSidecarSelectorFactory blobSidecarSelectorFactory;
  protected BlobSelectorFactory blobSelectorFactory;
  protected DataColumnSidecarSelectorFactory dataColumnSidecarSelectorFactory;
  protected ExecutionPayloadSelectorFactory executionPayloadSelectorFactory;
  protected StateSelectorFactory stateSelectorFactory;
  protected BeaconState beaconStateInternal;
  protected SignedBlockAndState bestBlock;
  protected Bytes32 blockRoot;
  protected RewardCalculator rewardCalculatorMock = mock(RewardCalculator.class);
  protected final CombinedChainDataClient mockCombinedChainDataClient =
      mock(CombinedChainDataClient.class);
  protected final BlobSidecarReconstructionProvider mockBlobSidecarReconstructionProvider =
      mock(BlobSidecarReconstructionProvider.class);
  protected final BlobReconstructionProvider mockBlobReconstructionProvider =
      mock(BlobReconstructionProvider.class);

  protected abstract Spec getSpec();

  @BeforeEach
  public void setup() {
    spec = getSpec();
    this.data = new DataStructureUtil(spec);
    specConfig = spec.getGenesisSpecConfig();
    storageSystem =
        InMemoryStorageSystemBuilder.create()
            .storageMode(StateStorageMode.ARCHIVE)
            .specProvider(spec)
            .numberOfValidators(3)
            .build();
    final UInt64 slot = UInt64.valueOf(specConfig.getSlotsPerEpoch() * 3L);
    final UInt64 actualBalance = specConfig.getMaxEffectiveBalance().plus(100000);
    final GenesisStateBuilder genesisBuilder =
        new GenesisStateBuilder().spec(spec).genesisTime(ZERO);
    storageSystem
        .chainBuilder()
        .getValidatorKeys()
        .forEach(key -> genesisBuilder.addValidator(key, actualBalance));
    storageSystem.chainUpdater().initializeGenesis(genesisBuilder.build());
    bestBlock = storageSystem.chainUpdater().advanceChain(slot);
    storageSystem.chainUpdater().updateBestBlock(bestBlock);
    storageSystem.chainUpdater().finalizeEpoch(slot);

    recentChainData = storageSystem.recentChainData();
    beaconStateInternal = bestBlock.getState();

    combinedChainDataClient = storageSystem.combinedChainDataClient();
    blockRoot = bestBlock.getRoot();
  }

  protected ChainDataProvider setupBySpec(
      final Spec spec, final DataStructureUtil dataStructureUtil) {
    return setupBySpec(spec, dataStructureUtil, 16);
  }

  protected ChainDataProvider setupBySpec(
      final Spec spec, final DataStructureUtil dataStructureUtil, final int validatorCount) {
    this.blockSelectorFactory = spy(new BlockSelectorFactory(spec, mockCombinedChainDataClient));
    this.stateSelectorFactory = spy(new StateSelectorFactory(spec, mockCombinedChainDataClient));
    this.blobSidecarSelectorFactory =
        spy(
            new BlobSidecarSelectorFactory(
                spec, mockCombinedChainDataClient, mockBlobSidecarReconstructionProvider));
    this.blobSelectorFactory =
        spy(
            new BlobSelectorFactory(
                spec, mockCombinedChainDataClient, mockBlobReconstructionProvider));
    this.dataColumnSidecarSelectorFactory =
        spy(new DataColumnSidecarSelectorFactory(spec, mockCombinedChainDataClient));
    this.executionPayloadSelectorFactory =
        spy(new ExecutionPayloadSelectorFactory(spec, mockCombinedChainDataClient));
    final ChainDataProvider provider =
        new ChainDataProvider(
            spec,
            recentChainData,
            mockCombinedChainDataClient,
            blockSelectorFactory,
            stateSelectorFactory,
            blobSidecarSelectorFactory,
            blobSelectorFactory,
            dataColumnSidecarSelectorFactory,
            executionPayloadSelectorFactory,
            rewardCalculatorMock,
            new LightClientUpdateStore(spec));

    if (spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ALTAIR)) {
      final SszList<Validator> validators =
          dataStructureUtil.randomSszList(
              dataStructureUtil.getBeaconStateSchema().getValidatorsSchema(),
              validatorCount,
              dataStructureUtil::randomValidator);
      final SyncCommittee currentSyncCommittee = dataStructureUtil.randomSyncCommittee(validators);

      final BeaconStateBuilderAltair builder =
          dataStructureUtil
              .stateBuilderAltair()
              .validators(validators)
              .slot(dataStructureUtil.randomEpoch())
              .currentSyncCommittee(currentSyncCommittee);
      final SignedBlockAndState signedBlockAndState =
          dataStructureUtil.randomSignedBlockAndState(builder.build());
      final ChainHead chainHead =
          ChainHead.create(StateAndBlockSummary.create(signedBlockAndState));
      when(mockCombinedChainDataClient.getChainHead()).thenReturn(Optional.of(chainHead));
      when(mockCombinedChainDataClient.getStateByBlockRoot(
              eq(signedBlockAndState.getBlock().getRoot())))
          .thenReturn(SafeFuture.completedFuture(Optional.of(signedBlockAndState.getState())));
    }

    return provider;
  }
}
