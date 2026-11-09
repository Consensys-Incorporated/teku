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

package tech.pegasys.teku.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.operations.AttesterSlashing;
import tech.pegasys.teku.spec.datastructures.operations.ProposerSlashing;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.datastructures.operations.SignedVoluntaryExit;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.OperationPool;
import tech.pegasys.teku.statetransition.attestation.AggregatingAttestationPool;
import tech.pegasys.teku.statetransition.attestation.AttestationManager;
import tech.pegasys.teku.statetransition.blobs.BlockBlobSidecarsTrackersPool;
import tech.pegasys.teku.statetransition.datacolumns.CustodyGroupCountManager;
import tech.pegasys.teku.statetransition.datacolumns.DataColumnSidecarManager;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.ProposersDataManager;
import tech.pegasys.teku.statetransition.payloadattestation.PayloadAttestationPool;
import tech.pegasys.teku.statetransition.synccommittee.SyncCommitteeContributionPool;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validatorcache.ActiveValidatorChannel;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.validator.api.SubmitDataError;

@SuppressWarnings("unchecked")
public class NodeDataProviderTest {
  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final AggregatingAttestationPool attestationPool = mock(AggregatingAttestationPool.class);
  private final BlockBlobSidecarsTrackersPool blockBlobSidecarsTrackersPool =
      mock(BlockBlobSidecarsTrackersPool.class);
  private final DataColumnSidecarManager dataColumnSidecarManager =
      mock(DataColumnSidecarManager.class);
  private final AttestationManager attestationManager = mock(AttestationManager.class);
  private final ActiveValidatorChannel validatorChannel = mock(ActiveValidatorChannel.class);
  private final ProposersDataManager proposersDataManager = mock(ProposersDataManager.class);
  private final ForkChoiceNotifier forkChoiceNotifier = mock(ForkChoiceNotifier.class);
  private final CustodyGroupCountManager custodyGroupCountManager =
      mock(CustodyGroupCountManager.class);
  private final PayloadAttestationPool payloadAttestationPool = mock(PayloadAttestationPool.class);
  private final ProposerPreferencesManager proposerPreferencesManager =
      mock(ProposerPreferencesManager.class);
  private final RecentChainData recentChainData = mock(RecentChainData.class);

  private final OperationPool<AttesterSlashing> attesterSlashingPool = mock(OperationPool.class);

  private final OperationPool<ProposerSlashing> proposerSlashingPool = mock(OperationPool.class);

  private final OperationPool<SignedVoluntaryExit> voluntaryExitPool = mock(OperationPool.class);

  private final OperationPool<SignedBlsToExecutionChange> blsToExecutionChangePool =
      mock(OperationPool.class);

  private final SyncCommitteeContributionPool syncCommitteeContributionPool =
      mock(SyncCommitteeContributionPool.class);
  private NodeDataProvider provider;

  @BeforeEach
  public void setup() {
    provider =
        new NodeDataProvider(
            attestationPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            blsToExecutionChangePool,
            syncCommitteeContributionPool,
            blockBlobSidecarsTrackersPool,
            attestationManager,
            false,
            validatorChannel,
            proposersDataManager,
            forkChoiceNotifier,
            recentChainData,
            dataColumnSidecarManager,
            custodyGroupCountManager,
            payloadAttestationPool,
            proposerPreferencesManager,
            spec);
  }

  @Test
  void shouldAddBlsToExecutionChangesToPool() throws ExecutionException, InterruptedException {
    when(blsToExecutionChangePool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT));
    final SafeFuture<List<SubmitDataError>> future =
        provider.postBlsToExecutionChanges(
            List.of(
                dataStructureUtil.randomSignedBlsToExecutionChange(),
                dataStructureUtil.randomSignedBlsToExecutionChange()));
    assertThat(future).isCompleted();
    assertThat(future.get()).isEqualTo(List.of());
  }

  @Test
  void blsToExecutionChanges_ReturnsListOfErrors() throws ExecutionException, InterruptedException {
    when(blsToExecutionChangePool.addLocal(any()))
        .thenReturn(SafeFuture.completedFuture(InternalValidationResult.ACCEPT))
        .thenReturn(
            SafeFuture.completedFuture(InternalValidationResult.reject("Computer says no")));
    final SafeFuture<List<SubmitDataError>> future =
        provider.postBlsToExecutionChanges(
            List.of(
                dataStructureUtil.randomSignedBlsToExecutionChange(),
                dataStructureUtil.randomSignedBlsToExecutionChange()));
    assertThat(future).isCompleted();
    assertThat(future.get())
        .isEqualTo(List.of(new SubmitDataError(UInt64.ONE, "Computer says no")));
  }

  @Test
  void attestationsMetaDataLookUp_UseFirstAttestationSlot_WhenSlotParamNotProvided() {
    final Spec specMock = setUpMockedSpec();
    when(attestationPool.getAttestations(any(), any()))
        .thenReturn(
            List.of(
                dataStructureUtil.randomAttestation(5), dataStructureUtil.randomAttestation(10)));
    provider.getAttestationsAndMetaData(Optional.empty(), Optional.empty());
    verify(specMock).atSlot(eq(UInt64.valueOf(5)));
  }

  @Test
  void attestationsMetaDataLookUp_UseSlot_WhenSlotParamProvided() {
    final Spec specMock = setUpMockedSpec();
    when(attestationPool.getAttestations(any(), any()))
        .thenReturn(
            List.of(
                dataStructureUtil.randomAttestation(5), dataStructureUtil.randomAttestation(10)));
    provider.getAttestationsAndMetaData(Optional.of(UInt64.valueOf(8)), Optional.empty());
    verify(specMock).atSlot(eq(UInt64.valueOf(8)));
  }

  @Test
  void attestationsMetaDataLookUp_UseCurrentSlot_WhenSlotParamNotProvided_EmptyList() {
    final Spec specMock = setUpMockedSpec();
    when(attestationPool.getAttestations(any(), any())).thenReturn(Collections.emptyList());
    final UInt64 currentSlot = UInt64.valueOf(8);
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(currentSlot));
    provider.getAttestationsAndMetaData(Optional.empty(), Optional.empty());
    verify(specMock).atSlot(eq(currentSlot));
  }

  @Test
  void attestationsMetaDataLookUp_UseSlotZero_WhenSlotParamNotProvided_EmptyList_NoCurrentSlot() {
    final Spec specMock = setUpMockedSpec();
    when(attestationPool.getAttestations(any(), any())).thenReturn(Collections.emptyList());
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.empty());
    provider.getAttestationsAndMetaData(Optional.empty(), Optional.empty());
    verify(specMock).atSlot(eq(UInt64.ZERO));
  }

  @Test
  void attesterSlashingsMetaDataLookUp_UseAttesterSlashingSlot_WhenListIsNotEmpty() {
    final UInt64 slot = UInt64.valueOf(12);
    final Spec specMock = setUpMockedSpec();
    when(attesterSlashingPool.getAll())
        .thenReturn(Set.of(dataStructureUtil.randomAttesterSlashingAtSlot(slot)));
    provider.getAttesterSlashingsAndMetaData();
    verify(specMock).atSlot(eq(slot));
  }

  @Test
  void attesterSlashingsMetaDataLookUp_UseCurrentSlot_WhenSlotParamNotProvided_EmptyList() {
    final Spec specMock = setUpMockedSpec();
    when(attesterSlashingPool.getAll()).thenReturn(Set.of());
    final UInt64 currentSlot = UInt64.valueOf(8);
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(currentSlot));
    provider.getAttesterSlashingsAndMetaData();
    verify(specMock).atSlot(eq(currentSlot));
  }

  @Test
  void attesterSlashingsMetaDataLookUp_UseSlotZero_WhenEmptyList_NoCurrentSlot() {
    final Spec specMock = setUpMockedSpec();
    when(attesterSlashingPool.getAll()).thenReturn(Set.of());
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.empty());
    provider.getAttesterSlashingsAndMetaData();
    verify(specMock).atSlot(eq(UInt64.ZERO));
  }

  @Test
  void proposerPreferencesMetaDataLookUp_UseFirstProposalSlot_WhenSlotParamNotProvided() {
    final Spec specMock = setUpMockedSpec();
    final DataStructureUtil gloasDataStructureUtil =
        new DataStructureUtil(TestSpecFactory.createMinimalGloas());
    final List<SignedProposerPreferences> preferences =
        List.of(
            gloasDataStructureUtil.randomSignedProposerPreferences(),
            gloasDataStructureUtil.randomSignedProposerPreferences());
    final Bytes32 dependentRoot = gloasDataStructureUtil.randomBytes32();
    when(proposerPreferencesManager.getSignedProposerPreferences(
            Optional.empty(), Optional.of(dependentRoot)))
        .thenReturn(preferences);

    final ObjectAndMetaData<List<SignedProposerPreferences>> result =
        provider.getProposerPreferencesAndMetaData(Optional.empty(), Optional.of(dependentRoot));

    assertThat(result.getData()).isEqualTo(preferences);
    verify(specMock).atSlot(eq(preferences.getFirst().getMessage().getProposalSlot()));
  }

  @Test
  void proposerPreferencesMetaDataLookUp_UseSlot_WhenSlotParamProvided() {
    final Spec specMock = setUpMockedSpec();
    final UInt64 slot = UInt64.valueOf(8);
    when(proposerPreferencesManager.getSignedProposerPreferences(
            Optional.of(slot), Optional.empty()))
        .thenReturn(List.of());

    provider.getProposerPreferencesAndMetaData(Optional.of(slot), Optional.empty());

    verify(specMock).atSlot(eq(slot));
  }

  @Test
  void proposerPreferencesMetaDataLookUp_UseCurrentSlot_WhenSlotParamNotProvided_EmptyList() {
    final Spec specMock = setUpMockedSpec();
    when(proposerPreferencesManager.getSignedProposerPreferences(any(), any()))
        .thenReturn(List.of());
    final UInt64 currentSlot = UInt64.valueOf(8);
    when(recentChainData.getCurrentSlot()).thenReturn(Optional.of(currentSlot));

    provider.getProposerPreferencesAndMetaData(Optional.empty(), Optional.empty());

    verify(specMock).atSlot(eq(currentSlot));
  }

  private Spec setUpMockedSpec() {
    final Spec specMock = mock(Spec.class);
    final SpecVersion specVersionMock = mock(SpecVersion.class);
    final SpecMilestone specMilestone = mock(SpecMilestone.class);
    when(specVersionMock.getMilestone()).thenReturn(specMilestone);
    when(specMock.atSlot(any())).thenReturn(specVersionMock);
    provider =
        new NodeDataProvider(
            attestationPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            blsToExecutionChangePool,
            syncCommitteeContributionPool,
            blockBlobSidecarsTrackersPool,
            attestationManager,
            false,
            validatorChannel,
            proposersDataManager,
            forkChoiceNotifier,
            recentChainData,
            dataColumnSidecarManager,
            custodyGroupCountManager,
            payloadAttestationPool,
            proposerPreferencesManager,
            specMock);
    return specMock;
  }
}
