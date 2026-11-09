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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.apache.tuweni.bytes.Bytes32;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
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
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DeletableSignerTest {
  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(UInt64.ONE);

  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final BLSSignature signature = dataStructureUtil.randomSignature();
  private final SafeFuture<BLSSignature> signatureFuture = SafeFuture.completedFuture(signature);
  private final Signer delegate = mock(Signer.class);

  private final DeletableSigner signer = new DeletableSigner(delegate);

  @Test
  void signBlock_shouldSignWhenActive() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(6);
    when(delegate.signBlock(block, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signBlock(block, forkInfo)).isCompletedWithValue(signature);
  }

  @Test
  void signBlock_shouldNotSignWhenDisabled() {
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(6);
    signer.delete();
    assertThatSafeFuture(signer.signBlock(block, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signBlock(block, forkInfo);
  }

  @Test
  void createRandaoReveal_shouldCreateWhenActive() {
    when(delegate.createRandaoReveal(UInt64.ONE, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.createRandaoReveal(UInt64.ONE, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void createRandaoReveal_shouldNotCreateWhenDisabled() {
    signer.delete();
    assertThatSafeFuture(signer.createRandaoReveal(UInt64.ONE, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).createRandaoReveal(UInt64.ONE, forkInfo);
  }

  @Test
  void signAttestationData_shouldSignWhenActive() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    when(delegate.signAttestationData(attestationData, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signAttestationData(attestationData, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signAttestationData_shouldNotSignWhenDisabled() {
    final AttestationData attestationData = dataStructureUtil.randomAttestationData();
    signer.delete();
    assertThatSafeFuture(signer.signAttestationData(attestationData, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signAttestationData(attestationData, forkInfo);
  }

  @Test
  void signAggregationSlot_shouldSignWhenActive() {
    when(delegate.signAggregationSlot(UInt64.ONE, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signAggregationSlot(UInt64.ONE, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signAggregationSlot_shouldNotSignWhenDisabled() {
    signer.delete();
    assertThatSafeFuture(signer.signAggregationSlot(UInt64.ONE, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signAggregationSlot(UInt64.ONE, forkInfo);
  }

  @Test
  void signAggregateAndProof_shouldSignWhenActive() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    when(delegate.signAggregateAndProof(aggregateAndProof, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signAggregateAndProof(aggregateAndProof, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signAggregateAndProof_shouldNotSignWhenDisabled() {
    final AggregateAndProof aggregateAndProof = dataStructureUtil.randomAggregateAndProof();
    signer.delete();
    assertThatSafeFuture(signer.signAggregateAndProof(aggregateAndProof, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signAggregateAndProof(aggregateAndProof, forkInfo);
  }

  @Test
  void signVoluntaryExit_shouldSignWhenActive() {
    final VoluntaryExit aggregateAndProof = dataStructureUtil.randomVoluntaryExit();
    when(delegate.signVoluntaryExit(aggregateAndProof, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signVoluntaryExit(aggregateAndProof, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signVoluntaryExit_shouldNotSignWhenDisabled() {
    final VoluntaryExit aggregateAndProof = dataStructureUtil.randomVoluntaryExit();
    signer.delete();
    assertThatSafeFuture(signer.signVoluntaryExit(aggregateAndProof, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signVoluntaryExit(aggregateAndProof, forkInfo);
  }

  @Test
  void signSyncCommitteeMessage_shouldSignWhenActive() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    when(delegate.signSyncCommitteeMessage(UInt64.ONE, root, forkInfo)).thenReturn(signatureFuture);
    assertThatSafeFuture(signer.signSyncCommitteeMessage(UInt64.ONE, root, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void shouldReleaseLockAfterSigning_Threaded() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    final SafeFuture<BLSSignature> signatureSafeFuture = new SafeFuture<>();
    when(delegate.signSyncCommitteeMessage(UInt64.ONE, root, forkInfo))
        .thenReturn(signatureSafeFuture);

    final SafeFuture<BLSSignature> future =
        signer.signSyncCommitteeMessage(UInt64.ONE, root, forkInfo);
    final Thread thread =
        new Thread(() -> signatureSafeFuture.complete(dataStructureUtil.randomSignature()));
    assertThat(future).isNotCompleted();
    thread.start();
    waitForThreadState(thread, Thread.State.TERMINATED);

    assertThat(future).isCompleted();
  }

  @Test
  void signSyncCommitteeMessage_shouldNotSignWhenDisabled() {
    final Bytes32 root = dataStructureUtil.randomBytes32();
    signer.delete();
    assertThatSafeFuture(signer.signSyncCommitteeMessage(UInt64.ONE, root, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);
    verify(delegate, never()).signSyncCommitteeMessage(UInt64.ONE, root, forkInfo);
  }

  @Test
  void signSyncCommitteeSelectionProof_shouldSignWhenActive() {
    final SyncAggregatorSelectionData syncAggregatorSelectionData =
        syncCommitteeUtil.createSyncAggregatorSelectionData(UInt64.ONE, UInt64.valueOf(1));
    when(delegate.signSyncCommitteeSelectionProof(syncAggregatorSelectionData, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(
            signer.signSyncCommitteeSelectionProof(syncAggregatorSelectionData, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signSyncCommitteeSelectionProof_shouldNotSignWhenDisabled() {
    final SyncAggregatorSelectionData syncAggregatorSelectionData =
        syncCommitteeUtil.createSyncAggregatorSelectionData(UInt64.ONE, UInt64.valueOf(1));
    signer.delete();

    assertThatSafeFuture(
            signer.signSyncCommitteeSelectionProof(syncAggregatorSelectionData, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);

    verify(delegate, never())
        .signSyncCommitteeSelectionProof(syncAggregatorSelectionData, forkInfo);
  }

  @Test
  void signContributionAndProof_shouldSignWhenActive() {

    final ContributionAndProof contributionAndProof =
        dataStructureUtil.randomSignedContributionAndProof(6L).getMessage();
    when(delegate.signContributionAndProof(contributionAndProof, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signContributionAndProof(contributionAndProof, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signContributionAndProof_shouldNotSignWhenDisabled() {
    final ContributionAndProof contributionAndProof =
        dataStructureUtil.randomSignedContributionAndProof(6L).getMessage();
    signer.delete();

    assertThatSafeFuture(signer.signContributionAndProof(contributionAndProof, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);

    verify(delegate, never()).signContributionAndProof(contributionAndProof, forkInfo);
  }

  @Test
  void signValidatorRegistration_shouldSignWhenActive() {

    final ValidatorRegistration validatorRegistration =
        dataStructureUtil.randomValidatorRegistration();
    when(delegate.signValidatorRegistration(validatorRegistration)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signValidatorRegistration(validatorRegistration))
        .isCompletedWithValue(signature);
  }

  @Test
  void signValidatorRegistration_shouldNotSignWhenDisabled() {
    final ValidatorRegistration validatorRegistration =
        dataStructureUtil.randomValidatorRegistration();
    signer.delete();

    assertThatSafeFuture(signer.signValidatorRegistration(validatorRegistration))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);

    verify(delegate, never()).signValidatorRegistration(validatorRegistration);
  }

  @Test
  void signExecutionPayloadBid_shouldSignWhenActive() {
    final ExecutionPayloadBid bid = dataStructureUtil.randomExecutionPayloadBid();
    when(delegate.signExecutionPayloadBid(bid, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signExecutionPayloadBid(bid, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signExecutionPayloadBid_shouldNotSignWhenDisabled() {
    final ExecutionPayloadBid bid = dataStructureUtil.randomExecutionPayloadBid();
    signer.delete();

    assertThatSafeFuture(signer.signExecutionPayloadBid(bid, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);

    verify(delegate, never()).signExecutionPayloadBid(bid, forkInfo);
  }

  @Test
  void signExecutionPayloadEnvelope_shouldSignWhenActive() {
    final ExecutionPayloadEnvelope envelope = dataStructureUtil.randomExecutionPayloadEnvelope();
    when(delegate.signExecutionPayloadEnvelope(envelope, forkInfo)).thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signExecutionPayloadEnvelope(envelope, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signExecutionPayloadEnvelope_shouldNotSignWhenDisabled() {
    final ExecutionPayloadEnvelope envelope = dataStructureUtil.randomExecutionPayloadEnvelope();
    signer.delete();

    assertThatSafeFuture(signer.signExecutionPayloadEnvelope(envelope, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);

    verify(delegate, never()).signExecutionPayloadEnvelope(envelope, forkInfo);
  }

  @Test
  void signPayloadAttestationData_shouldSignWhenActive() {
    final PayloadAttestationData payloadAttestationData =
        dataStructureUtil.randomPayloadAttestationData();
    when(delegate.signPayloadAttestationData(payloadAttestationData, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signPayloadAttestationData(payloadAttestationData, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signPayloadAttestationData_shouldNotSignWhenDisabled() {
    final PayloadAttestationData payloadAttestationData =
        dataStructureUtil.randomPayloadAttestationData();
    signer.delete();

    assertThatSafeFuture(signer.signPayloadAttestationData(payloadAttestationData, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);

    verify(delegate, never()).signPayloadAttestationData(payloadAttestationData, forkInfo);
  }

  @Test
  void signProposerPreferences_shouldSignWhenActive() {
    final ProposerPreferences proposerPreferences = dataStructureUtil.randomProposerPreferences();
    when(delegate.signProposerPreferences(proposerPreferences, forkInfo))
        .thenReturn(signatureFuture);

    assertThatSafeFuture(signer.signProposerPreferences(proposerPreferences, forkInfo))
        .isCompletedWithValue(signature);
  }

  @Test
  void signProposerPreferences_shouldNotSignWhenDisabled() {
    final ProposerPreferences proposerPreferences = dataStructureUtil.randomProposerPreferences();
    signer.delete();

    assertThatSafeFuture(signer.signProposerPreferences(proposerPreferences, forkInfo))
        .isCompletedExceptionallyWith(SignerNotActiveException.class);

    verify(delegate, never()).signProposerPreferences(proposerPreferences, forkInfo);
  }

  @Test
  void delete_shouldCallDeleteOnDelegate() {
    signer.delete();
    verify(delegate).delete();
  }

  @Test
  void signerSucceeds_shouldNotMarkDeletedUntilSignerComplete() {
    SafeFuture<BLSSignature> future = new SafeFuture<>();
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(6);
    when(delegate.signBlock(block, forkInfo)).thenReturn(future);

    final SafeFuture<BLSSignature> future2 = signer.signBlock(block, forkInfo);
    assertThatSafeFuture(future2).isNotCompleted();
    final SafeFuture<Void> disablingComplete = new SafeFuture<>();
    final Thread disableThread =
        new Thread(
            () -> {
              signer.delete();
              disablingComplete.complete(null);
            });
    disableThread.start();

    // Wait for the signer.disable() call to be waiting for the write lock
    waitForThreadState(disableThread, Thread.State.WAITING);

    assertThatSafeFuture(disablingComplete).isNotCompleted();
    future.complete(dataStructureUtil.randomSignature());
    assertThatSafeFuture(future2).isCompleted();

    // Confirm the delete can complete once signing has completed.
    waitForThreadState(disableThread, Thread.State.TERMINATED);
    assertThat(disablingComplete).isCompleted();
    verify(delegate).delete();
  }

  @Test
  void signerFails_shouldNotMarkDeletedUntilSignerComplete() {
    SafeFuture<BLSSignature> future = new SafeFuture<>();
    final BeaconBlock block = dataStructureUtil.randomBeaconBlock(6);
    when(delegate.signBlock(block, forkInfo)).thenReturn(future);

    final SafeFuture<BLSSignature> future2 = signer.signBlock(block, forkInfo);
    assertThatSafeFuture(future2).isNotCompleted();
    final SafeFuture<Void> disablingComplete = new SafeFuture<>();
    final Thread disableThread =
        new Thread(
            () -> {
              signer.delete();
              disablingComplete.complete(null);
            });
    disableThread.start();

    // Wait for the signer.disable() call to be waiting for the write lock
    waitForThreadState(disableThread, Thread.State.WAITING);

    assertThatSafeFuture(disablingComplete).isNotCompleted();
    future.completeExceptionally(new RuntimeException("computer says no"));
    assertThatSafeFuture(future2).isCompletedExceptionallyWith(RuntimeException.class);

    // Confirm the delete can complete once signing has completed.
    waitForThreadState(disableThread, Thread.State.TERMINATED);
    assertThat(disablingComplete).isCompleted();
    verify(delegate).delete();
  }

  private void waitForThreadState(final Thread thread, final Thread.State state) {
    final long startTime = System.currentTimeMillis();
    while (thread.getState() != state) {
      if (System.currentTimeMillis() - startTime > 5000) {
        Assertions.fail("Thread did not reach state " + state + " in a reasonable time");
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(500));
    }
  }
}
