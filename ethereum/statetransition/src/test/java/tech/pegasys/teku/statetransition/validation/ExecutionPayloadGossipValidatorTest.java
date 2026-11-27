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

package tech.pegasys.teku.statetransition.validation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.assertThatSafeFuture;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.SAVE_FOR_FUTURE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.TestSpecContext;
import tech.pegasys.teku.spec.TestSpecInvocationContextProvider.SpecContext;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.ExecutionPayloadCapella;
import tech.pegasys.teku.spec.datastructures.execution.versions.capella.Withdrawal;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateSchemaGloas;
import tech.pegasys.teku.spec.datastructures.validator.BroadcastValidationLevel;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.spec.util.DataStructureUtil;

@TestSpecContext(milestone = {SpecMilestone.GLOAS})
public class ExecutionPayloadGossipValidatorTest {
  private final Spec spec = mock(Spec.class);
  private final GossipValidationHelper gossipValidationHelper = mock(GossipValidationHelper.class);
  private final BlockGossipValidator blockGossipValidator = mock(BlockGossipValidator.class);
  private final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();
  private ExecutionPayloadGossipValidator validator;
  private DataStructureUtil dataStructureUtil;

  private SignedExecutionPayloadEnvelope signedEnvelope;
  private ExecutionPayloadEnvelope envelope;
  private UInt64 slot;
  private Bytes32 blockRoot;
  private BeaconBlock beaconBlock;
  private BeaconState blockState;
  private ExecutionPayload payload;
  private SchemaDefinitionsGloas schemaDefinitions;

  @BeforeEach
  void setUp(final SpecContext specContext) {
    dataStructureUtil = specContext.getDataStructureUtil();
    validator =
        new ExecutionPayloadGossipValidator(
            spec, gossipValidationHelper, blockGossipValidator, invalidBlockRoots);

    slot = dataStructureUtil.randomSlot();
    schemaDefinitions =
        SchemaDefinitionsGloas.required(specContext.getSpec().atSlot(slot).getSchemaDefinitions());
    // Non-empty transactions, so only the tests that add an empty one hit that rule
    payload =
        dataStructureUtil.randomExecutionPayload(
            slot, builder -> builder.transactions(List.of(Bytes.fromHexString("0x01"))));
    envelope =
        schemaDefinitions
            .getExecutionPayloadEnvelopeSchema()
            .create(
                payload,
                dataStructureUtil.randomExecutionRequests(slot),
                dataStructureUtil.randomBuilderIndex(),
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32());
    signedEnvelope =
        schemaDefinitions
            .getSignedExecutionPayloadEnvelopeSchema()
            .create(envelope, dataStructureUtil.randomSignature());
    slot = envelope.getSlot();
    blockRoot = envelope.getBeaconBlockRoot();

    beaconBlock = mockBlockWithMatchingBid();
    blockState =
        createBlockState(
            payload.getParentHash(),
            ExecutionPayloadCapella.required(payload).getWithdrawals().asList());

    when(gossipValidationHelper.getSlotForBlockRoot(envelope.getBeaconBlockRoot()))
        .thenReturn(Optional.of(slot));
    when(gossipValidationHelper.isBeforeFinalizedSlot(slot)).thenReturn(false);
    when(gossipValidationHelper.getStateAtBlockRoot(any(Bytes32.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockState)));
    when(spec.computeTimeAtSlot(any(BeaconState.class), eq(slot)))
        .thenReturn(payload.getTimestamp());
    final SpecVersion specVersion = mock(SpecVersion.class);
    final MiscHelpers miscHelpers = mock(MiscHelpers.class);
    final Bytes32 signingRoot = Bytes32.random();
    when(miscHelpers.computeSigningRoot(eq(envelope), any())).thenReturn(signingRoot);
    when(specVersion.miscHelpers()).thenReturn(miscHelpers);
    // Real config so the execution request and withdrawal limit rules see actual limits
    when(specVersion.getConfig()).thenReturn(specContext.getSpec().atSlot(slot).getConfig());
    when(spec.atSlot(slot)).thenReturn(specVersion);
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(true);
    // A gas limit that allows the payload's transactions
    when(gossipValidationHelper.getMaxPayloadGasLimit(any(), any()))
        .thenReturn(UInt64.valueOf(30_000_000));
    // The payload content hashes to the block hash it declares
    when(gossipValidationHelper.computeExecutionPayloadBlockHash(any()))
        .thenAnswer(
            invocation ->
                invocation.<ExecutionPayloadEnvelope>getArgument(0).getPayload().getBlockHash());
  }

  @TestTemplate
  void shouldAcceptWhenValid() {
    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnoreIfAlreadySeen() {
    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(ACCEPT);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            ignore(
                "Already received execution payload envelope with block root %s from builder with index %s",
                blockRoot, envelope.getBuilderIndex()));
  }

  @TestTemplate
  void shouldSaveForFutureIfBlockNotSeen() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockRoot)).thenReturn(Optional.empty());
    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldIgnoreIfSlotIsBeforeFinalized() {
    when(gossipValidationHelper.isBeforeFinalizedSlot(slot)).thenReturn(true);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            ignore("Signed execution payload envelope slot %s is before the finalized slot", slot));
  }

  @TestTemplate
  void shouldRejectIfBlockIsMarkedInvalid() {
    invalidBlockRoots.put(blockRoot, BlockImportResult.FAILED_INVALID_ANCESTRY);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject("Execution payload envelope block with root %s is invalid", blockRoot));
  }

  @TestTemplate
  void shouldRejectIfPayloadSlotIsDifferentFromBlockSlot() {
    when(gossipValidationHelper.getSlotForBlockRoot(blockRoot))
        .thenReturn(Optional.of(slot.plus(1)));
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Execution payload envelope slot %s does not match block slot %s",
                slot, slot.plus(1)));
  }

  @TestTemplate
  void shouldRejectIfBuilderIndexMismatch() {
    mockBlockWithBid(
        envelope.getParentBeaconBlockRoot(),
        envelope.getBuilderIndex().plus(1),
        payload.getPrevRandao(),
        payload.getGasLimit(),
        payload.getBlockHash(),
        envelope.getExecutionRequests().hashTreeRoot());

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid builder index. Execution payload envelope had %s but the block execution payload bid had %s",
                envelope.getBuilderIndex(), envelope.getBuilderIndex().plus(1)));
  }

  @TestTemplate
  void shouldRejectIfPayloadHashMismatch() {
    final Bytes32 bidBlockHash = dataStructureUtil.randomBytes32();
    mockBlockWithBid(
        envelope.getParentBeaconBlockRoot(),
        envelope.getBuilderIndex(),
        payload.getPrevRandao(),
        payload.getGasLimit(),
        bidBlockHash,
        envelope.getExecutionRequests().hashTreeRoot());

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid payload block hash. Execution Payload Envelope had %s but ExecutionPayload Bid had %s",
                payload.getBlockHash(), bidBlockHash));
  }

  @TestTemplate
  void shouldRejectIfExecutionRequestsMismatch() {
    final Bytes32 bidExecutionRequestsRoot = dataStructureUtil.randomBytes32();
    mockBlockWithBid(
        envelope.getParentBeaconBlockRoot(),
        envelope.getBuilderIndex(),
        payload.getPrevRandao(),
        payload.getGasLimit(),
        payload.getBlockHash(),
        bidExecutionRequestsRoot);

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid execution requests. Execution Payload Envelope had execution requests root of %s but ExecutionPayload Bid had %s",
                envelope.getExecutionRequests().hashTreeRoot(), bidExecutionRequestsRoot));
  }

  @TestTemplate
  void shouldRejectIfParentBeaconBlockRootMismatch() {
    final Bytes32 blockParentRoot = dataStructureUtil.randomBytes32();
    mockBlockWithBid(
        blockParentRoot,
        envelope.getBuilderIndex(),
        payload.getPrevRandao(),
        payload.getGasLimit(),
        payload.getBlockHash(),
        envelope.getExecutionRequests().hashTreeRoot());

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid parent beacon block root. Execution payload envelope had %s but the block had %s",
                envelope.getParentBeaconBlockRoot(), blockParentRoot));
  }

  @TestTemplate
  void shouldRejectIfPrevRandaoMismatch() {
    final Bytes32 bidPrevRandao = dataStructureUtil.randomBytes32();
    mockBlockWithBid(
        envelope.getParentBeaconBlockRoot(),
        envelope.getBuilderIndex(),
        bidPrevRandao,
        payload.getGasLimit(),
        payload.getBlockHash(),
        envelope.getExecutionRequests().hashTreeRoot());

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid payload prev randao. Execution Payload Envelope had %s but ExecutionPayload Bid had %s",
                payload.getPrevRandao(), bidPrevRandao));
  }

  @TestTemplate
  void shouldRejectIfGasLimitMismatch() {
    final UInt64 bidGasLimit = payload.getGasLimit().plus(1);
    mockBlockWithBid(
        envelope.getParentBeaconBlockRoot(),
        envelope.getBuilderIndex(),
        payload.getPrevRandao(),
        bidGasLimit,
        payload.getBlockHash(),
        envelope.getExecutionRequests().hashTreeRoot());

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid payload gas limit. Execution Payload Envelope had %s but ExecutionPayload Bid had %s",
                payload.getGasLimit(), bidGasLimit));
  }

  @TestTemplate
  void shouldAcceptWhenTransactionsCountIsAtTheLimit() {
    // MIN_TRANSACTION_GAS is 4096, so this gas limit allows exactly the single transaction
    when(gossipValidationHelper.getMaxPayloadGasLimit(beaconBlock, getBid()))
        .thenReturn(UInt64.valueOf(2 * 4096 - 1));

    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldRejectIfTooManyTransactions() {
    // MIN_TRANSACTION_GAS is 4096, so this gas limit allows no transactions
    final UInt64 maxGasLimit = UInt64.valueOf(4095);
    when(gossipValidationHelper.getMaxPayloadGasLimit(beaconBlock, getBid()))
        .thenReturn(maxGasLimit);

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Too many transactions. Execution payload envelope had %s but the maximum is %s for a gas limit of %s",
                1, 0, maxGasLimit));
  }

  @TestTemplate
  void shouldRejectIfParentHashIsNotTheLatestBlockHash() {
    final Bytes32 latestBlockHash = dataStructureUtil.randomBytes32();
    mockBlockState(
        createBlockState(
            latestBlockHash, ExecutionPayloadCapella.required(payload).getWithdrawals().asList()));

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid payload parent hash. Execution Payload Envelope had %s but the latest block hash is %s",
                payload.getParentHash(), latestBlockHash));
  }

  @TestTemplate
  void shouldRejectIfTimestampIsIncorrect() {
    final UInt64 expectedTimestamp = payload.getTimestamp().plus(1);
    when(spec.computeTimeAtSlot(any(BeaconState.class), eq(slot))).thenReturn(expectedTimestamp);

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid payload timestamp. Execution Payload Envelope had %s but expected %s",
                payload.getTimestamp(), expectedTimestamp));
  }

  @TestTemplate
  void shouldRejectIfPayloadContainsAnEmptyTransaction() {
    final ExecutionPayload payloadWithEmptyTransaction =
        dataStructureUtil.randomExecutionPayload(
            slot,
            builder ->
                builder
                    .parentHash(payload.getParentHash())
                    .prevRandao(payload.getPrevRandao())
                    .gasLimit(payload.getGasLimit())
                    .blockHash(payload.getBlockHash())
                    .timestamp(payload.getTimestamp())
                    .withdrawals(
                        () -> ExecutionPayloadCapella.required(payload).getWithdrawals().asList())
                    .transactions(List.of(Bytes.fromHexString("0x01"), Bytes.EMPTY)));
    final SignedExecutionPayloadEnvelope envelopeWithEmptyTransaction =
        createSignedEnvelopeWithPayload(payloadWithEmptyTransaction);

    assertThatSafeFuture(validator.validate(envelopeWithEmptyTransaction))
        .isCompletedWithValue(reject("Execution payload envelope contains an empty transaction"));
  }

  @TestTemplate
  void shouldRejectIfWithdrawalsDoNotMatchExpectedWithdrawals() {
    final List<Withdrawal> expectedWithdrawals =
        List.of(dataStructureUtil.randomWithdrawal(), dataStructureUtil.randomWithdrawal());
    final BeaconState blockStateWithOtherWithdrawals =
        createBlockState(payload.getParentHash(), expectedWithdrawals);
    mockBlockState(blockStateWithOtherWithdrawals);

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Invalid payload withdrawals. Execution Payload Envelope had withdrawals root %s but expected %s",
                ExecutionPayloadCapella.required(payload).getWithdrawals().hashTreeRoot(),
                BeaconStateGloas.required(blockStateWithOtherWithdrawals)
                    .getPayloadExpectedWithdrawals()
                    .hashTreeRoot()));
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalid() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));
  }

  @TestTemplate
  void shouldRejectAndNotMarkAsSeenIfBlockHashDoesNotMatchContent() {
    // Same block hash as the bid commits to, but the content hashes to something else
    final Bytes32 computedBlockHash = dataStructureUtil.randomBytes32();
    when(gossipValidationHelper.computeExecutionPayloadBlockHash(envelope))
        .thenReturn(computedBlockHash);

    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            reject(
                "Execution payload envelope block hash %s does not match the block hash %s computed from its content",
                payload.getBlockHash(), computedBlockHash));

    // not marked as seen, so the builder's correct envelope for the same key is still accepted
    when(gossipValidationHelper.computeExecutionPayloadBlockHash(envelope))
        .thenReturn(payload.getBlockHash());
    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldSaveForFutureIfStateIsUnavailable() {
    when(gossipValidationHelper.getStateAtBlockRoot(any(Bytes32.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.empty()));
    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(SAVE_FOR_FUTURE);
  }

  @TestTemplate
  void shouldNotMarkAsSeenIfValidationFails() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));

    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(true);

    assertThatSafeFuture(validator.validate(signedEnvelope)).isCompletedWithValue(ACCEPT);
  }

  @TestTemplate
  void shouldIgnoreIfAlreadySeenWithGossipLevel() {
    assertThatSafeFuture(
            validator.validate(signedEnvelope, Optional.of(BroadcastValidationLevel.GOSSIP)))
        .isCompletedWithValue(ACCEPT);
    assertThatSafeFuture(validator.validate(signedEnvelope))
        .isCompletedWithValue(
            ignore(
                "Already received execution payload envelope with block root %s from builder with index %s",
                blockRoot, envelope.getBuilderIndex()));
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalidWithGossipLevel() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);
    assertThatSafeFuture(
            validator.validate(signedEnvelope, Optional.of(BroadcastValidationLevel.GOSSIP)))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalidWithConsensusBroadcastValidationLevel() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);

    assertThatSafeFuture(
            validator.validate(signedEnvelope, Optional.of(BroadcastValidationLevel.CONSENSUS)))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));
  }

  @TestTemplate
  void shouldRejectIfSignatureIsInvalidWithConsensusAndEquivocationBroadcastValidationLevel() {
    when(gossipValidationHelper.isSignatureValidWithRespectToBuilderIndex(
            any(), any(), any(), any()))
        .thenReturn(false);

    assertThatSafeFuture(
            validator.validate(
                signedEnvelope, Optional.of(BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION)))
        .isCompletedWithValue(reject("Invalid signed execution payload envelope signature"));
  }

  @TestTemplate
  void shouldRejectIfBeaconBlockIsEquivocationWithConsensusAndEquivocationLevel() {
    when(blockGossipValidator.isBlockEquivocating(
            beaconBlock.getSlot(), beaconBlock.getProposerIndex(), beaconBlock.getRoot()))
        .thenReturn(true);

    assertThatSafeFuture(
            validator.validate(
                signedEnvelope, Optional.of(BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION)))
        .isCompletedWithValue(
            reject(
                "Execution payload envelope's beacon block with root %s is an equivocation",
                blockRoot));
  }

  @TestTemplate
  void shouldAcceptIfBeaconBlockIsNotEquivocationWithConsensusAndEquivocationLevel() {
    assertThatSafeFuture(
            validator.validate(
                signedEnvelope, Optional.of(BroadcastValidationLevel.CONSENSUS_AND_EQUIVOCATION)))
        .isCompletedWithValue(ACCEPT);
  }

  private BeaconBlock mockBlockWithMatchingBid() {
    return mockBlockWithBid(
        envelope.getParentBeaconBlockRoot(),
        envelope.getBuilderIndex(),
        payload.getPrevRandao(),
        payload.getGasLimit(),
        payload.getBlockHash(),
        envelope.getExecutionRequests().hashTreeRoot());
  }

  private BeaconBlock mockBlockWithBid(
      final Bytes32 parentRoot,
      final UInt64 builderIndex,
      final Bytes32 prevRandao,
      final UInt64 gasLimit,
      final Bytes32 blockHash,
      final Bytes32 executionRequestsRoot) {
    final ExecutionPayloadBidSchema bidSchema = schemaDefinitions.getExecutionPayloadBidSchema();
    final SignedExecutionPayloadBid bid =
        dataStructureUtil.randomSignedExecutionPayloadBid(
            bidSchema.create(
                dataStructureUtil.randomBytes32(),
                dataStructureUtil.randomBytes32(),
                blockHash,
                prevRandao,
                dataStructureUtil.randomEth1Address(),
                gasLimit,
                builderIndex,
                slot,
                dataStructureUtil.randomUInt64(),
                dataStructureUtil.randomUInt64(),
                bidSchema.getBlobKzgCommitmentsSchema().getDefault(),
                executionRequestsRoot));
    final BeaconBlock block =
        dataStructureUtil.randomBeaconBlock(
            slot,
            dataStructureUtil.randomBeaconBlockBody(
                builder -> builder.signedExecutionPayloadBid(bid)));
    final BeaconBlock blockWithParentRoot =
        new BeaconBlock(
            block.getSchema(),
            slot,
            block.getProposerIndex(),
            parentRoot,
            block.getStateRoot(),
            block.getBody());
    when(gossipValidationHelper.retrieveBlockByRoot(blockRoot))
        .thenReturn(SafeFuture.completedFuture(Optional.of(blockWithParentRoot)));
    return blockWithParentRoot;
  }

  private ExecutionPayloadBid getBid() {
    return beaconBlock.getBody().getOptionalSignedExecutionPayloadBid().orElseThrow().getMessage();
  }

  private BeaconState createBlockState(
      final Bytes32 latestBlockHash, final List<Withdrawal> payloadExpectedWithdrawals) {
    return BeaconStateGloas.required(dataStructureUtil.randomBeaconState())
        .updatedGloas(
            state -> {
              state.setLatestBlockHash(latestBlockHash);
              state.setPayloadExpectedWithdrawals(
                  BeaconStateSchemaGloas.required(state.getBeaconStateSchema())
                      .getPayloadExpectedWithdrawalsSchema()
                      .createFromElements(payloadExpectedWithdrawals));
            });
  }

  private void mockBlockState(final BeaconState state) {
    when(gossipValidationHelper.getStateAtBlockRoot(any(Bytes32.class)))
        .thenReturn(SafeFuture.completedFuture(Optional.of(state)));
  }

  private SignedExecutionPayloadEnvelope createSignedEnvelopeWithPayload(
      final ExecutionPayload newPayload) {
    final ExecutionPayloadEnvelope newEnvelope =
        envelope
            .getSchema()
            .create(
                newPayload,
                envelope.getExecutionRequests(),
                envelope.getBuilderIndex(),
                envelope.getBeaconBlockRoot(),
                envelope.getParentBeaconBlockRoot());
    return signedEnvelope.getSchema().create(newEnvelope, signedEnvelope.getSignature());
  }
}
