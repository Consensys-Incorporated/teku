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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.spec.SpecMilestone.ALTAIR;
import static tech.pegasys.teku.spec.SpecMilestone.BELLATRIX;
import static tech.pegasys.teku.spec.SpecMilestone.CAPELLA;
import static tech.pegasys.teku.spec.SpecMilestone.PHASE0;
import static tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregateAssert.assertThatSyncAggregate;
import static tech.pegasys.teku.spec.networks.Eth2Network.MINIMAL;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.builder.SpecConfigBuilder;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockContainer;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.altair.SyncAggregate;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.capella.BeaconBlockBodyCapella;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.operations.SignedBlsToExecutionChange;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class BlockFactoryPhase0Test extends AbstractBlockFactoryTest {
  private final Consumer<SpecConfigBuilder> configAdapter =
      builder -> builder.blsSignatureVerifier(BLSSignatureVerifier.NOOP);

  @Test
  public void shouldCreateBlockAfterNormalSlot() {
    assertBlockCreated(
        1, TestSpecFactory.create(PHASE0, MINIMAL, configAdapter), false, state -> {}, false);
  }

  @Test
  public void shouldCreateBlockAfterSkippedSlot() {
    assertBlockCreated(
        2, TestSpecFactory.create(PHASE0, MINIMAL, configAdapter), false, state -> {}, false);
  }

  @Test
  public void shouldCreateBlockAfterMultipleSkippedSlot() {
    assertBlockCreated(
        5, TestSpecFactory.create(PHASE0, MINIMAL, configAdapter), false, state -> {}, false);
  }

  @Test
  void shouldIncludeSyncAggregateWhenAltairIsActive() {
    final BeaconBlock block =
        assertBlockCreated(
                1,
                TestSpecFactory.create(ALTAIR, MINIMAL, configAdapter),
                false,
                state -> {},
                false)
            .blockContainer()
            .getBlock();
    final SyncAggregate result = getSyncAggregate(block);
    assertThatSyncAggregate(result).isNotNull();
    verify(syncCommitteeContributionPool)
        .createSyncAggregateForBlock(UInt64.ONE, block.getParentRoot());
  }

  @Test
  void shouldIncludeExecutionPayloadWhenBellatrixIsActive() {
    final Spec spec = TestSpecFactory.create(BELLATRIX, MINIMAL, configAdapter);
    final BeaconBlock block =
        assertBlockCreated(1, spec, false, state -> prepareDefaultPayload(spec), false)
            .blockContainer()
            .getBlock();
    final ExecutionPayload result = getExecutionPayload(block);
    assertThat(result).isEqualTo(executionPayload);
  }

  @Test
  void shouldCreateCapellaBlock() {
    final Spec spec = TestSpecFactory.create(CAPELLA, MINIMAL, configAdapter);
    final BeaconBlock block =
        assertBlockCreated(1, spec, true, state -> prepareValidPayload(spec, state), false)
            .blockContainer()
            .getBlock();
    final SszList<SignedBlsToExecutionChange> blsToExecutionChanges =
        BeaconBlockBodyCapella.required(block.getBody()).getBlsToExecutionChanges();
    assertThat(blsToExecutionChanges).isNotNull();
  }

  @Test
  void shouldIncludeExecutionPayloadHeaderWhenBellatrixIsActiveAndBlindedBlockRequested() {
    final Spec spec = TestSpecFactory.create(BELLATRIX, MINIMAL, configAdapter);
    final BeaconBlock block =
        assertBlockCreated(1, spec, false, state -> prepareDefaultPayload(spec), true)
            .blockContainer()
            .getBlock();
    final ExecutionPayloadHeader result = getExecutionPayloadHeader(block);
    assertThat(result).isEqualTo(executionPayloadHeader);
  }

  @Test
  void shouldThrowPostMergeWithWrongPayload() {
    final Spec spec = TestSpecFactory.create(BELLATRIX, MINIMAL, configAdapter);
    assertThatThrownBy(
            () -> assertBlockCreated(1, spec, true, state -> prepareDefaultPayload(spec), false))
        .hasCauseInstanceOf(StateTransitionException.class);
  }

  @Test
  void unblindSignedBlock_shouldThrowWhenUnblindingBlockWithInconsistentExecutionPayload() {
    final Spec spec = TestSpecFactory.create(BELLATRIX, MINIMAL, configAdapter);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock signedBlock = dataStructureUtil.randomSignedBlindedBeaconBlock(1);
    executionPayload = dataStructureUtil.randomExecutionPayload();

    assertThatThrownBy(() -> assertBlockUnblinded(signedBlock, spec))
        .hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void unblindSignedBlock_shouldPassthroughUnblindedBlocks() {
    final Spec spec = TestSpecFactory.create(BELLATRIX, MINIMAL, configAdapter);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalUnblindedSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    final SignedBlockContainer unblindedSignedBlockContainer =
        assertBlockUnblinded(originalUnblindedSignedBlock, spec);

    assertThat(unblindedSignedBlockContainer).isEqualTo(originalUnblindedSignedBlock);
  }

  @Test
  void unblindSignedBlock_shouldPassthroughInNonBellatrixBlocks() {
    final Spec spec = TestSpecFactory.create(ALTAIR, MINIMAL, configAdapter);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalAltairSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    final SignedBlockContainer unblindedSignedBlockContainer =
        assertBlockUnblinded(originalAltairSignedBlock, spec);

    assertThat(unblindedSignedBlockContainer).isEqualTo(originalAltairSignedBlock);
  }

  @Test
  void unblindSignedBlock_shouldUnblindBlockWhenBellatrixIsActive() {
    final Spec spec = TestSpecFactory.create(BELLATRIX, MINIMAL, configAdapter);
    final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

    final SignedBeaconBlock originalUnblindedSignedBlock =
        dataStructureUtil.randomSignedBeaconBlock(1);

    // now we have a blinded block
    final SignedBeaconBlock originalBlindedSignedBlock =
        assertBlockBlinded(originalUnblindedSignedBlock, spec);

    // let the unblinder return a consistent execution payload
    executionPayload =
        originalUnblindedSignedBlock
            .getMessage()
            .getBody()
            .getOptionalExecutionPayload()
            .orElseThrow();

    assertBlockUnblinded(originalBlindedSignedBlock, spec);
  }

  @Test
  void shouldCreateEmptyBlobSidecarsForBlock() {
    final BlockAndBlobSidecars blockAndBlobSidecars =
        createBlockAndBlobSidecars(false, TestSpecFactory.create(PHASE0, MINIMAL, configAdapter));

    assertThat(blockAndBlobSidecars.blobSidecars()).isEmpty();
  }

  @Test
  void shouldCreateEmptyBlobSidecarsForBlindedBlock() {
    final BlockAndBlobSidecars blockAndBlobSidecars =
        createBlockAndBlobSidecars(true, TestSpecFactory.create(PHASE0, MINIMAL, configAdapter));

    assertThat(blockAndBlobSidecars.blobSidecars()).isEmpty();
  }

  @Override
  public BlockFactory createBlockFactory(final Spec spec) {
    return new BlockFactoryPhase0(
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
