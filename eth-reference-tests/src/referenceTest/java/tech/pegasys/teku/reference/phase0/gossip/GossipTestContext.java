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
import static tech.pegasys.teku.reference.TestDataUtils.createAnchorFromStateAndMatchingBlock;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.async.eventthread.InlineEventThread;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.AnchorPoint;
import tech.pegasys.teku.spec.datastructures.state.Checkpoint;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.executionlayer.ExecutionLayerChannelStub;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoice;
import tech.pegasys.teku.statetransition.forkchoice.ForkChoiceStateProvider;
import tech.pegasys.teku.statetransition.forkchoice.MergeTransitionBlockValidator;
import tech.pegasys.teku.statetransition.forkchoice.NoopForkChoiceNotifier;
import tech.pegasys.teku.statetransition.forkchoice.TickProcessor;
import tech.pegasys.teku.statetransition.forkchoice.fastconfirmation.FastConfirmationTracker;
import tech.pegasys.teku.statetransition.util.DebugDataDumper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.statetransition.validation.ValidationResultCode;
import tech.pegasys.teku.storage.api.LateBlockReorgPreparationHandler;
import tech.pegasys.teku.storage.client.RecentChainData;
import tech.pegasys.teku.storage.server.StateStorageMode;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;

final class GossipTestContext {

  final Spec spec;
  final AnchorPoint anchorPoint;
  final RecentChainData recentChainData;
  final ForkChoice forkChoice;
  final ExecutionLayerChannelStub executionLayer;
  final StubMetricsSystem metricsSystem;

  private GossipTestContext(
      final Spec spec,
      final AnchorPoint anchorPoint,
      final RecentChainData recentChainData,
      final ForkChoice forkChoice,
      final ExecutionLayerChannelStub executionLayer,
      final StubMetricsSystem metricsSystem) {
    this.spec = spec;
    this.anchorPoint = anchorPoint;
    this.recentChainData = recentChainData;
    this.forkChoice = forkChoice;
    this.executionLayer = executionLayer;
    this.metricsSystem = metricsSystem;
  }

  static GossipTestContext create(
      final Spec spec, final BeaconState state, final List<SignedBeaconBlock> anchorBlocks) {
    final StubMetricsSystem metricsSystem = new StubMetricsSystem();
    final RecentChainData recentChainData =
        InMemoryStorageSystemBuilder.create()
            .specProvider(spec)
            .storageMode(StateStorageMode.ARCHIVE)
            .build()
            .recentChainData();
    final AnchorPoint anchorPoint =
        createAnchorFromStateAndMatchingBlock(spec, state, anchorBlocks);
    recentChainData.initializeFromAnchorPoint(anchorPoint, UInt64.ZERO);
    final InlineEventThread eventThread = new InlineEventThread();
    final MergeTransitionBlockValidator transitionBlockValidator =
        new MergeTransitionBlockValidator(spec, recentChainData);
    final ForkChoice forkChoice =
        new ForkChoice(
            spec,
            eventThread,
            recentChainData,
            new NoopForkChoiceNotifier(),
            new ForkChoiceStateProvider(eventThread, recentChainData),
            new TickProcessor(spec, recentChainData),
            transitionBlockValidator,
            FastConfirmationTracker.NOOP,
            true,
            LateBlockReorgPreparationHandler.NOOP,
            DebugDataDumper.NOOP,
            metricsSystem,
            AsyncBLSSignatureVerifier.wrap(BLSSignatureVerifier.NOOP));
    final ExecutionLayerChannelStub executionLayer = new ExecutionLayerChannelStub(spec, false);
    return new GossipTestContext(
        spec, anchorPoint, recentChainData, forkChoice, executionLayer, metricsSystem);
  }

  static void assertValidationResult(
      final String messageDesc, final String expected, final InternalValidationResult result) {
    switch (expected) {
      case "valid" ->
          assertThat(result.code())
              .describedAs(
                  "Expected %s to be valid but got %s: %s",
                  messageDesc, result.code(), result.getDescription().orElse(""))
              .isEqualTo(ValidationResultCode.ACCEPT);
      case "reject" ->
          assertThat(result.code())
              .describedAs(
                  "Expected %s to be rejected but got %s: %s",
                  messageDesc, result.code(), result.getDescription().orElse(""))
              .isEqualTo(ValidationResultCode.REJECT);
      case "ignore" ->
          assertThat(result.code())
              .describedAs(
                  "Expected %s to be ignored but got %s: %s",
                  messageDesc, result.code(), result.getDescription().orElse(""))
              .isIn(ValidationResultCode.IGNORE, ValidationResultCode.SAVE_FOR_FUTURE);
      default ->
          throw new AssertionError(
              "Unexpected expected value: " + expected + " for: " + messageDesc);
    }
  }

  @SuppressWarnings("unused")
  @JsonIgnoreProperties(ignoreUnknown = true)
  static final class FinalizedCheckpoint {

    @JsonProperty(value = "epoch", required = true)
    private long epoch;

    @JsonProperty(value = "root")
    private String root;

    @JsonProperty(value = "block")
    private String block;

    public String getBlock() {
      return block;
    }

    public Checkpoint toCheckpoint(final TestDefinition testDefinition, final Spec spec) {
      final Bytes32 checkpointRoot;
      if (root != null) {
        checkpointRoot = Bytes32.fromHexString(root);
      } else if (block != null) {
        final SignedBeaconBlock signedBlock =
            loadSsz(testDefinition, block + ".ssz_snappy", spec::deserializeSignedBeaconBlock);
        checkpointRoot = signedBlock.getRoot();
      } else {
        throw new IllegalStateException(
            "finalized_checkpoint must specify either 'root' or 'block'");
      }
      return new Checkpoint(UInt64.valueOf(epoch), checkpointRoot);
    }
  }
}
