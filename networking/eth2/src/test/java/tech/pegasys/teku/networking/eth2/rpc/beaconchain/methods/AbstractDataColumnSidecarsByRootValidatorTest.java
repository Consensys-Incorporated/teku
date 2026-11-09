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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;

import java.util.List;
import org.hyperledger.besu.metrics.noop.NoOpMetricsSystem;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.peers.DataColumnSidecarSignatureValidator;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.DataColumnSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.util.DataColumnIdentifier;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.statetransition.datacolumns.BlobKzgCommitmentsProvider;

@SuppressWarnings("JavaCase")
public abstract class AbstractDataColumnSidecarsByRootValidatorTest {

  protected final UInt64 currentForkEpoch = UInt64.valueOf(1);
  protected final Eth2Peer peer = mock(Eth2Peer.class);
  protected final KZG kzg = mock(KZG.class);
  protected Spec spec;
  protected DataStructureUtil dataStructureUtil;
  protected DataColumnSidecarsByRootValidator validator;
  protected UInt64 currentForkFirstSlot;
  protected final MetricsSystem metricsSystem = new NoOpMetricsSystem();
  protected final TimeProvider timeProvider = StubTimeProvider.withTimeInMillis(0);
  protected final DataColumnSidecarSignatureValidator dataColumnSidecarSignatureValidator =
      mock(DataColumnSidecarSignatureValidator.class);
  protected final BlobKzgCommitmentsProvider blobKzgCommitmentsProvider =
      mock(BlobKzgCommitmentsProvider.class);

  protected abstract Spec createSpec();

  protected abstract DataColumnSidecar createSidecarWithBrokenValidity(DataColumnSidecar sidecar);

  protected SignedBeaconBlock createBlock(final UInt64 slot) {
    return dataStructureUtil.randomSignedBeaconBlock(slot);
  }

  @BeforeEach
  void setUp() {
    spec = createSpec();
    currentForkFirstSlot = spec.computeStartSlotAtEpoch(currentForkEpoch);
    dataStructureUtil = new DataStructureUtil(spec);
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);
    when(dataColumnSidecarSignatureValidator.validateSignature(any()))
        .thenReturn(SafeFuture.completedFuture(true));
    when(blobKzgCommitmentsProvider.getBlobKzgCommitments(any(DataColumnSidecar.class)))
        .thenAnswer(
            invocation -> {
              final DataColumnSidecar sidecar = invocation.getArgument(0);
              return SafeFuture.completedFuture(sidecar.getMaybeKzgCommitments());
            });
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(true);
  }

  @Test
  void dataColumnSidecarIsCorrect() {
    final SignedBeaconBlock block1 = createBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(block1, UInt64.ZERO);
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

    assertDoesNotThrow(() -> validator.validate(dataColumnSidecar1_0));
  }

  @Test
  void dataColumnSidecarIdentifierNotRequested() {
    final SignedBeaconBlock block1 = createBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar2_0 =
        dataStructureUtil.randomDataColumnSidecar(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.increment()),
            UInt64.ZERO);
    final DataColumnIdentifier sidecarIdentifier1_0 =
        new DataColumnIdentifier(block1.getRoot(), UInt64.ZERO);
    validator =
        new DataColumnSidecarsByRootValidator(
            peer,
            spec,
            metricsSystem,
            timeProvider,
            dataColumnSidecarSignatureValidator,
            List.of(sidecarIdentifier1_0),
            blobKzgCommitmentsProvider);

    assertThatSafeFuture(validator.validate(dataColumnSidecar2_0))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  @Test
  void dataColumnSidecarFailsKzgVerification() {
    when(kzg.verifyCellProofBatch(any(), any(), any())).thenReturn(false);
    final SignedBeaconBlock block1 = createBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(block1, UInt64.ZERO);
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

    assertThatSafeFuture(validator.validate(dataColumnSidecar1_0))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED
                .describe())
        .hasMessageContaining("Invalid DataColumnSidecar KZG Proofs");
  }

  @Test
  void dataColumnSidecarKzgVerificationExceptionIsWrapped() {
    when(kzg.verifyCellProofBatch(any(), any(), any()))
        .thenThrow(new IllegalStateException("KZG verification exception"));
    final SignedBeaconBlock block1 = createBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(block1, UInt64.ZERO);
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

    assertThatSafeFuture(validator.validate(dataColumnSidecar1_0))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_KZG_VERIFICATION_FAILED
                .describe())
        .hasRootCauseMessage("KZG verification exception");
  }

  @Test
  void dataColumnSidecarResponseWithDuplicateSidecar() {
    final SignedBeaconBlock block1 = createBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(block1, UInt64.ZERO);
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

    assertDoesNotThrow(() -> validator.validate(dataColumnSidecar1_0).join());
    assertThatSafeFuture(validator.validate(dataColumnSidecar1_0))
        .isCompletedExceptionallyWith(DataColumnSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            DataColumnSidecarsResponseInvalidResponseException.InvalidResponseType
                .DATA_COLUMN_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  @Test
  void dataColumnSidecarFailsSignatureVerification() {
    when(dataColumnSidecarSignatureValidator.validateSignature(any()))
        .thenReturn(SafeFuture.completedFuture(false));
    final SignedBeaconBlock block1 = createBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(block1, UInt64.ZERO);
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

    // verifySignature is separate from validate
    assertThat(validator.verifySignature(dataColumnSidecar1_0)).isCompletedWithValue(false);
  }

  @Test
  void dataColumnSidecarFailsValidityCheck() {
    final SignedBeaconBlock block1 = createBlock(currentForkFirstSlot);
    final DataColumnSidecar dataColumnSidecar1_0 =
        dataStructureUtil.randomDataColumnSidecar(block1, UInt64.ZERO);
    final DataColumnSidecar dataColumnSidecar1_0_modified =
        createSidecarWithBrokenValidity(dataColumnSidecar1_0);
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
                .DATA_COLUMN_SIDECAR_VALIDITY_CHECK_FAILED
                .describe());
  }
}
