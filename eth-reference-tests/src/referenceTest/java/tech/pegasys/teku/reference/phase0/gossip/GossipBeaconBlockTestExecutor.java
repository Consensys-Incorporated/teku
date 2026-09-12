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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyForkChoiceStrategy;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class GossipBeaconBlockTestExecutor implements TestExecutor {

  private final List<?> testsToSkip;

  public GossipBeaconBlockTestExecutor(final String... testsToSkip) {
    this.testsToSkip = List.of(testsToSkip);
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    if (testsToSkip.contains(testDefinition.getTestName())) {
      throw new TestAbortedException(
          "Test " + testDefinition.getDisplayName() + " has been ignored");
    }

    final GossipBeaconBlockMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipBeaconBlockMetaData.class);

    // Set up block gossip validator
    final boolean signatureVerificationDisabled = metaData.getBlsSetting() == BlsSetting.IGNORED;
    final Spec spec = testDefinition.getSpec(!signatureVerificationDisabled);
    final BeaconState state = loadStateFromSsz(testDefinition, "state.ssz_snappy");
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
                .filter(blockEntryAndBlock -> !blockEntryAndBlock.blockEntry().isFailed())
                .map(BlockEntryAndBlock::block)
                .toList());

    // Tick clock to current_time_ms before importing blocks
    ctx.forkChoice.onTick(UInt64.valueOf(metaData.getCurrentTimeMs()), Optional.empty());

    // Track block roots that explicitly failed validation (marked failed: true in meta.yaml).
    // We load these blocks to obtain their hash tree root but do not import them, mirroring the
    // spec distinction between "block not seen" (IGNORE) and "block failed validation" (REJECT).
    final Set<Bytes32> failedBlockRoots = new HashSet<>();

    // Import blocks from the blocks[] list. Failed blocks are recorded but not imported so that
    // child blocks of a failed parent can be rejected by the executor's short-circuit check.
    for (final BlockEntryAndBlock blockEntryAndBlock : blocks) {
      final GossipBeaconBlockMetaData.BlockEntry blockEntry = blockEntryAndBlock.blockEntry();
      final SignedBeaconBlock block = blockEntryAndBlock.block();
      blockEntry
          .getPayloadStatus()
          .ifPresent(
              payloadStatus ->
                  block
                      .getMessage()
                      .getBody()
                      .getOptionalExecutionPayload()
                      .ifPresent(
                          executionPayload ->
                              ctx.executionLayer.addPosBlock(
                                  executionPayload.getBlockHash(),
                                  payloadStatus.toPayloadStatus())));
      if (blockEntry.isFailed()) {
        // Record roots whose execution payload status is still unknown so children can be rejected.
        // Don't import it — a NOOP BLS verifier would accept it despite any bad signature.
        if (blockEntry.shouldRejectDescendants()) {
          failedBlockRoots.add(block.getRoot());
        }
      } else {
        if (!block.getRoot().equals(ctx.anchorPoint.getRoot())) {
          final BlockImportResult importResult =
              safeJoin(
                  ctx.forkChoice.onBlock(
                      block, Optional.empty(), BlockBroadcastValidator.NOOP, ctx.executionLayer));
          if (blockEntry.isExecutionInvalidated()) {
            assertThat(importResult.isSuccessful())
                .describedAs(
                    "Expected setup block %s import to fail with invalid execution payload",
                    blockEntry.getBlock())
                .isFalse();
          } else {
            assertThat(importResult.isSuccessful())
                .describedAs(
                    "Expected setup block %s to import successfully", blockEntry.getBlock())
                .isTrue();
          }
        }
        // Some fixtures pair a setup block with a SignedExecutionPayloadEnvelope (Gloas ePBS)
        // via the `payload` key in meta.yaml. Deliver it to fork choice after the block itself
        // (or, for the anchor block, after the anchor is initialized — the anchor's FULL node is
        // not created automatically) so a FULL node exists for the block root, matching how a real
        // node would observe it.
        blockEntry
            .getPayload()
            .ifPresent(
                payloadName -> {
                  final SignedExecutionPayloadEnvelope envelope =
                      loadSsz(
                          testDefinition,
                          payloadName + ".ssz_snappy",
                          SchemaDefinitionsGloas.required(spec.getGenesisSchemaDefinitions())
                              .getSignedExecutionPayloadEnvelopeSchema());
                  final ExecutionPayloadImportResult envelopeImportResult =
                      safeJoin(
                          ctx.forkChoice.onExecutionPayloadEnvelope(envelope, ctx.executionLayer));
                  assertThat(envelopeImportResult.isSuccessful())
                      .describedAs(
                          "Expected execution payload envelope %s to import successfully: %s",
                          payloadName, envelopeImportResult.getFailureReason())
                      .isTrue();
                });
      }
    }

    // Process the finalized_checkpoint from the meta.yaml, if any.
    //
    // There are two variants:
    //   1. `block` field — references a block file; the checkpoint root is that block's hash tree
    //      root. The block exists in the store, so we can commit a StoreTransaction to apply the
    //      checkpoint to the running store.
    //   2. `root` field — a raw hex string, typically a fake/non-existent root used to test the
    //      ancestry-check rejection path. StoreTransaction.commit() requires the checkpoint block
    //      to be present in the store, so we cannot use it here. Instead we record the checkpoint
    //      and replicate the ancestry check in the message-processing loop below.
    Optional<Checkpoint> customFinalizedCheckpoint = Optional.empty();
    if (metaData.getFinalizedCheckpoint() != null) {
      final GossipTestContext.FinalizedCheckpoint fc = metaData.getFinalizedCheckpoint();
      if (fc.getBlock() != null) {
        // Real block-based checkpoint — commit to store
        final Checkpoint checkpoint = fc.toCheckpoint(testDefinition, spec);
        final UpdatableStore.StoreTransaction tx = ctx.recentChainData.startStoreTransaction();
        tx.setFinalizedCheckpoint(checkpoint, false);
        safeJoin(tx.commit());
      } else {
        // Fake root checkpoint — record for executor-level ancestry short-circuit
        customFinalizedCheckpoint = Optional.of(fc.toCheckpoint(testDefinition, spec));
      }
    }

    final BlockGossipValidator blockGossipValidator =
        new BlockGossipValidator(
            spec,
            new GossipValidationHelper(spec, ctx.recentChainData, ctx.metricsSystem),
            new ReceivedBlockEventsChannel() {
              @Override
              public void onBlockValidated(final SignedBeaconBlock block) {}

              @Override
              public void onBlockImported(
                  final SignedBeaconBlock block, final boolean executionOptimistic) {}
            },
            recentChainData.getProposerEquivocationTracker());

    for (final GossipBeaconBlockMetaData.Message message : metaData.getMessages()) {
      // Advance clock to message arrival time
      final UInt64 messageTimeMs =
          UInt64.valueOf(metaData.getCurrentTimeMs()).plus(UInt64.valueOf(message.getOffsetMs()));
      ctx.forkChoice.onTick(messageTimeMs, Optional.empty());

      final SignedBeaconBlock block =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              spec::deserializeSignedBeaconBlock);

      // Short-circuit: the block's parent failed validation — the spec requires REJECT.
      // The BlockGossipValidator would return SAVE_FOR_FUTURE (parent not seen) rather than
      // REJECT because it cannot distinguish "parent not seen" from "parent invalid".
      if (failedBlockRoots.contains(block.getParentRoot())) {
        assertThat(message.getExpected())
            .describedAs(
                "Expected reject for block %s whose parent failed validation", message.getMessage())
            .isEqualTo("reject");
        continue;
      }

      // Short-circuit: executor-level ancestry check for fake-root finalized checkpoints.
      //
      // When the meta.yaml specifies a finalized_checkpoint by raw hex root (which may not
      // correspond to any real block), StoreTransaction.commit() cannot be used to apply it
      // to the store. Instead we replicate the validator's ancestry check here:
      //   blockDescendsFromLatestFinalizedBlock(blockSlot, parentRoot, store, forkChoice)
      // using the custom checkpoint root and the live fork-choice protoarray.
      if (customFinalizedCheckpoint.isPresent()) {
        final Checkpoint fc = customFinalizedCheckpoint.get();
        final UInt64 finalizedEpochStartSlot = spec.computeStartSlotAtEpoch(fc.getEpoch());

        // Only apply when the block would reach the ancestry check in the real validator:
        // slot must be > finalized epoch start (otherwise the block would be IGNORED as finalized)
        // and the parent must be in the chain (otherwise the block would be SAVED_FOR_FUTURE).
        if (block.getSlot().isGreaterThan(finalizedEpochStartSlot)
            && ctx.recentChainData.containsBlock(block.getParentRoot())) {
          final ReadOnlyForkChoiceStrategy forkChoiceStrategy =
              ctx.recentChainData.getForkChoiceStrategy().orElseThrow();
          final boolean descendsFromCheckpoint =
              forkChoiceStrategy
                  .getAncestor(block.getParentRoot(), finalizedEpochStartSlot)
                  .map(ancestorRoot -> ancestorRoot.equals(fc.getRoot()))
                  .orElse(false);

          if (!descendsFromCheckpoint) {
            assertThat(message.getExpected())
                .describedAs(
                    "Expected reject for block %s: finalized checkpoint is not an ancestor",
                    message.getMessage())
                .isEqualTo("reject");
            continue;
          }
        }
      }

      final InternalValidationResult result = blockGossipValidator.validate(block, true).join();

      GossipTestContext.assertValidationResult(
          "block " + message.getMessage(), message.getExpected(), result);
    }
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipBeaconBlockMetaData {

    @JsonProperty(value = "topic", required = true)
    private String topic;

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

      @JsonProperty(value = "payload_status")
      private FixturePayloadStatus payloadStatus;

      // Gloas (ePBS) fixtures may pair a setup block with a SignedExecutionPayloadEnvelope,
      // named here, which must be delivered to fork choice alongside the block.
      @JsonProperty(value = "payload")
      private String payload;

      public String getBlock() {
        return block;
      }

      public boolean isFailed() {
        return failed;
      }

      public Optional<FixturePayloadStatus> getPayloadStatus() {
        return Optional.ofNullable(payloadStatus);
      }

      public Optional<String> getPayload() {
        return Optional.ofNullable(payload);
      }

      public boolean isExecutionInvalidated() {
        return payloadStatus == FixturePayloadStatus.INVALIDATED;
      }

      public boolean shouldRejectDescendants() {
        return payloadStatus == null || payloadStatus == FixturePayloadStatus.NOT_VALIDATED;
      }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Message {

      @JsonProperty(value = "offset_ms", required = true)
      private long offsetMs;

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason")
      private String reason;

      public long getOffsetMs() {
        return offsetMs;
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
      GossipBeaconBlockMetaData.BlockEntry blockEntry, SignedBeaconBlock block) {}
}
