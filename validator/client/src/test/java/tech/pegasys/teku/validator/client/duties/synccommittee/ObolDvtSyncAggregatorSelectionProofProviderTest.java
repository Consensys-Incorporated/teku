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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeSelectionProof;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.Validator;

class ObolDvtSyncAggregatorSelectionProofProviderTest {

  final Spec spec = TestSpecFactory.createMinimalAltair();
  final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  final SyncCommitteeUtil syncCommitteeUtil = spec.getSyncCommitteeUtilRequired(UInt64.ONE);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  final UInt64 slot = UInt64.ONE;
  final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  final Validator validator1 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  final Validator validator2 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  private ObolDvtSyncAggregatorSelectionProofProvider selectionProofProvider;

  @BeforeEach
  public void setUp() {
    selectionProofProvider = new ObolDvtSyncAggregatorSelectionProofProvider(validatorApiChannel);
    mockValidatorSignerForSyncCommitteeSelectionProofSigning(validator1);
    mockValidatorSignerForSyncCommitteeSelectionProofSigning(validator2);
  }

  @Test
  public void singleValidatorInSingleSyncSubcommittee() {
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0));

    final List<SyncCommitteeSelectionProof> obolIntegrationResponse =
        createResponseFromAssignments(assignments);
    when(validatorApiChannel.getSyncCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(obolIntegrationResponse)));

    final SafeFuture<Void> prepareSelectionProofFuture =
        selectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  // Expected signature for validator with assignment for subcommittee
                  assertSignedSelectionProofWasCreated(1, 0);
                  // Empty when querying non-assigned subcommittee index
                  assertThat(selectionProofProvider.getSelectionProofFuture(1, 999)).isEmpty();
                  // Empty when querying validator without duty assignments
                  assertThat(selectionProofProvider.getSelectionProofFuture(2, 111)).isEmpty();
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void singleValidatorInMultipleSyncSubcommittee() {
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0, 9));

    final List<SyncCommitteeSelectionProof> obolIntegrationResponse =
        createResponseFromAssignments(assignments);
    when(validatorApiChannel.getSyncCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(obolIntegrationResponse)));

    final SafeFuture<Void> prepareSelectionProofFuture =
        selectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  assertSignedSelectionProofWasCreated(1, 0);
                  assertSignedSelectionProofWasCreated(1, 1);
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void multipleValidatorsInSameSyncSubcommittee() {
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0), committeeAssignment(validator2, 2, 1));

    final List<SyncCommitteeSelectionProof> obolIntegrationResponse =
        createResponseFromAssignments(assignments);
    when(validatorApiChannel.getSyncCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(obolIntegrationResponse)));

    final SafeFuture<Void> prepareSelectionProofFuture =
        selectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  assertSignedSelectionProofWasCreated(1, 0);
                  assertSignedSelectionProofWasCreated(2, 0);
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void multipleValidatorsInMultipleSyncSubcommittee() {
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(
            committeeAssignment(validator1, 1, 0, 9), committeeAssignment(validator2, 2, 1, 10));

    final List<SyncCommitteeSelectionProof> obolIntegrationResponse =
        createResponseFromAssignments(assignments);
    when(validatorApiChannel.getSyncCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(obolIntegrationResponse)));

    final SafeFuture<Void> prepareSelectionProofFuture =
        selectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  assertSignedSelectionProofWasCreated(1, 0);
                  assertSignedSelectionProofWasCreated(1, 1);
                  assertSignedSelectionProofWasCreated(2, 0);
                  assertSignedSelectionProofWasCreated(2, 1);
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void missingValidatorFromObolSyncCommitteeResponse() {
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0), committeeAssignment(validator2, 2, 1));

    // Obol response contains only selection proof for validator 2
    final List<SyncCommitteeSelectionProof> obolIntegrationResponse =
        List.of(
            new SyncCommitteeSelectionProof.Builder()
                .validatorIndex(2)
                .subcommitteeIndex(0)
                .slot(slot)
                .selectionProof(
                    dataStructureUtil.randomSignature().toBytesCompressed().toHexString())
                .build());
    when(validatorApiChannel.getSyncCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(obolIntegrationResponse)));

    final SafeFuture<Void> prepareSelectionProofFuture =
        selectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  // Checking that future for subcommittee index 0 failed
                  final Optional<SafeFuture<BLSSignature>> selectionProofFuture0 =
                      selectionProofProvider.getSelectionProofFuture(1, 0);
                  assertThat(selectionProofFuture0).isPresent();
                  assertThat(selectionProofFuture0.get()).isCompletedExceptionally();

                  // Checking that future for subcommittee index 1 succeeded
                  final Optional<SafeFuture<BLSSignature>> selectionProofFuture1 =
                      selectionProofProvider.getSelectionProofFuture(2, 0);
                  assertThat(selectionProofFuture1).isPresent();
                  assertThat(selectionProofFuture1.get()).isCompleted();
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void extraValidatorFromObolSyncCommitteeResponse() {
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0));

    // Obol response with more validators than we are running on our VC
    final List<SyncCommitteeSelectionProof> obolIntegrationResponse =
        List.of(
            new SyncCommitteeSelectionProof.Builder()
                .validatorIndex(1)
                .subcommitteeIndex(0)
                .slot(slot)
                .selectionProof(
                    dataStructureUtil.randomSignature().toBytesCompressed().toHexString())
                .build(),
            new SyncCommitteeSelectionProof.Builder()
                .validatorIndex(2)
                .subcommitteeIndex(0)
                .slot(slot)
                .selectionProof(
                    dataStructureUtil.randomSignature().toBytesCompressed().toHexString())
                .build());
    when(validatorApiChannel.getSyncCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.of(obolIntegrationResponse)));

    final SafeFuture<Void> prepareSelectionProofFuture =
        selectionProofProvider
            .prepareSelectionProofForSlot(slot, assignments, syncCommitteeUtil, forkInfo)
            .thenAccept(
                __ -> {
                  // Checking that future for subcommittee index 0 succeeded
                  final Optional<SafeFuture<BLSSignature>> selectionProofFuture0 =
                      selectionProofProvider.getSelectionProofFuture(1, 0);
                  assertThat(selectionProofFuture0).isPresent();
                  assertThat(selectionProofFuture0.get()).isCompleted();

                  // Check that we didn't create a pending future for a validator we don't own
                  assertThat(selectionProofProvider.getSelectionProofFuture(2, 0)).isEmpty();
                });

    assertThat(prepareSelectionProofFuture).isCompleted();
  }

  @Test
  public void emptyResponseFromObolIntegrationFailsSelectionProofPreparation() {
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0));

    when(validatorApiChannel.getSyncCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    assertThat(
            selectionProofProvider.prepareSelectionProofForSlot(
                slot, assignments, syncCommitteeUtil, forkInfo))
        .isCompletedExceptionally();
  }

  @Test
  public void failedObolIntegrationFailsSelectionProofPreparation() {
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0));

    when(validatorApiChannel.getSyncCommitteeSelectionProof(any()))
        .thenReturn(SafeFuture.failedFuture(new RuntimeException("Boom!")));

    assertThat(
            selectionProofProvider.prepareSelectionProofForSlot(
                slot, assignments, syncCommitteeUtil, forkInfo))
        .isCompletedExceptionally();
  }

  @Test
  public void exceptionalObolIntegrationFailsSelectionProofPreparation() {
    final Collection<ValidatorAndCommitteeIndices> assignments =
        List.of(committeeAssignment(validator1, 1, 0));

    when(validatorApiChannel.getSyncCommitteeSelectionProof(any()))
        .thenThrow(new RuntimeException("Boom!"));

    assertThat(
            selectionProofProvider.prepareSelectionProofForSlot(
                slot, assignments, syncCommitteeUtil, forkInfo))
        .isCompletedExceptionally();
  }

  private List<SyncCommitteeSelectionProof> createResponseFromAssignments(
      final Collection<ValidatorAndCommitteeIndices> assignments) {
    final List<SyncCommitteeSelectionProof> response = new ArrayList<>();

    assignments.forEach(
        assignment ->
            syncCommitteeUtil
                .getSyncSubcommittees(assignment.getCommitteeIndices())
                .forEach(
                    subcommitteeIndex ->
                        response.add(
                            new SyncCommitteeSelectionProof.Builder()
                                .validatorIndex(assignment.getValidatorIndex())
                                .subcommitteeIndex(subcommitteeIndex)
                                .slot(slot)
                                .selectionProof(
                                    dataStructureUtil
                                        .randomSignature()
                                        .toBytesCompressed()
                                        .toHexString())
                                .build())));
    return response;
  }

  private void assertSignedSelectionProofWasCreated(
      final int validatorIndex, final int subcommitteeIndex) {
    final Optional<SafeFuture<BLSSignature>> selectionProofFuture =
        selectionProofProvider.getSelectionProofFuture(validatorIndex, subcommitteeIndex);
    assertThat(selectionProofFuture).isPresent();
    assertThat(selectionProofFuture.get()).isCompleted();
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
