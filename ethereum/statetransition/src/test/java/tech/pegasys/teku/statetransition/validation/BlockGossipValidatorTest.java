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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.networks.Eth2NetworkConfiguration.DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED;

import java.util.function.Function;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.bls.BLSTestUtil;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.BeaconBlockBody;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.gloas.BeaconBlockBodyBuilderGloas;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionRequests;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.MutableBeaconStateGloas;
import tech.pegasys.teku.spec.generator.ChainBuilder;
import tech.pegasys.teku.spec.generator.ChainBuilder.BlockOptions;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationTracker;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator.EquivocationCheckResult;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.client.ChainUpdater;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;

@TestSpecContext(
    milestone = {
      SpecMilestone.ALTAIR,
      SpecMilestone.BELLATRIX,
      SpecMilestone.DENEB,
      SpecMilestone.ELECTRA,
      SpecMilestone.FULU,
      SpecMilestone.GLOAS
    },
    signatureVerifierNoop = true)
public class BlockGossipValidatorTest {
  private Spec spec;
  private RecentChainData recentChainData;
  private StorageSystem storageSystem;
  private final ReceivedBlockEventsChannel receivedBlockEventsChannelPublisher =
      mock(ReceivedBlockEventsChannel.class);

  private BlockGossipValidator blockGossipValidator;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    spec = specContext.getSpec();
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis(false);
    recentChainData = storageSystem.recentChainData();
    seedGenesisExecutionPayloadForGloas();
    blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);
  }

  private void seedGenesisExecutionPayloadForGloas() {
    if (spec.getGenesisSpecConfig().getMilestone().isLessThan(SpecMilestone.GLOAS)) {
      return;
    }
    final InlineEventThread eventThread = new InlineEventThread();
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            new NoopForkChoiceNotifier(),
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            mock(MergeTransitionBlockValidator.class),
            FastConfirmationTracker.NOOP,
            DEFAULT_FORK_CHOICE_LATE_BLOCK_REORG_ENABLED,
            LateBlockReorgPreparationHandler.NOOP,
            mock(DebugDataDumper.class),
            storageSystem.getMetricsSystem(),
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.SIMPLE));
    forkChoice.applyGenesisExecutionPayloadForGloas().join();
  }

  @TestTemplate
  void shouldReturnValidForValidBlock() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    assertResultIsAccept(block, blockGossipValidator.validate(block, true));
  }

  @TestTemplate
  void shouldIgnoreAlreadyImportedBlock() {
    final SignedBeaconBlock block = storageSystem.chainUpdater().advanceChain().getBlock();

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldIgnoreAlreadySeenBlocks() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    assertResultIsAccept(block, blockGossipValidator.validate(block, true));

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldReturnSavedForFutureForBlockFromFuture() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBeaconBlock block =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldReturnSavedForFutureForBlockWithParentUnavailable() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();
    final UInt64 proposerIndex = signedBlock.getMessage().getProposerIndex();
    final BeaconBlock block =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            signedBlock.getSlot(),
            proposerIndex,
            Bytes32.ZERO,
            signedBlock.getMessage().getStateRoot(),
            signedBlock.getMessage().getBody());

    BLSSignature blockSignature =
        storageSystem
            .chainBuilder()
            .getSigner(proposerIndex.intValue())
            .signBlock(
                block,
                storageSystem.chainBuilder().getLatestBlockAndState().getState().getForkInfo())
            .join();
    final SignedBeaconBlock blockWithNoParent =
        SignedBeaconBlock.create(spec, block, blockSignature);

    assertThat(blockGossipValidator.validate(blockWithNoParent, true))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldReturnInvalidForBlockOlderThanFinalizedSlot() {
    UInt64 finalizedEpoch = UInt64.valueOf(10);
    UInt64 finalizedSlot = spec.computeStartSlotAtEpoch(finalizedEpoch);
    storageSystem.chainUpdater().advanceChain(finalizedSlot);
    storageSystem.chainUpdater().finalizeEpoch(finalizedEpoch);

    StorageSystem storageSystem2 = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem2.chainUpdater().initializeGenesis(false);
    final SignedBeaconBlock block =
        storageSystem2.chainBuilder().generateBlockAtSlot(finalizedSlot.minus(ONE)).getBlock();

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(InternalValidationResult::isIgnore);
  }

  @TestTemplate
  void shouldReturnInvalidForBlockWithWrongProposerIndex() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock signedBlock =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    final UInt64 invalidProposerIndex =
        signedBlock
            .getMessage()
            .getProposerIndex()
            .plus(ONE)
            .mod(storageSystem.chainBuilder().getValidatorKeys().size());

    final BeaconBlock block =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            signedBlock.getSlot(),
            invalidProposerIndex,
            signedBlock.getParentRoot(),
            signedBlock.getMessage().getStateRoot(),
            signedBlock.getMessage().getBody());

    final BLSSignature blockSignature =
        storageSystem
            .chainBuilder()
            .getSigner(invalidProposerIndex.intValue())
            .signBlock(
                block,
                storageSystem.chainBuilder().getLatestBlockAndState().getState().getForkInfo())
            .join();
    final SignedBeaconBlock invalidProposerSignedBlock =
        SignedBeaconBlock.create(spec, block, blockSignature);

    assertThat(blockGossipValidator.validate(invalidProposerSignedBlock, true))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.equals(
                    InternalValidationResult.reject(
                        "Block proposed by incorrect proposer (%s)", invalidProposerIndex)));
  }

  @TestTemplate
  void shouldReturnInvalidForBlockWithWrongSignature() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBeaconBlock block =
        SignedBeaconBlock.create(
            spec,
            storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock().getMessage(),
            BLSTestUtil.randomSignature(0));

    assertThat(blockGossipValidator.validate(block, true))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.equals(
                    InternalValidationResult.reject("Block signature is invalid")));
  }

  @TestTemplate
  void shouldReturnInvalidForBlockThatDoesNotDescendFromFinalizedCheckpoint() {

    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    final RecentChainData localRecentChainData = storageSystem.recentChainData();
    final ChainBuilder chainBuilder = ChainBuilder.create(spec);
    final ChainUpdater chainUpdater = new ChainUpdater(localRecentChainData, chainBuilder, spec);

    final BlockGossipValidator blockValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(
                spec, localRecentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);
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
    final SafeFuture<InternalValidationResult> result =
        blockValidator.validate(blockAndState.getBlock(), true);
    assertThat(result)
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.equals(
                    InternalValidationResult.reject(
                        "Block does not descend from finalized checkpoint")));
  }

  @TestTemplate
  void shouldReturnAcceptOnCorrectExecutionPayloadTimestamp(final SpecContext specContext) {
    specContext.assumeBellatrixActive();

    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem
        .chainUpdater()
        .initializeGenesisWithPayload(
            false, specContext.getDataStructureUtil().randomExecutionPayloadHeader());
    recentChainData = storageSystem.recentChainData();
    seedGenesisExecutionPayloadForGloas();
    blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    SignedBeaconBlock block = storageSystem.chainBuilder().generateBlockAtSlot(nextSlot).getBlock();

    assertResultIsAccept(block, blockGossipValidator.validate(block, true));
  }

  @TestTemplate
  void shouldReturnInvalidOnWrongExecutionPayloadTimestamp(final SpecContext specContext) {
    // The payload timestamp is checked from Bellatrix to Fulu only
    specContext.assumeMilestonesActive(SpecMilestone.BELLATRIX, SpecMilestone.FULU);

    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem
        .chainUpdater()
        .initializeGenesisWithPayload(
            false, specContext.getDataStructureUtil().randomExecutionPayloadHeader());
    recentChainData = storageSystem.recentChainData();
    blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final SignedBlockAndState signedBlockAndState =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(
                nextSlot,
                BlockOptions.create()
                    .setSkipStateTransition(true)
                    .setExecutionPayload(
                        specContext.getDataStructureUtil().randomExecutionPayload()));

    assertThat(blockGossipValidator.validate(signedBlockAndState.getBlock(), true))
        .isCompletedWithValueMatching(
            internalValidationResult ->
                internalValidationResult.equals(
                    InternalValidationResult.reject(
                        "Execution Payload timestamp is not consistent with block slot time")));
  }

  @TestTemplate
  void shouldNotTrackBlocksIfMarkAsReceivedIsFalse() {
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    final SignedBeaconBlock block = signedBlockAndState.getBlock();
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    assertResultIsAccept(block, blockGossipValidator.validate(block, false));
    assertThat(blockGossipValidator.performBlockEquivocationCheck(true, block))
        .isEqualByComparingTo(EquivocationCheckResult.FIRST_BLOCK_FOR_SLOT_PROPOSER);
  }

  @TestTemplate
  void shouldRejectWhenKzgCommitmentsExceedLimit(final SpecContext specContext) {
    // We check kzg commitments between Deneb and Fulu only
    specContext.assumeMilestonesActive(SpecMilestone.DENEB, SpecMilestone.FULU);
    storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem
        .chainUpdater()
        .initializeGenesisWithPayload(
            false, specContext.getDataStructureUtil().randomExecutionPayloadHeader());
    recentChainData = storageSystem.recentChainData();
    blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, recentChainData, storageSystem.getMetricsSystem()),
            receivedBlockEventsChannelPublisher);

    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final Integer maxBlobsPerBlock =
        specContext.getSpec().getMaxBlobsPerBlockAtSlot(nextSlot).orElseThrow();

    final SignedBlockAndState signedBlockAndState =
        storageSystem
            .chainBuilder()
            .generateBlockAtSlot(
                nextSlot,
                BlockOptions.create()
                    .setSkipStateTransition(true)
                    .setExecutionPayload(
                        specContext.getDataStructureUtil().randomExecutionPayload())
                    .setKzgCommitments(
                        specContext
                            .getDataStructureUtil()
                            .randomBlobKzgCommitments(maxBlobsPerBlock + 1)));

    assertThat(blockGossipValidator.validate(signedBlockAndState.getBlock(), true))
        .isCompletedWithValueMatching(
            result ->
                result.equals(
                    InternalValidationResult.reject(
                        "Block has %d kzg commitments, max allowed %d",
                        maxBlobsPerBlock + 1, maxBlobsPerBlock)));
  }

  @TestTemplate
  void shouldRejectBidNotBuildingOnTheParentsExecutionHead(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final Bytes32 unrelatedParentBlockHash = Bytes32.random();

    final SignedBeaconBlock invalidBlock =
        createBlockWithModifiedExecutionPayloadBid(
            signedBlockAndState,
            originalExecutionPayloadBid ->
                originalExecutionPayloadBid
                    .getSchema()
                    .create(
                        unrelatedParentBlockHash,
                        originalExecutionPayloadBid.getParentBlockRoot(),
                        originalExecutionPayloadBid.getBlockHash(),
                        originalExecutionPayloadBid.getPrevRandao(),
                        originalExecutionPayloadBid.getFeeRecipient(),
                        originalExecutionPayloadBid.getGasLimit(),
                        originalExecutionPayloadBid.getBuilderIndex(),
                        originalExecutionPayloadBid.getSlot(),
                        originalExecutionPayloadBid.getValue(),
                        originalExecutionPayloadBid.getExecutionPayment(),
                        originalExecutionPayloadBid.getBlobKzgCommitments(),
                        originalExecutionPayloadBid.getExecutionRequestsRoot()));
    assertThat(blockGossipValidator.validate(invalidBlock, true))
        .isCompletedWithValueMatching(
            result ->
                result.equals(
                    InternalValidationResult.reject(
                        "Bid does not build on the parent's execution head")));
  }

  @TestTemplate
  void shouldAcceptFirstGloasBlockWithPreGloasParent(final SpecContext specContext) {
    specContext.assumeGloasActive();

    final Spec forkTransitionSpec = TestSpecFactory.createMinimalWithGloasForkEpoch(ONE);
    final StorageSystem forkTransitionStorageSystem =
        InMemoryStorageSystemBuilder.buildDefault(forkTransitionSpec);
    forkTransitionStorageSystem.chainUpdater().initializeGenesis();
    final ReceivedBlockEventsChannel forkTransitionReceivedBlockEventsChannelPublisher =
        mock(ReceivedBlockEventsChannel.class);
    final GossipValidationHelper forkTransitionGossipValidationHelper =
        new GossipValidationHelper(
            forkTransitionSpec,
            forkTransitionStorageSystem.recentChainData(),
            forkTransitionStorageSystem.getMetricsSystem());
    final BlockGossipValidator forkTransitionBlockGossipValidator =
        new BlockGossipValidator(
            forkTransitionSpec,
            forkTransitionGossipValidationHelper,
            forkTransitionReceivedBlockEventsChannelPublisher);

    final UInt64 firstGloasSlot = forkTransitionSpec.computeStartSlotAtEpoch(ONE);
    final UInt64 preGloasParentSlot = firstGloasSlot.minus(ONE);
    final SignedBlockAndState preGloasParentBlockAndState =
        forkTransitionStorageSystem.chainUpdater().advanceChain(preGloasParentSlot);
    final SignedBlockAndState firstGloasBlockAndState =
        forkTransitionStorageSystem.chainBuilder().generateBlockAtSlot(firstGloasSlot);
    final SignedBeaconBlock firstGloasBlock = firstGloasBlockAndState.getBlock();
    forkTransitionStorageSystem.chainUpdater().setCurrentSlot(firstGloasSlot);

    assertThat(
            forkTransitionSpec
                .atSlot(preGloasParentBlockAndState.getSlot())
                .getMilestone()
                .isLessThan(SpecMilestone.GLOAS))
        .isTrue();
    assertThat(forkTransitionSpec.atSlot(firstGloasBlock.getSlot()).getMilestone())
        .isEqualTo(SpecMilestone.GLOAS);
    assertThat(
            forkTransitionGossipValidationHelper
                .getParentStateInBlockEpoch(
                    preGloasParentSlot, firstGloasBlock.getParentRoot(), firstGloasSlot)
                .join()
                .orElseThrow()
                .toVersionGloas())
        .isPresent();
    assertThat(forkTransitionBlockGossipValidator.validate(firstGloasBlock, true))
        .isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verify(forkTransitionReceivedBlockEventsChannelPublisher).onBlockValidated(firstGloasBlock);
  }

  @TestTemplate
  void shouldSaveForFutureWhenParentFullPayloadIsNotAvailable(final SpecContext specContext) {
    specContext.assumeGloasActive();

    final UInt64 parentSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState parentBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(parentSlot);
    storageSystem.chainUpdater().saveBlock(parentBlockAndState);

    final UInt64 childSlot = parentSlot.plus(ONE);
    final SignedBlockAndState childBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(childSlot);
    storageSystem.chainUpdater().setCurrentSlot(childSlot);

    assertThat(blockGossipValidator.validate(childBlockAndState.getBlock(), true))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldSaveForFutureWhenParentFullPayloadHashAlsoMatchesLatestBlockHash(
      final SpecContext specContext) {
    specContext.assumeGloasActive();

    final UInt64 parentSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState parentBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(parentSlot);
    final ExecutionPayloadBid parentBid =
        parentBlockAndState
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow()
            .getMessage();
    final SignedBlockAndState parentBlockAndStateWithOverlappingPayloadHashes =
        new SignedBlockAndState(
            parentBlockAndState.getBlock(),
            parentBlockAndState
                .getState()
                .updated(
                    state ->
                        MutableBeaconStateGloas.required(state)
                            .setLatestBlockHash(parentBid.getBlockHash())));
    storageSystem.chainUpdater().saveBlock(parentBlockAndStateWithOverlappingPayloadHashes);

    final UInt64 childSlot = parentSlot.plus(ONE);
    final SignedBlockAndState childBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(childSlot);
    final ExecutionPayloadBid childBid =
        childBlockAndState
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow()
            .getMessage();
    final ExecutionRequests parentExecutionRequests =
        childBlockAndState
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalParentExecutionRequests()
            .orElseThrow();
    final ExecutionRequests defaultParentExecutionRequests =
        SchemaDefinitionsGloas.required(spec.atSlot(childSlot).getSchemaDefinitions())
            .getExecutionRequestsSchema()
            .getDefault();
    storageSystem.chainUpdater().setCurrentSlot(childSlot);

    assertThat(childBid.getParentBlockHash()).isEqualTo(parentBid.getBlockHash());
    assertThat(
            BeaconStateGloas.required(parentBlockAndStateWithOverlappingPayloadHashes.getState())
                .getLatestBlockHash())
        .isEqualTo(parentBid.getBlockHash());
    assertThat(parentExecutionRequests.hashTreeRoot())
        .isEqualTo(parentBid.getExecutionRequestsRoot());
    assertThat(parentExecutionRequests).isNotEqualTo(defaultParentExecutionRequests);
    assertThat(blockGossipValidator.validate(childBlockAndState.getBlock(), true))
        .isCompletedWithValueMatching(InternalValidationResult::isSaveForFuture);
  }

  @TestTemplate
  void shouldAcceptGloasBlockBuildingOnEmptyParentWhenParentFullPayloadIsAvailable(
      final SpecContext specContext) {
    specContext.assumeGloasActive();

    final UInt64 parentSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState parentBlockAndState =
        storageSystem.chainUpdater().advanceChain(parentSlot);
    final ExecutionPayloadBid parentBid =
        parentBlockAndState
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalSignedExecutionPayloadBid()
            .orElseThrow()
            .getMessage();
    final Bytes32 parentEmptyExecutionBlockHash =
        BeaconStateGloas.required(parentBlockAndState.getState()).getLatestBlockHash();

    final UInt64 childSlot = parentSlot.plus(ONE);
    final SignedBlockAndState childBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(childSlot);
    final ExecutionRequests defaultParentExecutionRequests =
        SchemaDefinitionsGloas.required(spec.atSlot(childSlot).getSchemaDefinitions())
            .getExecutionRequestsSchema()
            .getDefault();
    final SignedBeaconBlock emptyParentChildBlock =
        createBlockWithModifiedExecutionPayloadBid(
            childBlockAndState,
            originalExecutionPayloadBid ->
                originalExecutionPayloadBid
                    .getSchema()
                    .create(
                        parentEmptyExecutionBlockHash,
                        originalExecutionPayloadBid.getParentBlockRoot(),
                        originalExecutionPayloadBid.getBlockHash(),
                        originalExecutionPayloadBid.getPrevRandao(),
                        originalExecutionPayloadBid.getFeeRecipient(),
                        originalExecutionPayloadBid.getGasLimit(),
                        originalExecutionPayloadBid.getBuilderIndex(),
                        originalExecutionPayloadBid.getSlot(),
                        originalExecutionPayloadBid.getValue(),
                        originalExecutionPayloadBid.getExecutionPayment(),
                        originalExecutionPayloadBid.getBlobKzgCommitments(),
                        defaultParentExecutionRequests.hashTreeRoot()),
            defaultParentExecutionRequests);
    storageSystem.chainUpdater().setCurrentSlot(childSlot);

    assertThat(parentBid.getBlockHash()).isNotEqualTo(parentEmptyExecutionBlockHash);
    assertResultIsAccept(
        emptyParentChildBlock, blockGossipValidator.validate(emptyParentChildBlock, true));
  }

  /**
   * The consensus-spec gossip rules don't require an EMPTY parent to carry default {@code
   * parent_execution_requests} -- that rule only applies during state transition (see {@code
   * BlockProcessorGloas#processParentExecutionPayload}), which is exercised separately. At gossip
   * time, a block building on an EMPTY parent with non-default parent execution requests (as long
   * as they respect {@code verify_execution_requests_limits}) must be accepted.
   */
  @TestTemplate
  void shouldAcceptGloasBlockBuildingOnEmptyParentWithNonDefaultParentExecutionRequests(
      final SpecContext specContext) {
    specContext.assumeGloasActive();

    final UInt64 parentSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState parentBlockAndState =
        storageSystem.chainUpdater().advanceChain(parentSlot);
    final Bytes32 parentEmptyExecutionBlockHash =
        BeaconStateGloas.required(parentBlockAndState.getState()).getLatestBlockHash();

    final UInt64 childSlot = parentSlot.plus(ONE);
    final SignedBlockAndState childBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(childSlot);
    final ExecutionRequests parentExecutionRequests =
        specContext.getDataStructureUtil().randomExecutionRequests(parentSlot);
    final SignedBeaconBlock emptyParentChildBlockWithParentExecutionRequests =
        createBlockWithModifiedExecutionPayloadBid(
            childBlockAndState,
            originalExecutionPayloadBid ->
                originalExecutionPayloadBid
                    .getSchema()
                    .create(
                        parentEmptyExecutionBlockHash,
                        originalExecutionPayloadBid.getParentBlockRoot(),
                        originalExecutionPayloadBid.getBlockHash(),
                        originalExecutionPayloadBid.getPrevRandao(),
                        originalExecutionPayloadBid.getFeeRecipient(),
                        originalExecutionPayloadBid.getGasLimit(),
                        originalExecutionPayloadBid.getBuilderIndex(),
                        originalExecutionPayloadBid.getSlot(),
                        originalExecutionPayloadBid.getValue(),
                        originalExecutionPayloadBid.getExecutionPayment(),
                        originalExecutionPayloadBid.getBlobKzgCommitments(),
                        parentExecutionRequests.hashTreeRoot()),
            parentExecutionRequests);
    storageSystem.chainUpdater().setCurrentSlot(childSlot);

    assertResultIsAccept(
        emptyParentChildBlockWithParentExecutionRequests,
        blockGossipValidator.validate(emptyParentChildBlockWithParentExecutionRequests, true));
  }

  @TestTemplate
  void shouldRejectBlockWithIncorrectExecutionPayloadBidParentRoot(final SpecContext specContext) {
    specContext.assumeGloasActive();
    final UInt64 nextSlot = recentChainData.getHeadSlot().plus(ONE);
    final SignedBlockAndState signedBlockAndState =
        storageSystem.chainBuilder().generateBlockAtSlot(nextSlot);
    storageSystem.chainUpdater().setCurrentSlot(nextSlot);

    final Bytes32 badParentBlockRoot = Bytes32.random();

    final SignedBeaconBlock invalidBlock =
        createBlockWithModifiedExecutionPayloadBid(
            signedBlockAndState,
            originalExecutionPayloadBid ->
                originalExecutionPayloadBid
                    .getSchema()
                    .create(
                        originalExecutionPayloadBid.getParentBlockHash(),
                        badParentBlockRoot,
                        originalExecutionPayloadBid.getBlockHash(),
                        originalExecutionPayloadBid.getPrevRandao(),
                        originalExecutionPayloadBid.getFeeRecipient(),
                        originalExecutionPayloadBid.getGasLimit(),
                        originalExecutionPayloadBid.getBuilderIndex(),
                        originalExecutionPayloadBid.getSlot(),
                        originalExecutionPayloadBid.getValue(),
                        originalExecutionPayloadBid.getExecutionPayment(),
                        originalExecutionPayloadBid.getBlobKzgCommitments(),
                        originalExecutionPayloadBid.getExecutionRequestsRoot()));

    assertThat(blockGossipValidator.validate(invalidBlock, true))
        .isCompletedWithValueMatching(
            result ->
                result.equals(
                    InternalValidationResult.reject(
                        "Execution payload bid has invalid parent block root %s, expecting %s",
                        badParentBlockRoot, signedBlockAndState.getBlock().getParentRoot())));
  }

  private SignedBeaconBlock createBlockWithModifiedExecutionPayloadBid(
      final SignedBlockAndState baseBlockAndState,
      final Function<ExecutionPayloadBid, ExecutionPayloadBid> bidModifier) {
    return createBlockWithModifiedExecutionPayloadBid(
        baseBlockAndState,
        bidModifier,
        baseBlockAndState
            .getBlock()
            .getMessage()
            .getBody()
            .getOptionalParentExecutionRequests()
            .orElseThrow());
  }

  private SignedBeaconBlock createBlockWithModifiedExecutionPayloadBid(
      final SignedBlockAndState baseBlockAndState,
      final Function<ExecutionPayloadBid, ExecutionPayloadBid> bidModifier,
      final ExecutionRequests parentExecutionRequests) {
    final SignedBeaconBlock originalSignedBeaconBlock = baseBlockAndState.getBlock();
    final BeaconBlockBody originalBeaconBlockBody =
        originalSignedBeaconBlock.getMessage().getBody();
    final SignedExecutionPayloadBid originalSignedBid =
        originalBeaconBlockBody.getOptionalSignedExecutionPayloadBid().orElseThrow();
    final ExecutionPayloadBid modifiedBid = bidModifier.apply(originalSignedBid.getMessage());
    final SignedExecutionPayloadBid modifiedSignedBid =
        originalSignedBid.getSchema().create(modifiedBid, originalSignedBid.getSignature());

    final BeaconBlockBodyBuilderGloas builder =
        new BeaconBlockBodyBuilderGloas(
            originalBeaconBlockBody.getSchema().toVersionGloas().orElseThrow());
    final BeaconBlockBody modifiedBody =
        builder
            .randaoReveal(originalBeaconBlockBody.getRandaoReveal())
            .eth1Data(originalBeaconBlockBody.getEth1Data())
            .graffiti(originalBeaconBlockBody.getGraffiti())
            .proposerSlashings(originalBeaconBlockBody.getProposerSlashings())
            .attesterSlashings(originalBeaconBlockBody.getAttesterSlashings())
            .attestations(originalBeaconBlockBody.getAttestations())
            .deposits(originalBeaconBlockBody.getDeposits())
            .voluntaryExits(originalBeaconBlockBody.getVoluntaryExits())
            .syncAggregate(originalBeaconBlockBody.getOptionalSyncAggregate().orElseThrow())
            .blsToExecutionChanges(
                originalBeaconBlockBody.getOptionalBlsToExecutionChanges().orElseThrow())
            .payloadAttestations(
                originalBeaconBlockBody.getOptionalPayloadAttestations().orElseThrow())
            .parentExecutionRequests(parentExecutionRequests)
            .signedExecutionPayloadBid(modifiedSignedBid)
            .build();

    final UInt64 proposerIndex = originalSignedBeaconBlock.getMessage().getProposerIndex();

    final BeaconBlock modifiedUnsignedBlock =
        new BeaconBlock(
            spec.getGenesisSchemaDefinitions().getBeaconBlockSchema(),
            originalSignedBeaconBlock.getSlot(),
            proposerIndex,
            originalSignedBeaconBlock.getParentRoot(),
            originalSignedBeaconBlock.getStateRoot(),
            modifiedBody);

    final BLSSignature newSignature =
        storageSystem
            .chainBuilder()
            .getSigner(proposerIndex.intValue())
            .signBlock(
                modifiedUnsignedBlock,
                storageSystem.chainBuilder().getLatestBlockAndState().getState().getForkInfo())
            .join();

    return SignedBeaconBlock.create(spec, modifiedUnsignedBlock, newSignature);
  }

  private void assertResultIsAccept(
      final SignedBeaconBlock block, final SafeFuture<InternalValidationResult> result) {
    assertThat(result).isCompletedWithValueMatching(InternalValidationResult::isAccept);
    verify(receivedBlockEventsChannelPublisher).onBlockValidated(block);
  }
}
