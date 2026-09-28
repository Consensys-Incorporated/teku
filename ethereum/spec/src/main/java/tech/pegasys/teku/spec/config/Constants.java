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

package tech.pegasys.teku.spec.config;

import java.time.Duration;

public class Constants {

  // Networking
  public static final int ATTESTATION_SUBNET_COUNT = 64;

  // Teku Networking Specific
  public static final int VALID_BLOCK_SET_SIZE = 1000;
  // Target holding 2 slots worth of payload attestations
  public static final int VALID_PAYLOAD_ATTESTATION_SET_SIZE = 512 * 2;
  // The cache is used for the `payload_present` voting, gossip and RPC fetch de-duplication, so no
  // need for a long term caching (1 epoch)
  public static final int VALID_EXECUTION_PAYLOAD_SET_SIZE = 32;
  // Target holding two slots worth of aggregators (16 aggregators, 64 committees and 2 slots)
  public static final int VALID_AGGREGATE_SET_SIZE = 16 * 64 * 2;
  // Tracking 10 slots worth of bids
  public static final int MAX_SLOTS_TO_TRACK_BUILDERS_BIDS = 10;
  // Preferences are published for the next epoch and need to persist until that epoch completes
  public static final int MAX_SLOTS_TO_TRACK_PROPOSER_PREFERENCES = 64;
  // Maximum number of (slot, parentBlockHash, parentBlockRoot) combinations to cache highest bids
  public static final int HIGHEST_BID_SET_SIZE = 10;
  // Target 2 different attestation data (aggregators normally agree) for two slots
  public static final int VALID_ATTESTATION_DATA_SET_SIZE = 2 * 64 * 2;
  public static final int VALID_VALIDATOR_INDEX_SET_SIZE = 10000;
  // Only need to maintain a cache for the current slot, so just needs to be as large as the
  // sync committee size.
  public static final int VALID_CONTRIBUTION_AND_PROOF_SET_SIZE = 512;
  public static final int VALID_SYNC_COMMITTEE_MESSAGE_SET_SIZE = 512;
  // When finalization is at its best case with 100% of votes we could have up to 3 full
  // epochs of non-finalized blocks
  public static final int BEST_CASE_NON_FINALIZED_EPOCHS = 3;

  public static final Duration ETH1_INDIVIDUAL_BLOCK_RETRY_TIMEOUT = Duration.ofMillis(500);
  public static final Duration ETH1_DEPOSIT_REQUEST_RETRY_TIMEOUT = Duration.ofSeconds(2);
  public static final Duration EL_ENGINE_BLOCK_EXECUTION_TIMEOUT = Duration.ofSeconds(8);
  public static final Duration EL_ENGINE_NON_BLOCK_EXECUTION_TIMEOUT = Duration.ofSeconds(1);
  public static final Duration ETH1_ENDPOINT_MONITOR_SERVICE_POLL_INTERVAL = Duration.ofSeconds(10);
  public static final Duration ETH1_VALID_ENDPOINT_CHECK_INTERVAL =
      Duration.ofSeconds(60); // usable
  public static final Duration ETH1_FAILED_ENDPOINT_CHECK_INTERVAL =
      Duration.ofSeconds(30); // network or API call failure
  public static final Duration ETH1_INVALID_ENDPOINT_CHECK_INTERVAL =
      Duration.ofSeconds(60); // syncing or wrong chainid
  public static final int MAXIMUM_CONCURRENT_ETH1_REQUESTS = 5;
  public static final int MAXIMUM_CONCURRENT_EE_REQUESTS = 5;
  public static final int MAXIMUM_CONCURRENT_NON_CRITICAL_EB_REQUESTS = 3;
  public static final int REPUTATION_MANAGER_CAPACITY = 1024;
  public static final Duration STORAGE_REQUEST_TIMEOUT = Duration.ofSeconds(60);
  public static final int STORAGE_QUERY_CHANNEL_PARALLELISM = 20; // # threads
  public static final int PROTOARRAY_FORKCHOICE_PRUNE_THRESHOLD = 256;

  // Teku Validator Client Specific
  public static final Duration GENESIS_DATA_RETRY_DELAY = Duration.ofSeconds(10);

  // Builder Specific
  // Maximum duration before timeout for each builder call
  public static final Duration BUILDER_CALL_TIMEOUT = Duration.ofSeconds(8);
  // Individual durations (per method) before timeout for each builder call. They must be less than
  // or equal to BUILDER_CALL_TIMEOUT
  public static final Duration BUILDER_STATUS_TIMEOUT = Duration.ofSeconds(1);
  public static final Duration BUILDER_REGISTER_VALIDATOR_TIMEOUT = Duration.ofSeconds(8);
  public static final Duration BUILDER_PROPOSAL_DELAY_TOLERANCE = Duration.ofSeconds(1);
  public static final Duration BUILDER_GET_PAYLOAD_TIMEOUT = Duration.ofSeconds(3);
  public static final int EPOCHS_PER_VALIDATOR_REGISTRATION_SUBMISSION = 1;

  // ZkChain prototype
  public static final long MAX_EXECUTION_PROOF_SUBNETS = 8;
}
