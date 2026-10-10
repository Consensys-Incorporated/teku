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

package tech.pegasys.teku.validator.client.duties.inclusionlists;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.spec.SpecMilestone.HEZE;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.signatures.Signer;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.FileBackedGraffitiProvider;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;
import tech.pegasys.teku.validator.client.duties.attestations.BatchAttestationSendingStrategy;

@TestSpecContext(milestone = {HEZE})
class InclusionListProductionDutyTest {

  private static final UInt64 SLOT = UInt64.valueOf(42);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private SchemaDefinitionsHeze schemaDefinitions;
  private ForkInfo fork;

  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);

  private InclusionListProductionDuty duty;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    schemaDefinitions = SchemaDefinitionsHeze.required(spec.getGenesisSchemaDefinitions());
    fork = dataStructureUtil.randomForkInfo();
    duty =
        new InclusionListProductionDuty(
            spec,
            SLOT,
            forkProvider,
            new BatchAttestationSendingStrategy<>(validatorApiChannel::sendSignedInclusionLists),
            ValidatorDutyMetrics.create(new StubMetricsSystem()),
            validatorApiChannel);
    when(forkProvider.getForkInfo(any())).thenReturn(completedFuture(fork));
    when(validatorApiChannel.sendSignedInclusionLists(any()))
        .thenReturn(completedFuture(Collections.emptyList()));
  }

  @TestTemplate
  void shouldDoNothingWhenNoValidatorsAdded() {
    assertThat(performDuty()).isEqualTo(DutyResult.NOOP);
    verifyNoInteractions(validatorApiChannel);
  }

  @TestTemplate
  void shouldSignAndSendInclusionListWithTransactions() {
    final Validator validator = createValidator();
    final InclusionList inclusionList = inclusionList(1, transaction(Bytes.of(1)));
    final BLSSignature signature = dataStructureUtil.randomSignature();
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList)));
    when(validator.getSigner().signInclusionList(inclusionList, fork))
        .thenReturn(completedFuture(signature));

    final DutyResult result = performDuty();

    assertThat(result.getSuccessCount()).isOne();
    verify(validatorApiChannel)
        .sendSignedInclusionLists(List.of(signedInclusionList(inclusionList, signature)));
  }

  @TestTemplate
  void shouldNotSignOrSendInclusionListWithoutTransactions() {
    final Validator validator = createValidator();
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList(1))));

    assertThat(performDuty()).isEqualTo(DutyResult.NOOP);
    verify(validator.getSigner(), never()).signInclusionList(any(), any());
    verify(validatorApiChannel, never()).sendSignedInclusionLists(any());
  }

  @TestTemplate
  void shouldNotSignOrSendInclusionListWithOnlyEmptyTransactions() {
    final Validator validator = createValidator();
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList(1, transaction(Bytes.EMPTY)))));

    assertThat(performDuty()).isEqualTo(DutyResult.NOOP);
    verify(validator.getSigner(), never()).signInclusionList(any(), any());
    verify(validatorApiChannel, never()).sendSignedInclusionLists(any());
  }

  @TestTemplate
  void shouldOnlySendInclusionListsWithTransactions() {
    final Validator emptyListValidator = createValidator();
    final Validator validator = createValidator();
    final InclusionList inclusionList = inclusionList(2, transaction(Bytes.of(1)));
    final BLSSignature signature = dataStructureUtil.randomSignature();
    duty.addValidator(emptyListValidator, 1);
    duty.addValidator(validator, 2);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.of(inclusionList(1))));
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.valueOf(2)))
        .thenReturn(completedFuture(Optional.of(inclusionList)));
    when(validator.getSigner().signInclusionList(inclusionList, fork))
        .thenReturn(completedFuture(signature));

    final DutyResult result = performDuty();

    assertThat(result.getSuccessCount()).isOne();
    verify(emptyListValidator.getSigner(), never()).signInclusionList(any(), any());
    verify(validatorApiChannel)
        .sendSignedInclusionLists(List.of(signedInclusionList(inclusionList, signature)));
  }

  @TestTemplate
  void shouldFailWhenInclusionListIsUnavailable() {
    final Validator validator = createValidator();
    duty.addValidator(validator, 1);
    when(validatorApiChannel.createInclusionList(SLOT, UInt64.ONE))
        .thenReturn(completedFuture(Optional.empty()));

    final DutyResult result = performDuty();

    assertThat(result.getFailureCount()).isOne();
    verify(validatorApiChannel, never()).sendSignedInclusionLists(any());
  }

  private DutyResult performDuty() {
    return safeJoin(duty.performDuty());
  }

  private Validator createValidator() {
    return new Validator(
        dataStructureUtil.randomPublicKey(), mock(Signer.class), new FileBackedGraffitiProvider());
  }

  private Transaction transaction(final Bytes bytes) {
    return schemaDefinitions.getExecutionPayloadSchema().getTransactionSchema().fromBytes(bytes);
  }

  private InclusionList inclusionList(final int validatorIndex, final Transaction... transactions) {
    return schemaDefinitions
        .getInclusionListSchema()
        .create(
            SLOT,
            UInt64.valueOf(validatorIndex),
            dataStructureUtil.randomBytes32(),
            List.of(transactions));
  }

  private SignedInclusionList signedInclusionList(
      final InclusionList inclusionList, final BLSSignature signature) {
    return schemaDefinitions.getSignedInclusionListSchema().create(inclusionList, signature);
  }
}
