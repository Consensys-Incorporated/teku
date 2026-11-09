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

package tech.pegasys.teku.validator.client.duties.synccommittee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.client.Validator;

class SyncAggregatorSelectionProofProviderTest {

  final Spec spec = TestSpecFactory.createMinimalAltair();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(UInt64.ONE);
  final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  final UInt64 slot = UInt64.ONE;
  final Validator validator1 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  final Validator validator2 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  SyncAggregatorSelectionProofProvider syncAggregatorSelectionProofProvider;

  @BeforeEach
  public void setUp() {
    syncAggregatorSelectionProofProvider = new SyncAggregatorSelectionProofProvider();
  }

  @Test
  public void singleValidatorInSingleSyncSubcommittee() {
    final BLSSignature expectedSignature =
        mockValidatorSignerForSyncCommitteeSelectionProofSigning(validator1);

    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0));

    final SafeFuture<Void> prepareSelectionProofFuture =
        syncAggregatorSelectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  // Expected signature for validator with assignment for subcommittee
                  assertSignedSelectionProofWasCreated(1, 0, expectedSignature);
                  // Empty when querying non-assigned subcommittee index
                  assertThat(syncAggregatorSelectionProofProvider.getSelectionProofFuture(1, 999))
                      .isEmpty();
                  // Empty when querying validator without duty assignments
                  assertThat(syncAggregatorSelectionProofProvider.getSelectionProofFuture(2, 111))
                      .isEmpty();
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void singleValidatorInMultipleSyncSubcommittee() {
    final BLSSignature expectedSignature =
        mockValidatorSignerForSyncCommitteeSelectionProofSigning(validator1);

    // Minimal subcommittee size is 8
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0, 9));

    final SafeFuture<Void> prepareSelectionProofFuture =
        syncAggregatorSelectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  assertSignedSelectionProofWasCreated(1, 0, expectedSignature);
                  assertSignedSelectionProofWasCreated(1, 1, expectedSignature);
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void multipleValidatorsInSameSyncSubcommittee() {
    final BLSSignature expectedSignatureValidator1 =
        mockValidatorSignerForSyncCommitteeSelectionProofSigning(validator1);
    final BLSSignature expectedSignatureValidator2 =
        mockValidatorSignerForSyncCommitteeSelectionProofSigning(validator2);

    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0), committeeAssignment(validator2, 2, 1));

    final SafeFuture<Void> prepareSelectionProofFuture =
        syncAggregatorSelectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  assertSignedSelectionProofWasCreated(1, 0, expectedSignatureValidator1);
                  assertSignedSelectionProofWasCreated(2, 0, expectedSignatureValidator2);
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void multipleValidatorsInMultipleSyncSubcommittee() {
    final BLSSignature expectedSignatureValidator1 =
        mockValidatorSignerForSyncCommitteeSelectionProofSigning(validator1);
    final BLSSignature expectedSignatureValidator2 =
        mockValidatorSignerForSyncCommitteeSelectionProofSigning(validator2);

    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(
            committeeAssignment(validator1, 1, 0, 9), committeeAssignment(validator2, 2, 1, 10));

    final SafeFuture<Void> prepareSelectionProofFuture =
        syncAggregatorSelectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  assertSignedSelectionProofWasCreated(1, 0, expectedSignatureValidator1);
                  assertSignedSelectionProofWasCreated(1, 1, expectedSignatureValidator1);
                  assertSignedSelectionProofWasCreated(2, 0, expectedSignatureValidator2);
                  assertSignedSelectionProofWasCreated(2, 1, expectedSignatureValidator2);
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void failedSignatureHandling() {
    // Signature for subcommittee index 0 will fail
    final SyncAggregatorSelectionData syncAggregatorSelectionDataSubCommitteeZero =
        syncCommitteeUtil.createSyncAggregatorSelectionData(slot, UInt64.ZERO);
    doReturn(SafeFuture.failedFuture(new RuntimeException("Boom")))
        .when(validator1.getSigner())
        .signSyncCommitteeSelectionProof(eq(syncAggregatorSelectionDataSubCommitteeZero), any());

    // Signature for subcommittee index 1 will succeed
    final SyncAggregatorSelectionData syncAggregatorSelectionDataSubCommitteeOne =
        syncCommitteeUtil.createSyncAggregatorSelectionData(slot, UInt64.ONE);
    doReturn(SafeFuture.completedFuture(dataStructureUtil.randomSignature()))
        .when(validator1.getSigner())
        .signSyncCommitteeSelectionProof(eq(syncAggregatorSelectionDataSubCommitteeOne), any());

    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0, 9));

    final SafeFuture<Void> prepareSelectionProofFuture =
        syncAggregatorSelectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  // Checking that future for subcommittee index 0 failed
                  final Optional<SafeFuture<BLSSignature>> selectionProofFuture0 =
                      syncAggregatorSelectionProofProvider.getSelectionProofFuture(1, 0);
                  assertThat(selectionProofFuture0).isPresent();
                  assertThat(selectionProofFuture0.get()).isCompletedExceptionally();

                  // Checking that future for subcommittee index 1 succeeded
                  final Optional<SafeFuture<BLSSignature>> selectionProofFuture1 =
                      syncAggregatorSelectionProofProvider.getSelectionProofFuture(1, 1);
                  assertThat(selectionProofFuture1).isPresent();
                  assertThat(selectionProofFuture1.get()).isCompleted();
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  private void assertSignedSelectionProofWasCreated(
      final int validatorIndex, final int subcommitteeIndex, final BLSSignature expectedSignature) {
    final Optional<SafeFuture<BLSSignature>> selectionProofFuture =
        syncAggregatorSelectionProofProvider.getSelectionProofFuture(
            validatorIndex, subcommitteeIndex);
    assertThat(selectionProofFuture).isPresent();
    assertThat(selectionProofFuture.get()).isCompletedWithValue(expectedSignature);
  }

  private BLSSignature mockValidatorSignerForSyncCommitteeSelectionProofSigning(
      final Validator validator) {
    final BLSSignature signature = dataStructureUtil.randomSignature();
    when(validator.getSigner().signSyncCommitteeSelectionProof(any(), any()))
        .thenReturn(SafeFuture.completedFuture(signature));
    return signature;
  }

  private ValidatorAndCommitteeIndices committeeAssignment(
      final Validator validator, final int validatorIndex, final int... committeeIndices) {
    final ValidatorAndCommitteeIndices assignment =
        new ValidatorAndCommitteeIndices(validator, validatorIndex);
    assignment.addCommitteeIndices(IntList.of(committeeIndices));
    return assignment;
  }
}
