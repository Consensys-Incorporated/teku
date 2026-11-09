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

package tech.pegasys.teku.spec.logic.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.spec.constants.LightClientConstants.EXECUTION_BLOCK_HASH_GINDEX_GLOAS;
import static tech.pegasys.teku.spec.constants.LightClientConstants.EXECUTION_PAYLOAD_GINDEX;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.ssz.collections.SszBytes32Vector;
import tech.pegasys.teku.infrastructure.ssz.tree.GIndexUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientFinalityUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientHeader;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientOptimisticUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.logic.common.helpers.Predicates;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(allMilestones = true, ignoredMilestones = SpecMilestone.PHASE0)
public class LightClientUtilTest {
  private Spec spec;
  private DataStructureUtil dataStructureUtil;
  private LightClientUtil lightClientUtil;

  @BeforeEach
  void setup(final TestSpecInvocationContextProvider.SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    lightClientUtil = spec.getLightClientUtilRequired(UInt64.ZERO);
  }

  @TestTemplate
  public void createLightClientBootstrap_shouldReturnValidBootstrap() {
    final SignedBlockAndState blockAndState =
        blockWithPostState(1, dataStructureUtil.randomBytes32());
    final BeaconState state = blockAndState.getState();
    final LightClientBootstrap bootstrap =
        lightClientUtil.createLightClientBootstrap(state, blockAndState.getBlock());

    assertHeaderMatchesBlock(bootstrap.getLightClientHeader(), blockAndState.getBlock());
    assertThat(bootstrap.getCurrentSyncCommittee())
        .isEqualTo(BeaconStateAltair.required(state).getCurrentSyncCommittee());
    assertProofReconstructsRoot(
        bootstrap.getCurrentSyncCommittee().hashTreeRoot(),
        bootstrap.getSyncCommitteeBranch(),
        fieldGIndex(state, BeaconStateFields.CURRENT_SYNC_COMMITTEE),
        state.hashTreeRoot());
  }

  @TestTemplate
  public void createLightClientBootstrap_shouldMatchBootstrapCreatedFromState() {
    final SignedBlockAndState blockAndState =
        blockWithPostState(1, dataStructureUtil.randomBytes32());
    final BeaconStateAltair state = BeaconStateAltair.required(blockAndState.getState());

    assertThat(
            lightClientUtil.createLightClientBootstrap(
                blockAndState.getBlock(),
                state.getCurrentSyncCommittee(),
                state.createCurrentSyncCommitteeProof()))
        .isEqualTo(
            lightClientUtil.createLightClientBootstrap(
                blockAndState.getState(), blockAndState.getBlock()));
  }

  @TestTemplate
  public void currentSyncCommitteeProof_shouldReconstructStateRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final BeaconStateAltair stateAltair = BeaconStateAltair.required(state);

    assertProofReconstructsStateRoot(
        state,
        stateAltair.getCurrentSyncCommittee().hashTreeRoot(),
        stateAltair.createCurrentSyncCommitteeProof(),
        fieldGIndex(state, BeaconStateFields.CURRENT_SYNC_COMMITTEE));
  }

  @TestTemplate
  public void nextSyncCommitteeProof_shouldReconstructStateRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final BeaconStateAltair stateAltair = BeaconStateAltair.required(state);

    assertProofReconstructsStateRoot(
        state,
        stateAltair.getNextSyncCommittee().hashTreeRoot(),
        stateAltair.createNextSyncCommitteeProof(),
        fieldGIndex(state, BeaconStateFields.NEXT_SYNC_COMMITTEE));
  }

  @TestTemplate
  public void finalityBranchProof_shouldReconstructStateRoot() {
    final BeaconState state = dataStructureUtil.randomBeaconState();

    assertProofReconstructsStateRoot(
        state,
        state.getFinalizedCheckpoint().getRoot(),
        BeaconStateAltair.required(state).createFinalityBranchProof(),
        finalizedRootGIndex(state));
  }

  @TestTemplate
  public void createLightClientUpdate_shouldProveNextSyncCommitteeWithinSamePeriod() {
    final SignedBlockAndState attested = blockWithPostState(1, dataStructureUtil.randomBytes32());
    final SignedBlockAndState signature = blockWithPostState(2, attested.getBlock().getRoot());

    final LightClientUpdate update =
        lightClientUtil.createLightClientUpdate(
            signature.getState(),
            signature.getBlock(),
            attested.getState(),
            attested.getBlock(),
            Optional.empty());

    assertThat(update.getSignatureSlot().get()).isEqualTo(signature.getBlock().getSlot());
    assertThat(update.getNextSyncCommittee())
        .isEqualTo(BeaconStateAltair.required(attested.getState()).getNextSyncCommittee());
    assertThat(update.getFinalityBranch().stream().allMatch(root -> root.get().isZero())).isTrue();

    assertProofReconstructsStateRoot(
        attested.getState(),
        update.getNextSyncCommittee().hashTreeRoot(),
        update.getNextSyncCommitteeBranch(),
        fieldGIndex(attested.getState(), BeaconStateFields.NEXT_SYNC_COMMITTEE));
  }

  @TestTemplate
  public void createLightClientUpdate_shouldUseDefaultsWhenSignatureSlotInLaterPeriod() {
    final SpecConfigAltair config = SpecConfigAltair.required(spec.getGenesisSpecConfig());
    final long nextPeriodSlot =
        (long) config.getEpochsPerSyncCommitteePeriod() * config.getSlotsPerEpoch() + 1;
    final SignedBlockAndState attested = blockWithPostState(1, dataStructureUtil.randomBytes32());
    final SignedBlockAndState signature =
        blockWithPostState(nextPeriodSlot, attested.getBlock().getRoot());

    final LightClientUpdate update =
        lightClientUtil.createLightClientUpdate(
            signature.getState(),
            signature.getBlock(),
            attested.getState(),
            attested.getBlock(),
            Optional.empty());

    final LightClientUpdateSchema schema =
        SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
            .getLightClientUpdateSchema();
    assertThat(update.getNextSyncCommittee())
        .isEqualTo(schema.getNextSyncCommitteeSchema().getDefault());
    assertThat(update.getNextSyncCommitteeBranch())
        .isEqualTo(schema.getSyncCommitteeBranchSchema().getDefault());
  }

  @TestTemplate
  public void createLightClientUpdate_shouldProveFinalizedHeaderFromAttestedState() {
    final SignedBeaconBlock finalizedBlock = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBlockAndState attested =
        blockWithPostState(
            2,
            dataStructureUtil.randomBytes32(),
            Optional.of(new Checkpoint(UInt64.ZERO, finalizedBlock.getRoot())));
    final SignedBlockAndState signature = blockWithPostState(3, attested.getBlock().getRoot());

    final LightClientUpdate update =
        lightClientUtil.createLightClientUpdate(
            signature.getState(),
            signature.getBlock(),
            attested.getState(),
            attested.getBlock(),
            Optional.of(finalizedBlock));

    assertHeaderMatchesBlock(update.getFinalizedHeader(), finalizedBlock);

    assertProofReconstructsStateRoot(
        attested.getState(),
        finalizedBlock.getRoot(),
        update.getFinalityBranch(),
        finalizedRootGIndex(attested.getState()));
  }

  @TestTemplate
  public void withFinalizedHeader_shouldMatchUpdateCreatedWithFinalizedBlock() {
    final SignedBeaconBlock finalizedBlock = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBlockAndState attested =
        blockWithPostState(
            2,
            dataStructureUtil.randomBytes32(),
            Optional.of(new Checkpoint(UInt64.ZERO, finalizedBlock.getRoot())));
    final SignedBlockAndState signature = blockWithPostState(3, attested.getBlock().getRoot());
    final LightClientUpdate withoutFinality =
        lightClientUtil.createLightClientUpdate(
            signature.getState(),
            signature.getBlock(),
            attested.getState(),
            attested.getBlock(),
            Optional.empty());

    assertThat(
            lightClientUtil.withFinalizedHeader(
                withoutFinality,
                BeaconStateAltair.required(attested.getState()).createFinalityBranchProof(),
                finalizedBlock))
        .isEqualTo(
            lightClientUtil.createLightClientUpdate(
                signature.getState(),
                signature.getBlock(),
                attested.getState(),
                attested.getBlock(),
                Optional.of(finalizedBlock)));
  }

  @TestTemplate
  public void createLightClientUpdate_shouldRejectFinalizedBlockNotMatchingCheckpoint() {
    final SignedBeaconBlock finalizedBlock = dataStructureUtil.randomSignedBeaconBlock(1);
    final SignedBlockAndState attested = blockWithPostState(2, dataStructureUtil.randomBytes32());
    final SignedBlockAndState signature = blockWithPostState(3, attested.getBlock().getRoot());

    assertThatThrownBy(
            () ->
                lightClientUtil.createLightClientUpdate(
                    signature.getState(),
                    signature.getBlock(),
                    attested.getState(),
                    attested.getBlock(),
                    Optional.of(finalizedBlock)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("finalized checkpoint");
  }

  @TestTemplate
  public void createLightClientUpdate_shouldUseDefaultHeaderForGenesisFinalizedBlock() {
    final SignedBeaconBlock genesisBlock = dataStructureUtil.randomSignedBeaconBlock(0);
    final SignedBlockAndState attested =
        blockWithPostState(
            2,
            dataStructureUtil.randomBytes32(),
            Optional.of(new Checkpoint(UInt64.ZERO, Bytes32.ZERO)));
    final SignedBlockAndState signature = blockWithPostState(3, attested.getBlock().getRoot());

    final LightClientUpdate update =
        lightClientUtil.createLightClientUpdate(
            signature.getState(),
            signature.getBlock(),
            attested.getState(),
            attested.getBlock(),
            Optional.of(genesisBlock));

    assertThat(update.getFinalizedHeader())
        .isEqualTo(
            SchemaDefinitionsAltair.required(spec.getGenesisSchemaDefinitions())
                .getLightClientHeaderSchema()
                .getDefault());

    assertProofReconstructsStateRoot(
        attested.getState(),
        Bytes32.ZERO,
        update.getFinalityBranch(),
        finalizedRootGIndex(attested.getState()));
  }

  @TestTemplate
  public void createLightClientUpdate_shouldRejectStateThatIsNotThePostStateOfTheBlock() {
    final SignedBlockAndState attested = blockWithPostState(1, dataStructureUtil.randomBytes32());
    final SignedBlockAndState signature = blockWithPostState(2, attested.getBlock().getRoot());
    final SignedBlockAndState unrelated = blockWithPostState(2, attested.getBlock().getRoot());

    assertThatThrownBy(
            () ->
                lightClientUtil.createLightClientUpdate(
                    unrelated.getState(),
                    signature.getBlock(),
                    attested.getState(),
                    attested.getBlock(),
                    Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("post-state of the signature block");
  }

  @TestTemplate
  public void createLightClientFinalityUpdate_shouldCarryOverUpdateFields() {
    final LightClientUpdate update = dataStructureUtil.randomLightClientUpdate(UInt64.ZERO);

    final LightClientFinalityUpdate finalityUpdate =
        lightClientUtil.createLightClientFinalityUpdate(update);

    assertThat(finalityUpdate.getAttestedHeader()).isEqualTo(update.getAttestedHeader());
    assertThat(finalityUpdate.getFinalizedHeader()).isEqualTo(update.getFinalizedHeader());
    assertThat(finalityUpdate.getFinalityBranch()).isEqualTo(update.getFinalityBranch());
    assertThat(finalityUpdate.getSyncAggregate()).isEqualTo(update.getSyncAggregate());
    assertThat(finalityUpdate.getSignatureSlot()).isEqualTo(update.getSignatureSlot());
  }

  @TestTemplate
  public void createLightClientOptimisticUpdate_shouldCarryOverUpdateFields() {
    final LightClientUpdate update = dataStructureUtil.randomLightClientUpdate(UInt64.ZERO);

    final LightClientOptimisticUpdate optimisticUpdate =
        lightClientUtil.createLightClientOptimisticUpdate(update);

    assertThat(optimisticUpdate.getAttestedHeader()).isEqualTo(update.getAttestedHeader());
    assertThat(optimisticUpdate.getSyncAggregate()).isEqualTo(update.getSyncAggregate());
    assertThat(optimisticUpdate.getSignatureSlot()).isEqualTo(update.getSignatureSlot());
  }

  private SignedBlockAndState blockWithPostState(final long slot, final Bytes32 parentRoot) {
    return blockWithPostState(slot, parentRoot, Optional.empty());
  }

  private SignedBlockAndState blockWithPostState(
      final long slot, final Bytes32 parentRoot, final Optional<Checkpoint> finalizedCheckpoint) {
    final SignedBeaconBlock candidate = dataStructureUtil.randomSignedBeaconBlock(slot, parentRoot);
    final BeaconState state =
        dataStructureUtil
            .randomBeaconState(UInt64.valueOf(slot))
            .updated(
                mutableState -> {
                  mutableState.setLatestBlockHeader(
                      new BeaconBlockHeader(
                          candidate.getSlot(),
                          candidate.getMessage().getProposerIndex(),
                          parentRoot,
                          Bytes32.ZERO,
                          candidate.getMessage().getBodyRoot()));
                  finalizedCheckpoint.ifPresent(mutableState::setFinalizedCheckpoint);
                });
    return new SignedBlockAndState(
        SignedBeaconBlock.create(
            spec,
            candidate.getMessage().withStateRoot(state.hashTreeRoot()),
            candidate.getSignature()),
        state);
  }

  private long fieldGIndex(final BeaconState state, final BeaconStateFields field) {
    return state.getSchema().getChildGeneralizedIndex(state.getSchema().getFieldIndex(field));
  }

  private long finalizedRootGIndex(final BeaconState state) {
    return GIndexUtil.gIdxCompose(
        fieldGIndex(state, BeaconStateFields.FINALIZED_CHECKPOINT),
        Checkpoint.SSZ_SCHEMA.getChildGeneralizedIndex(
            Checkpoint.SSZ_SCHEMA.getFieldIndex("root")));
  }

  private void assertHeaderMatchesBlock(
      final LightClientHeader header, final SignedBeaconBlock block) {
    assertThat(header.getBeacon()).isEqualTo(BeaconBlockHeader.fromBlock(block.getMessage()));

    final Bytes32 bodyRoot = block.getMessage().getBody().hashTreeRoot();
    header
        .toVersionCapella()
        .ifPresent(
            capella ->
                assertProofReconstructsRoot(
                    capella.getExecution().hashTreeRoot(),
                    capella.getExecutionBranch(),
                    EXECUTION_PAYLOAD_GINDEX,
                    bodyRoot));
    header
        .toVersionGloas()
        .ifPresent(
            gloas ->
                assertProofReconstructsRoot(
                    gloas.getExecutionBlockHash().get(),
                    gloas.getExecutionBranch(),
                    EXECUTION_BLOCK_HASH_GINDEX_GLOAS,
                    bodyRoot));
  }

  private void assertProofReconstructsStateRoot(
      final BeaconState state,
      final Bytes32 leaf,
      final SszBytes32Vector branch,
      final long gIndex) {
    assertProofReconstructsRoot(leaf, branch, gIndex, state.hashTreeRoot());
  }

  private void assertProofReconstructsRoot(
      final Bytes32 leaf, final SszBytes32Vector branch, final long gIndex, final Bytes32 root) {
    final int depth = branch.size();
    assertThat(depth).isEqualTo(GIndexUtil.gIdxGetDepth(gIndex));

    final int index = GIndexUtil.gIdxGetChildIndex(gIndex, depth);
    assertThat(
            new Predicates(spec.getGenesisSpecConfig())
                .isValidMerkleBranch(leaf, branch, depth, index, root))
        .isTrue();
  }
}
