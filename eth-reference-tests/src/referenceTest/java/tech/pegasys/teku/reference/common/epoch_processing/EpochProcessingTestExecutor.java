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
import static tech.pegasys.teku.reference.TestDataUtils.loadStateFromSsz;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Files;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.TestExecutor;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.EpochProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.SlotProcessingException;
import tech.pegasys.teku.spec.logic.common.statetransition.exceptions.StateTransitionException;

public class EpochProcessingTestExecutor implements TestExecutor {

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
    final BeaconState preState = loadStateFromSsz(testDefinition, "pre.ssz_snappy");
    final String postStateFileName = "post.ssz_snappy";

    final SpecVersion genesisSpec = testDefinition.getSpec().getGenesisSpec();
    final EpochProcessor epochProcessor = genesisSpec.getEpochProcessor();
    final ValidatorStatusFactory validatorStatusFactory = genesisSpec.getValidatorStatusFactory();
    final EpochProcessingExecutor processor =
        new EpochProcessingExecutor(epochProcessor, validatorStatusFactory);

    if (Files.exists(testDefinition.getTestDirectory().resolve(postStateFileName))) {
      final BeaconState expectedPostState = loadStateFromSsz(testDefinition, postStateFileName);
      final BeaconState result = executeOperation(preState, processor);

      assertThat(result).isEqualTo(expectedPostState);
    } else {
      assertThatThrownBy(() -> executeOperation(preState, processor))
          // Currently the only
          .isInstanceOfAny(
              StateTransitionException.class,
              SlotProcessingException.class,
              EpochProcessingException.class,
              ArithmeticException.class);
    }
  }

  private BeaconState executeOperation(
      final BeaconState preState, final EpochProcessingExecutor processor)
      throws EpochProcessingException {
    return preState.updated(state -> processor.executeOperation(operation, state));
  }
}
