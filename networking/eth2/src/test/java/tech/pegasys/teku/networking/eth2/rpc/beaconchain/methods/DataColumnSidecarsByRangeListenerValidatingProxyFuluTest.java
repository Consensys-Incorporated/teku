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

package tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsFulu;

@SuppressWarnings("JavaCase")
public class DataColumnSidecarsByRangeListenerValidatingProxyFuluTest
    extends AbstractDataColumnSidecarsByRangeListenerValidatingProxyTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetFulu();
  }

  @Override
  protected DataColumnSidecar createSidecarWithInvalidStructure(
      final DataColumnSidecar validSidecar) {
    final SchemaDefinitionsFulu schemaDefinitions =
        SchemaDefinitionsFulu.required(
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
    final DataColumnSidecarSchema<?> sidecarSchema = schemaDefinitions.getDataColumnSidecarSchema();

    // Create an invalid sidecar by setting empty KZG commitments
    return sidecarSchema.create(
        builder ->
            builder
                .index(validSidecar.getIndex())
                .column(validSidecar.getColumn())
                .kzgCommitments(sidecarSchema.getKzgCommitmentsSchema().of())
                .kzgProofs(validSidecar.getKzgProofs())
                .signedBlockHeader(
                    DataColumnSidecarFulu.required(validSidecar).getSignedBlockHeader())
                .kzgCommitmentsInclusionProof(
                    DataColumnSidecarFulu.required(validSidecar)
                        .getKzgCommitmentsInclusionProof()
                        .asListUnboxed()));
  }

  @Test
  void dataColumnSidecarFailsInclusionProofVerification() {
    final SignedBeaconBlock block1 = createBlock(ONE);
    final SchemaDefinitionsFulu schemaDefinitions =
        SchemaDefinitionsFulu.required(
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions());
    final DataColumnSidecarSchema<?> sidecarSchema = schemaDefinitions.getDataColumnSidecarSchema();

    final List<UInt64> columns = List.of(ZERO, ONE);

    listenerWrapper =
        new DataColumnSidecarsByRangeListenerValidatingProxy(
            spec,
            peer,
            listener,
            metricsSystem,
            timeProvider,
            signatureValidator,
            ONE,
            UInt64.valueOf(1),
            columns,
            blobKzgCommitmentsProvider);

    final DataColumnSidecar dataColumnSidecar =
        dataStructureUtil.randomDataColumnSidecar(block1, ZERO);
    final DataColumnSidecar dataColumnSidecarModified =
        sidecarSchema.create(
            builder ->
                builder
                    .index(dataColumnSidecar.getIndex())
                    .column(dataColumnSidecar.getColumn())
                    .kzgCommitments(
                        DataColumnSidecarFulu.required(dataColumnSidecar).getKzgCommitments())
                    .kzgProofs(dataColumnSidecar.getKzgProofs())
                    .signedBlockHeader(
                        DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader())
                    .kzgCommitmentsInclusionProof(
                        DataColumnSidecarFulu.required(dataColumnSidecar)
                            .getKzgCommitmentsInclusionProof()
                            .asListUnboxed()
                            .stream()
                            .map(Bytes32::not) // modify inclusion proof list
                            .toList()));

    final SafeFuture<?> result = listenerWrapper.onResponse(dataColumnSidecarModified);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(DataColumnSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED
                .describe());
  }
}
