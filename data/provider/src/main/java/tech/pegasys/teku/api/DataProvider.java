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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.function.IntSupplier;
import tech.pegasys.teku.beacon.sync.SyncService;
import tech.pegasys.teku.networking.eth2.Eth2P2PNetwork;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarManager;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.lightclient.LightClientUpdateStore;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.client.BlobReconstructionProvider;
import tech.pegasys.teku.storage.client.BlobSidecarReconstructionProvider;
import tech.pegasys.teku.storage.client.CombinedChainDataClient;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;

public class DataProvider {

  private final NetworkDataProvider networkDataProvider;
  private final ChainDataProvider chainDataProvider;
  private final SyncDataProvider syncDataProvider;
  private final ValidatorDataProvider validatorDataProvider;
  private final NodeDataProvider nodeDataProvider;
  private final ConfigProvider configProvider;
  private final ExecutionClientDataProvider executionClientDataProvider;

  private DataProvider(
      final ConfigProvider configProvider,
      final NetworkDataProvider networkDataProvider,
      final NodeDataProvider nodeDataProvider,
      final ChainDataProvider chainDataProvider,
      final SyncDataProvider syncDataProvider,
      final ValidatorDataProvider validatorDataProvider,
      final ExecutionClientDataProvider executionClientDataProvider) {
    this.configProvider = configProvider;
    this.networkDataProvider = networkDataProvider;
    this.nodeDataProvider = nodeDataProvider;
    this.chainDataProvider = chainDataProvider;
    this.syncDataProvider = syncDataProvider;
    this.validatorDataProvider = validatorDataProvider;
    this.executionClientDataProvider = executionClientDataProvider;
  }

  public ConfigProvider getConfigProvider() {
    return configProvider;
  }

  public NetworkDataProvider getNetworkDataProvider() {
    return networkDataProvider;
  }

  public ChainDataProvider getChainDataProvider() {
    return chainDataProvider;
  }

  public SyncDataProvider getSyncDataProvider() {
    return syncDataProvider;
  }

  public ValidatorDataProvider getValidatorDataProvider() {
    return validatorDataProvider;
  }

  public NodeDataProvider getNodeDataProvider() {
    return nodeDataProvider;
  }

  public ExecutionClientDataProvider getExecutionClientDataProvider() {
    return executionClientDataProvider;
  }

  public static DataProvider.Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private Spec spec;
    private RecentChainData recentChainData;
    private CombinedChainDataClient combinedChainDataClient;
    private RewardCalculator rewardCalculator;
    private Eth2P2PNetwork p2pNetwork;
    private SyncService syncService;
    private ValidatorApiChannel validatorApiChannel;
    private AggregatingAttestationPool attestationPool;
    private BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool;
    private AttestationManager attestationManager;
    private ActiveValidatorChannel activeValidatorChannel;
    private OperationPool<AttesterSlashing> attesterSlashingPool;
    private OperationPool<ProposerSlashing> proposerSlashingPool;
    private OperationPool<SignedVoluntaryExit> voluntaryExitPool;
    private OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool;
    private SyncCommitteeContributionPool syncCommitteeContributionPool;
    private ProposersDataManager proposersDataManager;
    private ForkChoiceNotifier forkChoiceNotifier;
    private boolean isLivenessTrackingEnabled = true;
    private IntSupplier rejectedExecutionSupplier;
    private BlobSidecarReconstructionProvider blobSidecarReconstructionProvider;
    private BlobReconstructionProvider blobReconstructionProvider;
    private LightClientUpdateStore lightClientUpdateStore;
    private DataColumnSidecarManager dataColumnSidecarManager;
    private CustodyGroupCountManager custodyGroupCountManager;
    private PayloadAttestationPool payloadAttestationPool;
    private ProposerPreferencesManager proposerPreferencesManager = ProposerPreferencesManager.NOOP;

    public Builder recentChainData(final RecentChainData recentChainData) {
      this.recentChainData = recentChainData;
      return this;
    }

    public Builder combinedChainDataClient(final CombinedChainDataClient combinedChainDataClient) {
      this.combinedChainDataClient = combinedChainDataClient;
      return this;
    }

    public Builder rewardCalculator(final RewardCalculator rewardCalculator) {
      this.rewardCalculator = rewardCalculator;
      return this;
    }

    public Builder p2pNetwork(final Eth2P2PNetwork p2pNetwork) {
      this.p2pNetwork = p2pNetwork;
      return this;
    }

    public Builder syncService(final SyncService syncService) {
      this.syncService = syncService;
      return this;
    }

    public Builder validatorApiChannel(final ValidatorApiChannel validatorApiChannel) {
      this.validatorApiChannel = validatorApiChannel;
      return this;
    }

    public Builder attestationPool(final AggregatingAttestationPool attestationPool) {
      this.attestationPool = attestationPool;
      return this;
    }

    public Builder blockBlobSidecarsTrackersPool(
        final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool) {
      this.blockBlobSidecarsTrackersPool = blockBlobSidecarsTrackersPool;
      return this;
    }

    public Builder attestationManager(final AttestationManager attestationManager) {
      this.attestationManager = attestationManager;
      return this;
    }

    public Builder isLivenessTrackingEnabled(final boolean isLivenessTrackingEnabled) {
      this.isLivenessTrackingEnabled = isLivenessTrackingEnabled;
      return this;
    }

    public Builder activeValidatorChannel(final ActiveValidatorChannel activeValidatorChannel) {
      this.activeValidatorChannel = activeValidatorChannel;
      return this;
    }

    public Builder attesterSlashingPool(
        final OperationPool<AttesterSlashing> attesterSlashingPool) {
      this.attesterSlashingPool = attesterSlashingPool;
      return this;
    }

    public Builder proposerSlashingPool(
        final OperationPool<ProposerSlashing> proposerSlashingPool) {
      this.proposerSlashingPool = proposerSlashingPool;
      return this;
    }

    public Builder voluntaryExitPool(final OperationPool<SignedVoluntaryExit> voluntaryExitPool) {
      this.voluntaryExitPool = voluntaryExitPool;
      return this;
    }

    public Builder blsToExecutionChangePool(
        final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool) {
      this.blsToExecutionChangePool = blsToExecutionChangePool;
      return this;
    }

    public Builder syncCommitteeContributionPool(
        final SyncCommitteeContributionPool syncCommitteeContributionPool) {
      this.syncCommitteeContributionPool = syncCommitteeContributionPool;
      return this;
    }

    public Builder proposersDataManager(final ProposersDataManager proposersDataManager) {
      this.proposersDataManager = proposersDataManager;
      return this;
    }

    public Builder forkChoiceNotifier(final ForkChoiceNotifier forkChoiceNotifier) {
      this.forkChoiceNotifier = forkChoiceNotifier;
      return this;
    }

    public Builder spec(final Spec spec) {
      this.spec = spec;
      return this;
    }

    public Builder blobSidecarReconstructionProvider(
        final BlobSidecarReconstructionProvider blobSidecarReconstructionProvider) {
      this.blobSidecarReconstructionProvider = blobSidecarReconstructionProvider;
      return this;
    }

    public Builder blobReconstructionProvider(
        final BlobReconstructionProvider blobReconstructionProvider) {
      this.blobReconstructionProvider = blobReconstructionProvider;
      return this;
    }

    public Builder lightClientUpdateStore(final LightClientUpdateStore lightClientUpdateStore) {
      this.lightClientUpdateStore = lightClientUpdateStore;
      return this;
    }

    public Builder dataColumnSidecarManager(
        final DataColumnSidecarManager dataColumnSidecarManager) {
      this.dataColumnSidecarManager = dataColumnSidecarManager;
      return this;
    }

    public DataProvider build() {
      final ConfigProvider configProvider = new ConfigProvider(spec);
      final NetworkDataProvider networkDataProvider = new NetworkDataProvider(p2pNetwork);
      final NodeDataProvider nodeDataProvider =
          new NodeDataProvider(
              attestationPool,
              attesterSlashingPool,
              proposerSlashingPool,
              voluntaryExitPool,
              blsToExecutionChangePool,
              syncCommitteeContributionPool,
              blockBlobSidecarsTrackersPool,
              attestationManager,
              isLivenessTrackingEnabled,
              activeValidatorChannel,
              proposersDataManager,
              forkChoiceNotifier,
              recentChainData,
              dataColumnSidecarManager,
              custodyGroupCountManager,
              payloadAttestationPool,
              proposerPreferencesManager,
              spec);
      final ChainDataProvider chainDataProvider =
          new ChainDataProvider(
              spec,
              recentChainData,
              combinedChainDataClient,
              rewardCalculator,
              blobSidecarReconstructionProvider,
              blobReconstructionProvider,
              lightClientUpdateStore);
      final SyncDataProvider syncDataProvider =
          new SyncDataProvider(syncService, rejectedExecutionSupplier);
      final ValidatorDataProvider validatorDataProvider =
          new ValidatorDataProvider(spec, validatorApiChannel, combinedChainDataClient);
      final ExecutionClientDataProvider executionClientDataProvider =
          new ExecutionClientDataProvider();

      checkNotNull(configProvider, "Expect config Provider");
      checkNotNull(networkDataProvider, "Expect Network Data Provider");
      checkNotNull(chainDataProvider, "Expect Chain Data Provider");
      checkNotNull(syncDataProvider, "Expect Sync Data Provider");
      checkNotNull(validatorDataProvider, "Expect Validator Data Provider");
      checkNotNull(executionClientDataProvider, "Expect Execution Client Data Provider");
      return new DataProvider(
          configProvider,
          networkDataProvider,
          nodeDataProvider,
          chainDataProvider,
          syncDataProvider,
          validatorDataProvider,
          executionClientDataProvider);
    }

    public Builder rejectedExecutionSupplier(final IntSupplier rejectedExecutionCountSupplier) {
      this.rejectedExecutionSupplier = rejectedExecutionCountSupplier;
      return this;
    }

    public Builder custodyGroupCountManager(
        final CustodyGroupCountManager custodyGroupCountManager) {
      this.custodyGroupCountManager = custodyGroupCountManager;
      return this;
    }

    public Builder payloadAttestationPool(final PayloadAttestationPool payloadAttestationPool) {
      this.payloadAttestationPool = payloadAttestationPool;
      return this;
    }

    public Builder proposerPreferencesManager(
        final ProposerPreferencesManager proposerPreferencesManager) {
      this.proposerPreferencesManager = proposerPreferencesManager;
      return this;
    }
  }
}
