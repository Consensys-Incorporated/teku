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

package tech.pegasys.teku.reference.phase0.gossip;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.pegasys.teku.infrastructure.async.SafeFutureAssert.safeJoin;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes32;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBidSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayload;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.PayloadStatus;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.OperationAddedSubscriber;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.execution.ProposerPreferencesManager;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadGossipValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ProposerPreferencesGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class GossipExecutionPayloadBidTestExecutor implements TestExecutor {
  private final List<String> testsToSkip;

  private static final int MIN_BID_INCREMENT_PERCENTAGE = 1;

  public GossipExecutionPayloadBidTestExecutor(final String... testsToSkip) {
    this.testsToSkip = List.of(testsToSkip);
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    if (testsToSkip.contains(testDefinition.getTestName())) {
      throw new TestAbortedException(
          "Test " + testDefinition.getDisplayName() + " has been ignored");
    }

    final GossipExecutionPayloadBidMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipExecutionPayloadBidMetaData.class);
    final boolean signatureVerificationDisabled = metaData.getBlsSetting() == BlsSetting.IGNORED;
    final Spec spec = testDefinition.getSpec(!signatureVerificationDisabled);
    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");

    final SchemaDefinitionsGloas schemaDefinitions =
        SchemaDefinitionsGloas.required(spec.atSlot(state.getSlot()).getSchemaDefinitions());
    final SignedProposerPreferencesSchema proposerPreferencesSchema =
        schemaDefinitions.getSignedProposerPreferencesSchema();
    final SignedExecutionPayloadEnvelopeSchema envelopeSchema =
        schemaDefinitions.getSignedExecutionPayloadEnvelopeSchema();
    final SignedExecutionPayloadBidSchema signedBidSchema =
        schemaDefinitions.getSignedExecutionPayloadBidSchema();

    final List<BlockEntryAndBlock> blocks =
        metaData.getBlocks().stream()
            .map(
                blockEntry ->
                    new BlockEntryAndBlock(
                        blockEntry,
                        loadSsz(
                            testDefinition,
                            blockEntry.getBlock() + ".ssz_snappy",
                            spec::deserializeSignedBeaconBlock)))
            .toList();

    final GossipTestContext ctx =
        GossipTestContext.create(
            spec,
            state,
            blocks.stream()
                .filter(b -> !b.blockEntry().isFailed())
                .map(BlockEntryAndBlock::block)
                .toList());

    final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();

    for (final BlockEntryAndBlock blockEntryAndBlock : blocks) {
      final GossipExecutionPayloadBidMetaData.BlockEntry blockEntry =
          blockEntryAndBlock.blockEntry();
      final SignedBeaconBlock block = blockEntryAndBlock.block();
      if (block.getRoot().equals(ctx.anchorPoint.getRoot())) {
        continue;
      }
      // Advance the fork choice clock enough to import this block's slot. Some tests validate
      // messages before the last block's slot has started (the disparity tests), so tick to the
      // slot start here rather than to current_time_ms. The gossip validation time is controlled
      // separately via the per-message helper below.
      final UInt64 blockSlotStartMs =
          spec.computeTimeMillisAtSlot(block.getSlot(), ctx.recentChainData.getGenesisTimeMillis());
      ctx.forkChoice.onTick(blockSlotStartMs, Optional.empty());
      final BlockImportResult importResult =
          safeJoin(
              ctx.forkChoice.onBlock(
                  block, Optional.empty(), BlockBroadcastValidator.NOOP, ctx.executionLayer));
      if (blockEntry.isFailed()) {
        invalidBlockRoots.put(
            block.getRoot(), BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
      } else {
        assertThat(importResult.isSuccessful())
            .describedAs("Expected setup block %s to import successfully", blockEntry.getBlock())
            .isTrue();
      }
      // blockEntry.getPayload() names the payload already revealed for this block in the setup
      // store, which is what makes the fork choice node FULL. It is distinct from the gossip level
      // seen cache below: a fixture that expects the bid's parent payload to be unknown still names
      // the payload here but ships no envelope file, so the file's presence is what decides.
      if (blockEntry.getPayload() != null) {
        final Path envelopeFile =
            testDefinition.getTestDirectory().resolve(blockEntry.getPayload() + ".ssz_snappy");
        if (Files.exists(envelopeFile)) {
          applyExecutionPayloadToStore(
              spec,
              ctx.recentChainData,
              ctx.forkChoice,
              loadSsz(
                  testDefinition,
                  blockEntry.getPayload() + ".ssz_snappy",
                  envelopeSchema::sszDeserialize));
        }
      }
    }

    // The finalized checkpoint from meta.yaml is deliberately not committed to the store. None of
    // the execution payload bid gossip rules consult finalization; pyspec only needs it so that
    // get_head works. Committing it here would prune every pre-finalization block, which breaks
    // the dependent-root lookup the proposer preferences prerequisite messages rely on, and can
    // leave the store with a justified root it no longer holds.

    // Use a mutable time reference so the gossip validators see each message's current_time_ms even
    // when that time is earlier than the store's time (which only moves forward). This is necessary
    // for the disparity-boundary tests, where blocks must be imported after their slot starts but
    // messages are validated at a time before that slot starts.
    final UInt64[] validationTimeMs = {UInt64.valueOf(metaData.getCurrentTimeMs())};

    // Models the spec's seen.execution_payloads: a gossip level cache of payloads that passed
    // gossip validation, keyed by execution block hash. It is deliberately not the fork choice
    // store. These fixtures reuse one beacon block across the whole suite while varying the payload
    // revealed for it, so a payload can be gossip valid while being inconsistent with the bid that
    // block committed to -- which full envelope import rightly rejects. Teku's production lookup
    // reads the revealed payload back out of fork choice, so it is overridden here to consult this
    // cache instead.
    final Map<Bytes32, UInt64> seenExecutionPayloadGasLimits = new ConcurrentHashMap<>();

    // The fixtures declare a finalized checkpoint that replaying their blocks alone never reaches:
    // the chain only runs to slot 16, so epoch processing can finalize at most epoch 0. is_active_
    // builder requires builder.deposit_epoch < state.finalized_checkpoint.epoch, so with a state
    // finalized at epoch 0 no builder can ever be active. Apply the declared checkpoint to the
    // validation state instead of committing it to the store, which would prune the
    // pre-finalization
    // blocks that the proposer preferences dependent-root lookup needs.
    final Optional<Checkpoint> declaredFinalizedCheckpoint =
        Optional.ofNullable(metaData.getFinalizedCheckpoint())
            .map(checkpoint -> checkpoint.toCheckpoint(testDefinition, spec));

    final GossipValidationHelper gossipValidationHelper =
        new GossipValidationHelper(spec, ctx.recentChainData, ctx.metricsSystem) {
          @Override
          public UInt64 getCurrentTimeMillis() {
            return validationTimeMs[0];
          }

          @Override
          public Optional<UInt64> getGasLimitForExecutionPayload(
              final Bytes32 blockRoot, final Bytes32 blockHash) {
            final Optional<UInt64> seenGasLimit =
                Optional.ofNullable(seenExecutionPayloadGasLimits.get(blockHash));
            return seenGasLimit.isPresent()
                ? seenGasLimit
                : super.getGasLimitForExecutionPayload(blockRoot, blockHash);
          }

          @Override
          public SafeFuture<Optional<BeaconState>> getParentStateInBlockEpoch(
              final UInt64 parentBlockSlot, final Bytes32 parentBlockRoot, final UInt64 slot) {
            return super.getParentStateInBlockEpoch(parentBlockSlot, parentBlockRoot, slot)
                .thenApply(
                    maybeState ->
                        maybeState.map(
                            parentState ->
                                declaredFinalizedCheckpoint
                                    .map(
                                        checkpoint ->
                                            parentState.updated(
                                                mutableState ->
                                                    mutableState.setFinalizedCheckpoint(
                                                        checkpoint)))
                                    .orElse(parentState)));
          }
        };

    final BlockGossipValidator blockGossipValidator =
        new BlockGossipValidator(
            spec,
            gossipValidationHelper,
            new ReceivedBlockEventsChannel() {
              @Override
              public void onBlockValidated(final SignedBeaconBlock block) {}

              @Override
              public void onBlockImported(
                  final SignedBeaconBlock block, final boolean executionOptimistic) {}
            });

    // acceptedPreferences tracks proposer preferences that have been accepted by the validator,
    // so the bid validator can look them up to check bid compatibility.
    final Map<UInt64, ProposerPreferences> acceptedPreferences = new ConcurrentHashMap<>();
    final ProposerPreferencesManager proposerPreferencesManager =
        new ProposerPreferencesManager() {
          @Override
          public SafeFuture<InternalValidationResult> addLocal(
              final SignedProposerPreferences signedProposerPreferences) {
            return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
          }

          @Override
          public SafeFuture<InternalValidationResult> addRemote(
              final SignedProposerPreferences signedProposerPreferences) {
            return SafeFuture.completedFuture(InternalValidationResult.ACCEPT);
          }

          @Override
          public Optional<ProposerPreferences> getProposerPreferences(final UInt64 slot) {
            return Optional.ofNullable(acceptedPreferences.get(slot));
          }

          @Override
          public void subscribeOperationAdded(
              final OperationAddedSubscriber<SignedProposerPreferences> subscriber) {}
        };

    final ProposerPreferencesGossipValidator preferencesValidator =
        new ProposerPreferencesGossipValidator(spec, gossipValidationHelper, ctx.recentChainData);
    final ExecutionPayloadGossipValidator envelopeValidator =
        new ExecutionPayloadGossipValidator(
            spec, gossipValidationHelper, blockGossipValidator, invalidBlockRoots);
    final ExecutionPayloadBidGossipValidator bidValidator =
        new ExecutionPayloadBidGossipValidator(
            spec, gossipValidationHelper, proposerPreferencesManager, MIN_BID_INCREMENT_PERCENTAGE);

    for (final GossipExecutionPayloadBidMetaData.Message message : metaData.getMessages()) {
      validationTimeMs[0] = UInt64.valueOf(message.getCurrentTimeMs());
      ctx.forkChoice.onTick(UInt64.valueOf(message.getCurrentTimeMs()), Optional.empty());

      final String messageName = message.getMessage();
      final InternalValidationResult result;
      if (messageName.startsWith("proposer_preferences_")) {
        final SignedProposerPreferences signedPreferences =
            loadSsz(
                testDefinition,
                messageName + ".ssz_snappy",
                proposerPreferencesSchema::sszDeserialize);
        result = safeJoin(preferencesValidator.validate(signedPreferences));
        if (result.isAccept()) {
          acceptedPreferences.put(
              signedPreferences.getMessage().getProposalSlot(), signedPreferences.getMessage());
        }
      } else if (messageName.startsWith("execution_payload_envelope_")) {
        final SignedExecutionPayloadEnvelope signedEnvelope =
            loadSsz(testDefinition, messageName + ".ssz_snappy", envelopeSchema::sszDeserialize);
        result = safeJoin(envelopeValidator.validate(signedEnvelope));
        if (result.isAccept()) {
          final ExecutionPayload payload = signedEnvelope.getMessage().getPayload();
          seenExecutionPayloadGasLimits.put(payload.getBlockHash(), payload.getGasLimit());
          applyExecutionPayloadToStore(spec, ctx.recentChainData, ctx.forkChoice, signedEnvelope);
        }
      } else if (messageName.startsWith("execution_payload_bid_")) {
        final SignedExecutionPayloadBid signedBid =
            loadSsz(testDefinition, messageName + ".ssz_snappy", signedBidSchema::sszDeserialize);
        result = safeJoin(bidValidator.validate(signedBid));
      } else {
        throw new AssertionError("Unknown message type for: " + messageName);
      }

      GossipTestContext.assertValidationResult(
          "message " + messageName, message.getExpected(), result);
    }
  }

  /**
   * Reveals an execution payload in the store without running full envelope verification, marking
   * its fork choice node FULL.
   *
   * <p>These fixtures reuse a single beacon block across the suite while varying the payload
   * revealed for it -- the gas limit tests exist precisely to vary the parent payload's gas limit
   * -- so the payload frequently does not match the gas limit the block's bid committed to. pyspec
   * builds that store state directly and never verifies it, whereas {@code
   * ForkChoice#onExecutionPayloadEnvelope} rightly refuses it. Verification is the subject of the
   * execution payload envelope tests, not these, so the store state is set up directly here.
   */
  private static void applyExecutionPayloadToStore(
      final Spec spec,
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      final SignedExecutionPayloadEnvelope signedEnvelope) {
    final UpdatableStore.StoreTransaction transaction = recentChainData.startStoreTransaction();
    spec.atSlot(signedEnvelope.getSlot())
        .getForkChoiceUtil()
        .applyExecutionPayloadToStore(transaction, signedEnvelope, false);
    safeJoin(transaction.commit());
    recentChainData
        .getStore()
        .getForkChoiceStrategy()
        .onExecutionPayloadResult(signedEnvelope.getBeaconBlockRoot(), PayloadStatus.VALID, false);
    // Recompute the head so it reflects the newly revealed payload; the full envelope import path
    // does this itself, and without it the chain head keeps the payload status it had before.
    safeJoin(forkChoice.processHead());
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipExecutionPayloadBidMetaData {

    @JsonProperty(value = "blocks", required = true)
    private List<BlockEntry> blocks;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "current_time_ms", required = true)
    private long currentTimeMs;

    @JsonProperty(value = "bls_setting", defaultValue = "0")
    private int blsSetting;

    @JsonProperty(value = "finalized_checkpoint")
    private GossipTestContext.FinalizedCheckpoint finalizedCheckpoint;

    public List<BlockEntry> getBlocks() {
      return blocks;
    }

    public List<Message> getMessages() {
      return messages;
    }

    public long getCurrentTimeMs() {
      return currentTimeMs;
    }

    public BlsSetting getBlsSetting() {
      return BlsSetting.forCode(blsSetting);
    }

    public GossipTestContext.FinalizedCheckpoint getFinalizedCheckpoint() {
      return finalizedCheckpoint;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BlockEntry {

      @JsonProperty(value = "block", required = true)
      private String block;

      @JsonProperty(value = "failed", defaultValue = "false")
      private boolean failed;

      @JsonProperty(value = "payload")
      private String payload;

      public String getBlock() {
        return block;
      }

      public boolean isFailed() {
        return failed;
      }

      public String getPayload() {
        return payload;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Message {

      @JsonProperty(value = "current_time_ms", required = true)
      private long currentTimeMs;

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason")
      private String reason;

      public long getCurrentTimeMs() {
        return currentTimeMs;
      }

      public String getMessage() {
        return message;
      }

      public String getExpected() {
        return expected;
      }
    }
  }

  private record BlockEntryAndBlock(
      GossipExecutionPayloadBidMetaData.BlockEntry blockEntry, SignedBeaconBlock block) {}
}
