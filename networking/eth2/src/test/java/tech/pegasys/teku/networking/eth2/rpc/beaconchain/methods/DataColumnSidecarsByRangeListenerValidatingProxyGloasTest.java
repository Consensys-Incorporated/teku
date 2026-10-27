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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSchema;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blobs.versions.gloas.DataColumnSidecarSchemaGloas;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class DataColumnSidecarsByRangeListenerValidatingProxyGloasTest
    extends AbstractDataColumnSidecarsByRangeListenerValidatingProxyTest {

  @Override
  protected Spec createSpec() {
    return TestSpecFactory.createMainnetGloas();
  }

  @Override
  protected SignedBeaconBlock createBlock(final UInt64 slot) {
    final SignedBeaconBlock block =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(slot, 3);
    final SszList<SszKZGCommitment> commitments =
        block.getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();
    when(blobKzgCommitmentsProvider.getBlobKzgCommitments(
            argThat(
                (DataColumnSidecar sidecar) ->
                    sidecar != null && sidecar.getBeaconBlockRoot().equals(block.getRoot()))))
        .thenReturn(SafeFuture.completedFuture(Optional.of(commitments)));
    return block;
  }

  @Override
  protected DataColumnSidecar createSidecarWithInvalidStructure(
      final DataColumnSidecar validSidecar) {
    final DataColumnSidecarSchemaGloas sidecarSchema =
        (DataColumnSidecarSchemaGloas) validSidecar.getSchema();
    final DataColumnSchema dataColumnSchema =
        SchemaDefinitionsGloas.required(spec.atSlot(validSidecar.getSlot()).getSchemaDefinitions())
            .getDataColumnSchema();
    return sidecarSchema.create(
        builder ->
            builder
                .index(validSidecar.getIndex())
                .column(dataColumnSchema.create(List.of()))
                .kzgProofs(validSidecar.getKzgProofs().getSchema().createFromElements(List.of()))
                .slot(validSidecar.getSlot())
                .beaconBlockRoot(validSidecar.getBeaconBlockRoot()));
  }

  @Override
  @Test
  void dataColumnSidecarFailsKzgVerification() {
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(false);
    final SignedBeaconBlock block1 = createBlock(ONE);
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

    final SafeFuture<?> result = listenerWrapper.onResponse(dataColumnSidecar);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(DataColumnSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED
                .describe())
        .hasMessageContaining("DataColumnSidecar's KZG proofs do not match the bid's KZG proofs");
  }

  @Test
  void fuluSchemaSidecarForGloasBlockFailsValidation() {
    final SignedBeaconBlock block1 = createBlock(ONE);
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
    final DataColumnSidecar fuluSchemaSidecar =
        new DataStructureUtil(TestSpecFactory.createMainnetFulu())
            .randomDataColumnSidecar(block1.asHeader(), ZERO);

    final SafeFuture<?> result = listenerWrapper.onResponse(fuluSchemaSidecar);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(DataColumnSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_VALIDITY_CHECK_FAILED
                .describe());
    verify(blobKzgCommitmentsProvider, never()).getBlobKzgCommitments(fuluSchemaSidecar);
    verify(listener, never()).onResponse(any());
  }
}
