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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.versions.deneb.BlockContentsDeneb;
import tech.pegasys.teku.spec.datastructures.execution.BlobsBundle;
import tech.pegasys.teku.spec.datastructures.type.SszKZGCommitment;
import tech.pegasys.teku.spec.datastructures.type.SszKZGProof;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockFactoryDenebTest extends AbstractBlockFactoryTest {

  private final Spec spec =
      TestSpecFactory.createMinimalDeneb(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldCreateBlockContents() {

    final BlobsBundle blobsBundle = prepareBlobsBundle(spec, 3);

    final BlockContainer blockContainer =
        assertBlockCreated(1, spec, false, state -> prepareValidPayload(spec, state), false)
            .blockContainer();

    assertThat(blockContainer).isInstanceOf(BlockContentsDeneb.class);
    assertThat(blockContainer.getBlock().getBody().getOptionalBlobKzgCommitments())
        .hasValueSatisfying(blobKzgCommitments -> assertThat(blobKzgCommitments).hasSize(3));
    assertThat(blockContainer.getBlobs())
        .map(SszCollection::asList)
        .hasValue(blobsBundle.getBlobs());
    assertThat(blockContainer.getKzgProofs())
        .map(proofs -> proofs.stream().map(SszKZGProof::getKZGProof).toList())
        .hasValue(blobsBundle.getProofs());
  }

  @Test
  void shouldCreateBlindedBeaconBlockWhenBlindedBlockRequested() {

    final SszList<SszKZGCommitment> blobKzgCommitments = prepareBuilderBlobKzgCommitments(spec, 3);

    final BlockContainer blockContainer =
        assertBlockCreated(1, spec, false, state -> prepareValidPayload(spec, state), true)
            .blockContainer();

    assertThat(blockContainer).isInstanceOf(BeaconBlock.class);
    final BeaconBlock blindedBeaconBlock = (BeaconBlock) blockContainer;
    assertThat(blindedBeaconBlock.getBlock().getBody().getOptionalBlobKzgCommitments())
        .hasValue(blobKzgCommitments);
  }

  @Test
  void unblindSignedBlock_shouldPassthroughUnblindedBlock() {

    final SignedBeaconBlock signedBlock = dataStructureUtil.randomSignedBeaconBlock();

    final SignedBeaconBlock unblindedSignedBlock = assertBlockUnblinded(signedBlock, spec);

    assertThat(unblindedSignedBlock).isEqualTo(signedBlock);
  }

  @Test
  void unblindSignedBlock_shouldUnblindBeaconBlock() {

    final SignedBeaconBlock expectedUnblindedBlock = dataStructureUtil.randomSignedBeaconBlock();
    final SignedBeaconBlock blindedBlock = assertBlockBlinded(expectedUnblindedBlock, spec);

    // let the unblinder return a consistent execution payload
    executionPayload =
        expectedUnblindedBlock.getMessage().getBody().getOptionalExecutionPayload().orElseThrow();

    final SignedBeaconBlock unblindedBlock = assertBlockUnblinded(blindedBlock, spec);

    verify(executionLayer).getUnblindedPayload(unblindedBlock, BlockPublishingPerformance.NOOP);

    assertThat(unblindedBlock.isBlinded()).isFalse();
    assertThat(unblindedBlock).isEqualTo(expectedUnblindedBlock);
  }

  @Test
  void shouldCreateValidBlobSidecarsForBlockContents() {
    final Spec spec = TestSpecFactory.createMinimalDeneb();
    final int blobsCount = 3;
    final BlobsBundle blobsBundle = prepareBlobsBundle(spec, blobsCount);

    final BlockAndBlobSidecars blockAndBlobSidecars = createBlockAndBlobSidecars(false, spec);

    final List<BlobSidecar> blobSidecars = blockAndBlobSidecars.blobSidecars();

    verifyNoInteractions(executionLayer);

    final SszList<SszKZGCommitment> expectedCommitments =
        blockAndBlobSidecars
            .block()
            .getSignedBlock()
            .getMessage()
            .getBody()
            .getOptionalBlobKzgCommitments()
            .orElseThrow();

    assertThat(blobSidecars).hasSize(blobsCount).hasSameSizeAs(expectedCommitments);

    IntStream.range(0, blobSidecars.size())
        .forEach(
            index -> {
              final BlobSidecar blobSidecar = blobSidecars.get(index);
              // check sidecar is created using the prepared BlobsBundle
              assertThat(blobSidecar.getKZGProof()).isEqualTo(blobsBundle.getProofs().get(index));
              assertThat(blobSidecar.getBlob()).isEqualTo(blobsBundle.getBlobs().get(index));
              assertThat(blobSidecar.getSszKZGCommitment())
                  .isEqualTo(expectedCommitments.get(index));
            });
  }

  @Test
  void shouldCreateValidBlobSidecarsForBlindedBlock() {
    final Spec spec = TestSpecFactory.createMinimalDeneb();
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    // random payload required to construct a valid BuilderPayload
    executionPayload = dataStructureUtil.randomExecutionPayload();

    final int blobsCount = 3;
    final tech.pegasys.teku.spec.datastructures.builder.BlobsBundle blobsBundle =
        prepareBuilderPayload(spec, blobsCount).getOptionalBlobsBundle().orElseThrow();

    final BlockAndBlobSidecars blockAndBlobSidecars = createBlockAndBlobSidecars(true, spec);

    final SignedBlockContainer block = blockAndBlobSidecars.block();
    final List<BlobSidecar> blobSidecars = blockAndBlobSidecars.blobSidecars();

    verify(executionLayer).getCachedUnblindedPayload(block.getSlot());

    final SszList<SszKZGCommitment> expectedCommitments =
        block.getSignedBlock().getMessage().getBody().getOptionalBlobKzgCommitments().orElseThrow();

    assertThat(blobSidecars).hasSize(blobsCount).hasSameSizeAs(expectedCommitments);

    IntStream.range(0, blobSidecars.size())
        .forEach(
            index -> {
              final BlobSidecar blobSidecar = blobSidecars.get(index);
              // check sidecar is created using the cached BuilderPayload
              assertThat(blobSidecar.getSszKZGProof())
                  .isEqualTo(blobsBundle.getProofs().get(index));
              assertThat(blobSidecar.getBlob()).isEqualTo(blobsBundle.getBlobs().get(index));
              assertThat(blobSidecar.getSszKZGCommitment())
                  .isEqualTo(expectedCommitments.get(index));
            });
  }

  @Override
  public BlockFactory createBlockFactory(final Spec spec) {
    return new BlockFactoryDeneb(
        spec,
        new BlockOperationSelectorFactory(
            spec,
            attestationsPool,
            attesterSlashingPool,
            proposerSlashingPool,
            voluntaryExitPool,
            blsToExecutionChangePool,
            syncCommitteeContributionPool,
            payloadAttestationPool,
            depositProvider,
            eth1DataCache,
            graffitiBuilder,
            forkChoiceNotifier,
            executionLayer,
            executionPayloadBidManager,
            executionPayloadManager,
            metricsSystem,
            timeProvider));
  }
}
