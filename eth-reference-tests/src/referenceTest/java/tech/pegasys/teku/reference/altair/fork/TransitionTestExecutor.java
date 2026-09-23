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

package tech.pegasys.teku.reference.altair.fork;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static tech.pegasys.teku.infrastructure.ssz.SszDataAssert.assertThatSszData;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.reference.TestDataUtils;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;

public class TransitionTestExecutor implements TestExecutor {

  public static final ImmutableMap<String, TestExecutor> TRANSITION_TEST_TYPES =
      ImmutableMap.of("transition/core", new TransitionTestExecutor());

  @Override
  public void runTest(final TestDefinition testDefinition) throws Throwable {
    final MetaData metadata = TestDataUtils.loadYaml(testDefinition, "meta.yaml", MetaData.class);
    processUpgrade(testDefinition, metadata);
  }

  private void processUpgrade(final TestDefinition testDefinition, final MetaData metadata) {
    final SpecMilestone milestone = SpecMilestone.forName(metadata.postFork);
    final UInt64 forkEpoch = UInt64.valueOf(metadata.forkEpoch);
    final SpecConfigAndParent<? extends SpecConfig> config =
        SpecConfigLoader.loadConfig(
            testDefinition.getConfigName(),
            builder -> {
              switch (milestone) {
                case ALTAIR -> builder.altairForkEpoch(forkEpoch);
                case BELLATRIX ->
                    builder.altairForkEpoch(UInt64.ZERO).bellatrixForkEpoch(forkEpoch);
                case CAPELLA ->
                    builder
                        .altairForkEpoch(UInt64.ZERO)
                        .bellatrixForkEpoch(UInt64.ZERO)
                        .capellaForkEpoch(forkEpoch);
                case DENEB ->
                    builder
                        .altairForkEpoch(UInt64.ZERO)
                        .bellatrixForkEpoch(UInt64.ZERO)
                        .capellaForkEpoch(UInt64.ZERO)
                        .denebForkEpoch(forkEpoch);
                case ELECTRA ->
                    builder
                        .altairForkEpoch(UInt64.ZERO)
                        .bellatrixForkEpoch(UInt64.ZERO)
                        .capellaForkEpoch(UInt64.ZERO)
                        .denebForkEpoch(UInt64.ZERO)
                        .electraForkEpoch(forkEpoch);
                case FULU ->
                    builder
                        .altairForkEpoch(UInt64.ZERO)
                        .bellatrixForkEpoch(UInt64.ZERO)
                        .capellaForkEpoch(UInt64.ZERO)
                        .denebForkEpoch(UInt64.ZERO)
                        .electraForkEpoch(UInt64.ZERO)
                        .fuluForkEpoch(forkEpoch);
                case GLOAS ->
                    builder
                        .altairForkEpoch(UInt64.ZERO)
                        .bellatrixForkEpoch(UInt64.ZERO)
                        .capellaForkEpoch(UInt64.ZERO)
                        .denebForkEpoch(UInt64.ZERO)
                        .electraForkEpoch(UInt64.ZERO)
                        .fuluForkEpoch(UInt64.ZERO)
                        .gloasForkEpoch(forkEpoch);
                case HEZE ->
                    builder
                        .altairForkEpoch(UInt64.ZERO)
                        .bellatrixForkEpoch(UInt64.ZERO)
                        .capellaForkEpoch(UInt64.ZERO)
                        .denebForkEpoch(UInt64.ZERO)
                        .electraForkEpoch(UInt64.ZERO)
                        .fuluForkEpoch(UInt64.ZERO)
                        .gloasForkEpoch(UInt64.ZERO)
                        .hezeForkEpoch(forkEpoch);
                default ->
                    throw new IllegalStateException(
                        "Unhandled fork transition for test "
                            + testDefinition.getDisplayName()
                            + ": "
                            + milestone);
              }
            });
    final Spec spec = SpecFactory.create(config);
    final BeaconState preState =
        TestDataUtils.loadSsz(testDefinition, "pre.ssz_snappy", spec::deserializeBeaconState);
    // Without a post state the transition is invalid and the last block must be rejected
    final boolean expectValid =
        Files.exists(testDefinition.getTestDirectory().resolve("post.ssz_snappy"));
    final BLSSignatureVerifier signatureVerifier =
        metadata.blsSetting == 2 ? BLSSignatureVerifier.NOOP : BLSSignatureVerifier.SIMPLE;

    BeaconState result = preState;
    for (int i = 0; i < metadata.blocksCount; i++) {
      final SignedBeaconBlock block =
          TestDataUtils.loadSsz(
              testDefinition, "blocks_" + i + ".ssz_snappy", spec::deserializeSignedBeaconBlock);

      if (!expectValid && i == metadata.blocksCount - 1) {
        final BeaconState finalPreState = result;
        assertThatThrownBy(
                () -> spec.processBlock(finalPreState, block, signatureVerifier, Optional.empty()))
            .isInstanceOf(StateTransitionException.class);
        return;
      }

      try {
        result = spec.processBlock(result, block, signatureVerifier, Optional.empty());
      } catch (final StateTransitionException e) {
        Assertions.fail(
            "Failed to process block " + i + " at slot " + block.getSlot() + ": " + e.getMessage(),
            e);
      }
    }
    final BeaconState postState =
        TestDataUtils.loadSsz(testDefinition, "post.ssz_snappy", spec::deserializeBeaconState);
    assertThatSszData(result).isEqualByGettersTo(postState);
  }

  @SuppressWarnings({"unused", "UnusedVariable"})
  private static class MetaData {
    @JsonProperty(value = "post_fork", required = true)
    private String postFork;

    @JsonProperty(value = "fork_epoch", required = true)
    private int forkEpoch;

    @JsonProperty(value = "blocks_count", required = true)
    private int blocksCount;

    @JsonProperty(value = "fork_block", required = true)
    private int forkBlock;

    @SuppressWarnings("FieldCanBeFinal")
    @JsonProperty(value = "bls_setting", defaultValue = "0")
    private int blsSetting = 0;
  }
}
