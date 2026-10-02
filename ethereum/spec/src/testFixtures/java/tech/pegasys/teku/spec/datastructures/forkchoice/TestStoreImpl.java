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

package tech.pegasys.teku.spec.datastructures.forkchoice;

import static java.util.stream.Collectors.toList;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.BlockCheckpoints;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.SlotAndBlockRoot;
import tech.pegasys.teku.spec.datastructures.blocks.StateAndBlockSummary;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedBlindedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.SlotAndExecutionPayloadSummary;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.CheckpointState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class TestStoreImpl implements MutableStore, VoteUpdater {
  private final Spec spec;
  protected UInt64 timeMillis;
  protected UInt64 genesisTime;
  protected final Optional<Checkpoint> initialCheckpoint;
  protected Checkpoint justifiedCheckpoint;
  protected Checkpoint finalizedCheckpoint;
  protected Checkpoint bestJustifiedCheckpoint;
  protected Map<Bytes32, SignedBeaconBlock> blocks;
  protected Map<Bytes32, BeaconState> blockStates;
  protected Map<Bytes32, BlockCheckpoints> blockCheckpoints;
  protected Map<Checkpoint, BeaconState> checkpointStates;
  protected Map<UInt64, VoteTracker> votes;
  protected Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars;
  protected Optional<UInt64> earliestBlobSidecarSlot;
  protected Optional<Bytes32> latestCanonicalBlockRoot;
  protected Optional<UInt64> custodyGroupCount;
  protected Map<Bytes32, SignedExecutionPayloadEnvelope> executionPayloads;
  protected Optional<Bytes32> proposerBoostRoot = Optional.empty();

  protected final TestReadOnlyForkChoiceStrategy forkChoiceStrategy =
      new TestReadOnlyForkChoiceStrategy();

  TestStoreImpl(
      final Spec spec,
      final UInt64 time,
      final UInt64 genesisTime,
      final Optional<Checkpoint> initialCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint bestJustifiedCheckpoint,
      final Map<Bytes32, SignedBeaconBlock> blocks,
      final Map<Bytes32, BeaconState> blockStates,
      final Map<Bytes32, BlockCheckpoints> blockCheckpoints,
      final Map<Checkpoint, BeaconState> checkpointStates,
      final Map<UInt64, VoteTracker> votes,
      final Map<SlotAndBlockRoot, List<BlobSidecar>> blobSidecars,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot,
      final Optional<Bytes32> maybeLatestCanonicalBlockRoot,
      final Optional<UInt64> maybeCustodyGroupCount,
      final Map<Bytes32, SignedExecutionPayloadEnvelope> executionPayloads) {
    this.spec = spec;
    this.timeMillis = secondsToMillis(time);
    this.genesisTime = genesisTime;
    this.initialCheckpoint = initialCheckpoint;
    this.justifiedCheckpoint = justifiedCheckpoint;
    this.finalizedCheckpoint = finalizedCheckpoint;
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
    this.blocks = blocks;
    this.blockStates = blockStates;
    this.blockCheckpoints = blockCheckpoints;
    this.checkpointStates = checkpointStates;
    this.votes = votes;
    this.blobSidecars = blobSidecars;
    this.earliestBlobSidecarSlot = maybeEarliestBlobSidecarSlot;
    this.latestCanonicalBlockRoot = maybeLatestCanonicalBlockRoot;
    this.custodyGroupCount = maybeCustodyGroupCount;
    this.executionPayloads = executionPayloads;
  }

  // Readonly methods
  @Override
  public UInt64 getTimeInMillis() {
    return timeMillis;
  }

  @Override
  public UInt64 getGenesisTime() {
    return genesisTime;
  }

  @Override
  public Optional<Checkpoint> getInitialCheckpoint() {
    return initialCheckpoint;
  }

  @Override
  public Checkpoint getJustifiedCheckpoint() {
    return justifiedCheckpoint;
  }

  @Override
  public Checkpoint getFinalizedCheckpoint() {
    return finalizedCheckpoint;
  }

  @Override
  public Optional<UInt64> getCustodyGroupCount() {
    return custodyGroupCount;
  }

  @Override
  public UInt64 getLatestFinalizedBlockSlot() {
    return blocks.get(finalizedCheckpoint.getRoot()).getSlot();
  }

  @Override
  public AnchorPoint getLatestFinalized() {
    final SignedBeaconBlock block = getSignedBlock(finalizedCheckpoint.getRoot());
    final BeaconState state = getBlockState(finalizedCheckpoint.getRoot());
    return AnchorPoint.create(spec, finalizedCheckpoint, state, Optional.of(block));
  }

  @Override
  public Optional<SlotAndExecutionPayloadSummary> getFinalizedOptimisticTransitionPayload() {
    return Optional.empty();
  }

  @Override
  public Checkpoint getBestJustifiedCheckpoint() {
    return bestJustifiedCheckpoint;
  }

  @Override
  public Optional<Bytes32> getProposerBoostRoot() {
    return proposerBoostRoot;
  }

  @Override
  public ReadOnlyForkChoiceStrategy getForkChoiceStrategy() {
    return forkChoiceStrategy;
  }

  private SignedBeaconBlock getSignedBlock(final Bytes32 blockRoot) {
    return blocks.get(blockRoot);
  }

  private Optional<SignedBlockAndState> getBlockAndState(final Bytes32 blockRoot) {
    final SignedBeaconBlock block = getSignedBlock(blockRoot);
    final BeaconState state = getBlockState(blockRoot);
    if (block == null || state == null) {
      return Optional.empty();
    }
    return Optional.of(new SignedBlockAndState(block, state));
  }

  private SignedExecutionPayloadEnvelope getSignedExecutionPayload(final Bytes32 blockRoot) {
    return executionPayloads.get(blockRoot);
  }

  @Override
  public boolean containsBlock(final Bytes32 blockRoot) {
    return blocks.containsKey(blockRoot);
  }

  @Override
  public List<Bytes32> getOrderedBlockRoots() {
    return blocks.values().stream()
        .sorted(Comparator.comparing(SignedBeaconBlock::getSlot))
        .map(SignedBeaconBlock::getRoot)
        .collect(toList());
  }

  private BeaconState getBlockState(final Bytes32 blockRoot) {
    return blockStates.get(blockRoot);
  }

  private Optional<BeaconState> getCheckpointState(final Checkpoint checkpoint) {
    return Optional.ofNullable(checkpointStates.get(checkpoint));
  }

  @Override
  public UInt64 getHighestVotedValidatorIndex() {
    return votes.keySet().stream().max(Comparator.naturalOrder()).orElse(UInt64.ZERO);
  }

  // Prunable methods
  @Override
  public Optional<BeaconState> getBlockStateIfAvailable(final Bytes32 blockRoot) {
    return Optional.ofNullable(getBlockState(blockRoot));
  }

  @Override
  public Optional<SignedBeaconBlock> getBlockIfAvailable(final Bytes32 blockRoot) {
    return Optional.ofNullable(getSignedBlock(blockRoot));
  }

  @Override
  public Optional<SignedExecutionPayloadEnvelope> getExecutionPayloadIfAvailable(
      final Bytes32 blockRoot) {
    return Optional.ofNullable(getSignedExecutionPayload(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBeaconBlock>> retrieveSignedBlock(final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(getBlockIfAvailable(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBlockAndState>> retrieveBlockAndState(final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(getBlockAndState(blockRoot));
  }

  @Override
  public SafeFuture<Optional<StateAndBlockSummary>> retrieveStateAndBlockSummary(
      final Bytes32 blockRoot) {
    return retrieveBlockAndState(blockRoot).thenApply(res -> res.map(a -> a));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveBlockState(final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(getBlockStateIfAvailable(blockRoot));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveBlockState(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return SafeFuture.failedFuture(new UnsupportedOperationException("Not implemented"));
  }

  @Override
  public SafeFuture<Optional<SignedExecutionPayloadEnvelope>> retrieveSignedExecutionPayload(
      final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(getExecutionPayloadIfAvailable(blockRoot));
  }

  @Override
  public SafeFuture<Optional<SignedBlindedExecutionPayloadEnvelope>>
      retrieveSignedBlindedExecutionPayload(final Bytes32 blockRoot) {
    return SafeFuture.completedFuture(
        getExecutionPayloadIfAvailable(blockRoot)
            .map(executionPayload -> executionPayload.blind(spec)));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(final Checkpoint checkpoint) {
    return SafeFuture.completedFuture(getCheckpointState(checkpoint));
  }

  @Override
  public SafeFuture<CheckpointState> retrieveFinalizedCheckpointAndState() {
    final BeaconState state = getCheckpointState(finalizedCheckpoint).orElseThrow();
    final SignedBeaconBlock block = getSignedBlock(finalizedCheckpoint.getRoot());
    return SafeFuture.completedFuture(
        CheckpointState.create(spec, finalizedCheckpoint, block, state));
  }

  @Override
  public SafeFuture<Optional<BeaconState>> retrieveCheckpointState(
      final Checkpoint checkpoint, final BeaconState latestStateAtEpoch) {
    if (!latestStateAtEpoch.getSlot().equals(checkpoint.getEpochStartSlot(spec))) {
      throw new UnsupportedOperationException("Checkpoint state calculation not supported");
    }
    return SafeFuture.completedFuture(Optional.of(latestStateAtEpoch));
  }

  @Override
  public Optional<BeaconState> getJustifiedStateIfAvailable() {
    return Optional.ofNullable(checkpointStates.get(justifiedCheckpoint));
  }

  @Override
  public Optional<BeaconState> getCheckpointStateIfAvailable(final Checkpoint checkpoint) {
    return Optional.ofNullable(checkpointStates.get(checkpoint));
  }

  @Override
  public UInt64 getReorgThreshold() {
    return UInt64.ZERO;
  }

  @Override
  public UInt64 getParentThreshold() {
    return UInt64.ZERO;
  }

  @Override
  public void computeBalanceThresholds(final BeaconState justifiedState) {}

  @Override
  public Optional<Boolean> isFfgCompetitive(final Bytes32 headRoot, final Bytes32 parentRoot) {
    return Optional.empty();
  }

  @Override
  public boolean satisfiesInclusionList(final Bytes32 blockRoot) {
    return false;
  }

  @Override
  public Optional<List<BlobSidecar>> getBlobSidecarsIfAvailable(
      final SlotAndBlockRoot slotAndBlockRoot) {
    return Optional.ofNullable(blobSidecars.get(slotAndBlockRoot));
  }

  @Override
  public SafeFuture<Optional<UInt64>> retrieveEarliestBlobSidecarSlot() {
    return SafeFuture.completedFuture(earliestBlobSidecarSlot);
  }

  // Mutable methods
  @Override
  public void putBlockAndState(
      final SignedBeaconBlock block,
      final BeaconState state,
      final BlockCheckpoints checkpoints,
      final Optional<List<BlobSidecar>> blobSidecars,
      final Optional<UInt64> maybeEarliestBlobSidecarSlot) {
    blocks.put(block.getRoot(), block);
    blockStates.put(block.getRoot(), state);
    blockCheckpoints.put(block.getRoot(), checkpoints);
    blobSidecars.ifPresent(
        sidecars -> this.blobSidecars.put(block.getSlotAndBlockRoot(), sidecars));
    if (earliestBlobSidecarSlot.isEmpty()) {
      earliestBlobSidecarSlot = maybeEarliestBlobSidecarSlot;
    }
  }

  @Override
  public void putExecutionPayload(
      final SignedExecutionPayloadEnvelope executionPayload, final boolean executionOptimistic) {
    executionPayloads.put(executionPayload.getBeaconBlockRoot(), executionPayload);
  }

  @Override
  public void putUnsatisfiedInclusionListBlock(final Bytes32 blockRoot) {
    // NO-OP
  }

  @Override
  public void putStateRoot(final Bytes32 stateRoot, final SlotAndBlockRoot slotAndBlockRoot) {
    // NO-OP
  }

  @Override
  public void pullUpBlockCheckpoints(final Bytes32 blockRoot) {
    final BlockCheckpoints blockCheckpoints = this.blockCheckpoints.get(blockRoot);
    if (blockCheckpoints == null) {
      return;
    }
    this.blockCheckpoints.put(blockRoot, blockCheckpoints.realizeNextEpoch());
  }

  @Override
  public void setTimeMillis(final UInt64 time) {
    this.timeMillis = time;
  }

  @Override
  public void setGenesisTime(final UInt64 genesisTime) {
    this.genesisTime = genesisTime;
  }

  @Override
  public void setJustifiedCheckpoint(final Checkpoint justifiedCheckpoint) {
    this.justifiedCheckpoint = justifiedCheckpoint;
  }

  @Override
  public void setFinalizedCheckpoint(
      final Checkpoint finalizedCheckpoint, final boolean fromOptimisticBlock) {
    this.finalizedCheckpoint = finalizedCheckpoint;
  }

  @Override
  public void setBestJustifiedCheckpoint(final Checkpoint bestJustifiedCheckpoint) {
    this.bestJustifiedCheckpoint = bestJustifiedCheckpoint;
  }

  @Override
  public void setProposerBoostRoot(final Bytes32 boostedBlockRoot) {
    proposerBoostRoot = Optional.of(boostedBlockRoot);
  }

  @Override
  public void setLatestCanonicalBlockRoot(final Bytes32 latestCanonicalBlockRoot) {
    this.latestCanonicalBlockRoot = Optional.of(latestCanonicalBlockRoot);
  }

  @Override
  public void setCustodyGroupCount(final UInt64 custodyGroupCount) {
    this.custodyGroupCount = Optional.of(custodyGroupCount);
  }

  @Override
  public void removeFinalizedOptimisticTransitionPayload() {}

  @Override
  public void removeProposerBoostRoot() {
    proposerBoostRoot = Optional.empty();
  }

  @Override
  public VoteTracker getVote(final UInt64 validatorIndex) {
    VoteTracker vote = votes.get(validatorIndex);
    return vote != null ? vote : VoteTracker.DEFAULT;
  }

  @Override
  public VoteSnapshot getVoteSnapshot() {
    final UInt64 highestVotedValidatorIndex = getHighestVotedValidatorIndex();
    final VoteTracker[] voteArray = new VoteTracker[highestVotedValidatorIndex.intValue() + 1];
    votes.forEach((validatorIndex, vote) -> voteArray[validatorIndex.intValue()] = vote);
    return VoteSnapshot.create(highestVotedValidatorIndex, voteArray);
  }

  @Override
  public void putVote(final UInt64 validatorIndex, final VoteTracker vote) {
    votes.put(validatorIndex, vote);
  }

  @Override
  public void commit() {}

  @Override
  public SlotAndForkChoiceNode applyForkChoiceScoreChanges(
      final UInt64 currentSlot,
      final UInt64 currentEpoch,
      final Checkpoint finalizedCheckpoint,
      final Checkpoint justifiedCheckpoint,
      final List<UInt64> justifiedCheckpointEffectiveBalances,
      final Optional<Bytes32> proposerBoostRoot,
      final UInt64 proposerScoreBoostAmount) {
    throw new UnsupportedOperationException("Not implemented");
  }

  private class TestReadOnlyForkChoiceStrategy implements ReadOnlyForkChoiceStrategy {

    @Override
    public Optional<UInt64> blockSlot(final Bytes32 blockRoot) {
      return Optional.ofNullable(blocks.get(blockRoot)).map(SignedBeaconBlock::getSlot);
    }

    @Override
    public Optional<Bytes32> blockParentRoot(final Bytes32 blockRoot) {
      return Optional.ofNullable(blocks.get(blockRoot)).map(SignedBeaconBlock::getParentRoot);
    }

    @Override
    public Optional<UInt64> executionBlockNumber(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<Bytes32> executionBlockHash(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<Bytes32> getAncestor(final Bytes32 blockRoot, final UInt64 slot) {
      return Optional.empty();
    }

    @Override
    public Optional<ForkChoiceNode> getAncestorNode(final ForkChoiceNode node, final UInt64 slot) {
      // Base-only: this fixture does not model the Gloas three-state tree.
      return getAncestor(node.blockRoot(), slot).map(ForkChoiceNode::createBase);
    }

    @Override
    public Optional<ForkChoiceNode> getParentBeaconBlockNode(final ForkChoiceNode node) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<SlotAndBlockRoot> findCommonAncestor(
        final Bytes32 blockRoot1, final Bytes32 blockRoot2) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public List<Bytes32> getBlockRootsAtSlot(final UInt64 slot) {
      return blocks.values().stream()
          .filter(block -> block.getSlot().equals(slot))
          .map(SignedBeaconBlock::getRoot)
          .collect(toList());
    }

    @Override
    public List<ProtoNodeData> getChainHeads(final boolean includeNonViableChainHeads) {
      final Map<Bytes32, ProtoNodeData> headsByRoot = new HashMap<>();
      getOrderedBlockRoots()
          .forEach(
              root -> {
                final SignedBeaconBlock block = blocks.get(root);
                final Optional<ExecutionPayload> executionPayload =
                    block.getMessage().getBody().getOptionalExecutionPayload();
                headsByRoot.put(
                    root,
                    new ProtoNodeData(
                        block.getSlot(),
                        root,
                        block.getParentRoot(),
                        block.getStateRoot(),
                        executionPayload.map(ExecutionPayload::getBlockNumber).orElse(UInt64.ZERO),
                        executionPayload.map(ExecutionPayload::getBlockHash).orElse(Bytes32.ZERO),
                        UInt64.ZERO,
                        ProtoNodeValidationStatus.VALID,
                        blockCheckpoints.get(root),
                        UInt64.ZERO,
                        ForkChoicePayloadStatus.PAYLOAD_STATUS_PENDING));
                headsByRoot.remove(block.getParentRoot());
              });
      return new ArrayList<>(headsByRoot.values());
    }

    @Override
    public Optional<Bytes32> getOptimisticallySyncedTransitionBlockRoot(final Bytes32 head) {
      return Optional.empty();
    }

    @Override
    public List<ProtoNodeData> getBlockData() {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean contains(final Bytes32 blockRoot) {
      return blocks.containsKey(blockRoot);
    }

    @Override
    public Optional<Boolean> isOptimistic(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean isFullyValidated(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public Optional<ProtoNodeData> getBlockData(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public boolean shouldExtendPayload(
        final ReadOnlyStore store, final SlotAndBlockRoot slotAndBlockRoot) {
      return store.getExecutionPayloadIfAvailable(slotAndBlockRoot.getBlockRoot()).isPresent();
    }

    @Override
    public boolean shouldBuildOnFull(
        final ReadOnlyStore store, final UInt64 slot, final ForkChoiceNode head) {
      return shouldExtendPayload(store, new SlotAndBlockRoot(slot, head.blockRoot()));
    }

    @Override
    public Optional<UInt64> getWeight(final Bytes32 blockRoot) {
      throw new UnsupportedOperationException("Not implemented");
    }
  }
}
