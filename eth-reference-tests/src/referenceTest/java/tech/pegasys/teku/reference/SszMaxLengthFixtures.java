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

import java.util.Set;
import tech.pegasys.teku.ethtests.finder.TestDefinition;
import tech.pegasys.teku.infrastructure.ssz.sos.SszMaxLengthExceededException;

/**
 * TEMPORARY. Teku enforces the consensus count limits of progressive lists (e.g. {@code
 * MAX_ATTESTATIONS_ELECTRA}) in the SSZ schemas, as consensus-specs PR 5564 allows. Reference test
 * fixtures that exceed such a limit are always ones expected to be invalid, but they fail at
 * deserialization with {@link SszMaxLengthExceededException} before the executor can assert the
 * rejection it was written for.
 *
 * <p>Those fixtures are listed here by display name, as printed in test reports. A listed fixture
 * passes when, and only when, it is rejected by an SSZ limit. To remove this once the fixtures
 * respect the limits: delete this class, its test, and the call in {@link Eth2ReferenceTestCase}.
 */
public class SszMaxLengthFixtures {

  public static final SszMaxLengthFixtures REJECTED_BY_SSZ_LIMITS =
      new SszMaxLengthFixtures(
          Set.of(
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_contains_deposits",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_payload_attestations",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_proposer_slashings",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_parent_builder_exit_requests",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_parent_withdrawal_requests",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_bls_to_execution_changes",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_attester_slashings",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_parent_consolidation_requests",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_parent_builder_deposit_requests",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_voluntary_exits",
              "gloas - minimal - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_attestations",
              "gloas - minimal - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_builder_deposit_requests",
              "gloas - minimal - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_builder_exit_requests",
              "gloas - minimal - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_withdrawal_requests",
              "gloas - minimal - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_consolidation_requests",
              "gloas - minimal - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_withdrawals",
              "gloas - minimal - operations/attester_slashing - invalid_too_many_attesting_indices",
              "gloas - minimal - operations/parent_execution_payload - invalid_too_many_withdrawal_requests",
              "gloas - minimal - operations/parent_execution_payload - invalid_too_many_builder_deposit_requests",
              "gloas - minimal - operations/parent_execution_payload - invalid_too_many_consolidation_requests",
              "gloas - minimal - operations/parent_execution_payload - invalid_too_many_builder_exit_requests",
              "gloas - minimal - sanity/blocks - invalid_too_many_deposits",
              "gloas - minimal - sanity/blocks - invalid_too_many_proposer_slashings",
              "gloas - minimal - sanity/blocks - invalid_too_many_payload_attestations",
              "gloas - minimal - sanity/blocks - invalid_old_style_deposit_rejected",
              "gloas - minimal - sanity/blocks - invalid_too_many_bls_to_execution_changes",
              "gloas - minimal - sanity/blocks - invalid_too_many_voluntary_exits",
              "gloas - minimal - sanity/blocks - invalid_too_many_attester_slashings",
              "gloas - minimal - sanity/blocks - invalid_too_many_attestations",
              // mainnet
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_contains_deposits",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_attestations",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_attester_slashings",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_bls_to_execution_changes",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_parent_builder_deposit_requests",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_parent_builder_exit_requests",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_parent_consolidation_requests",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_parent_withdrawal_requests",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_payload_attestations",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_proposer_slashings",
              "gloas - mainnet - networking/gossip_beacon_block - gossip_beacon_block__reject_too_many_voluntary_exits",
              "gloas - mainnet - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_builder_deposit_requests",
              "gloas - mainnet - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_builder_exit_requests",
              "gloas - mainnet - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_consolidation_requests",
              "gloas - mainnet - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_withdrawal_requests",
              "gloas - mainnet - networking/gossip_execution_payload_envelope - gossip_execution_payload_envelope__reject_too_many_withdrawals",
              "gloas - mainnet - operations/parent_execution_payload - invalid_too_many_builder_deposit_requests",
              "gloas - mainnet - operations/parent_execution_payload - invalid_too_many_builder_exit_requests",
              "gloas - mainnet - operations/parent_execution_payload - invalid_too_many_consolidation_requests",
              "gloas - mainnet - operations/parent_execution_payload - invalid_too_many_withdrawal_requests",
              "gloas - mainnet - sanity/blocks - invalid_old_style_deposit_rejected",
              "gloas - mainnet - sanity/blocks - invalid_too_many_attestations",
              "gloas - mainnet - sanity/blocks - invalid_too_many_attester_slashings",
              "gloas - mainnet - sanity/blocks - invalid_too_many_bls_to_execution_changes",
              "gloas - mainnet - sanity/blocks - invalid_too_many_deposits",
              "gloas - mainnet - sanity/blocks - invalid_too_many_payload_attestations",
              "gloas - mainnet - sanity/blocks - invalid_too_many_proposer_slashings",
              "gloas - mainnet - sanity/blocks - invalid_too_many_voluntary_exits"));

  private final Set<String> fixtures;

  SszMaxLengthFixtures(final Set<String> fixtures) {
    this.fixtures = fixtures;
  }

  /**
   * Runs a reference test, inverting the outcome for the listed fixtures.
   *
   * <p>A fixture that is not listed runs as usual. A listed one passes only if the executor throws
   * {@link SszMaxLengthExceededException}: any other exception propagates as a normal failure, and
   * completing without one fails with an {@link AssertionError} asking for the entry to be removed,
   * so the list cannot outlive the limit that put a fixture on it.
   *
   * @param testDefinition the fixture, matched against the list by {@link
   *     TestDefinition#getDisplayName()}
   * @param executor the executor that would normally run the fixture
   * @throws Throwable the executor's own failure for an unlisted fixture, or one that is listed but
   *     fails for a reason other than an SSZ limit
   */
  public void run(final TestDefinition testDefinition, final TestExecutor executor)
      throws Throwable {
    if (!fixtures.contains(key(testDefinition))) {
      executor.runTest(testDefinition);
      return;
    }
    try {
      executor.runTest(testDefinition);
    } catch (final SszMaxLengthExceededException expected) {
      return;
    }
    throw new AssertionError(
        testDefinition
            + " is no longer rejected by an SSZ limit, remove it from "
            + getClass().getSimpleName());
  }

  private static String key(final TestDefinition testDefinition) {
    return testDefinition.getDisplayName();
  }
}
