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

package tech.pegasys.teku.statetransition.forkchoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannel;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class ForkChoicePayloadExecutorHezeTest {

  private final Spec spec =
      TestSpecFactory.createMinimalHeze(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final SafeFuture<PayloadStatus> executionResult = new SafeFuture<>();
  private final ExecutionLayerChannel executionLayer = mock(ExecutionLayerChannel.class);
  private final ExecutionPayload payload = dataStructureUtil.randomExecutionPayload();
  private final SignedExecutionPayloadEnvelope signedEnvelope =
      dataStructureUtil.randomSignedExecutionPayloadEnvelope(0);
  private final NewPayloadRequest requestWithoutInclusionLists =
      new NewPayloadRequest(payload, List.of(), Bytes32.ZERO, List.of());

  @BeforeEach
  void setUp() {
    when(executionLayer.engineNewPayload(any(), any())).thenReturn(executionResult);
  }

  @Test
  void optimisticallyExecute_shouldAddInclusionListTransactionsWhenPresent() {
    final InclusionList inclusionList = dataStructureUtil.randomInclusionList(2);
    final ForkChoicePayloadExecutorHeze payloadExecutor =
        new ForkChoicePayloadExecutorHeze(signedEnvelope, executionLayer, List.of(inclusionList));

    final boolean result =
        payloadExecutor.optimisticallyExecute(Optional.empty(), requestWithoutInclusionLists);

    final NewPayloadRequest request = captureNewPayloadRequest();
    assertThat(request.getInclusionList())
        .hasValueSatisfying(
            transactions ->
                assertThat(transactions)
                    .containsExactlyElementsOf(inclusionList.getTransactions()));
    assertThat(result).isTrue();
  }

  @Test
  void optimisticallyExecute_shouldDeduplicateTransactionsWithinAndAcrossLists() {
    final var schema =
        SchemaDefinitionsHeze.required(spec.getGenesisSchemaDefinitions()).getInclusionListSchema();
    final var transactionSchema = schema.getTransactionSchema();
    final InclusionList first =
        schema.create(
            UInt64.ZERO,
            UInt64.ZERO,
            Bytes32.ZERO,
            List.of(
                transactionSchema.fromBytes(Bytes.of(1)),
                transactionSchema.fromBytes(Bytes.of(1))));
    final InclusionList second =
        schema.create(
            UInt64.ZERO,
            UInt64.ONE,
            Bytes32.ZERO,
            List.of(
                transactionSchema.fromBytes(Bytes.of(1)),
                transactionSchema.fromBytes(Bytes.of(2))));
    final ForkChoicePayloadExecutorHeze payloadExecutor =
        new ForkChoicePayloadExecutorHeze(signedEnvelope, executionLayer, List.of(first, second));

    payloadExecutor.optimisticallyExecute(Optional.empty(), requestWithoutInclusionLists);

    assertThat(captureNewPayloadRequest().getInclusionList())
        .hasValueSatisfying(
            transactions ->
                assertThat(transactions)
                    .extracting(Transaction::getBytes)
                    .containsExactly(Bytes.of(1), Bytes.of(2)));
  }

  @Test
  void optimisticallyExecute_shouldAddEmptyInclusionListWhenNonePresent() {
    final ForkChoicePayloadExecutorHeze payloadExecutor =
        new ForkChoicePayloadExecutorHeze(signedEnvelope, executionLayer, List.of());

    final boolean result =
        payloadExecutor.optimisticallyExecute(Optional.empty(), requestWithoutInclusionLists);

    final NewPayloadRequest request = captureNewPayloadRequest();
    assertThat(request.getInclusionList())
        .hasValueSatisfying(transactions -> assertThat(transactions).isEmpty());
    assertThat(result).isTrue();
  }

  @Test
  void optimisticallyExecute_shouldPreserveExistingInclusionList() {
    final List<Transaction> existingInclusionList =
        List.of(dataStructureUtil.randomExecutionPayloadTransaction());
    final InclusionList inclusionList = dataStructureUtil.randomInclusionList(2);
    final NewPayloadRequest requestWithInclusionList =
        new NewPayloadRequest(payload, List.of(), Bytes32.ZERO, List.of(), existingInclusionList);
    final ForkChoicePayloadExecutorHeze payloadExecutor =
        new ForkChoicePayloadExecutorHeze(signedEnvelope, executionLayer, List.of(inclusionList));

    final boolean result =
        payloadExecutor.optimisticallyExecute(Optional.empty(), requestWithInclusionList);

    final NewPayloadRequest request = captureNewPayloadRequest();
    assertThat(request.getInclusionList()).hasValue(existingInclusionList);
    assertThat(result).isTrue();
  }

  private NewPayloadRequest captureNewPayloadRequest() {
    final ArgumentCaptor<NewPayloadRequest> requestCaptor =
        ArgumentCaptor.forClass(NewPayloadRequest.class);
    verify(executionLayer).engineNewPayload(requestCaptor.capture(), eq(UInt64.ZERO));
    return requestCaptor.getValue();
  }
}
