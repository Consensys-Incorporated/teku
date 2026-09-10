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
import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedProposerPreferencesSchema;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ProposerPreferencesGossipValidator;

public class GossipProposerPreferencesTestExecutor implements TestExecutor {

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final GossipProposerPreferencesMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipProposerPreferencesMetaData.class);
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
                .filter(b -> !b.blockEntry().isFailed() && !b.blockEntry().isPending())
                .map(BlockEntryAndBlock::block)
                .toList());

    for (final BlockEntryAndBlock blockEntryAndBlock : blocks) {
      final GossipProposerPreferencesMetaData.BlockEntry blockEntry =
          blockEntryAndBlock.blockEntry();
      final SignedBeaconBlock block = blockEntryAndBlock.block();
      if (block.getRoot().equals(ctx.anchorPoint.getRoot())) {
        continue;
      }
      // A pending block is one pyspec added to store.blocks without adding its post-state to
      // store.block_states, so that the "dependent block passes validation" rule fires. Teku's
      // store cannot hold a block without its state, so leave it unimported: validation then stops
      // one rule earlier, on "dependent root has not been seen", which is also an IGNORE.
      if (blockEntry.isPending()) {
        continue;
      }
      // Advance the fork choice clock enough to import this block's slot. Some tests validate
      // messages before the last block's slot has started (e.g. the lookahead disparity tests), so
      // tick to the slot start here rather than to current_time_ms. The gossip validation time is
      // controlled separately via the per-message helper below.
      final UInt64 blockSlotStartMs =
          spec.computeTimeMillisAtSlot(block.getSlot(), ctx.recentChainData.getGenesisTimeMillis());
      ctx.forkChoice.onTick(blockSlotStartMs, Optional.empty());
      final BlockImportResult importResult =
          safeJoin(
              ctx.forkChoice.onBlock(
                  block, Optional.empty(), BlockBroadcastValidator.NOOP, ctx.executionLayer));
      if (!blockEntry.isFailed()) {
        assertThat(importResult.isSuccessful())
            .describedAs("Expected setup block %s to import successfully", blockEntry.getBlock())
            .isTrue();
      }
    }

    // Use a mutable time reference so the gossip validator sees each message's current_time_ms even
    // when that time is earlier than the store's time (which only moves forward). This is necessary
    // for the disparity-boundary tests, where blocks must be imported after their slot starts but
    // messages are validated at a time before that slot starts.
    final UInt64[] validationTimeMs = {UInt64.valueOf(metaData.getCurrentTimeMs())};
    final GossipValidationHelper gossipValidationHelper =
        new GossipValidationHelper(spec, ctx.recentChainData, ctx.metricsSystem) {
          @Override
          public UInt64 getCurrentTimeMillis() {
            return validationTimeMs[0];
          }
        };
    final SignedProposerPreferencesSchema schema =
        SchemaDefinitionsGloas.required(spec.atSlot(state.getSlot()).getSchemaDefinitions())
            .getSignedProposerPreferencesSchema();
    final ProposerPreferencesGossipValidator validator =
        new ProposerPreferencesGossipValidator(spec, gossipValidationHelper, ctx.recentChainData);

    for (final GossipProposerPreferencesMetaData.Message message : metaData.getMessages()) {
      validationTimeMs[0] = UInt64.valueOf(message.getCurrentTimeMs());
      ctx.forkChoice.onTick(UInt64.valueOf(message.getCurrentTimeMs()), Optional.empty());

      final SignedProposerPreferences signedProposerPreferences =
          loadSsz(testDefinition, message.getMessage() + ".ssz_snappy", schema::sszDeserialize);
      final InternalValidationResult result =
          safeJoin(validator.validate(signedProposerPreferences));

      GossipTestContext.assertValidationResult(
          "proposer preferences " + message.getMessage(), message.getExpected(), result);
    }
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipProposerPreferencesMetaData {

    @JsonProperty(value = "blocks", required = true)
    private List<BlockEntry> blocks;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "current_time_ms", required = true)
    private long currentTimeMs;

    @JsonProperty(value = "bls_setting", defaultValue = "0")
    private int blsSetting;

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

      @JsonProperty(value = "pending", defaultValue = "false")
      private boolean pending;

      public String getBlock() {
        return block;
      }

      public boolean isFailed() {
        return failed;
      }

      public boolean isPending() {
        return pending;
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
      GossipProposerPreferencesMetaData.BlockEntry blockEntry, SignedBeaconBlock block) {}
}
