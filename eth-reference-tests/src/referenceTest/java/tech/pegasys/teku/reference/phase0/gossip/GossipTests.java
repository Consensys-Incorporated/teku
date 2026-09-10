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

import com.google.common.collect.ImmutableMap;
import tech.pegasys.teku.reference.TestExecutor;

/** Executable gossip validations */
public class GossipTests {

  public static final ImmutableMap<String, TestExecutor> GOSSIP_TEST_TYPES =
      ImmutableMap.<String, TestExecutor>builder()
          // TODO: https://github.com/Consensys-Incorporated/teku/issues/11229
          .put("networking/gossip_attester_slashing", TestExecutor.IGNORE_TESTS)
          .put(
              "networking/gossip_beacon_aggregate_and_proof",
              new GossipBeaconAggregateAndProofTestExecutor(
                  // TODO: https://github.com/Consensys/teku/issues/11153
                  "gossip_beacon_aggregate_and_proof__ignore_payload_pending_el_validation"))
          .put(
              "networking/gossip_beacon_attestation",
              new GossipBeaconAttestationTestExecutor(
                  // TODO: https://github.com/Consensys/teku/issues/11153
                  "gossip_beacon_attestation__ignore_payload_pending_el_validation"))
          .put("networking/gossip_blob_sidecar", new GossipBlobSidecarTestExecutor())
          // TODO: https://github.com/Consensys/teku/issues/10578
          .put("networking/gossip_data_column_sidecar", TestExecutor.IGNORE_TESTS)
          .put("networking/gossip_partial_data_column_sidecar", TestExecutor.IGNORE_TESTS)
          .put(
              "networking/gossip_bls_to_execution_change",
              new GossipBlsToExecutionChangeTestExecutor(
                  // TODO: this test should be fixed in the next consensus-specs release
                  "gossip_bls_to_execution_change__ignore_pre_capella"))
          .put("networking/gossip_beacon_block", new GossipBeaconBlockTestExecutor())
          .put(
              "networking/gossip_sync_committee_contribution_and_proof",
              new GossipSyncCommitteeContributionAndProofTestExecutor())
          .put(
              "networking/gossip_sync_committee_message",
              new GossipSyncCommitteeMessageTestExecutor())
          // TODO: https://github.com/Consensys-Incorporated/teku/issues/11229
          .put("networking/gossip_proposer_slashing", TestExecutor.IGNORE_TESTS)
          // TODO: https://github.com/Consensys-Incorporated/teku/issues/11229
          .put("networking/gossip_voluntary_exit", TestExecutor.IGNORE_TESTS)
          .put(
              "networking/gossip_payload_attestation_message",
              new GossipPayloadAttestationMessageTestExecutor())
          .put(
              // TODO: https://github.com/Consensys-Incorporated/teku/issues/11232
              "networking/gossip_proposer_preferences", TestExecutor.IGNORE_TESTS)
          .put(
              "networking/gossip_execution_payload_envelope",
              new GossipExecutionPayloadEnvelopeTestExecutor())
          .put(
              "networking/gossip_execution_payload_bid",
              // TODO: https://github.com/Consensys-Incorporated/teku/issues/11233
              new GossipExecutionPayloadBidTestExecutor(
                  "gossip_execution_payload_bid__ignore_builder_exit_in_parent_payload",
                  "gossip_execution_payload_bid__ignore_builder_exit_with_pending_balance"))
          .build();
}
