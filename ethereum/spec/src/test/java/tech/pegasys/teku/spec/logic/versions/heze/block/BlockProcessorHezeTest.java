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

package tech.pegasys.teku.spec.logic.versions.heze.block;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.execution.NewPayloadRequest;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsHeze;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockProcessorHezeTest {
  private final Spec spec = TestSpecFactory.createMinimalHeze();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void computeNewPayloadRequest_shouldDeduplicateTransactionsWithinAndAcrossLists()
      throws Exception {
    final var schemas = SchemaDefinitionsHeze.required(spec.getGenesisSchemaDefinitions());
    final var schema = schemas.getInclusionListSchema();
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
    final BeaconBlockBody body = mock(BeaconBlockBody.class);
    when(body.getOptionalExecutionPayload())
        .thenReturn(Optional.of(dataStructureUtil.randomExecutionPayload()));
    when(body.getOptionalBlobKzgCommitments())
        .thenReturn(Optional.of(schemas.getBlobKzgCommitmentsSchema().getDefault()));
    when(body.getOptionalExecutionRequests())
        .thenReturn(Optional.of(dataStructureUtil.randomExecutionRequests()));

    final NewPayloadRequest request =
        spec.getGenesisSpec()
            .getBlockProcessor()
            .computeNewPayloadRequest(
                dataStructureUtil.randomBeaconState(), body, Optional.of(List.of(first, second)));

    assertThat(request.getInclusionList())
        .hasValueSatisfying(
            transactions ->
                assertThat(transactions)
                    .extracting(Transaction::getBytes)
                    .containsExactly(Bytes.of(1), Bytes.of(2)));
  }
}
