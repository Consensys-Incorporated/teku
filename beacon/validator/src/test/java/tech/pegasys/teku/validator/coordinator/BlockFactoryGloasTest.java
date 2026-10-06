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

package tech.pegasys.teku.validator.coordinator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethereum.performance.trackers.BlockPublishingPerformance;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyGloas;
import tech.pegasys.teku.spec.datastructures.blocks.versions.gloas.BlockContentsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

public class BlockFactoryGloasTest extends AbstractBlockFactoryTest {

  private final Spec spec =
      TestSpecFactory.createMinimalGloas(
          builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP));
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  @Test
  void shouldCreateBlock() {
    prepareBlobsBundle(spec, 3);

    final BlockContainer blockContainer =
        assertBlockCreated(1, spec, false, state -> prepareValidPayload(spec, state), false, false)
            .blockContainer();

    assertThat(blockContainer).isInstanceOf(BeaconBlock.class);
    assertThat(blockContainer.getBlock().getBody()).isInstanceOf(BeaconBlockBodyGloas.class);
  }

  @Test
  void shouldCreateBlockContentsWhenIncludePayloadIsTrue() {
    prepareBlobsBundle(spec, 3);

    final BlockContainer blockContainer =
        assertBlockCreated(1, spec, false, state -> prepareValidPayload(spec, state), false, true)
            .blockContainer();

    assertThat(blockContainer).isInstanceOf(BlockContentsGloas.class);
    assertThat(blockContainer.getExecutionPayloadEnvelope()).isPresent();
    assertThat(blockContainer.getBlock().getBody()).isInstanceOf(BeaconBlockBodyGloas.class);
  }

  @Test
  void unblindSignedBlock_shouldPassthroughUnblindedBlock() {
    final SignedBeaconBlock signedBlock = dataStructureUtil.randomSignedBeaconBlock();
    final SignedBeaconBlock unblindedSignedBlock = assertBlockUnblinded(signedBlock, spec);
    assertThat(unblindedSignedBlock).isEqualTo(signedBlock);
  }

  // this theoretically will never happen, but testing throwing the exception
  @Test
  void unblindSignedBlock_shouldFailIfBlockIsBlinded() {
    final SignedBeaconBlock signedBlindedBlock = mock(SignedBeaconBlock.class);
    when(signedBlindedBlock.isBlinded()).thenReturn(true);
    final BlockFactory blockFactory = createBlockFactory(spec);
    assertThatThrownBy(
            () ->
                blockFactory
                    .unblindSignedBlockIfBlinded(
                        signedBlindedBlock, BlockPublishingPerformance.NOOP)
                    .join())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Blocks in ePBS should be all unblinded");
  }

  @Override
  public BlockFactory createBlockFactory(final Spec spec) {
    return new BlockFactoryGloas(
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
