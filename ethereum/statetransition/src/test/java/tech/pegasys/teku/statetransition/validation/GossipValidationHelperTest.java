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

package tech.pegasys.teku.statetransition.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;
import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PROPOSER_LOOKAHEAD;

import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLS;
import tech.pegasys.teku.bls.BLSKeyGenerator;
import tech.pegasys.teku.bls.BLSKeyPair;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFutureAssert;
import tech.pegasys.teku.infrastructure.ssz.SszMutableContainer;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.collections.SszUInt64VectorSchema;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoiceNode;
import tech.pegasys.teku.spec.datastructures.forkchoice.ForkChoicePayloadStatus;
import tech.pegasys.teku.spec.datastructures.forkchoice.ProtoNodeData;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu.BeaconStateSchemaFulu;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.gloas.Builder;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.storage.client.ChainHead;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.storage.store.UpdatableStore;

@TestSpecContext(
    signatureVerifierNoop = true,
    milestone = {SpecMilestone.PHASE0, SpecMilestone.FULU, SpecMilestone.GLOAS})
public class GossipValidationHelperTest {
  private Spec spec;
  private RecentChainData recentChainData;
  private DataStructureUtil dataStructureUtil;
  private StorageSystem storageSystem;
  private GossipValidationHelper gossipValidationHelper;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    dataStructureUtil = specContext.getDataStructureUtil();
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis(false);
    recentChainData = storageSystem.recentChainData();

    gossipValidationHelper =
        new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem());
  }

  @TestTemplate
  void isSlotFinalized_shouldComputeCorrectly() {

    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    assertThat(gossipValidationHelper.isSlotFinalized(finalizedSlot)).isFalse();

    storageSystem.chainUpdater().advanceChain(finalizedSlot);
    storageSystem.chainUpdater().finalizeEpoch(finalizedEpoch);

    assertThat(gossipValidationHelper.isSlotFinalized(finalizedSlot)).isTrue();
    assertThat(gossipValidationHelper.isSlotFinalized(finalizedSlot.plus(1))).isFalse();
  }

  @TestTemplate
  void isBeforeFinalizedSlot_shouldComputeCorrectly() {

    final UInt64 finalizedEpoch = UInt64.valueOf(2);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);

    assertThat(gossipValidationHelper.isBeforeFinalizedSlot(finalizedSlot)).isFalse();
    assertThat(gossipValidationHelper.isBeforeFinalizedSlot(finalizedSlot.minus(1))).isFalse();

    storageSystem.chainUpdater().advanceChain(finalizedSlot);
    storageSystem.chainUpdater().finalizeEpoch(finalizedEpoch);

    assertThat(gossipValidationHelper.isBeforeFinalizedSlot(finalizedSlot.minus(1))).isTrue();
    assertThat(gossipValidationHelper.isBeforeFinalizedSlot(finalizedSlot)).isFalse();
    assertThat(gossipValidationHelper.isBeforeFinalizedSlot(finalizedSlot.plus(1))).isFalse();
  }

  @TestTemplate
  void isSlotFromFuture_shouldComputeCorrectly() {
    final UInt64 slot2 = UInt64.valueOf(2);

    storageSystem.chainUpdater().setCurrentSlot(UInt64.ONE);
    assertThat(gossipValidationHelper.isSlotFromFuture(slot2)).isTrue();

    final UInt64 slot2TimeMillis =
        spec.computeTimeMillisAtSlot(
            slot2,
            secondsToMillis(
                recentChainData.getBestState().orElseThrow().getImmediately().getGenesisTime()));

    final UInt64 notYetInsideTolerance =
        slot2TimeMillis.minusMinZero(gossipValidationHelper.getMaxOffsetTimeInMillis()).decrement();
    storageSystem.chainUpdater().setTimeMillis(notYetInsideTolerance);
    assertThat(gossipValidationHelper.isSlotFromFuture(slot2)).isTrue();

    final UInt64 insideTolerance =
        slot2TimeMillis.minusMinZero(gossipValidationHelper.getMaxOffsetTimeInMillis());
    storageSystem.chainUpdater().setTimeMillis(insideTolerance);
    assertThat(gossipValidationHelper.isSlotFromFuture(slot2)).isFalse();
  }

  @TestTemplate
  void isEpochFromFuture_shouldComputeCorrectly() {
    final UInt64 epoch2 = UInt64.valueOf(2);
    final UInt64 epoch2StartSlot = spec.computeStartSlotAtEpoch(epoch2);

    storageSystem.chainUpdater().setCurrentSlot(UInt64.ONE);
    assertThat(gossipValidationHelper.isEpochFromFuture(epoch2)).isTrue();

    final UInt64 epoch2StartTimeMillis =
        spec.computeTimeMillisAtSlot(
            epoch2StartSlot,
            secondsToMillis(
                recentChainData.getBestState().orElseThrow().getImmediately().getGenesisTime()));

    final UInt64 notYetInsideTolerance =
        epoch2StartTimeMillis
            .minusMinZero(gossipValidationHelper.getMaxOffsetTimeInMillis())
            .decrement();
    storageSystem.chainUpdater().setTimeMillis(notYetInsideTolerance);
    assertThat(gossipValidationHelper.isEpochFromFuture(epoch2)).isTrue();

    final UInt64 insideTolerance =
        epoch2StartTimeMillis.minusMinZero(gossipValidationHelper.getMaxOffsetTimeInMillis());
    storageSystem.chainUpdater().setTimeMillis(insideTolerance);
    assertThat(gossipValidationHelper.isEpochFromFuture(epoch2)).isFalse();
  }

  @TestTemplate
  void isSignatureValidWithRespectToProposerIndex_shouldComputeCorrectly() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    final BeaconState headState =
        SafeFutureAssert.safeJoin(recentChainData.getBestState().orElseThrow());

    final Bytes32 domain =
        spec.getDomain(
            Domain.BEACON_PROPOSER,
            spec.getCurrentEpoch(headState),
            headState.getFork(),
            headState.getGenesisValidatorsRoot());
    final Bytes signingRoot = spec.computeSigningRoot(signedBlock.getMessage(), domain);

    assertThat(
            gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
                signingRoot, signedBlock.getProposerIndex(), signedBlock.getSignature(), headState))
        .isTrue();

    // wrong proposer index
    assertThat(
            gossipValidationHelper.isSignatureValidWithRespectToProposerIndex(
                signingRoot,
                signedBlock.getProposerIndex().increment(),
                signedBlock.getSignature(),
                headState))
        .isFalse();
  }

  @TestTemplate
  void isSignatureValidWithRespectToBuilderIndex_shouldComputeCorrectly(
      final SpecContext specContext) {
    specContext.assumeGloasActive();

    final BeaconState state = recentChainData.getBestState().orElseThrow().getImmediately();
    final UInt64 builderIndex = UInt64.ZERO;

    // Get the builder's public key to ensure builder exists
    if (spec.getBuilderPubKey(state, builderIndex).isEmpty()) {
      // Skip test if no builders in state
      return;
    }

    // Create a builder with a known keypair
    final BLSKeyPair builderKeyPair = BLSKeyGenerator.generateKeyPairs(1).get(0);
    final Builder builder =
        dataStructureUtil.builderBuilder().publicKey(builderKeyPair.getPublicKey()).build();

    // Create a modified state with our builder at index 0
    final BeaconState modifiedState =
        state.updated(
            mutableState -> {
              MutableBeaconStateGloas gloasState = MutableBeaconStateGloas.required(mutableState);
              final SszMutableList<Builder> builders = gloasState.getBuilders();
              if (builders.size() > builderIndex.intValue()) {
                builders.set(builderIndex.intValue(), builder);
              }
            });

    // Create a message and sign it with the builder's keypair
    final Bytes message = dataStructureUtil.randomBytes32();
    final BLSSignature signature = BLS.sign(builderKeyPair.getSecretKey(), message);

    // Verify signature is valid with correct builder index
    assertThat(
            gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
                message, builderIndex, signature, modifiedState))
        .isTrue();

    // Verify signature is invalid with wrong builder index
    assertThat(
            gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
                message, builderIndex.increment(), signature, modifiedState))
        .isFalse();
  }

  @TestTemplate
  void isProposerTheExpectedProposer_shouldComputeCorrectly() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    final BeaconState headState =
        SafeFutureAssert.safeJoin(recentChainData.getBestState().orElseThrow());

    assertThat(
            gossipValidationHelper.isProposerTheExpectedProposer(
                signedBlock.getProposerIndex(), nextSlot, headState))
        .isTrue();

    // wrong proposer index
    assertThat(
            gossipValidationHelper.isProposerTheExpectedProposer(
                signedBlock.getProposerIndex().increment(), nextSlot, headState))
        .isFalse();
  }

  @TestTemplate
  void isProposerTheExpectedProposer_GetsProposerFromStateInFulu() {
    assumeThat(spec.atEpoch(ZERO).getMilestone()).isGreaterThanOrEqualTo(SpecMilestone.FULU);
    final UInt64 defaultProposerIndex = UInt64.valueOf(1234);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final BeaconState headState =
        SafeFutureAssert.safeJoin(recentChainData.getBestState().orElseThrow());
    final BeaconStateSchemaFulu beaconStateSchema =
        (BeaconStateSchemaFulu)
            spec.forMilestone(SpecMilestone.FULU).getSchemaDefinitions().getBeaconStateSchema();
    final SszUInt64VectorSchema<?> proposerLookaheadVectorSchema =
        beaconStateSchema.getProposerLookaheadSchema();
    final SszMutableContainer writableCopy = headState.createWritableCopy();
    writableCopy.set(
        beaconStateSchema.getFieldIndex(PROPOSER_LOOKAHEAD),
        proposerLookaheadVectorSchema.createFromElements(
            IntStream.range(0, proposerLookaheadVectorSchema.getLength())
                .mapToObj(__ -> SszUInt64.of(defaultProposerIndex))
                .toList()));
    final BeaconState modifiedState = (BeaconState) writableCopy.commitChanges();

    assertThat(
            gossipValidationHelper.isProposerTheExpectedProposer(
                defaultProposerIndex, nextSlot, modifiedState))
        .isTrue();
    assertThat(
            gossipValidationHelper.isProposerTheExpectedProposer(
                defaultProposerIndex.increment(), nextSlot, modifiedState))
        .isFalse();
  }

  @TestTemplate
  void getSlotForBlockRoot_shouldComputeCorrectly() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState nextBlockAndState =
        storageSystem.chainUpdater().advanceChain(nextSlot);

    assertThat(gossipValidationHelper.getSlotForBlockRoot(nextBlockAndState.getRoot()))
        .containsSame(nextSlot);

    assertThat(gossipValidationHelper.getSlotForBlockRoot(dataStructureUtil.randomBytes32()))
        .isEmpty();
  }

  @TestTemplate
  void isBlockAvailable_shouldComputeCorrectly() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState nextBlockAndState =
        storageSystem.chainUpdater().advanceChain(nextSlot);

    assertThat(gossipValidationHelper.isBlockAvailable(nextBlockAndState.getRoot())).isTrue();

    assertThat(gossipValidationHelper.isBlockAvailable(dataStructureUtil.randomBytes32()))
        .isFalse();
  }

  @TestTemplate
  void getGasLimitForExecutionPayload_shouldReturnGasLimitFromAvailableExecutionPayload(
      final SpecContext specContext) {
    assumeThat(specContext.getSpecMilestone()).isEqualTo(SpecMilestone.GLOAS);
    final RecentChainData recentChainData = mock(RecentChainData.class);
    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);
    final Bytes32 blockRoot = executionPayload.getBeaconBlockRoot();
    final Bytes32 blockHash = executionPayload.getMessage().getPayload().getBlockHash();
    final UInt64 gasLimit = executionPayload.getMessage().getPayload().getGasLimit();

    when(recentChainData.getExecutionGasLimitForBlockRootAndHash(blockRoot, blockHash))
        .thenReturn(Optional.of(gasLimit));

    final GossipValidationHelper helper =
        new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem());
    assertThat(helper.getGasLimitForExecutionPayload(blockRoot, blockHash)).contains(gasLimit);
  }

  @TestTemplate
  void getGasLimitForExecutionPayload_shouldReturnEmptyWhenExecutionPayloadIsUnavailable(
      final SpecContext specContext) {
    assumeThat(specContext.getSpecMilestone()).isEqualTo(SpecMilestone.GLOAS);
    final RecentChainData recentChainData = mock(RecentChainData.class);
    final Bytes32 blockRoot = dataStructureUtil.randomBytes32();
    final Bytes32 blockHash = dataStructureUtil.randomBytes32();

    when(recentChainData.getExecutionGasLimitForBlockRootAndHash(blockRoot, blockHash))
        .thenReturn(Optional.empty());

    final GossipValidationHelper helper =
        new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem());
    assertThat(helper.getGasLimitForExecutionPayload(blockRoot, blockHash)).isEmpty();
  }

  @TestTemplate
  void isBidCompatibleWithHead_shouldAcceptBidBuildingOnHeadParent(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BidCompatibility bidCompatibility = createBidCompatibility();
    final ExecutionPayloadBid bid =
        createBidForParent(
            bidCompatibility.headBid(),
            bidCompatibility.headBid().getParentBlockHash(),
            bidCompatibility.headBlock().getParentRoot(),
            bidCompatibility.headBlock().getSlot());

    assertThat(bidCompatibility.helper().isBidCompatibleWithHead(bid)).isTrue();
  }

  @TestTemplate
  void isBidCompatibleWithHead_shouldAcceptBidBuildingOnFullHead(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BidCompatibility bidCompatibility = createBidCompatibility();
    final UInt64 bidSlot = bidCompatibility.headBlock().getSlot().plus(ONE);
    final ExecutionPayloadBid bid =
        createBidForParent(
            bidCompatibility.headBid(),
            bidCompatibility.headBid().getBlockHash(),
            bidCompatibility.headBlock().getRoot(),
            bidSlot);
    when(bidCompatibility
            .strategy()
            .shouldBuildOnFull(bidCompatibility.store(), bidSlot, bidCompatibility.headNode()))
        .thenReturn(true);

    assertThat(bidCompatibility.helper().isBidCompatibleWithHead(bid)).isTrue();
  }

  @TestTemplate
  void isBidCompatibleWithHead_shouldAcceptBidBuildingOnEmptyHead(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BidCompatibility bidCompatibility = createBidCompatibility();
    final UInt64 bidSlot = bidCompatibility.headBlock().getSlot().plus(ONE);
    final ExecutionPayloadBid bid =
        createBidForParent(
            bidCompatibility.headBid(),
            bidCompatibility.headBid().getParentBlockHash(),
            bidCompatibility.headBlock().getRoot(),
            bidSlot);

    assertThat(bidCompatibility.helper().isBidCompatibleWithHead(bid)).isTrue();
  }

  @TestTemplate
  void isBidCompatibleWithHead_shouldRejectWrongPayloadForFullHead(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BidCompatibility bidCompatibility = createBidCompatibility();
    final UInt64 bidSlot = bidCompatibility.headBlock().getSlot().plus(ONE);
    final ExecutionPayloadBid bid =
        createBidForParent(
            bidCompatibility.headBid(),
            bidCompatibility.headBid().getParentBlockHash(),
            bidCompatibility.headBlock().getRoot(),
            bidSlot);
    when(bidCompatibility
            .strategy()
            .shouldBuildOnFull(bidCompatibility.store(), bidSlot, bidCompatibility.headNode()))
        .thenReturn(true);

    assertThat(bidCompatibility.helper().isBidCompatibleWithHead(bid)).isFalse();
  }

  @TestTemplate
  void isBidCompatibleWithHead_shouldRejectBidForUnrelatedBlock(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BidCompatibility bidCompatibility = createBidCompatibility();
    final ExecutionPayloadBid bid =
        createBidForParent(
            bidCompatibility.headBid(),
            bidCompatibility.headBid().getParentBlockHash(),
            dataStructureUtil.randomBytes32(),
            bidCompatibility.headBlock().getSlot().plus(ONE));

    assertThat(bidCompatibility.helper().isBidCompatibleWithHead(bid)).isFalse();
  }

  @TestTemplate
  void isBidCompatibleWithHead_shouldRejectWhenHeadBlockIsUnavailable(
      final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BidCompatibility bidCompatibility = createBidCompatibility();
    final ExecutionPayloadBid bid =
        createBidForParent(
            bidCompatibility.headBid(),
            bidCompatibility.headBid().getBlockHash(),
            bidCompatibility.headBlock().getRoot(),
            bidCompatibility.headBlock().getSlot().plus(ONE));
    when(bidCompatibility.store().getBlockIfAvailable(bidCompatibility.headBlock().getRoot()))
        .thenReturn(Optional.empty());

    assertThat(bidCompatibility.helper().isBidCompatibleWithHead(bid)).isFalse();
  }

  @TestTemplate
  void isBidCompatibleWithHead_shouldUseExecutionHashForPreGloasHead(
      final SpecContext specContext) {
    specContext.assumeGloasActive();
    final Spec forkTransitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(ONE);
    final UInt64 firstGloasSlot = forkTransitionSpec.computeStartSlotAtEpoch(ONE);
    final SignedBeaconBlock preGloasHead =
        new DataStructureUtil(forkTransitionSpec)
            .randomSignedBeaconBlock(firstGloasSlot.minus(ONE));
    final Bytes32 executionBlockHash = dataStructureUtil.randomBytes32();
    final ExecutionPayloadBid template = dataStructureUtil.randomExecutionPayloadBid();
    final ExecutionPayloadBid compatibleBid =
        createBidForParent(template, executionBlockHash, preGloasHead.getRoot(), firstGloasSlot);
    final ExecutionPayloadBid incompatibleBid =
        createBidForParent(
            template, dataStructureUtil.randomBytes32(), preGloasHead.getRoot(), firstGloasSlot);

    final ChainHead chainHead = mock(ChainHead.class);
    final RecentChainData recentChainData = mock(RecentChainData.class);
    final UpdatableStore store = mock(UpdatableStore.class);
    when(chainHead.getRoot()).thenReturn(preGloasHead.getRoot());
    when(chainHead.getExecutionBlockHash()).thenReturn(executionBlockHash);
    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHead));
    when(recentChainData.getStore()).thenReturn(store);
    when(store.getBlockIfAvailable(preGloasHead.getRoot())).thenReturn(Optional.of(preGloasHead));
    final GossipValidationHelper helper =
        new GossipValidationHelper(
            forkTransitionSpec, recentChainData, storageSystem.getMetricsSystem());

    assertThat(helper.isBidCompatibleWithHead(compatibleBid)).isTrue();
    assertThat(helper.isBidCompatibleWithHead(incompatibleBid)).isFalse();
  }

  @TestTemplate
  void getParentStateInBlockEpoch_shouldComputeCorrectly() {
    final UInt64 firstSlotAtEpoch1 = spec.computeStartSlotAtEpoch(ONE);

    final UInt64 lastSlotInEpoch0 = firstSlotAtEpoch1.minus(1);

    final SignedBlockAndState lastBlockStateInEpoch0 =
        storageSystem.chainUpdater().advanceChain(firstSlotAtEpoch1.minus(2));

    // should get parent state in same epoch
    assertThatSafeFuture(
            gossipValidationHelper.getParentStateInBlockEpoch(
                lastBlockStateInEpoch0.getSlot(),
                lastBlockStateInEpoch0.getRoot(),
                lastSlotInEpoch0))
        .isCompletedWithValueMatching(
            beaconState -> beaconState.orElseThrow().equals(lastBlockStateInEpoch0.getState()));

    // should generate a state for epoch 1
    assertThatSafeFuture(
            gossipValidationHelper.getParentStateInBlockEpoch(
                lastBlockStateInEpoch0.getSlot(),
                lastBlockStateInEpoch0.getRoot(),
                firstSlotAtEpoch1))
        .isCompletedWithValueMatching(
            beaconState -> beaconState.orElseThrow().getSlot().equals(firstSlotAtEpoch1));
  }

  @TestTemplate
  void currentFinalizedCheckpointIsAncestorOfBlock_shouldReturnValid() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    assertThat(
            gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
                block.getSlot(), block.getParentRoot()))
        .isTrue();
  }

  @TestTemplate
  void
      currentFinalizedCheckpointIsAncestorOfBlock_shouldReturnInvalidForBlockThatDoesNotDescendFromFinalizedCheckpoint() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    final RecentChainData localRecentChainData = storageSystem.recentChainData();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final ChainUpdater chainUpdater = new ChainUpdater(localRecentChainData, chainBuilder, spec);

    final GossipValidationHelper gossipValidationHelper =
        new GossipValidationHelper(spec, localRecentChainData, storageSystem.getMetricsSystem());
    chainUpdater.initializeGenesis();

    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(1));

    final ChainBuilder chainBuilderFork = chainBuilder.fork();
    final ChainUpdater chainUpdaterFork =
        new ChainUpdater(storageSystem.recentChainData(), chainBuilderFork, spec);

    final UInt64 startSlotOfFinalizedEpoch = spec.computeStartSlotAtEpoch(UInt64.valueOf(4));

    chainUpdaterFork.advanceChain(20);

    chainUpdater.finalizeEpoch(4);

    SignedBlockAndState blockAndState =
        chainBuilderFork.generateBlockAtSlot(startSlotOfFinalizedEpoch.increment());
    chainUpdater.saveBlockTime(blockAndState);

    assertThat(
            gossipValidationHelper.currentFinalizedCheckpointIsAncestorOfBlock(
                blockAndState.getSlot(), blockAndState.getParentRoot()))
        .isFalse();
  }

  @TestTemplate
  void isSlotCurrent_shouldRejectOutsideLowerBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 slotStartTimeMillis = getSlotStartTimeMillis(slot);
    final UInt64 currentTime =
        slotStartTimeMillis.minus(gossipValidationHelper.getMaxOffsetTimeInMillis()).decrement();
    assertIsSlotCurrent(slot, currentTime, false);
  }

  @TestTemplate
  void isSlotCurrent_shouldAcceptLowerBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 slotStartTimeMillis = getSlotStartTimeMillis(slot);
    final UInt64 currentTime =
        slotStartTimeMillis.minus(gossipValidationHelper.getMaxOffsetTimeInMillis());
    assertIsSlotCurrent(slot, currentTime, true);
  }

  @TestTemplate
  void isSlotCurrent_shouldAcceptUpperBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 nextSlotStartTimeMillis = getSlotStartTimeMillis(slot.increment());
    final UInt64 currentTime =
        nextSlotStartTimeMillis.plus(gossipValidationHelper.getMaxOffsetTimeInMillis());
    assertIsSlotCurrent(slot, currentTime, true);
  }

  @TestTemplate
  void isSlotCurrent_shouldRejectOutsideUpperBound() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 nextSlotStartTimeMillis = getSlotStartTimeMillis(slot.increment());
    final UInt64 currentTime =
        nextSlotStartTimeMillis.plus(gossipValidationHelper.getMaxOffsetTimeInMillis()).increment();
    assertIsSlotCurrent(slot, currentTime, false);
  }

  @TestTemplate
  void isCurrentOrNextSlot() {
    final UInt64 currentSlot = UInt64.valueOf(10);
    // Sit well inside the slot: at a slot boundary the disparity allowance legitimately makes the
    // adjacent slot current too, which is covered separately below.
    storageSystem
        .chainUpdater()
        .setTimeMillis(
            getSlotStartTimeMillis(currentSlot).plus(spec.getSlotDurationMillis(currentSlot) / 2));

    assertThat(gossipValidationHelper.isSlotCurrentOrNext(currentSlot)).isTrue();
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(currentSlot.plus(ONE))).isTrue();
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(currentSlot.minus(ONE))).isFalse();
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(currentSlot.plus(2))).isFalse();
  }

  @TestTemplate
  void isCurrentOrNextSlot_shouldAllowTheDisparityAllowanceBeforeTheSlotStarts() {
    final UInt64 slot = UInt64.valueOf(10);
    // The slot after next becomes acceptable once we are within the disparity allowance of the next
    // slot starting, because by then the next slot may already be current
    final UInt64 justBeforeNextSlot =
        getSlotStartTimeMillis(slot.plus(ONE))
            .minus(gossipValidationHelper.getMaxOffsetTimeInMillis());

    storageSystem.chainUpdater().setTimeMillis(justBeforeNextSlot.minus(ONE));
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(slot.plus(2))).isFalse();

    storageSystem.chainUpdater().setTimeMillis(justBeforeNextSlot);
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(slot.plus(2))).isTrue();
  }

  @TestTemplate
  void hasSlotStarted_shouldAllowUpToTheDisparityAllowance() {
    final UInt64 slot = UInt64.valueOf(1000);
    final UInt64 lastTimeBeforeStart =
        getSlotStartTimeMillis(slot).plus(gossipValidationHelper.getMaxOffsetTimeInMillis());

    storageSystem.chainUpdater().setTimeMillis(lastTimeBeforeStart);
    assertThat(gossipValidationHelper.hasSlotStarted(slot)).isFalse();

    storageSystem.chainUpdater().setTimeMillis(lastTimeBeforeStart.increment());
    assertThat(gossipValidationHelper.hasSlotStarted(slot)).isTrue();
  }

  @TestTemplate
  void hasSlotStarted_shouldBeFalseWellBeforeTheSlot() {
    final UInt64 slot = UInt64.valueOf(1000);
    storageSystem.chainUpdater().setTimeMillis(getSlotStartTimeMillis(slot.minus(10)));
    assertThat(gossipValidationHelper.hasSlotStarted(slot)).isFalse();
  }

  @TestTemplate
  void isPossibleDependentRoot_shouldAcceptBlockWithChildAtOrAfterEpochStart() {
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(ONE);
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    final SignedBlockAndState lastBlockBeforeEpoch =
        chainUpdater.advanceChainUntil(epochStartSlot.minus(ONE));
    // Extending into the epoch makes the preceding block the latest one before the boundary
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(epochStartSlot));

    assertThat(
            gossipValidationHelper.isPossibleDependentRoot(
                lastBlockBeforeEpoch.getRoot(), epochStartSlot))
        .isTrue();
  }

  @TestTemplate
  void isPossibleDependentRoot_shouldRejectBlockWhoseChildIsBeforeEpochStart() {
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(ONE);
    assumeThat(epochStartSlot).isGreaterThan(UInt64.valueOf(2));
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    final SignedBlockAndState twoBlocksBeforeEpoch =
        chainUpdater.advanceChainUntil(epochStartSlot.minus(2));
    chainUpdater.updateBestBlock(chainUpdater.advanceChainUntil(epochStartSlot));

    // Its only child sits before the boundary, so it is not the latest block before the epoch on
    // any branch, and it is not the head either
    assertThat(
            gossipValidationHelper.isPossibleDependentRoot(
                twoBlocksBeforeEpoch.getRoot(), epochStartSlot))
        .isFalse();
  }

  @TestTemplate
  void isPossibleDependentRoot_shouldAcceptHeadWithNoChildYet() {
    final ChainUpdater chainUpdater = storageSystem.chainUpdater();
    final SignedBlockAndState head = chainUpdater.advanceChainUntil(2);
    chainUpdater.updateBestBlock(head);
    // The head has no child at all yet, but the next block to extend it would land in the epoch
    final UInt64 epochStartSlot = spec.computeStartSlotAtEpoch(UInt64.valueOf(3));

    assertThat(gossipValidationHelper.isPossibleDependentRoot(head.getRoot(), epochStartSlot))
        .isTrue();
  }

  @TestTemplate
  void isValidBuilder_shouldReturnTrueForActiveBuilder(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final UInt64 finalizedEpoch = UInt64.valueOf(4);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    storageSystem.chainUpdater().advanceChainUntil(finalizedSlot);
    final SignedBlockAndState blockAndState = storageSystem.chainUpdater().finalizeCurrentChain();
    final BeaconState originalState = blockAndState.getState();
    final UInt64 slot = originalState.getSlot();
    final UInt64 builderIndex = UInt64.valueOf(0);
    final BeaconState modifiedState =
        originalState.updated(
            mutableState ->
                MutableBeaconStateGloas.required(mutableState)
                    .getBuilders()
                    .append(dataStructureUtil.builderBuilder().depositEpoch(ZERO).build()));
    assertThat(gossipValidationHelper.isActiveBuilder(builderIndex, modifiedState, slot)).isTrue();
  }

  @TestTemplate
  void isActiveBuilder_shouldHandleOutOfBound(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BeaconState state = recentChainData.getBestState().orElseThrow().getImmediately();
    final UInt64 slot = state.getSlot();
    final UInt64 invalidIndex = UInt64.valueOf(state.getValidators().size());
    assertThat(gossipValidationHelper.isActiveBuilder(invalidIndex, state, slot)).isFalse();
  }

  @TestTemplate
  void isActiveBuilder_shouldReturnFalseForInactive(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final UInt64 finalizedEpoch = UInt64.valueOf(4);
    final UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    storageSystem.chainUpdater().advanceChainUntil(finalizedSlot);
    final SignedBlockAndState blockAndState = storageSystem.chainUpdater().finalizeCurrentChain();
    final BeaconState originalState = blockAndState.getState();
    final UInt64 slot = originalState.getSlot();
    final UInt64 builderIndex = UInt64.valueOf(0);
    final BeaconState modifiedState =
        originalState.updated(
            mutableState ->
                MutableBeaconStateGloas.required(mutableState)
                    .getBuilders()
                    .append(
                        dataStructureUtil
                            .builderBuilder()
                            .depositEpoch(
                                originalState.getFinalizedCheckpoint().getEpoch().increment())
                            .build()));
    assertThat(gossipValidationHelper.isActiveBuilder(builderIndex, modifiedState, slot)).isFalse();
  }

  private UInt64 getSlotStartTimeMillis(final UInt64 slot) {
    return spec.computeTimeAtSlot(slot, recentChainData.getGenesisTime()).times(1000);
  }

  @TestTemplate
  void builderHasEnoughBalanceForBid_shouldReturnFalseWhenBidExceedsExcessBalance(
      final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BeaconState originalState = recentChainData.getBestState().orElseThrow().getImmediately();
    final UInt64 slot = originalState.getSlot();

    // Set builder balance to a known value
    final UInt64 builderBalance = UInt64.valueOf(50_000_000_000L); // 50 ETH in Gwei

    final BeaconState modifiedState =
        originalState.updated(
            mutableState ->
                MutableBeaconStateGloas.required(mutableState)
                    .getBuilders()
                    .append(dataStructureUtil.builderBuilder().balance(builderBalance).build()));

    // Bid value that exceeds excess balance (minActivationBalance + bidValue > builderBalance)
    final UInt64 excessiveBidValue = builderBalance.plus(1);

    assertThat(
            gossipValidationHelper.builderHasEnoughBalanceForBid(
                excessiveBidValue, UInt64.ZERO, modifiedState, slot))
        .isFalse();
  }

  @TestTemplate
  void builderHasEnoughBalanceForBid_shouldReturnTrueWhenBuilderHasSufficientBalance(
      final SpecContext specContext) {
    specContext.assumeGloasActive();
    final BeaconState originalState = recentChainData.getBestState().orElseThrow().getImmediately();
    final UInt64 slot = originalState.getSlot();

    // Set builder balance to a large value
    final UInt64 builderBalance = UInt64.valueOf(100_000_000_000L); // 100 ETH in Gwei
    final BeaconState modifiedState =
        originalState.updated(
            mutableState ->
                MutableBeaconStateGloas.required(mutableState)
                    .getBuilders()
                    .append(dataStructureUtil.builderBuilder().balance(builderBalance).build()));

    // Reasonable bid value
    final UInt64 bidValue = UInt64.valueOf(1_000_000_000L); // 1 ETH in Gwei

    assertThat(
            gossipValidationHelper.builderHasEnoughBalanceForBid(
                bidValue, UInt64.ZERO, modifiedState, slot))
        .isTrue();
  }

  @TestTemplate
  void validatePayloadStatus_shouldSaveForFutureWhenAttestedPayloadIsOptimistic(
      final SpecContext specContext) {
    specContext.assumeGloasActive();
    final PayloadStatusFixture fixture = createPayloadStatusFixture(true);

    final InternalValidationResult result =
        fixture
            .helper()
            .validatePayloadStatus(
                spec.atSlot(fixture.attestationData().getSlot()).getAttestationUtil(),
                fixture.attestationData(),
                Set.of());

    assertThat(result).isEqualTo(InternalValidationResult.SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void validatePayloadStatus_shouldAcceptWhenAttestedPayloadIsValidated(
      final SpecContext specContext) {
    specContext.assumeGloasActive();
    final PayloadStatusFixture fixture = createPayloadStatusFixture(false);

    final InternalValidationResult result =
        fixture
            .helper()
            .validatePayloadStatus(
                spec.atSlot(fixture.attestationData().getSlot()).getAttestationUtil(),
                fixture.attestationData(),
                Set.of());

    assertThat(result.isAccept()).isTrue();
  }

  @TestTemplate
  void isSlotCurrent_shouldRejectMaximumSlotWithoutOverflow() {
    assertThat(gossipValidationHelper.isSlotCurrent(UInt64.MAX_VALUE)).isFalse();
  }

  @TestTemplate
  void isSlotCurrentOrNext_shouldRejectMaximumSlotWithoutOverflow() {
    assertThat(gossipValidationHelper.isSlotCurrentOrNext(UInt64.MAX_VALUE)).isFalse();
  }

  @TestTemplate
  void hasSlotStarted_shouldRejectMaximumSlotWithoutOverflow() {
    assertThat(gossipValidationHelper.hasSlotStarted(UInt64.MAX_VALUE)).isFalse();
  }

  private PayloadStatusFixture createPayloadStatusFixture(final boolean payloadOptimistic) {
    final UInt64 attestedBlockSlot = UInt64.valueOf(10);
    final UInt64 attestationSlot = attestedBlockSlot.plus(ONE);
    final Bytes32 attestedBlockRoot = dataStructureUtil.randomBytes32();
    final AttestationData attestationData =
        new AttestationData(
            attestationSlot,
            ONE,
            attestedBlockRoot,
            dataStructureUtil.randomCheckpoint(),
            dataStructureUtil.randomCheckpoint());

    final SignedExecutionPayloadEnvelope executionPayload =
        dataStructureUtil.randomSignedExecutionPayloadEnvelope(1);

    final RecentChainData recentChainDataMock = mock(RecentChainData.class);
    final UpdatableStore storeMock = mock(UpdatableStore.class);
    final ReadOnlyForkChoiceStrategy strategyMock = mock(ReadOnlyForkChoiceStrategy.class);
    final ProtoNodeData protoNodeDataMock = mock(ProtoNodeData.class);

    when(recentChainDataMock.getSlotForBlockRoot(attestedBlockRoot))
        .thenReturn(Optional.of(attestedBlockSlot));
    when(recentChainDataMock.getStore()).thenReturn(storeMock);
    when(storeMock.getExecutionPayloadIfAvailable(attestedBlockRoot))
        .thenReturn(Optional.of(executionPayload));
    when(recentChainDataMock.getForkChoiceStrategy()).thenReturn(Optional.of(strategyMock));
    when(strategyMock.getBlockData(attestedBlockRoot, ForkChoicePayloadStatus.PAYLOAD_STATUS_FULL))
        .thenReturn(Optional.of(protoNodeDataMock));
    when(protoNodeDataMock.isOptimistic()).thenReturn(payloadOptimistic);

    final GossipValidationHelper helper =
        new GossipValidationHelper(spec, recentChainDataMock, storageSystem.getMetricsSystem());
    return new PayloadStatusFixture(helper, attestationData);
  }

  private record PayloadStatusFixture(
      GossipValidationHelper helper, AttestationData attestationData) {}

  private void assertIsSlotCurrent(
      final UInt64 slot, final UInt64 currentTime, final boolean expectedResult) {
    final RecentChainData recentChainDataMock = mock(RecentChainData.class);
    final UpdatableStore storeMock = mock(UpdatableStore.class);
    when(recentChainDataMock.getStore()).thenReturn(storeMock);
    when(storeMock.getTimeInMillis()).thenReturn(currentTime);
    when(recentChainDataMock.getCurrentSlot()).thenReturn(Optional.of(slot));
    when(recentChainDataMock.getGenesisTimeMillis())
        .thenReturn(recentChainData.getGenesisTimeMillis());
    final GossipValidationHelper gossipValidationHelperMocked =
        new GossipValidationHelper(spec, recentChainDataMock, storageSystem.getMetricsSystem());
    assertThat(gossipValidationHelperMocked.isSlotCurrent(slot)).isEqualTo(expectedResult);
  }

  private BidCompatibility createBidCompatibility() {
    final SignedBeaconBlock headBlock = dataStructureUtil.randomSignedBeaconBlock(UInt64.ONE);
    final ExecutionPayloadBid headBid =
        headBlock
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow()
            .getMessage();
    final ForkChoiceNode headNode = ForkChoiceNode.createFull(headBlock.getRoot());
    final ChainHead chainHead = mock(ChainHead.class);
    final RecentChainData recentChainData = mock(RecentChainData.class);
    final UpdatableStore store = mock(UpdatableStore.class);
    final ReadOnlyForkChoiceStrategy strategy = mock(ReadOnlyForkChoiceStrategy.class);
    when(chainHead.getRoot()).thenReturn(headBlock.getRoot());
    when(chainHead.getParentRoot()).thenReturn(headBlock.getParentRoot());
    when(chainHead.getExecutionBlockHash()).thenReturn(headBid.getBlockHash());
    when(chainHead.getForkChoiceNode()).thenReturn(headNode);
    when(recentChainData.getChainHead()).thenReturn(Optional.of(chainHead));
    when(recentChainData.getStore()).thenReturn(store);
    when(recentChainData.getForkChoiceStrategy()).thenReturn(Optional.of(strategy));
    when(store.getBlockIfAvailable(headBlock.getRoot())).thenReturn(Optional.of(headBlock));
    final GossipValidationHelper helper =
        new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem());
    return new BidCompatibility(helper, store, strategy, headNode, headBlock, headBid);
  }

  private ExecutionPayloadBid createBidForParent(
      final ExecutionPayloadBid template,
      final Bytes32 parentBlockHash,
      final Bytes32 parentBlockRoot,
      final UInt64 slot) {
    return template
        .getSchema()
        .create(
            parentBlockHash,
            parentBlockRoot,
            template.getBlockHash(),
            template.getPrevRandao(),
            template.getFeeRecipient(),
            template.getGasLimit(),
            template.getBuilderIndex(),
            slot,
            template.getValue(),
            template.getExecutionPayment(),
            template.getBlobKzgCommitments(),
            template.getExecutionRequestsRoot());
  }

  private record BidCompatibility(
      GossipValidationHelper helper,
      UpdatableStore store,
      ReadOnlyForkChoiceStrategy strategy,
      ForkChoiceNode headNode,
      SignedBeaconBlock headBlock,
      ExecutionPayloadBid headBid) {}
}
