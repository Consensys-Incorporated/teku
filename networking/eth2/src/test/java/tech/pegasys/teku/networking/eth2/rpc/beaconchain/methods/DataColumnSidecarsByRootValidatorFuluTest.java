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

import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecarSchema;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarFulu;
import tech.pegasys.teku.spec.datastructures.blobs.versions.fulu.DataColumnSidecarSchemaFulu;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;

@SuppressWarnings("JavaCase")
public class DataColumnSidecarsByRootValidatorFuluTest
    extends AbstractDataColumnSidecarsByRootValidatorTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMinimalWithFuluForkEpoch(currentForkEpoch);
  }

  @Override
  protected DataColumnSidecar createSidecarWithBrokenValidity(final DataColumnSidecar sidecar) {
    return breakValidity(sidecar);
  }

  @Test
  void dataColumnSidecarFailsInclusionProofVerification() {
    final SignedBeaconBlock block1 = createBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(block1, UInt64.ZERO);
    final DataColumnSidecar dataColumnSidecar1_0_modified =
        breakInclusionProof(dataColumnSidecar1_0);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        DataColumnIdentifier.createFromSidecar(dataColumnSidecar1_0);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            blobKzgCommitmentsProvider);

    assertThatSafeFuture(validator.validate(dataColumnSidecar1_0_modified))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED
                .describe());
  }

  public static DataColumnSidecar breakInclusionProof(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarSchemaFulu dataColumnSidecarSchemaFulu =
        DataColumnSidecarSchemaFulu.required(
            (DataColumnSidecarSchema<?>) dataColumnSidecar.getSchema());
    return dataColumnSidecarSchemaFulu.create(
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
  }

  public static DataColumnSidecar breakValidity(final DataColumnSidecar dataColumnSidecar) {
    final DataColumnSidecarSchemaFulu dataColumnSidecarSchemaFulu =
        DataColumnSidecarSchemaFulu.required(dataColumnSidecar.getSchema());
    return dataColumnSidecarSchemaFulu.create(
        builder ->
            builder
                .index(dataColumnSidecar.getIndex())
                .column(dataColumnSidecar.getColumn())
                .kzgCommitments(
                    DataColumnSidecarFulu.required(dataColumnSidecar).getKzgCommitments())
                // wrong number of proofs
                .kzgProofs(
                    dataColumnSidecar
                        .getKzgProofs()
                        .getSchema()
                        .createFromElements(
                            dataColumnSidecar.getKzgProofs().stream().skip(1).toList()))
                .signedBlockHeader(
                    DataColumnSidecarFulu.required(dataColumnSidecar).getSignedBlockHeader())
                .kzgCommitmentsInclusionProof(
                    DataColumnSidecarFulu.required(dataColumnSidecar)
                        .getKzgCommitmentsInclusionProof()
                        .asListUnboxed()));
  }
}
