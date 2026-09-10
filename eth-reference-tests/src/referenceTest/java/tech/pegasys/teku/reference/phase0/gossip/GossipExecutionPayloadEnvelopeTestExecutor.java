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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelopeSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.block.ReceivedBlockEventsChannel;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.BlockGossipValidator;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadGossipValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;

public class GossipExecutionPayloadEnvelopeTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final GossipExecutionPayloadEnvelopeMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipExecutionPayloadEnvelopeMetaData.class);
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
                .filter(b -> !b.blockEntry().isFailed())
                .map(BlockEntryAndBlock::block)
                .toList());

    ctx.forkChoice.onTick(UInt64.valueOf(metaData.getCurrentTimeMs()), Optional.empty());

    final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();

    for (final BlockEntryAndBlock blockEntryAndBlock : blocks) {
      final GossipExecutionPayloadEnvelopeMetaData.BlockEntry blockEntry =
          blockEntryAndBlock.blockEntry();
      final SignedBeaconBlock block = blockEntryAndBlock.block();
      if (block.getRoot().equals(ctx.anchorPoint.getRoot())) {
        continue;
      }
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
    }

    // The declared finalized checkpoint drives the "envelope is from a slot at or after the
    // finalized slot" rule. It is applied by overriding the helper rather than committing it to the
    // store: these fixtures identify it by raw root, which need not be a block the store holds, and
    // committing it would prune the setup blocks the remaining rules rely on.
    final Optional<Checkpoint> declaredFinalizedCheckpoint =
        Optional.ofNullable(metaData.getFinalizedCheckpoint())
            .map(checkpoint -> checkpoint.toCheckpoint(testDefinition, spec));
    final GossipValidationHelper gossipValidationHelper =
        new GossipValidationHelper(spec, ctx.recentChainData, ctx.metricsSystem) {
          @Override
          public boolean isBeforeFinalizedSlot(final UInt64 slot) {
            return declaredFinalizedCheckpoint
                .map(checkpoint -> slot.isLessThan(checkpoint.getEpochStartSlot(spec)))
                .orElseGet(() -> super.isBeforeFinalizedSlot(slot));
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
    final SignedExecutionPayloadEnvelopeSchema schema =
        SchemaDefinitionsGloas.required(spec.atSlot(state.getSlot()).getSchemaDefinitions())
            .getSignedExecutionPayloadEnvelopeSchema();
    final ExecutionPayloadGossipValidator validator =
        new ExecutionPayloadGossipValidator(
            spec, gossipValidationHelper, blockGossipValidator, invalidBlockRoots);

    for (final GossipExecutionPayloadEnvelopeMetaData.Message message : metaData.getMessages()) {
      ctx.forkChoice.onTick(UInt64.valueOf(message.getCurrentTimeMs()), Optional.empty());

      final SignedExecutionPayloadEnvelope signedEnvelope =
          loadSsz(testDefinition, message.getMessage() + ".ssz_snappy", schema::sszDeserialize);
      final InternalValidationResult result = safeJoin(validator.validate(signedEnvelope));

      GossipTestContext.assertValidationResult(
          "execution payload envelope " + message.getMessage(), message.getExpected(), result);
    }
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipExecutionPayloadEnvelopeMetaData {

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

    public GossipTestContext.FinalizedCheckpoint getFinalizedCheckpoint() {
      return finalizedCheckpoint;
    }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class BlockEntry {

      @JsonProperty(value = "block", required = true)
      private String block;

      @JsonProperty(value = "failed", defaultValue = "false")
      private boolean failed;

      public String getBlock() {
        return block;
      }

      public boolean isFailed() {
        return failed;
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
      GossipExecutionPayloadEnvelopeMetaData.BlockEntry blockEntry, SignedBeaconBlock block) {}
}
