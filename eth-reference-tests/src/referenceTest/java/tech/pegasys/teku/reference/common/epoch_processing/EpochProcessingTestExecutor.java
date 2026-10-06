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

package tech.pegasys.teku.reference.common.epoch_processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assertions.assertAll;
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.function.Executable;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.async.ExceptionThrowingFunction;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;

public class EpochProcessingTestExecutor implements TestExecutor {

  private static final String PRE_STATE_FILE = "pre.ssz_snappy";
  private static final String POST_STATE_FILE = "post.ssz_snappy";
  static final String PRE_EPOCH_STATE_FILE = "pre_epoch.ssz_snappy";
  private static final String POST_EPOCH_STATE_FILE = "post_epoch.ssz_snappy";

  // These vectors put PendingAttestations with empty aggregation bits in the state, which no valid
  // block can produce. The spec never reads them at epoch 0, but Teku builds validator statuses
  // from them up front and rejects the bitlist size, so only the per-operation check is run.
  static final Set<String> SKIP_FULL_EPOCH_CHECK =
      Set.of(
          "phase0 - minimal - epoch_processing/participation_record_updates - updated_participation_record",
          "phase0 - mainnet - epoch_processing/participation_record_updates - updated_participation_record");

  public static final ImmutableMap<String, TestExecutor> EPOCH_PROCESSING_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .put(
              "epoch_processing/slashings",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_SLASHINGS))
          .put(
              "epoch_processing/registry_updates",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_REGISTRY_UPDATES))
          .put(
              "epoch_processing/rewards_and_penalties",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_REWARDS_AND_PENALTIES))
          .put(
              "epoch_processing/justification_and_finalization",
              new EpochProcessingTestExecutor(
                  EpochOperation.PROCESS_JUSTIFICATION_AND_FINALIZATION))
          .put(
              "epoch_processing/effective_balance_updates",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_EFFECTIVE_BALANCE_UPDATES))
          .put(
              "epoch_processing/eth1_data_reset",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_ETH1_DATA_RESET))
          .put(
              "epoch_processing/participation_flag_updates",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_PARTICIPATION_FLAG_UPDATES))

          // Altair calls the method participation_flag_updates and phase0 calls it
          // participation_record_updates but both map to the same operation in teku
          .put(
              "epoch_processing/participation_record_updates",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_PARTICIPATION_FLAG_UPDATES))
          .put(
              "epoch_processing/randao_mixes_reset",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_RANDAO_MIXES_RESET))
          .put(
              "epoch_processing/historical_roots_update",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_HISTORICAL_ROOTS_UPDATE))
          .put(
              "epoch_processing/historical_summaries_update",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_HISTORICAL_SUMMARIES_UPDATE))
          .put(
              "epoch_processing/slashings_reset",
              new EpochProcessingTestExecutor(EpochOperation.PROCESS_SLASHINGS_RESET))
          .put(
              "epoch_processing/sync_committee_updates",
              new EpochProcessingTestExecutor(EpochOperation.SYNC_COMMITTEE_UPDATES))
          .put(
              "epoch_processing/inactivity_updates",
              new EpochProcessingTestExecutor(EpochOperation.INACTIVITY_UPDATES))
          .put(
              "epoch_processing/pending_consolidations",
              new EpochProcessingTestExecutor(EpochOperation.PENDING_CONSOLIDATIONS))
          .put(
              "epoch_processing/pending_deposits",
              new EpochProcessingTestExecutor(EpochOperation.PENDING_DEPOSITS))
          .put(
              "epoch_processing/pending_deposits_churn",
              new EpochProcessingTestExecutor(EpochOperation.PENDING_DEPOSITS))
          .put(
              "epoch_processing/proposer_lookahead",
              new EpochProcessingTestExecutor(EpochOperation.PROPOSER_LOOKAHEAD))
          .put(
              "epoch_processing/builder_pending_payments",
              new EpochProcessingTestExecutor(EpochOperation.BUILDER_PENDING_PAYMENTS))
          .put(
              "epoch_processing/ptc_window",
              new EpochProcessingTestExecutor(EpochOperation.PTC_WINDOW))
          .build();

  private final EpochOperation operation;

  public EpochProcessingTestExecutor(final EpochOperation operation) {
    this.operation = operation;
  }

  @Override
  public void runTest(final TestDefinition testDefinition) throws Exception {
    final SpecVersion genesisSpec = testDefinition.getSpec().getGenesisSpec();
    final EpochProcessor epochProcessor = genesisSpec.getEpochProcessor();
    final ValidatorStatusFactory validatorStatusFactory = genesisSpec.getValidatorStatusFactory();
    final EpochProcessingExecutor processor =
        new EpochProcessingExecutor(epochProcessor, validatorStatusFactory);

    // Run both checks and report both failures: a full epoch failure alone points at an
    // interaction between sub-transitions rather than at the operation under test.
    assertAll(
        () -> assertIsolatedOperationTransition(testDefinition, processor),
        () -> assertFullEpochTransition(testDefinition, epochProcessor));
  }

  private void assertIsolatedOperationTransition(
      final TestDefinition testDefinition, final EpochProcessingExecutor processor)
      throws Throwable {
    assertTransition(
        testDefinition,
        PRE_STATE_FILE,
        POST_STATE_FILE,
        preState -> preState.updated(state -> processor.executeOperation(operation, state)));
  }

  private static void assertFullEpochTransition(
      final TestDefinition testDefinition, final EpochProcessor epochProcessor) throws Throwable {
    // Some vectors don't ship full epoch states (see consensus-specs #4155)
    if (!Files.exists(testDefinition.getTestDirectory().resolve(PRE_EPOCH_STATE_FILE))) {
      return;
    }
    final Executable fullEpochCheck =
        () ->
            assertTransition(
                testDefinition,
                PRE_EPOCH_STATE_FILE,
                POST_EPOCH_STATE_FILE,
                epochProcessor::processEpoch);
    if (!SKIP_FULL_EPOCH_CHECK.contains(testDefinition.getDisplayName())) {
      fullEpochCheck.execute();
      return;
    }
    // Keep skipped vectors failing for the known reason only, so the list can't go stale or hide
    // a different failure
    assertThat(catchThrowable(fullEpochCheck::execute))
        .describedAs("full epoch check passes now, remove it from SKIP_FULL_EPOCH_CHECK")
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Aggregation bitlist size");
  }

  /** Expects the transition to fail when the post state file is absent, as the vectors do. */
  private static void assertTransition(
      final TestDefinition testDefinition,
      final String preStateFileName,
      final String postStateFileName,
      final ExceptionThrowingFunction<BeaconState, BeaconState> transition)
      throws Throwable {
    final BeaconState preState = loadStateFromSsz(testDefinition, preStateFileName);
    if (Files.exists(testDefinition.getTestDirectory().resolve(postStateFileName))) {
      final BeaconState expectedPostState = loadStateFromSsz(testDefinition, postStateFileName);
      final BeaconState actualPostState = transition.apply(preState);
      // Only name the differing fields: rendering whole states on failure exhausts the heap when
      // one bug fails many tests at once.
      final List<String> differingFields =
          IntStream.range(0, expectedPostState.size())
              .filter(
                  i ->
                      !actualPostState
                          .get(i)
                          .hashTreeRoot()
                          .equals(expectedPostState.get(i).hashTreeRoot()))
              .mapToObj(i -> expectedPostState.getSchema().getFieldNames().get(i))
              .toList();
      assertThat(differingFields)
          .describedAs("%s -> %s: differing state fields", preStateFileName, postStateFileName)
          .isEmpty();
    } else {
      assertThatThrownBy(() -> transition.apply(preState))
          .describedAs("%s -> exception", preStateFileName)
          .isInstanceOfAny(
              StateTransitionException.class,
              SlotProcessingException.class,
              EpochProcessingException.class,
              ArithmeticException.class);
    }
  }
}
