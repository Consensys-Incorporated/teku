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

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.ssz.schema.SszContainerSchema;
import tech.pegasys.teku.infrastructure.ssz.schema.SszFieldName;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigGloas;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBodySchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodySchemaGloas;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadFields;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequestsSchema;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ExecutionRequestsFields;
import tech.pegasys.teku.spec.datastructures.execution.versions.gloas.ExecutionRequestsFieldsGloas;

/**
 * Every progressive list that appears in a network message declares the limit the spec assigns to
 * it (the {@code LIMIT} of the corresponding {@code ProgressiveList} type), enforced by the schema
 * on construction and deserialization.
 */
class GloasProgressiveListLimitsTest {

  private final Spec spec = TestSpecFactory.createMinimalGloas();
  private final SpecConfigGloas config = SpecConfigGloas.required(spec.getGenesisSpecConfig());
  private final SchemaDefinitionsGloas schemaDefinitions =
      SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions());

  @Test
  void blockBodyOperationLists() {
    final BeaconBlockBodySchema<?> body = schemaDefinitions.getBeaconBlockBodySchema();
    assertThat(body.getProposerSlashingsSchema().getMaxLength())
        .isEqualTo(config.getMaxProposerSlashings());
    assertThat(body.getAttesterSlashingsSchema().getMaxLength())
        .isEqualTo(config.getMaxAttesterSlashingsElectra());
    assertThat(body.getAttestationsSchema().getMaxLength())
        .isEqualTo(config.getMaxAttestationsElectra());
    // Gloas blocks must not contain deposits
    assertThat(body.getDepositsSchema().getMaxLength()).isZero();
    assertThat(body.getVoluntaryExitsSchema().getMaxLength())
        .isEqualTo(config.getMaxVoluntaryExits());
    assertThat(
            BeaconBlockBodySchemaGloas.required(body)
                .getBlsToExecutionChangesSchema()
                .getMaxLength())
        .isEqualTo(config.getMaxBlsToExecutionChanges());
    assertThat(
            BeaconBlockBodySchemaGloas.required(body).getPayloadAttestationsSchema().getMaxLength())
        .isEqualTo(config.getMaxPayloadAttestations());
  }

  @Test
  void executionRequestLists() {
    final ExecutionRequestsSchema<?> requests = schemaDefinitions.getExecutionRequestsSchema();
    // deposit requests have no Gloas limit
    assertThat(requests.getDepositRequestsSchema().getMaxLength()).isEqualTo(Long.MAX_VALUE);
    assertThat(listMaxLength(requests, ExecutionRequestsFields.WITHDRAWALS))
        .isEqualTo(config.getMaxWithdrawalRequestsPerPayload());
    assertThat(listMaxLength(requests, ExecutionRequestsFields.CONSOLIDATIONS))
        .isEqualTo(config.getMaxConsolidationRequestsPerPayload());
    assertThat(listMaxLength(requests, ExecutionRequestsFieldsGloas.BUILDER_DEPOSITS))
        .isEqualTo(config.getMaxBuilderDepositRequestsPerPayload());
    assertThat(listMaxLength(requests, ExecutionRequestsFieldsGloas.BUILDER_EXITS))
        .isEqualTo(config.getMaxBuilderExitRequestsPerPayload());
  }

  @Test
  void executionPayloadWithdrawals() {
    assertThat(
            listMaxLength(
                schemaDefinitions.getExecutionPayloadSchema(), ExecutionPayloadFields.WITHDRAWALS))
        .isEqualTo(config.getMaxWithdrawalsPerPayload());
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
