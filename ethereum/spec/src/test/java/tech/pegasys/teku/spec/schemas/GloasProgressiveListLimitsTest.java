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

package tech.pegasys.teku.spec.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszByteListSchema;
import tech.pegasys.teku.infrastructure.ssz.sos.SszMaxLengthExceededException;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadSchema;
import tech.pegasys.teku.spec.datastructures.execution.Transaction;

/**
 * Every progressive list that appears in a network message declares the limit the spec assigns to
 * it (the {@code LIMIT} of the corresponding {@code ProgressiveList} type), enforced by the schema
 * on construction and deserialization.
 *
 * <p>The lists of the beacon block body and of execution requests are covered by {@link
 * ProgressiveListLimitsSanityTest}.
 */
class GloasProgressiveListLimitsTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final SpecConfigGloas config = SpecConfigGloas.required(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsGloas schemaDefinitions =
      SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions());

  @Test
  void executionPayloadWithdrawals() {
    assertThat(
            listMaxLength(
                schemaDefinitions.getExecutionPayloadSchema(), ExecutionPayloadFields.WITHDRAWALS))
        .isEqualTo(config.getMaxWithdrawalsPerPayload());
  }

  @Test
  void executionPayloadTransactions() {
    final SszListSchema<Transaction, ?> transactions = transactionsSchema(schemaDefinitions);
    assertThat(transactions.getMaxLength()).isEqualTo(config.getMaxTransactionsPerPayload());
    assertThat(schemaDefinitions.getExecutionPayloadSchema().getTransactionSchema().getMaxLength())
        .isEqualTo(config.getMaxBytesPerTransaction());
  }

  @Test
  void executionPayloadTransactionsAreRejectedAboveLimits() {
    // both limits are far above anything a fixture holds, so shrink them to exercise the packed
    // transactions list: too many transactions, then a transaction with too many bytes
    final SchemaDefinitionsGloas tiny =
        SchemaDefinitionsGloas.required(
            TestSpecFactory.createMinimalGloas(
                    builder ->
                        builder.bellatrixBuilder(
                            b -> b.maxTransactionsPerPayload(2).maxBytesPerTransaction(2)))
                .getGenesisSchemaDefinitions());
    final SszListSchema<Transaction, ?> transactions = transactionsSchema(tiny);
    final SszListSchema<Transaction, ?> unlimited = transactionsSchema(schemaDefinitions);
    final SszByteListSchema<Transaction> transactionSchema =
        schemaDefinitions.getExecutionPayloadSchema().getTransactionSchema();

    final Bytes threeTransactions =
        unlimited
            .createFromElements(
                List.of(
                    transactionSchema.fromBytes(Bytes.of(1)),
                    transactionSchema.fromBytes(Bytes.of(2)),
                    transactionSchema.fromBytes(Bytes.of(3))))
            .sszSerialize();
    assertThatThrownBy(() -> transactions.sszDeserialize(threeTransactions))
        .isInstanceOf(SszMaxLengthExceededException.class)
        .hasMessage("List length 3 exceeds max length 2");

    final Bytes oversizedTransaction =
        unlimited
            .createFromElements(List.of(transactionSchema.fromBytes(Bytes.of(1, 2, 3))))
            .sszSerialize();
    assertThatThrownBy(() -> transactions.sszDeserialize(oversizedTransaction))
        .isInstanceOf(SszMaxLengthExceededException.class)
        .hasMessage("List length 3 exceeds max length 2");

    final Bytes withinLimits =
        unlimited
            .createFromElements(List.of(transactionSchema.fromBytes(Bytes.of(1, 2))))
            .sszSerialize();
    assertThat(transactions.sszDeserialize(withinLimits).get(0).getBytes())
        .isEqualTo(Bytes.of(1, 2));
  }

  @SuppressWarnings("unchecked")
  private static SszListSchema<Transaction, ?> transactionsSchema(
      final SchemaDefinitionsGloas schemaDefinitions) {
    final ExecutionPayloadSchema<?> payload = schemaDefinitions.getExecutionPayloadSchema();
    return (SszListSchema<Transaction, ?>)
        payload.getChildSchema(payload.getFieldIndex(ExecutionPayloadFields.TRANSACTIONS));
  }

  @Test
  void attestationBitsAndIndices() {
    assertThat(schemaDefinitions.getAttestationSchema().getAggregationBitsSchema().getMaxLength())
        .isEqualTo(config.getMaxValidatorsPerAttestation());
    assertThat(
            schemaDefinitions
                .getIndexedAttestationSchema()
                .getAttestingIndicesSchema()
                .getMaxLength())
        .isEqualTo(config.getMaxValidatorsPerAttestation());
  }

  @Test
  void blobCommitmentsAndColumns() {
    final int limit = config.getMaxBlobCommitmentsPerBlock();
    assertThat(
            schemaDefinitions
                .getExecutionPayloadBidSchema()
                .getBlobKzgCommitmentsSchema()
                .getMaxLength())
        .isEqualTo(limit);
    final DataColumnSidecarSchema<?> sidecar = schemaDefinitions.getDataColumnSidecarSchema();
    assertThat(listMaxLength(sidecar, DataColumnSidecarSchema.FIELD_BLOB)).isEqualTo(limit);
    assertThat(listMaxLength(sidecar, DataColumnSidecarSchema.FIELD_KZG_PROOFS)).isEqualTo(limit);
  }

  private static long listMaxLength(
      final SszContainerSchema<?> container, final SszFieldName field) {
    return ((SszListSchema<?, ?>) container.getChildSchema(container.getFieldIndex(field)))
        .getMaxLength();
  }
}
