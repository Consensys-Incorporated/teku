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

package tech.pegasys.teku.reference;

import com.google.common.collect.ImmutableMap;
import org.junit.jupiter.api.Assertions;
import tech.pegasys.teku.ethtests.TestFork;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.reference.altair.fork.ForkUpgradeTestExecutor;
import tech.pegasys.teku.reference.altair.fork.TransitionTestExecutor;
import tech.pegasys.teku.reference.altair.rewards.RewardsTestExecutorAltair;
import tech.pegasys.teku.reference.altair.rewards.RewardsTestExecutorBellatrix;
import tech.pegasys.teku.reference.common.epoch_processing.EpochProcessingTestExecutor;
import tech.pegasys.teku.reference.common.operations.OperationsTestExecutor;
import tech.pegasys.teku.reference.deneb.merkle_proof.MerkleProofTests;
import tech.pegasys.teku.reference.fulu.networking.NetworkingTests;
import tech.pegasys.teku.reference.phase0.bls.BlsTests;
import tech.pegasys.teku.reference.phase0.forkchoice.ForkChoiceTestExecutor;
import tech.pegasys.teku.reference.phase0.genesis.GenesisTests;
import tech.pegasys.teku.reference.phase0.gossip.GossipTests;
import tech.pegasys.teku.reference.phase0.kzg.KzgTests;
import tech.pegasys.teku.reference.phase0.rewards.RewardsTestExecutorPhase0;
import tech.pegasys.teku.reference.phase0.sanity.SanityTests;
import tech.pegasys.teku.reference.phase0.shuffling.ShufflingTestExecutor;
import tech.pegasys.teku.reference.phase0.slashing_protection_interchange.SlashingProtectionInterchangeTestExecutor;
import tech.pegasys.teku.reference.phase0.ssz_generic.SszGenericTests;
import tech.pegasys.teku.reference.phase0.ssz_static.SszTestExecutor;

public abstract class Eth2ReferenceTestCase {

  private static final ImmutableMap<String, TestExecutor> COMMON_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(BlsTests.BLS_TEST_TYPES)
          .putAll(KzgTests.KZG_TEST_TYPES)
          .putAll(ForkChoiceTestExecutor.FORK_CHOICE_TEST_TYPES)
          .putAll(GenesisTests.GENESIS_TEST_TYPES)
          .putAll(ShufflingTestExecutor.SHUFFLING_TEST_TYPES)
          .putAll(EpochProcessingTestExecutor.EPOCH_PROCESSING_TEST_TYPES)
          .putAll(SszTestExecutor.SSZ_TEST_TYPES)
          .putAll(SszGenericTests.SSZ_GENERIC_TEST_TYPES)
          .putAll(OperationsTestExecutor.OPERATIONS_TEST_TYPES)
          .putAll(SanityTests.SANITY_TEST_TYPES)
          .putAll(GossipTests.GOSSIP_TEST_TYPES)
          .putAll(ForkChoiceTestExecutor.FAST_CONFIRMATION_TEST_TYPES)
          .put("slashing-protection-interchange", new SlashingProtectionInterchangeTestExecutor())
          .put("light_client/single_merkle_proof", TestExecutor.IGNORE_TESTS)
          .put("light_client/sync", TestExecutor.IGNORE_TESTS)
          .put("light_client/update_ranking", TestExecutor.IGNORE_TESTS)
          .put("light_client/data_collection", TestExecutor.IGNORE_TESTS)
          .build();

  private static final ImmutableMap<String, TestExecutor> PHASE_0_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(RewardsTestExecutorPhase0.REWARDS_TEST_TYPES)
          .build();

  private static final ImmutableMap<String, TestExecutor> ALTAIR_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(TransitionTestExecutor.TRANSITION_TEST_TYPES)
          .putAll(ForkUpgradeTestExecutor.FORK_UPGRADE_TEST_TYPES)
          .putAll(RewardsTestExecutorAltair.REWARDS_TEST_TYPES)
          .build();

  private static final ImmutableMap<String, TestExecutor> BELLATRIX_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(TransitionTestExecutor.TRANSITION_TEST_TYPES)
          .putAll(ForkUpgradeTestExecutor.FORK_UPGRADE_TEST_TYPES)
          .putAll(RewardsTestExecutorBellatrix.REWARDS_TEST_TYPES)
          .build();

  private static final ImmutableMap<String, TestExecutor> CAPELLA_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(TransitionTestExecutor.TRANSITION_TEST_TYPES)
          .putAll(ForkUpgradeTestExecutor.FORK_UPGRADE_TEST_TYPES)
          .putAll(RewardsTestExecutorBellatrix.REWARDS_TEST_TYPES)
          .build();

  private static final ImmutableMap<String, TestExecutor> DENEB_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(TransitionTestExecutor.TRANSITION_TEST_TYPES)
          .putAll(ForkUpgradeTestExecutor.FORK_UPGRADE_TEST_TYPES)
          .putAll(RewardsTestExecutorBellatrix.REWARDS_TEST_TYPES)
          .putAll(MerkleProofTests.MERKLE_PROOF_TEST_TYPES)
          .build();

  private static final ImmutableMap<String, TestExecutor> ELECTRA_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(TransitionTestExecutor.TRANSITION_TEST_TYPES)
          .putAll(ForkUpgradeTestExecutor.FORK_UPGRADE_TEST_TYPES)
          .putAll(RewardsTestExecutorBellatrix.REWARDS_TEST_TYPES)
          .putAll(MerkleProofTests.MERKLE_PROOF_TEST_TYPES)
          .build();

  private static final ImmutableMap<String, TestExecutor> FULU_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(TransitionTestExecutor.TRANSITION_TEST_TYPES)
          .putAll(ForkUpgradeTestExecutor.FORK_UPGRADE_TEST_TYPES)
          .putAll(RewardsTestExecutorBellatrix.REWARDS_TEST_TYPES)
          .putAll(MerkleProofTests.MERKLE_PROOF_TEST_TYPES)
          .putAll(NetworkingTests.NETWORKING_TEST_TYPES)
          .build();

  private static final ImmutableMap<String, TestExecutor> GLOAS_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          .putAll(TransitionTestExecutor.TRANSITION_TEST_TYPES)
          .putAll(ForkUpgradeTestExecutor.FORK_UPGRADE_TEST_TYPES)
          .putAll(RewardsTestExecutorBellatrix.REWARDS_TEST_TYPES)
          .putAll(MerkleProofTests.MERKLE_PROOF_TEST_TYPES)
          .putAll(NetworkingTests.NETWORKING_TEST_TYPES)
          .build();

  protected void runReferenceTest(final TestDefinition testDefinition) throws Throwable {
    SszMaxLengthFixtures.REJECTED_BY_SSZ_LIMITS.run(testDefinition, getExecutorFor(testDefinition));
  }

  private TestExecutor getExecutorFor(final TestDefinition testDefinition) {
    // Look for fork-specific tests first
    TestExecutor testExecutor =
        switch (testDefinition.getFork()) {
          case TestFork.PHASE0 -> PHASE_0_TEST_TYPES.get(testDefinition.getTestType());
          case TestFork.ALTAIR -> ALTAIR_TEST_TYPES.get(testDefinition.getTestType());
          case TestFork.BELLATRIX -> BELLATRIX_TEST_TYPES.get(testDefinition.getTestType());
          case TestFork.CAPELLA -> CAPELLA_TEST_TYPES.get(testDefinition.getTestType());
          case TestFork.DENEB -> DENEB_TEST_TYPES.get(testDefinition.getTestType());
          case TestFork.ELECTRA -> ELECTRA_TEST_TYPES.get(testDefinition.getTestType());
          case TestFork.FULU -> FULU_TEST_TYPES.get(testDefinition.getTestType());
          case TestFork.GLOAS -> GLOAS_TEST_TYPES.get(testDefinition.getTestType());
          default -> null;
        };

    // Look for a common test type if no specific override present
    if (testExecutor == null) {
      testExecutor = COMMON_TEST_TYPES.get(testDefinition.getTestType());
    }

    if (testExecutor == null) {
      return Assertions.fail("Unsupported test type " + testDefinition.getTestType());
    }
    return testExecutor;
  }
}
