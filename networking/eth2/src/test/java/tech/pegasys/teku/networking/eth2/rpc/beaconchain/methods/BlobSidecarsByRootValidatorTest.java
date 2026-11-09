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

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.networking.libp2p.rpc.BlobIdentifier;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("JavaCase")
@TestSpecContext(milestone = {DENEB, ELECTRA})
public class BlobSidecarsByRootValidatorTest {

  private final UInt64 currentForkEpoch = UInt64.valueOf(1);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private BlobSidecarsByRootValidator validator;
  private UInt64 currentForkFirstSlot;

  @BeforeEach
  void setUp(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec =
        switch (specContext.getSpecMilestone()) {
          case PHASE0 -> throw new IllegalArgumentException("Phase0 is an unsupported milestone");
          case ALTAIR -> throw new IllegalArgumentException("Altair is an unsupported milestone");
          case BELLATRIX ->
              throw new IllegalArgumentException("Bellatrix is an unsupported milestone");
          case CAPELLA -> throw new IllegalArgumentException("Capella is an unsupported milestone");
          case DENEB -> TestSpecFactory.createMinimalWithDenebForkEpoch(currentForkEpoch);
          case ELECTRA -> TestSpecFactory.createMinimalWithElectraForkEpoch(currentForkEpoch);
          case FULU -> TestSpecFactory.createMinimalWithFuluForkEpoch(currentForkEpoch);
          case GLOAS -> TestSpecFactory.createMinimalWithGloasForkEpoch(currentForkEpoch);
          case HEZE -> TestSpecFactory.createMinimalWithHezeForkEpoch(currentForkEpoch);
        };
    currentForkFirstSlot = spec.computeStartSlotAtEpoch(currentForkEpoch);
    dataStructureUtil = new DataStructureUtil(spec);
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(true);
  }

  @TestTemplate
  void blobSidecarIsCorrect() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final BlobIdentifier blobIdentifier1_0 = new BlobIdentifier(block1.getRoot(), UInt64.ZERO);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);

    validator = new BlobSidecarsByRootValidator(peer, spec, List.of(blobIdentifier1_0));
    assertDoesNotThrow(() -> validator.validate(blobSidecar1_0));
  }

  @TestTemplate
  void blobSidecarIdentifierNotRequested() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final BlobIdentifier blobIdentifier2_0 =
        new BlobIdentifier(dataStructureUtil.randomBytes32(), UInt64.ZERO);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);

    validator = new BlobSidecarsByRootValidator(peer, spec, List.of(blobIdentifier2_0));
    assertThatThrownBy(() -> validator.validate(blobSidecar1_0))
        .isExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  @TestTemplate
  void blobSidecarFailsKzgVerification() {
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(false);
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final BlobIdentifier blobIdentifier1_0 = new BlobIdentifier(block1.getRoot(), UInt64.ZERO);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);

    validator = new BlobSidecarsByRootValidator(peer, spec, List.of(blobIdentifier1_0));
    assertThatThrownBy(() -> validator.validate(blobSidecar1_0))
        .isExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_KZG_VERIFICATION_FAILED
                .describe());
  }

  @TestTemplate
  void blobSidecarFailsInclusionProofVerification() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final BlobIdentifier blobIdentifier1_0 = new BlobIdentifier(block1.getRoot(), UInt64.ZERO);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_0_modified = breakInclusionProof(blobSidecar1_0);

    validator = new BlobSidecarsByRootValidator(peer, spec, List.of(blobIdentifier1_0));
    assertThatThrownBy(() -> validator.validate(blobSidecar1_0_modified))
        .isExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED
                .describe());
  }

  @TestTemplate
  void blobSidecarResponseWithDuplicateSidecar() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final BlobIdentifier blobIdentifier1_0 = new BlobIdentifier(block1.getRoot(), UInt64.ZERO);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);

    validator = new BlobSidecarsByRootValidator(peer, spec, List.of(blobIdentifier1_0));
    assertDoesNotThrow(() -> validator.validate(blobSidecar1_0));
    assertThatThrownBy(() -> validator.validate(blobSidecar1_0))
        .isExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_IDENTIFIER
                .describe());
  }

  public static BlobSidecar breakInclusionProof(final BlobSidecar blobSidecar) {
    return blobSidecar
        .getSchema()
        .create(
            blobSidecar.getIndex(),
            blobSidecar.getBlob(),
            blobSidecar.getKZGCommitment(),
            blobSidecar.getKZGProof(),
            blobSidecar.getSignedBeaconBlockHeader(),
            IntStream.range(0, blobSidecar.getKzgCommitmentInclusionProof().size())
                .mapToObj(
                    index -> {
                      if (index == 0) {
                        return blobSidecar.getKzgCommitmentInclusionProof().get(index).get().not();
                      } else {
                        return blobSidecar.getKzgCommitmentInclusionProof().get(index).get();
                      }
                    })
                .toList());
  }
}
