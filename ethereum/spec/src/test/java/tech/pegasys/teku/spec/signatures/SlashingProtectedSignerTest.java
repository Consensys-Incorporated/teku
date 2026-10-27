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

package tech.pegasys.teku.spec.signatures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.builder.ValidatorRegistration;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.PayloadAttestationData;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.operations.AggregateAndProof;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.VoluntaryExit;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class SlashingProtectedSignerTest {
  private final DataStructureUtil dataStructureUtil =
      new DataStructureUtil(TestSpecFactory.createMinimalGloas());

  private final BLSPublicKey publicKey = dataStructureUtil.randomPublicKey();
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final BLSSignature signature = dataStructureUtil.randomSignature();
  private final SafeFuture<BLSSignature> signatureFuture = SafeFuture.completedFuture(signature);
  private final Signer delegate = mock(Signer.class);
  private final SlashingProtector slashingProtector = mock(SlashingProtector.class);

  private final SlashingProtectedSigner signer =
      new SlashingProtectedSigner(publicKey, slashingProtector, delegate);

  @Test
  void signBlock_shouldSignWhenSlashingProtectionAllowsIt() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(6);
    when(slashingProtector.maySignBlock(
            publicKey, forkInfo.getGenesisValidatorsRoot(), block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(true));
    when(delegate.signBlock(block, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signBlock(block, forkInfo)).isCompletedWithValue(signature);
  }

  @Test
  void signBlock_shouldNotSignWhenSlashingProtectionRejects() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(6);
    when(slashingProtector.maySignBlock(
            publicKey, forkInfo.getGenesisValidatorsRoot(), block.getSlot()))
        .thenReturn(SafeFuture.completedFuture(false));
    when(delegate.signBlock(block, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signBlock(block, forkInfo))
        .isCompletedExceptionallyWith(SlashableConditionException.class);
  }

  @Test
  void signAttestationData_shouldSignWhenSlashingProtectionAllowsIt() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    when(slashingProtector.maySignAttestation(
            publicKey,
            forkInfo.getGenesisValidatorsRoot(),
            attestationData.getSource().getEpoch(),
            attestationData.getTarget().getEpoch()))
        .thenReturn(SafeFuture.completedFuture(true));
    when(delegate.signAttestationData(attestationData, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signAttestationData(attestationData, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signAttestationData_shouldNotSignWhenSlashingProtectionRejects() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    when(slashingProtector.maySignAttestation(
            publicKey,
            forkInfo.getGenesisValidatorsRoot(),
            attestationData.getSource().getEpoch(),
            attestationData.getTarget().getEpoch()))
        .thenReturn(SafeFuture.completedFuture(false));
    when(delegate.signAttestationData(attestationData, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signAttestationData(attestationData, forkInfo))
        .isCompletedExceptionallyWith(SlashableConditionException.class);
  }

  @Test
  void createRandaoReveal_shouldAlwaysSign() {
    when(delegate.createRandaoReveal(UInt64.ONE, forkInfo)).thenReturn(signatureFuture);

    assertThat(signer.createRandaoReveal(UInt64.ONE, forkInfo)).isCompletedWithValue(signature);
  }

  @Test
  void signAggregationSlot_shouldAlwaysSign() {
    when(delegate.signAggregationSlot(UInt64.ONE, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signAggregationSlot(UInt64.ONE, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signAggregateAndProof_shouldAlwaysSign() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    when(delegate.signAggregateAndProof(aggregateAndProof, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signAggregateAndProof(aggregateAndProof, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signVoluntaryExit_shouldAlwaysSign() {
    final VoluntaryExit voluntaryExit = dataStructureUtil.randomVoluntaryExit();
    when(delegate.signVoluntaryExit(voluntaryExit, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signVoluntaryExit(voluntaryExit, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signValidatorRegistration_shouldAlwaysSign() {
    final ValidatorRegistration validatorRegistration =
        dataStructureUtil.randomValidatorRegistration();
    when(delegate.signValidatorRegistration(validatorRegistration)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signValidatorRegistration(validatorRegistration))
        .isCompletedWithValue(signature);
  }

  @Test
  void signExecutionPayloadBid_shouldAlwaysSign() {
    final ExecutionPayloadBid bid = dataStructureUtil.randomExecutionPayloadBid();
    when(delegate.signExecutionPayloadBid(bid, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signExecutionPayloadBid(bid, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signExecutionPayloadEnvelope_shouldAlwaysSign() {
    final ExecutionPayloadEnvelope envelope = dataStructureUtil.randomExecutionPayloadEnvelope();
    when(delegate.signExecutionPayloadEnvelope(envelope, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signExecutionPayloadEnvelope(envelope, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signPayloadAttestationData_shouldAlwaysSign() {
    final PayloadAttestationData payloadAttestationData =
        dataStructureUtil.randomPayloadAttestationData();
    when(delegate.signPayloadAttestationData(payloadAttestationData, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signPayloadAttestationData(payloadAttestationData, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signProposerPreferences_shouldAlwaysSign() {
    final ProposerPreferences proposerPreferences = dataStructureUtil.randomProposerPreferences();
    when(delegate.signProposerPreferences(proposerPreferences, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signProposerPreferences(proposerPreferences, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void delete_shouldCallDeleteOnDelegateAndEvictSigningRecord() {
    signer.delete();
    final InOrder inOrder = inOrder(delegate, slashingProtector);
    inOrder.verify(delegate).delete();
    inOrder.verify(slashingProtector).evictSigningRecord(publicKey);
  }
}
