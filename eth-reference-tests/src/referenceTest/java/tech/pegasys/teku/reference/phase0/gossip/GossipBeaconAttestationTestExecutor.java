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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import org.opentest4j.TestAbortedException;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.datastructures.attestation.ValidatableAttestation;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadEnvelope;
import tech.pegasys.teku.spec.datastructures.operations.Attestation;
import tech.pegasys.teku.spec.datastructures.operations.AttestationSchema;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.results.BlockImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult;
import tech.pegasys.teku.spec.logic.common.statetransition.results.ExecutionPayloadImportResult.FailureReason;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsElectra;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsGloas;
import tech.pegasys.teku.statetransition.validation.AttestationValidator;
import tech.pegasys.teku.statetransition.validation.BlockBroadcastValidator;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.store.UpdatableStore;

public class GossipBeaconAttestationTestExecutor implements TestExecutor {

  private final List<String> testsToSkip;

  public GossipBeaconAttestationTestExecutor(final String... testsToSkip) {
    this.testsToSkip = List.of(testsToSkip);
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    if (testsToSkip.contains(testDefinition.getTestName())) {
      throw new TestAbortedException(
          "Test " + testDefinition.getDisplayName() + " has been ignored");
    }

    final GossipBeaconAttestationMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", GossipBeaconAttestationMetaData.class);
    final boolean signatureVerificationDisabled = metaData.getBlsSetting() == BlsSetting.IGNORED;
    final BLSSignatureVerifier blsVerifier =
        signatureVerificationDisabled ? BLSSignatureVerifier.NOOP : BLSSignatureVerifier.SIMPLE;
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

    // Blocks marked failed: true in meta.yaml are recorded as invalid (by root) and not imported,
    // mirroring the invalidBlockRoots map maintained in production by BlockManager. The attestation
    // validator rejects attestations voting for one of these roots.
    final Map<Bytes32, BlockImportResult> invalidBlockRoots = new HashMap<>();

    // Block roots whose execution payload envelope failed verification/DA-check, mirroring the
    // blockRootsWithInvalidExecutionPayload set maintained in production by
    // DefaultExecutionPayloadManager. The attestation validator rejects full-payload votes for
    // one of these roots.
    final Set<Bytes32> blockRootsWithInvalidExecutionPayload = new HashSet<>();

    for (final BlockEntryAndBlock blockEntryAndBlock : blocks) {
      final GossipBeaconAttestationMetaData.BlockEntry blockEntry = blockEntryAndBlock.blockEntry();
      final SignedBeaconBlock block = blockEntryAndBlock.block();
      if (blockEntry.isFailed()) {
        // Record the root of this invalid block so attestations voting for it are rejected.
        // Don't import it — a NOOP BLS verifier would accept it despite the bad signature.
        invalidBlockRoots.put(
            block.getRoot(), BlockImportResult.FAILED_DESCENDANT_OF_INVALID_BLOCK);
        continue;
      }
      if (!block.getRoot().equals(ctx.anchorPoint.getRoot())) {
        final BlockImportResult importResult =
            safeJoin(
                ctx.forkChoice.onBlock(
                    block, Optional.empty(), BlockBroadcastValidator.NOOP, ctx.executionLayer));
        assertThat(importResult.isSuccessful())
            .describedAs("Expected setup block %s to import successfully", blockEntry.getBlock())
            .isTrue();
      }
      // Some fixtures pair a setup block with a SignedExecutionPayloadEnvelope (Gloas ePBS) via
      // the `payload` key in meta.yaml. Deliver it to fork choice after the block itself so a
      // FULL node exists for the block root, matching how a real node would observe it.
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
                blockEntry
                    .getPayloadStatus()
                    .ifPresent(
                        payloadStatus ->
                            ctx.executionLayer.addPosBlock(
                                envelope.getMessage().getPayload().getBlockHash(),
                                payloadStatus.toPayloadStatus()));
                final ExecutionPayloadImportResult envelopeImportResult =
                    safeJoin(
                        ctx.forkChoice.onExecutionPayloadEnvelope(envelope, ctx.executionLayer));
                if (envelopeImportResult.isSuccessful()) {
                  assertThat(blockEntry.getPayloadStatus())
                      .describedAs(
                          "Expected execution payload envelope %s to fail to import since"
                              + " payload_status is INVALIDATED",
                          payloadName)
                      .isNotEqualTo(Optional.of(FixturePayloadStatus.INVALIDATED));
                } else if (envelopeImportResult.getFailureReason()
                        == FailureReason.FAILED_VERIFICATION
                    || envelopeImportResult.getFailureReason()
                        == FailureReason.FAILED_DATA_AVAILABILITY_CHECK_INVALID) {
                  blockRootsWithInvalidExecutionPayload.add(envelope.getBeaconBlockRoot());
                } else {
                  throw new AssertionError(
                      "Unexpected execution payload envelope import failure for "
                          + payloadName
                          + ": "
                          + envelopeImportResult.getFailureReason());
                }
              });
    }

    Optional<Checkpoint> customFinalizedCheckpoint = Optional.empty();
    if (metaData.getFinalizedCheckpoint() != null) {
      final GossipTestContext.FinalizedCheckpoint finalizedCheckpoint =
          metaData.getFinalizedCheckpoint();
      if (finalizedCheckpoint.getBlock() != null) {
        final Checkpoint checkpoint = finalizedCheckpoint.toCheckpoint(testDefinition, spec);
        final UpdatableStore.StoreTransaction tx = ctx.recentChainData.startStoreTransaction();
        tx.setFinalizedCheckpoint(checkpoint, false);
        safeJoin(tx.commit());
      } else {
        customFinalizedCheckpoint =
            Optional.of(finalizedCheckpoint.toCheckpoint(testDefinition, spec));
      }
    }

    final AttestationValidator attestationValidator =
        new AttestationValidator(
            spec,
            AsyncBLSSignatureVerifier.wrap(blsVerifier),
            createGossipValidationHelper(
                spec, ctx.recentChainData, ctx.metricsSystem, customFinalizedCheckpoint),
            invalidBlockRoots,
            blockRootsWithInvalidExecutionPayload);

    // Track seen attestations (by hash tree root) for "already seen" duplicate detection
    final Set<Bytes32> seenAttestationRoots = new HashSet<>();

    for (final GossipBeaconAttestationMetaData.Message message : metaData.getMessages()) {
      // Advance clock to message arrival time
      final UInt64 messageTimeMs =
          UInt64.valueOf(metaData.getCurrentTimeMs()).plus(UInt64.valueOf(message.getOffsetMs()));
      ctx.forkChoice.onTick(messageTimeMs, Optional.empty());

      final Attestation attestation =
          loadSsz(
              testDefinition,
              message.getMessage() + ".ssz_snappy",
              getGossipAttestationSchema(spec));
      final Bytes32 attestationRoot = attestation.hashTreeRoot();

      // Already-seen check: same attestation from same validator seen before
      if (seenAttestationRoots.contains(attestationRoot)) {
        assertThat(message.getExpected())
            .describedAs("Expected ignore for already-seen attestation %s", message.getMessage())
            .isEqualTo("ignore");
        continue;
      } else {
        seenAttestationRoots.add(attestationRoot);
      }

      final ValidatableAttestation validatableAttestation =
          ValidatableAttestation.fromNetwork(spec, attestation, message.getSubnetId());
      final InternalValidationResult result =
          attestationValidator.validate(validatableAttestation).join();

      GossipTestContext.assertValidationResult(
          "attestation " + message.getMessage(), message.getExpected(), result);
    }
  }

  private static AttestationSchema<? extends Attestation> getGossipAttestationSchema(
      final Spec spec) {
    if (spec.getGenesisSpec().getMilestone().isGreaterThanOrEqualTo(SpecMilestone.ELECTRA)) {
      return SchemaDefinitionsElectra.required(spec.getGenesisSchemaDefinitions())
          .getSingleAttestationSchema();
    }
    return spec.getGenesisSchemaDefinitions().getAttestationSchema();
  }

  private static GossipValidationHelper createGossipValidationHelper(
      final Spec spec,
      final RecentChainData recentChainData,
      final StubMetricsSystem metricsSystem,
      final Optional<Checkpoint> finalizedCheckpointOverride) {
    return finalizedCheckpointOverride
        .<GossipValidationHelper>map(
            finalizedCheckpoint ->
                new GossipValidationHelper(spec, recentChainData, metricsSystem) {
                  @Override
                  public boolean currentFinalizedCheckpointIsAncestorOfAttestationBlock(
                      final Bytes32 blockRoot) {
                    return spec.getAncestor(
                            getForkChoiceStrategy(),
                            blockRoot,
                            finalizedCheckpoint.getEpochStartSlot(spec))
                        .map(ancestorRoot -> ancestorRoot.equals(finalizedCheckpoint.getRoot()))
                        .orElse(false);
                  }
                })
        .orElseGet(() -> new GossipValidationHelper(spec, recentChainData, metricsSystem));
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  private static class GossipBeaconAttestationMetaData {

    @JsonProperty(value = "topic", required = true)
    private String topic;

    @JsonProperty(value = "blocks", required = true)
    private List<BlockEntry> blocks;

    @JsonProperty(value = "messages", required = true)
    private List<Message> messages;

    @JsonProperty(value = "current_time_ms", required = true)
    private long currentTimeMs;

    @JsonProperty(value = "bls_setting", required = false, defaultValue = "0")
    private int blsSetting;

    @JsonProperty(value = "finalized_checkpoint", required = false)
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

      @JsonProperty(value = "failed", required = false, defaultValue = "false")
      private boolean failed;

      @JsonProperty(value = "payload_status", required = false)
      private FixturePayloadStatus payloadStatus;

      // Gloas (ePBS) fixtures may pair a setup block with a SignedExecutionPayloadEnvelope,
      // named here, which must be delivered to fork choice alongside the block.
      @JsonProperty(value = "payload", required = false)
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
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Message {

      @JsonProperty(value = "subnet_id", required = true)
      private int subnetId;

      @JsonProperty(value = "offset_ms", required = true)
      private long offsetMs;

      @JsonProperty(value = "message", required = true)
      private String message;

      @JsonProperty(value = "expected", required = true)
      private String expected;

      @JsonProperty(value = "reason", required = false)
      private String reason;

      public int getSubnetId() {
        return subnetId;
      }

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
      GossipBeaconAttestationMetaData.BlockEntry blockEntry, SignedBeaconBlock block) {}
}
