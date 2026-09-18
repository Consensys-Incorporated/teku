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

package tech.pegasys.teku.reference.phase0.sanity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static tech.pegasys.teku.reference.BlsSetting.IGNORED;
import static tech.pegasys.teku.reference.TestDataUtils.loadSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;
import static tech.pegasys.teku.reference.TestDataUtils.loadYaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.assertj.core.api.AbstractThrowableAssert;
import tech.pegasys.teku.bls.BLSSignatureVerifier;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.sos.SszDeserializeException;
import tech.pegasys.teku.reference.BlsSetting;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;

public class SanityBlocksTestExecutor implements TestExecutor {

  private static final String EXPECTED_STATE_FILENAME = "post.ssz_snappy";
  private static final String STATE_ROOT_MISMATCH_ERROR_MESSAGE =
      "Block state root does NOT match the calculated state root";

  private static final List<String> IGNORED_TEST_NAMES = List.of();

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    if (IGNORED_TEST_NAMES.contains(testDefinition.getTestName())) {
      return;
    }

    final SanityBlocksMetaData metaData =
        loadYaml(testDefinition, "meta.yaml", SanityBlocksMetaData.class);
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz_snappy");
    // loaded lazily: an invalid block may already be rejected by the SSZ schemas
    final Supplier<List<SignedBeaconBlock>> blocks =
        () ->
            IntStream.range(0, metaData.getBlocksCount())
                .mapToObj(
                    index ->
                        loadSsz(
                            testDefinition,
                            "blocks_" + index + ".ssz_snappy",
                            testDefinition.getSpec()::deserializeSignedBeaconBlock))
                .collect(Collectors.toList());

    final Optional<BeaconState> expectedState;
    if (Files.exists(testDefinition.getTestDirectory().resolve(EXPECTED_STATE_FILENAME))) {
      expectedState = Optional.of(loadStateFromSsz(testDefinition, EXPECTED_STATE_FILENAME));
    } else {
      expectedState = Optional.empty();
    }

    runBlockProcessor(this::applyBlocks, testDefinition, metaData, preState, blocks, expectedState);
  }

  private void runBlockProcessor(
      final BlocksProcessor processor,
      final TestDefinition testDefinition,
      final SanityBlocksMetaData metaData,
      final BeaconState preState,
      final Supplier<List<SignedBeaconBlock>> blocks,
      final Optional<BeaconState> expectedState) {
    final Spec spec = testDefinition.getSpec();
    expectedState.ifPresentOrElse(
        (state) ->
            assertThat(processor.processBlocks(spec, metaData, preState, blocks.get()))
                .isEqualTo(state),
        () -> {
          final Throwable failure =
              catchThrowable(() -> processor.processBlocks(spec, metaData, preState, blocks.get()));
          if (failure instanceof SszDeserializeException) {
            // rejected by the SSZ schemas (e.g. operation count limits) before processing
            return;
          }
          final AbstractThrowableAssert<?, ? extends Throwable> throwableAssert =
              assertThat(failure).hasCauseInstanceOf(StateTransitionException.class);
          /*
           We don't have a better way to know if the test case cares about a state root mismatch until this
           issue is resolved: https://github.com/ethereum/consensus-specs/issues/3122
          */
          if (testDefinition.getTestName().contains("invalid_incorrect_state_root")) {
            throwableAssert.hasMessageContaining(STATE_ROOT_MISMATCH_ERROR_MESSAGE);
          } else {
            throwableAssert
                .as("Expected state transition failure not caused by state root mismatch")
                .hasMessageNotContaining(STATE_ROOT_MISMATCH_ERROR_MESSAGE);
          }
        });
  }

  private BeaconState applyBlocks(
      final Spec spec,
      final SanityBlocksMetaData metaData,
      final BeaconState preState,
      final List<SignedBeaconBlock> blocks) {
    try {
      BeaconState result = preState;
      for (SignedBeaconBlock block : blocks) {
        result =
            spec.processBlock(
                result,
                block,
                metaData.getBlsSetting() == IGNORED
                    ? BLSSignatureVerifier.NOOP
                    : BLSSignatureVerifier.SIMPLE,
                Optional.empty());
      }
      return result;
    } catch (StateTransitionException e) {
      throw new RuntimeException(e);
    }
  }

  private static class SanityBlocksMetaData {

    @JsonProperty(value = "blocks_count", required = true)
    private int blocksCount;

    @JsonProperty(value = "bls_setting", required = false, defaultValue = "0")
    private int blsSetting;

    @JsonProperty(value = "reveal_deadlines_setting", required = false, defaultValue = "0")
    @SuppressWarnings("unused")
    private int revealDeadlinesSetting;

    public int getBlocksCount() {
      return blocksCount;
    }

    public BlsSetting getBlsSetting() {
      return BlsSetting.forCode(blsSetting);
    }
  }

  private interface BlocksProcessor {

    BeaconState processBlocks(
        final Spec spec,
        final SanityBlocksMetaData metaData,
        final BeaconState preState,
        final List<SignedBeaconBlock> blocks);
  }
}
