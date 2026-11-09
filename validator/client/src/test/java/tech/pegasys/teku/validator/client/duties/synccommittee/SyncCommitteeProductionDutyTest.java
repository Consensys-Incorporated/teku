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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.ValidatorLogger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.SubmitDataError;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;

class SyncCommitteeProductionDutyTest {

  private static final String MESSAGE_TYPE = "sync committee message";
  private final Spec spec = TestSpecFactory.createMinimalAltair();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ValidatorLogger validatorLogger = mock(ValidatorLogger.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ForkInfo forkInfo = dataStructureUtil.randomForkInfo();
  private final Bytes32 blockRoot = dataStructureUtil.randomBytes32();

  private final Validator validator = createValidator();

  @BeforeEach
  void setUp() {
    when(forkProvider.getForkInfo(any())).thenReturn(SafeFuture.completedFuture(forkInfo));
    when(validatorApiChannel.sendSyncCommitteeMessages(any()))
        .thenReturn(SafeFuture.completedFuture(emptyList()));
  }

  @Test
  void shouldReturnNoOpWhenNoValidatorsAssigned() {
    final SyncCommitteeProductionDuty duties = createDuty();
    assertThat(duties.produceMessages(UInt64.ONE, blockRoot)).isCompletedWithValue(DutyResult.NOOP);
    verifyNoInteractions(validatorApiChannel);
  }

  @Test
  void shouldFailToProduceMessagesWhenForkProviderFails() {
    final SyncCommitteeProductionDuty duty = createDuty(committeeAssignment(validator, 55, 1));
    final UInt64 slot = UInt64.valueOf(25);
    final Exception exception = new RuntimeException("Oh dang");
    when(forkProvider.getForkInfo(any())).thenReturn(SafeFuture.failedFuture(exception));

    produceMessagesAndReport(duty, slot);

    verify(validatorLogger)
        .dutyFailed(
            MESSAGE_TYPE, slot, Set.of(validator.getPublicKey().toAbbreviatedString()), exception);
  }

  @Test
  void shouldReportPartialSuccessWhenSomeMessagesAreProducedAndOthersFail() {
    final UInt64 slot = UInt64.valueOf(48);
    final int validatorIndex1 = 11;
    final int validatorIndex2 = 22;
    final Validator validator2 = createValidator();
    final BLSSignature signature1 = dataStructureUtil.randomSignature();
    final Exception exception = new RuntimeException("Boom");
    final SyncCommitteeProductionDuty duties =
        createDuty(
            committeeAssignment(validator, validatorIndex1, 1, 2, 3),
            committeeAssignment(validator2, validatorIndex2, 1, 5));

    when(validator.getSigner().signSyncCommitteeMessage(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(signature1));
    when(validator2.getSigner().signSyncCommitteeMessage(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.failedFuture(exception));

    produceMessagesAndReport(duties, slot);

    assertSentMessages(createMessage(slot, blockRoot, validatorIndex1, signature1));

    verify(validatorLogger)
        .dutyCompleted(MESSAGE_TYPE, slot, 1, Set.of(blockRoot), Optional.empty());
    verify(validatorLogger)
        .dutyFailed(
            MESSAGE_TYPE, slot, Set.of(validator2.getPublicKey().toAbbreviatedString()), exception);
  }

  @Test
  void shouldReportPartialFailureWhenBeaconNodeRejectsSomeMessages() {
    final UInt64 slot = UInt64.valueOf(48);
    final int validatorIndex1 = 11;
    final int validatorIndex2 = 22;
    final Validator validator2 = createValidator();
    final BLSSignature signature1 = dataStructureUtil.randomSignature();
    final BLSSignature signature2 = dataStructureUtil.randomSignature();
    final SyncCommitteeProductionDuty duties =
        createDuty(
            committeeAssignment(validator, validatorIndex1, 1, 2, 3),
            committeeAssignment(validator2, validatorIndex2, 1, 5));

    when(validator.getSigner().signSyncCommitteeMessage(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(signature1));
    when(validator2.getSigner().signSyncCommitteeMessage(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(signature2));

    when(validatorApiChannel.sendSyncCommitteeMessages(any()))
        .thenReturn(
            SafeFuture.completedFuture(List.of(new SubmitDataError(UInt64.ZERO, "API Rejected"))));

    produceMessagesAndReport(duties, slot);

    assertSentMessages(
        createMessage(slot, blockRoot, validatorIndex1, signature1),
        createMessage(slot, blockRoot, validatorIndex2, signature2));

    verify(validatorLogger)
        .dutyCompleted(MESSAGE_TYPE, slot, 1, Set.of(blockRoot), Optional.empty());
    verify(validatorLogger)
        .dutyFailed(
            eq(MESSAGE_TYPE),
            eq(slot),
            eq(Set.of(validator.getPublicKey().toAbbreviatedString())),
            argThat(error -> error.getMessage().equals("API Rejected")));
  }

  @Test
  void shouldProduceMessage() {
    final UInt64 slot = UInt64.valueOf(25);
    final int validatorIndex = 23;
    final BLSSignature signature = dataStructureUtil.randomSignature();
    final SyncCommitteeProductionDuty duties =
        createDuty(committeeAssignment(validator, validatorIndex, 1));

    when(validator.getSigner().signSyncCommitteeMessage(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(signature));

    produceMessagesAndReport(duties, slot);

    verify(validatorApiChannel)
        .sendSyncCommitteeMessages(
            List.of(createMessage(slot, blockRoot, validatorIndex, signature)));
    verify(validatorLogger)
        .dutyCompleted(MESSAGE_TYPE, slot, 1, Set.of(blockRoot), Optional.empty());
  }

  @Test
  void shouldProduceOneMessageForEachValidator() {
    final UInt64 slot = UInt64.valueOf(48);
    final int validatorIndex1 = 11;
    final int validatorIndex2 = 22;
    final Validator validator2 = createValidator();
    final BLSSignature signature1 = dataStructureUtil.randomSignature();
    final BLSSignature signature2 = dataStructureUtil.randomSignature();
    final SyncCommitteeProductionDuty duties =
        createDuty(
            committeeAssignment(validator, validatorIndex1, 1, 2, 3),
            committeeAssignment(validator2, validatorIndex2, 1, 5));

    when(validator.getSigner().signSyncCommitteeMessage(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(signature1));
    when(validator2.getSigner().signSyncCommitteeMessage(slot, blockRoot, forkInfo))
        .thenReturn(SafeFuture.completedFuture(signature2));

    produceMessagesAndReport(duties, slot);

    assertSentMessages(
        createMessage(slot, blockRoot, validatorIndex1, signature1),
        createMessage(slot, blockRoot, validatorIndex2, signature2));

    verify(validatorLogger)
        .dutyCompleted(MESSAGE_TYPE, slot, 2, Set.of(blockRoot), Optional.empty());
  }

  @SuppressWarnings("unchecked")
  private void assertSentMessages(final SyncCommitteeMessage... messages) {
    ArgumentCaptor<List<SyncCommitteeMessage>> argumentCaptor = ArgumentCaptor.forClass(List.class);
    verify(validatorApiChannel).sendSyncCommitteeMessages(argumentCaptor.capture());

    assertThat(argumentCaptor.getValue()).containsExactlyInAnyOrder(messages);
  }

  private SyncCommitteeMessage createMessage(
      final UInt64 slot,
      final Bytes32 blockRoot,
      final int validatorIndex,
      final BLSSignature signature) {
    return SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
        .getSyncCommitteeMessageSchema()
        .create(slot, blockRoot, UInt64.valueOf(validatorIndex), signature);
  }

  private void produceMessagesAndReport(
      final SyncCommitteeProductionDuty duties, final UInt64 slot) {
    final SafeFuture<DutyResult> result = duties.produceMessages(slot, blockRoot);
    assertThat(result).isCompleted();
    safeJoin(result).report(MESSAGE_TYPE, slot, validatorLogger);
  }

  private SyncCommitteeProductionDuty createDuty(
      final ValidatorAndCommitteeIndices... assignments) {
    return new SyncCommitteeProductionDuty(
        spec, forkProvider, validatorApiChannel, asList(assignments));
  }

  private Validator createValidator() {
    return new Validator(dataStructureUtil.randomPublicKey(), mock(Signer.class), Optional::empty);
  }

  private ValidatorAndCommitteeIndices committeeAssignment(
      final Validator validator, final int validatorIndex, final int... committeeIndices) {
    final ValidatorAndCommitteeIndices assignment =
        new ValidatorAndCommitteeIndices(validator, validatorIndex);
    assignment.addCommitteeIndices(IntOpenHashSet.toSet(Arrays.stream(committeeIndices)));
    return assignment;
  }
}
