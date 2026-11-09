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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRootValidatorTest.breakInclusionProof;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.kzg.KZG;
import tech.pegasys.teku.networking.eth2.peers.Eth2Peer;
import tech.pegasys.teku.networking.p2p.rpc.RpcResponseListener;
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
public class BlobSidecarsByRootListenerValidatingProxyTest {

  private final UInt64 currentForkEpoch = UInt64.valueOf(1);
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<BlobSidecar> listener = mock(RpcResponseListener.class);

  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private UInt64 currentForkFirstSlot;
  private BlobSidecarsByRootListenerValidatingProxy listenerWrapper;

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
    dataStructureUtil = new DataStructureUtil(spec);
    currentForkFirstSlot = spec.computeStartSlotAtEpoch(currentForkEpoch);
    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);
    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(true);
  }

  @TestTemplate
  void blobSidecarsAreCorrect() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final SignedBeaconBlock block2 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(1));
    final SignedBeaconBlock block3 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(2));
    final SignedBeaconBlock block4 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(3));
    final List<BlobIdentifier> blobIdentifiers =
        List.of(
            new BlobIdentifier(block1.getRoot(), UInt64.ZERO),
            new BlobIdentifier(block1.getRoot(), UInt64.ONE),
            new BlobIdentifier(block2.getRoot(), UInt64.ZERO),
            new BlobIdentifier(block2.getRoot(), UInt64.ONE), // will be missed, shouldn't be fatal
            new BlobIdentifier(block3.getRoot(), UInt64.ZERO),
            new BlobIdentifier(block4.getRoot(), UInt64.ZERO));
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(peer, spec, listener, blobIdentifiers);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 1);
    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block2, 0);
    final BlobSidecar blobSidecar3_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block3, 0);
    final BlobSidecar blobSidecar4_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block4, 0);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar2_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar3_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar4_0).join());
  }

  @TestTemplate
  void blobSidecarIdentifierNotRequested() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final SignedBeaconBlock block2 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(1));
    final List<BlobIdentifier> blobIdentifiers =
        List.of(
            new BlobIdentifier(block1.getRoot(), UInt64.ZERO),
            new BlobIdentifier(block1.getRoot(), UInt64.ONE));
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(peer, spec, listener, blobIdentifiers);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 1);
    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block2, 0);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_1).join());
    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
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
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(
            block1.getRoot(), spec.computeStartSlotAtEpoch(currentForkEpoch.minus(1)));
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, List.of(blobIdentifier));

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_KZG_VERIFICATION_FAILED
                .describe());
  }

  @TestTemplate
  void blobSidecarFailsInclusionProofVerification() {
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final BlobIdentifier blobIdentifier =
        new BlobIdentifier(
            block1.getRoot(), spec.computeStartSlotAtEpoch(currentForkEpoch.minus(1)));
    listenerWrapper =
        new BlobSidecarsByRootListenerValidatingProxy(
            peer, spec, listener, List.of(blobIdentifier));

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_0_modified = breakInclusionProof(blobSidecar1_0);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_0_modified);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_INCLUSION_PROOF_VERIFICATION_FAILED
                .describe());
  }
}
