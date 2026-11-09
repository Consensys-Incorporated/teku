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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import it.unimi.dsi.fastutil.ints.IntList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SignedContributionAndProof;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncAggregatorSelectionData;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeContribution;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;

class SyncCommitteeAggregationDutyTest {

  private static final String TYPE = "sync_aggregate";

  private final Spec spec = TestSpecFactory.createAltair(createSpecConfig());
  private final SyncCommitteeUtil syncCommitteeUtil =
      spec.getSyncCommitteeUtilRequired(UInt64.ZERO);
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final UInt64 slot = dataStructureUtil.randomUInt64();
  private final Bytes32 beaconBlockRoot = dataStructureUtil.randomBytes32();
  private final ValidatorLogger validatorLogger = Mockito.mock(ValidatorLogger.class);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final SyncAggregatorSelectionProofProvider syncAggregatorSelectionProofProvider =
      spy(new SyncAggregatorSelectionProofProvider());
  private final BLSSignature nonAggregatorSignature =
      BLSSignature.fromBytesCompressed(
          Bytes.fromHexString(
              "0xce401f767ab1917f925fe299ad51a57d52f7cc80deb1cc20fa2b3aa983e4e4d23056d79f01f3c97e29c8905da17e70e30c2a3f6bdd83dbc4ddf530e02e8f4d7ba22260e12e5f5fe7875b48e79660615b275597e87b2d33e076664b3da1737852"));
  private final BLSSignature aggregatorSignature =
      BLSSignature.fromBytesCompressed(
          Bytes.fromHexString(
              "0x8f5c34de9e22ceaa7e8d165fc0553b32f02188539e89e2cc91e2eb9077645986550d872ee3403204ae5d554eae3cac12124e18d2324bccc814775316aaef352abc0450812b3ca9fde96ecafa911b3b8bfddca8db4027f08e29c22a9c370ad933"));
  private final Validator validator1 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  private final Validator validator2 =
      new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  private final SyncCommitteeContribution contribution =
      dataStructureUtil.randomSyncCommitteeContribution(slot, beaconBlockRoot);
  private final BLSSignature contributionSignature = dataStructureUtil.randomSignature();
  private final ContributionAndProof contributionAndProof =
      syncCommitteeUtil.createContributionAndProof(
          UInt64.valueOf(11), contribution, aggregatorSignature);

  @BeforeEach
  void setUp() {
    when(forkProvider.getForkInfo(any())).thenReturn(SafeFuture.completedFuture(forkInfo));

    // Default to not returning errors when sending
    when(validatorApiChannel.sendSignedContributionAndProofs(any()))
        .thenReturn(SafeFuture.COMPLETE);

    assertThat(syncCommitteeUtil.isSyncCommitteeAggregator(nonAggregatorSignature)).isFalse();
    assertThat(syncCommitteeUtil.isSyncCommitteeAggregator(aggregatorSignature)).isTrue();
  }

  private static SpecConfigAndParent<? extends SpecConfig> createSpecConfig() {
    return SpecConfigLoader.loadConfig(
        "minimal",
        modifier ->
            modifier
                .altairForkEpoch(UInt64.ZERO)
                .altairBuilder(altairModifier -> altairModifier.syncCommitteeSize(512)));
  }

  @Test
  void shouldDoNothingWhenAssignmentsAreEmpty() {
    final SyncCommitteeAggregationDuty duty = createDuty();
    final SafeFuture<DutyResult> result = duty.produceAggregates(slot, beaconBlockRoot);
    assertThat(result).isCompletedWithValue(DutyResult.NOOP);
    verifyNoInteractions(validatorApiChannel);
  }

  @Test
  void shouldFailWhenRetrievingForkInfoFails() {
    final SyncCommitteeAggregationDuty duty =
        createDuty(committeeAssignment(validator1, 233, 1), committeeAssignment(validator2, 45, 2));
    final Exception exception = new RuntimeException("Ooopsie");
    when(forkProvider.getForkInfo(any())).thenReturn(SafeFuture.failedFuture(exception));

    produceAggregatesAndReport(duty);

    verify(validatorLogger)
        .dutyFailed(
            TYPE,
            slot,
            Set.of(
                validator1.getPublicKey().toAbbreviatedString(),
                validator2.getPublicKey().toAbbreviatedString()),
            exception);
  }

  @Test
  void shouldDoNothingWhenNoValidatorsAreAggregators() {
    final SyncCommitteeAggregationDuty duty = createDuty(committeeAssignment(validator1, 11, 1));

    final SyncAggregatorSelectionData expectedSigningData =
        syncCommitteeUtil.createSyncAggregatorSelectionData(slot, UInt64.ZERO);
    when(validator1.getSigner().signSyncCommitteeSelectionProof(expectedSigningData, forkInfo))
        .thenReturn(SafeFuture.completedFuture(nonAggregatorSignature));

    final SafeFuture<DutyResult> result = duty.produceAggregates(slot, beaconBlockRoot);
    assertThat(result).isCompletedWithValue(DutyResult.NOOP);
  }

  @Test
  void shouldFailWhenSigningSelectionProofFails() {
    final SyncCommitteeAggregationDuty duty = createDuty(committeeAssignment(validator1, 11, 1));
    final Exception exception = new RuntimeException("So sad...");

    when(validator1.getSigner().signSyncCommitteeSelectionProof(any(), any()))
        .thenReturn(SafeFuture.failedFuture(exception));

    produceAggregatesAndReport(duty);
    verify(validatorLogger)
        .dutyFailed(TYPE, slot, Set.of(validator1.getPublicKey().toAbbreviatedString()), exception);
  }

  @Test
  void shouldCreateAndSendSignedContributionAndProof() {
    final int committeeIndex = 9;
    final int subcommitteeIndex = 0;
    withValidatorAggregatingSubnet(validator1, subcommitteeIndex);

    final SyncCommitteeAggregationDuty duty =
        createDuty(committeeAssignment(validator1, 11, committeeIndex));

    produceAggregatesAndReport(duty);

    final List<SignedContributionAndProof> expectedSignedContributions =
        List.of(
            syncCommitteeUtil.createSignedContributionAndProof(
                contributionAndProof, contributionSignature));
    verify(validatorLogger).dutyCompleted(TYPE, slot, 1, Set.of(beaconBlockRoot), Optional.empty());
    verify(validatorApiChannel).sendSignedContributionAndProofs(expectedSignedContributions);
  }

  @Test
  void shouldSendSuccessfulAggregatesWhenSomeFail() {
    // Validator 1 aggregates successfully
    withValidatorAggregatingSubnet(validator1, 0);

    // Validator 2 signer is offline
    final Exception exception = new RuntimeException("Too bad");
    when(validator2.getSigner().signSyncCommitteeSelectionProof(any(), any()))
        .thenReturn(SafeFuture.failedFuture(exception));

    final SyncCommitteeAggregationDuty duty =
        createDuty(committeeAssignment(validator1, 11, 0), committeeAssignment(validator2, 22, 6));

    produceAggregatesAndReport(duty);

    final List<SignedContributionAndProof> expectedSignedContributions =
        List.of(
            syncCommitteeUtil.createSignedContributionAndProof(
                contributionAndProof, contributionSignature));
    // Successfully complete for validator1
    verify(validatorLogger).dutyCompleted(TYPE, slot, 1, Set.of(beaconBlockRoot), Optional.empty());
    verify(validatorApiChannel).sendSignedContributionAndProofs(expectedSignedContributions);

    // Validator 2 fails
    verify(validatorLogger)
        .dutyFailed(TYPE, slot, Set.of(validator2.getPublicKey().toAbbreviatedString()), exception);
  }

  @Test
  void shouldReportAggregationSkippedWhenContributionIsEmpty() {
    withValidatorAggregatingSubnet(validator1, 0);

    // No aggregate available
    when(validatorApiChannel.createSyncCommitteeContribution(slot, 0, beaconBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));

    final SyncCommitteeAggregationDuty duty = createDuty(committeeAssignment(validator1, 11, 0));

    final SafeFuture<DutyResult> result = duty.produceAggregates(slot, beaconBlockRoot);
    assertThat(result).isCompletedWithValue(DutyResult.NOOP);

    verify(validatorLogger).syncCommitteeAggregationSkipped(slot);
  }

  @Test
  void shouldReportFailureWhenSendingFails() {
    withValidatorAggregatingSubnet(validator1, 0);
    withValidatorAggregatingSubnet(validator2, 0);

    final IllegalArgumentException exception = new IllegalArgumentException("Bang");
    when(validatorApiChannel.sendSignedContributionAndProofs(any()))
        .thenReturn(SafeFuture.failedFuture(exception));

    final SyncCommitteeAggregationDuty duty =
        createDuty(
            committeeAssignment(
                validator1, contributionAndProof.getAggregatorIndex().intValue(), 0),
            committeeAssignment(
                validator2, contributionAndProof.getAggregatorIndex().intValue(), 1));
    produceAggregatesAndReport(duty);

    verify(validatorLogger)
        .dutyFailed(
            TYPE,
            slot,
            Set.of(
                validator1.getPublicKey().toAbbreviatedString(),
                validator2.getPublicKey().toAbbreviatedString()),
            exception);
  }

  @Test
  public void shouldReportFailureWhenSyncAggregatorFailsPreparingSelectionProof() {
    withValidatorAggregatingSubnet(validator1, 0);
    withValidatorAggregatingSubnet(validator2, 0);

    final IllegalArgumentException exception = new IllegalArgumentException("Bang");
    doThrow(exception)
        .when(syncAggregatorSelectionProofProvider)
        .prepareSelectionProofForSlot(eq(slot), any(), any(), eq(forkInfo));

    final SyncCommitteeAggregationDuty duty =
        createDuty(
            committeeAssignment(
                validator1, contributionAndProof.getAggregatorIndex().intValue(), 0),
            committeeAssignment(
                validator2, contributionAndProof.getAggregatorIndex().intValue(), 1));
    produceAggregatesAndReport(duty);

    verify(validatorLogger)
        .dutyFailed(
            TYPE,
            slot,
            Set.of(
                validator1.getPublicKey().toAbbreviatedString(),
                validator2.getPublicKey().toAbbreviatedString()),
            exception);
  }

  @Test
  public void shouldReportFailureWhenSyncAggregatorFailsFindingSelectionProof() {
    withValidatorAggregatingSubnet(validator1, 0);
    withValidatorAggregatingSubnet(validator2, 0);

    final IllegalArgumentException exception = new IllegalArgumentException("Bang");
    doThrow(exception)
        .when(syncAggregatorSelectionProofProvider)
        .getSelectionProofFuture(anyInt(), anyInt());

    final SyncCommitteeAggregationDuty duty =
        createDuty(
            committeeAssignment(
                validator1, contributionAndProof.getAggregatorIndex().intValue(), 0),
            committeeAssignment(
                validator2, contributionAndProof.getAggregatorIndex().intValue(), 1));
    produceAggregatesAndReport(duty);

    verify(validatorLogger)
        .dutyFailed(
            TYPE,
            slot,
            Set.of(
                validator1.getPublicKey().toAbbreviatedString(),
                validator2.getPublicKey().toAbbreviatedString()),
            exception);
  }

  @Test
  public void shouldReportFailureWhenSyncAggregatorCannotFindSelectionProofForValidator() {
    final int validatorIndex = contributionAndProof.getAggregatorIndex().intValue();
    final int subcommitteeIndex = 0;

    withValidatorAggregatingSubnet(validator1, subcommitteeIndex);

    doReturn(Optional.empty())
        .when(syncAggregatorSelectionProofProvider)
        .getSelectionProofFuture(eq(validatorIndex), eq(subcommitteeIndex));

    final SyncCommitteeAggregationDuty duty =
        createDuty(committeeAssignment(validator1, validatorIndex, subcommitteeIndex));
    produceAggregatesAndReport(duty);

    final ArgumentCaptor<Exception> exceptionArgumentCaptor =
        ArgumentCaptor.forClass(Exception.class);
    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE),
            eq(slot),
            eq(Set.of(validator1.getPublicKey().toAbbreviatedString())),
            exceptionArgumentCaptor.capture());

    assertThat(exceptionArgumentCaptor.getValue())
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Can't find aggregation selection proof for validator");
  }

  @Test
  public void shouldReportFailureOnlyForValidatorsThatFailedAggregation() {
    final int committeeIndex = 9;
    final int subcommitteeIndex = 0;
    withValidatorAggregatingSubnet(validator1, subcommitteeIndex);
    withValidatorAggregatingSubnet(validator2, subcommitteeIndex);

    // Preparing only validator2 to fail its aggregation
    doReturn(Optional.empty())
        .when(syncAggregatorSelectionProofProvider)
        .getSelectionProofFuture(eq(22), eq(0));

    final SyncCommitteeAggregationDuty duty =
        createDuty(
            committeeAssignment(validator1, 11, committeeIndex),
            committeeAssignment(validator2, 22, committeeIndex));

    produceAggregatesAndReport(duty);

    // Check that validator1 aggregation was produced and reported successfully
    final List<SignedContributionAndProof> expectedSignedContributions =
        List.of(
            syncCommitteeUtil.createSignedContributionAndProof(
                contributionAndProof, contributionSignature));
    verify(validatorLogger).dutyCompleted(TYPE, slot, 1, Set.of(beaconBlockRoot), Optional.empty());
    verify(validatorApiChannel).sendSignedContributionAndProofs(expectedSignedContributions);

    // Check that validator2 aggregation failed and was reported as failure
    verify(validatorLogger)
        .dutyFailed(
            eq(TYPE), eq(slot), eq(Set.of(validator2.getPublicKey().toAbbreviatedString())), any());
  }

  private void withValidatorAggregatingSubnet(
      final Validator validator, final int subcommitteeIndex) {
    final SyncAggregatorSelectionData expectedSigningData =
        syncCommitteeUtil.createSyncAggregatorSelectionData(
            slot, UInt64.valueOf(subcommitteeIndex));
    when(validator.getSigner().signSyncCommitteeSelectionProof(expectedSigningData, forkInfo))
        .thenReturn(SafeFuture.completedFuture(aggregatorSignature));

    // Provide defaults to ensure the aggregation succeeds
    when(validatorApiChannel.createSyncCommitteeContribution(
            slot, subcommitteeIndex, beaconBlockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(contribution)));
    when(validator.getSigner().signContributionAndProof(contributionAndProof, forkInfo))
        .thenReturn(SafeFuture.completedFuture(contributionSignature));
  }

  private SyncCommitteeAggregationDuty createDuty(
      final ValidatorAndCommitteeIndices... assignments) {
    return new SyncCommitteeAggregationDuty(
        spec,
        forkProvider,
        validatorApiChannel,
        validatorLogger,
        List.of(assignments),
        syncAggregatorSelectionProofProvider);
  }

  private ValidatorAndCommitteeIndices committeeAssignment(
      final Validator validator, final int validatorIndex, final int... committeeIndices) {
    final ValidatorAndCommitteeIndices assignment =
        new ValidatorAndCommitteeIndices(validator, validatorIndex);
    assignment.addCommitteeIndices(IntList.of(committeeIndices));
    return assignment;
  }

  private void produceAggregatesAndReport(final SyncCommitteeAggregationDuty duty) {
    final SafeFuture<DutyResult> result = duty.produceAggregates(slot, beaconBlockRoot);
    assertThat(result).isCompleted();
    safeJoin(result).report(TYPE, slot, validatorLogger);
  }
}
