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

package tech.pegasys.teku.spec.datastructures.state;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Objects;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockSummary;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

/**
 * Represents an "anchor" - a trusted, finalized (block, state, checkpoint) tuple from which we can
 * sync.
 *
 * <p>NOTE: Anchor point is a teku concept. <br>
 * - state needs to be the anchor_state as used in fork choice<br>
 * - checkpoint is an actual checkpoint as defined in fork choice, is the first slot of epoch<br>
 * - blockSummary is the block from the finalized anchor state
 */
public class AnchorPoint extends StateAndBlockSummary {
  private final Spec spec;
  private final Checkpoint checkpoint;
  private final boolean isGenesis;

  private AnchorPoint(
      final Spec spec,
      final Checkpoint checkpoint,
      final BeaconState state,
      final BeaconBlockSummary blockSummary) {
    super(blockSummary, state);
    checkArgument(
        checkpoint.getRoot().equals(blockSummary.getRoot()), "Checkpoint and block must match");
    checkArgument(
        checkpoint.getEpochStartSlot(spec).isGreaterThanOrEqualTo(blockSummary.getSlot()),
        "Block must be at or prior to the start of the checkpoint epoch");

    this.spec = spec;
    this.checkpoint = checkpoint;
    this.isGenesis = checkpoint.getEpoch().equals(SpecConfig.GENESIS_EPOCH);
  }

  public static AnchorPoint create(
      final Spec spec,
      final Checkpoint checkpoint,
      final BeaconState state,
      final Optional<SignedBeaconBlock> block) {
    final BeaconBlockSummary blockSummary =
        block.<BeaconBlockSummary>map(a -> a).orElseGet(() -> BeaconBlockHeader.fromState(state));
    return new AnchorPoint(spec, checkpoint, state, blockSummary);
  }

  public static AnchorPoint create(
      final Spec spec, final Checkpoint checkpoint, final SignedBlockAndState blockAndState) {
    return new AnchorPoint(spec, checkpoint, blockAndState.getState(), blockAndState.getBlock());
  }

  public static AnchorPoint fromGenesisState(final Spec spec, final BeaconState genesisState) {
    checkArgument(isGenesisState(genesisState), "Invalid genesis state supplied");

    final BeaconBlock genesisBlock = BeaconBlock.fromGenesisState(spec, genesisState);
    final SignedBeaconBlock signedGenesisBlock =
        SignedBeaconBlock.create(spec, genesisBlock, BLSSignature.empty());

    final Bytes32 genesisBlockRoot = genesisBlock.hashTreeRoot();
    final Bytes32 stateBodyRoot = genesisState.getLatestBlockHeader().getBodyRoot();
    checkArgument(
        stateBodyRoot.equals(genesisBlock.getBodyRoot()),
        "Genesis state latest block header body root %s does not match genesis block body root %s",
        stateBodyRoot,
        genesisBlock.getBodyRoot());
    final UInt64 genesisEpoch = spec.getCurrentEpoch(genesisState);
    final Checkpoint genesisCheckpoint = new Checkpoint(genesisEpoch, genesisBlockRoot);

    return new AnchorPoint(spec, genesisCheckpoint, genesisState, signedGenesisBlock);
  }

  public static AnchorPoint fromInitialState(final Spec spec, final BeaconState state) {
    if (isGenesisState(state)) {
      return fromGenesisState(spec, state);
    } else {
      final BeaconBlockHeader header = BeaconBlockHeader.fromState(state);

      // Calculate closest epoch boundary to use for the checkpoint
      final UInt64 epoch = spec.computeNextEpochBoundary(state.getSlot());
      final Checkpoint checkpoint = new Checkpoint(epoch, header.hashTreeRoot());

      return new AnchorPoint(spec, checkpoint, state, header);
    }
  }

  public static AnchorPoint fromInitialBlockAndState(
      final Spec spec, final SignedBlockAndState blockAndState) {
    return fromInitialBlockAndState(spec, blockAndState.getBlock(), blockAndState.getState());
  }

  private static boolean isGenesisState(final BeaconState state) {
    return state.getSlot().equals(SpecConfig.GENESIS_SLOT);
  }

  private static AnchorPoint fromInitialBlockAndState(
      final Spec spec, final SignedBeaconBlock block, final BeaconState state) {
    checkArgument(
        Objects.equals(block.getStateRoot(), state.hashTreeRoot()),
        "State must belong to the given block");

    // Calculate closest epoch boundary to use for the checkpoint
    final UInt64 epoch = spec.computeNextEpochBoundary(state.getSlot());
    final Checkpoint checkpoint = new Checkpoint(epoch, block.getRoot());

    return new AnchorPoint(spec, checkpoint, state, block);
  }

  public boolean isGenesis() {
    return isGenesis;
  }

  public Checkpoint getCheckpoint() {
    return checkpoint;
  }

  public UInt64 getBlockSlot() {
    return blockSummary.getSlot();
  }

  public UInt64 getEpoch() {
    return checkpoint.getEpoch();
  }

  public UInt64 getEpochStartSlot() {
    return checkpoint.getEpochStartSlot(spec);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final AnchorPoint that = (AnchorPoint) o;
    return isGenesis == that.isGenesis && Objects.equals(checkpoint, that.checkpoint);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), checkpoint, isGenesis);
  }
}
