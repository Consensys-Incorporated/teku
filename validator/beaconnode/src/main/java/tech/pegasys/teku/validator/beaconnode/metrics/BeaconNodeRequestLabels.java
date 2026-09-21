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

package tech.pegasys.teku.validator.beaconnode.metrics;

public class BeaconNodeRequestLabels {

  public static final String GET_GENESIS_METHOD = "get_genesis";
  public static final String GET_VALIDATOR_INDICES_METHOD = "get_validator_indices";
  public static final String GET_VALIDATOR_STATUSES_METHOD = "get_validator_statuses";
  public static final String GET_ATTESTATION_DUTIES_METHOD = "get_attestation_duties";
  public static final String GET_PROPOSER_DUTIES_REQUESTS_METHOD = "get_proposer_duties";
  public static final String GET_PEER_COUNT_METHOD = "get_peer_count";
  public static final String GET_SYNC_COMMITTEE_DUTIES_METHOD = "get_sync_committee_duties";
  public static final String CREATE_UNSIGNED_BLOCK_METHOD = "create_unsigned_block";
  public static final String CREATE_ATTESTATION_METHOD = "create_attestation";
  public static final String CREATE_AGGREGATE_METHOD = "create_aggregate";
  public static final String CREATE_SYNC_COMMITTEE_CONTRIBUTION_METHOD =
      "create_sync_committee_contribution";
  public static final String BEACON_COMMITTEE_SUBSCRIPTION_METHOD = "beacon_committee_subscription";
  public static final String SYNC_COMMITTEE_SUBNET_SUBSCRIPTION_METHOD =
      "sync_committee_subnet_subscription";
  public static final String PERSISTENT_SUBNETS_SUBSCRIPTION_METHOD =
      "persistent_subnets_subscription";
  public static final String PUBLISH_ATTESTATION_METHOD = "publish_attestation";
  public static final String PUBLISH_AGGREGATE_AND_PROOFS_METHOD = "publish_aggregate_and_proofs";
  public static final String PUBLISH_BLOCK_METHOD = "publish_block";
  public static final String SEND_SYNC_COMMITTEE_MESSAGES_METHOD = "send_sync_committee_messages";
  public static final String SEND_CONTRIBUTIONS_AND_PROOFS_METHOD = "send_contributions_and_proofs";
  public static final String PREPARE_BEACON_PROPOSERS_METHOD = "prepare_beacon_proposers";
  public static final String REGISTER_VALIDATORS_METHOD = "register_validators";
  public static final String GET_VALIDATORS_LIVENESS = "get_validators_liveness";
  public static final String BEACON_COMMITTEE_SELECTIONS = "beacon_committee_selections";
  public static final String SYNC_COMMITTEE_SELECTIONS = "sync_committee_selections";
  public static final String GET_PTC_DUTIES_METHOD = "get_ptc_duties";
  public static final String CREATE_PAYLOAD_ATTESTATION_METHOD = "create_payload_attestation";
  public static final String SEND_PAYLOAD_ATTESTATION_MESSAGES_METHOD =
      "send_payload_attestation_messages";
  public static final String SEND_PROPOSER_PREFERENCES_METHOD = "send_proposer_preferences";
  public static final String SEND_BUILDER_PREFERENCES_METHOD = "send_builder_preferences";
  // Builder namespace
  public static final String CREATE_UNSIGNED_EXECUTION_PAYLOAD_BID_METHOD =
      "create_unsigned_execution_payload_bid";
  public static final String PUBLISH_EXECUTION_PAYLOAD_BID_METHOD = "publish_execution_payload_bid";
  public static final String CREATE_UNSIGNED_EXECUTION_PAYLOAD_METHOD =
      "create_unsigned_execution_payload";
  public static final String PUBLISH_EXECUTION_PAYLOAD_METHOD = "publish_execution_payload";
}
