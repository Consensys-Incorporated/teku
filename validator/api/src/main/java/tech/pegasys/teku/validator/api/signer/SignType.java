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

package tech.pegasys.teku.validator.api.signer;

public enum SignType {
  RANDAO_REVEAL("randao_reveal"),
  BLOCK("block"),
  BLOCK_V2("block_v2"),
  ATTESTATION("attestation"),
  AGGREGATION_SLOT("aggregation_slot"),
  AGGREGATE_AND_PROOF("aggregate_and_proof"),
  VOLUNTARY_EXIT("voluntary_exit"),
  SYNC_COMMITTEE_MESSAGE("sync_committee_message"),
  SYNC_AGGREGATOR_SELECTION_DATA("sync_aggregator_selection_data"),
  SYNC_COMMITTEE_SELECTION_PROOF("sync_committee_selection_proof"),
  SYNC_COMMITTEE_CONTRIBUTION_AND_PROOF("sync_committee_contribution_and_proof"),
  VALIDATOR_REGISTRATION("validator_registration"),
  CONTRIBUTION_AND_PROOF("contribution_and_proof"),
  BEACON_BLOCK("beacon_block"),
  BLOB_SIDECAR("blob_sidecar"),
  EXECUTION_PAYLOAD_BID("execution_payload_bid"),
  EXECUTION_PAYLOAD_ENVELOPE("execution_payload_envelope"),
  PAYLOAD_ATTESTATION_MESSAGE("payload_attestation_message"),
  PROPOSER_PREFERENCES("proposer_preferences"),
  BUILDER_REQUEST_AUTH("builder_request_auth");

  private final String name;

  SignType(final String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
