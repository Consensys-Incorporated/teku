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
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.networking.eth2.rpc.beaconchain.methods.BlobSidecarsByRootValidatorTest.breakInclusionProof;
import static tech.pegasys.teku.spec.SpecMilestone.DENEB;
import static tech.pegasys.teku.spec.SpecMilestone.ELECTRA;

import java.util.stream.IntStream;
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
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@SuppressWarnings("JavaCase")
@TestSpecContext(milestone = {DENEB, ELECTRA})
public class BlobSidecarsByRangeListenerValidatingProxyTest {

  private final UInt64 currentForkEpoch = UInt64.valueOf(1);
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private UInt64 currentForkFirstSlot;
  private BlobSidecarsByRangeListenerValidatingProxy listenerWrapper;
  private Integer maxBlobsPerBlock;
  private final Eth2Peer peer = mock(Eth2Peer.class);
  private final KZG kzg = mock(KZG.class);

  @SuppressWarnings("unchecked")
  private final RpcResponseListener<BlobSidecar> listener = mock(RpcResponseListener.class);

  @BeforeEach
  void setUp(final SpecContext specContext) {
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
    maxBlobsPerBlock = spec.getMaxBlobsPerBlockForHighestMilestone().orElseThrow();

    spec.reinitializeForTesting(
        AvailabilityCheckerFactory.NOOP_BLOB_SIDECAR,
        AvailabilityCheckerFactory.NOOP_DATACOLUMN_SIDECAR,
        kzg);

    when(listener.onResponse(any())).thenReturn(SafeFuture.completedFuture(null));
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(true);
  }

  @TestTemplate
  void blobSidecarFailsKzgVerification() {
    when(kzg.verifyBlobKzgProof(any(), any(), any())).thenReturn(false);
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot), 0);

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
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot), 0);
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

  @TestTemplate
  void blobSidecarSlotSmallerThanFromSlot() {
    final UInt64 startSlot = currentForkFirstSlot.plus(1);
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final BlobSidecar blobSidecar0_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot), 0);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar0_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_SLOT_NOT_IN_RANGE
                .describe());
  }

  @TestTemplate
  void blobSidecarsSlotsAreCorrect() {
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 1);
    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(
                currentForkFirstSlot.plus(1).longValue(), block1.getRoot()),
            0);
    final BlobSidecar blobSidecar3_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(
                currentForkFirstSlot.plus(2).longValue(), blobSidecar2_0.getBlockRoot()),
            0);
    final BlobSidecar blobSidecar4_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(
                currentForkFirstSlot.plus(3).longValue(), blobSidecar3_0.getBlockRoot()),
            0);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_1).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar2_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar3_0).join());
    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar4_0).join());
  }

  @TestTemplate
  void blobSidecarSlotGreaterThanToSlot() {
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(8);
    // This requests 8 slots (1, 2, 3, 4, 5, 6, 7, 8) so 9 will be unexpected.
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot), 0);
    final BlobSidecar blobSidecar3_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(2)), 0);
    final BlobSidecar blobSidecar5_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(4)), 0);
    final BlobSidecar blobSidecar8_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(7)), 0);
    final BlobSidecar blobSidecar9_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(
                currentForkFirstSlot.plus(8).longValue(), blobSidecar8_0.getBlockRoot()),
            0);

    safeJoin(listenerWrapper.onResponse(blobSidecar1_0));
    safeJoin(listenerWrapper.onResponse(blobSidecar3_0));
    safeJoin(listenerWrapper.onResponse(blobSidecar5_0));
    safeJoin(listenerWrapper.onResponse(blobSidecar8_0));

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar9_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_SLOT_NOT_IN_RANGE
                .describe());
  }

  @TestTemplate
  void blobSidecarParentRootDoesNotMatch() {
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot), 0);
    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(1)), 0);

    safeJoin(listenerWrapper.onResponse(blobSidecar1_0));

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNKNOWN_PARENT
                .describe());
  }

  @TestTemplate
  void blobSidecarIndexIsGreaterOrEqualThanMaxBlobs() {
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);

    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final int exceedingBlobCount = spec.getMaxBlobsPerBlockForHighestMilestone().orElseThrow() + 1;
    final int exceedingBlobIndex = exceedingBlobCount - 1;
    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(
            currentForkFirstSlot, exceedingBlobCount);

    IntStream.range(0, exceedingBlobCount - 1)
        .mapToObj(
            i -> dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, i))
        .forEach(blobSidecar -> safeJoin(listenerWrapper.onResponse(blobSidecar)));

    final SafeFuture<?> result =
        listenerWrapper.onResponse(
            dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
                block1, exceedingBlobIndex));

    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_INDEX
                .describe());
  }

  @TestTemplate
  void blobSidecarIndexIsInTheSameBlockButNotNext() {
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);

    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlockWithCommitments(currentForkFirstSlot, 3);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_2 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 2);

    safeJoin(listenerWrapper.onResponse(blobSidecar1_0));

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_2);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_INDEX
                .describe());
  }

  @TestTemplate
  void isFirstBlobSidecarAfterAnEmptyBlobsBlock() {
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);

    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final SignedBeaconBlock block1 =
        dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 0);
    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(block1, 1);
    final BlobSidecar blobSidecar3_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(3)), 0);

    safeJoin(listenerWrapper.onResponse(blobSidecar1_0));
    safeJoin(listenerWrapper.onResponse(blobSidecar1_1));
    safeJoin(listenerWrapper.onResponse(blobSidecar3_0));
  }

  @TestTemplate
  void firstBlobSidecarIndexIsINotZero() {
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final BlobSidecar blobSidecar1_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(1)), 1);

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_1);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_INDEX
                .describe());
  }

  @TestTemplate
  void firstBlobSidecarIndexInNextBlockIsNotZero() {
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot), 0);
    final BlobSidecar blobSidecar2_1 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(
                currentForkFirstSlot.plus(1).longValue(), blobSidecar1_0.getBlockRoot(), true),
            1);

    assertDoesNotThrow(() -> listenerWrapper.onResponse(blobSidecar1_0).join());

    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar2_1);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_INDEX
                .describe());
  }

  @TestTemplate
  void blobSidecarUnexpectedSlot() {
    final UInt64 startSlot = currentForkFirstSlot;
    final UInt64 count = UInt64.valueOf(4);
    listenerWrapper =
        new BlobSidecarsByRangeListenerValidatingProxy(
            spec, peer, listener, maxBlobsPerBlock, startSlot, count);

    final BlobSidecar blobSidecar2_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot.plus(1)), 0);
    final BlobSidecar blobSidecar1_0 =
        dataStructureUtil.randomBlobSidecarWithValidInclusionProofForBlock(
            dataStructureUtil.randomSignedBeaconBlock(currentForkFirstSlot), 0);

    safeJoin(listenerWrapper.onResponse(blobSidecar2_0));
    final SafeFuture<?> result = listenerWrapper.onResponse(blobSidecar1_0);
    assertThat(result).isCompletedExceptionally();
    assertThatThrownBy(result::get)
        .hasCauseExactlyInstanceOf(BlobSidecarsResponseInvalidResponseException.class);
    assertThatThrownBy(result::get)
        .hasMessageContaining(
            BlobSidecarsResponseInvalidResponseException.InvalidResponseType
                .BLOB_SIDECAR_UNEXPECTED_SLOT
                .describe());
  }
}
