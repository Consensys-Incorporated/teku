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

package tech.pegasys.teku.infrastructure.http;

import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_ACCEPTED;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_BAD_REQUEST;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_FORBIDDEN;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_INTERNAL_SERVER_ERROR;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NOT_FOUND;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_NO_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_OK;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_PARTIAL_CONTENT;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_SERVICE_UNAVAILABLE;
import static tech.pegasys.teku.infrastructure.http.HttpStatusCodes.SC_UNSUPPORTED_MEDIA_TYPE;

import java.util.List;

public class RestApiConstants {

  public static final String SLOT = "slot";
  public static final String EPOCH = "epoch";
  public static final String INCLUDE_PAYLOAD = "include_payload";
  public static final String COMMITTEE_INDEX = "committee_index";
  public static final String SUBCOMMITTEE_INDEX = "subcommittee_index";
  public static final String RANDAO_REVEAL = "randao_reveal";
  public static final String SKIP_RANDAO_VERIFICATION = "skip_randao_verification";
  public static final String GRAFFITI = "graffiti";
  public static final String ATTESTATION_DATA_ROOT = "attestation_data_root";
  public static final String INDEX = "index";
  public static final String TOPICS = "topics";
  public static final String START_PERIOD = "start_period";
  public static final String COUNT = "count";
  public static final String BUILDER_INDEX = "builder_index";

  public static final String BLOCK_ROOT = "block_root";
  public static final String PARENT_ROOT = "parent_root";
  public static final String BEACON_BLOCK_ROOT = "beacon_block_root";
  public static final String STATUS = "status";

  public static final String TAG_NODE = "Node";
  public static final String TAG_VALIDATOR = "Validator";
  public static final String TAG_BEACON = "Beacon";
  public static final String TAG_BUILDER = "Builder";
  public static final String TAG_EXPERIMENTAL = "Experimental";
  public static final String TAG_CONFIG = "Config";
  public static final String TAG_EVENTS = "Events";
  public static final String TAG_VALIDATOR_REQUIRED = "Validator Required Api";
  public static final String TAG_REWARDS = "Rewards";
  public static final String TAG_DEBUG = "Debug";
  public static final String TAG_TEKU = "Teku";

  // Preferred tags order in Swagger UI
  public static final List<String> PREFERRED_DISPLAY_TAGS_ORDER =
      List.of(
          TAG_BEACON,
          TAG_VALIDATOR_REQUIRED,
          TAG_VALIDATOR,
          TAG_BUILDER,
          TAG_REWARDS,
          TAG_EVENTS,
          TAG_CONFIG,
          TAG_NODE,
          TAG_TEKU,
          TAG_DEBUG,
          TAG_EXPERIMENTAL);

  // Use "" + instead of Integer.toString so they are constants and can be used in annotations
  public static final String RES_OK = "" + SC_OK;
  public static final String RES_ACCEPTED = "" + SC_ACCEPTED;
  public static final String RES_NO_CONTENT = "" + SC_NO_CONTENT;
  public static final String RES_PARTIAL_CONTENT = "" + SC_PARTIAL_CONTENT;
  public static final String RES_BAD_REQUEST = "" + SC_BAD_REQUEST;
  public static final String RES_FORBIDDEN = "" + SC_FORBIDDEN;
  public static final String RES_NOT_FOUND = "" + SC_NOT_FOUND;
  public static final String RES_INTERNAL_ERROR = "" + SC_INTERNAL_SERVER_ERROR;
  public static final String RES_UNSUPPORTED_MEDIA_TYPE = "" + SC_UNSUPPORTED_MEDIA_TYPE;
  public static final String RES_SERVICE_UNAVAILABLE = "" + SC_SERVICE_UNAVAILABLE;

  public static final String INVALID_BODY_SUPPLIED = "Invalid body supplied.";

  public static final String SERVICE_UNAVAILABLE =
      "Beacon node is currently syncing and not serving requests";

  public static final String EPOCH_QUERY_DESCRIPTION = "`uint64` Epoch number to query.";
  public static final String SLOT_QUERY_DESCRIPTION =
      "`UInt64` Slot to query in the canonical chain.";
  public static final String SLOT_PATH_DESCRIPTION =
      "The slot for which the block should be proposed.";

  public static final String COMMITTEE_INDEX_QUERY_DESCRIPTION =
      "`uint64` Committee index to query.";

  public static final String PARAM_PEER_ID = "peer_id";
  public static final String PARAM_PEER_ID_DESCRIPTION =
      "Cryptographic hash of a peer’s public key. [Read more](https://docs.libp2p.io/concepts/peer-id/)";

  public static final String PARAM_BLOCK_ID = "block_id";
  public static final String PARAM_BLOCK_ID_DESCRIPTION =
      "Block identifier. Can be one of: "
          + "\"head\" (canonical head in node's view), "
          + "\"genesis\", "
          + "\"finalized\", "
          + "&lt;slot&gt;, "
          + "&lt;hex encoded blockRoot with 0x prefix&gt;.";
  public static final String PARAM_STATE_ID = "state_id";
  public static final String PARAM_STATE_ID_DESCRIPTION =
      "State identifier. Can be one of: "
          + "\"head\" (canonical head in node's view), "
          + "\"genesis\", "
          + "\"finalized\", "
          + "\"justified\", "
          + "&lt;slot&gt;, "
          + "&lt;hex encoded stateRoot with 0x prefix&gt;.";

  public static final String PARAM_BROADCAST_VALIDATION = "broadcast_validation";
  public static final String PARAM_BROADCAST_VALIDATION_DESCRIPTION =
      """
      Level of validation that must be applied to a block before it is broadcast. \
      Possible values:
      - **`gossip`** (default): lightweight gossip checks only
      - **`consensus`**: full consensus checks, including validation of all signatures and \
        blocks fields _except_ for the execution payload transactions.
      - **`consensus_and_equivocation`**: the same as `consensus`, with an extra equivocation \
        check immediately before the block is broadcast. If the block is found to be an
        equivocation it fails validation.
      If the block fails the requested level of a validation a 400 status MUST be returned \
      immediately and the block MUST NOT be broadcast to the network.
      If validation succeeds, the block must still be fully verified before it is \
      incorporated into the state and a 20x status is returned to the caller.""";

  public static final String PARAM_ID = "id";
  public static final String PARAM_VALIDATOR_ID = "validator_id";
  public static final String PARAM_VALIDATOR_DESCRIPTION =
      "Either hex encoded public key (with 0x prefix) or validator index";

  public static final String PARAM_STATUS_DESCRIPTION =
      "valid values:   pending_initialized, "
          + "  pending_queued, "
          + "  active_ongoing, "
          + "  active_exiting, "
          + "  active_slashed, "
          + "  exited_unslashed, "
          + "  exited_slashed, "
          + "  withdrawal_possible, "
          + "  withdrawal_done, "
          + "  active, "
          + "  pending, "
          + "  exited, "
          + "  withdrawal";

  public static final String SYNCING_STATUS = "syncing_status";
  public static final String SYNCING_STATUS_DESCRIPTION =
      "Customize syncing status instead of default status code (" + SC_PARTIAL_CONTENT + ")";

  public static final String DEPENDENT_ROOT = "dependent_root";
  public static final String EXECUTION_OPTIMISTIC = "execution_optimistic";
  public static final String FINALIZED = "finalized";
  public static final String EXECUTION_PAYLOAD_BLINDED = "execution_payload_blinded";
  public static final String EXECUTION_PAYLOAD_VALUE = "execution_payload_value";
  public static final String CONSENSUS_BLOCK_VALUE = "consensus_block_value";
  public static final String INCLUDE_EXECUTION_PAYLOAD = "execution_payload_included";
  public static final String TARGET_PEER_COUNT = "target_peer_count";
  public static final String TARGET_PEER_COUNT_DESCRIPTION =
      "Returns "
          + SC_SERVICE_UNAVAILABLE
          + " status code when current peer count is below than target";

  public static final String REQUIRE_PREPARED_PROPOSERS = "require_prepared_proposers";
  public static final String REQUIRE_PREPARED_PROPOSERS_DESCRIPTION =
      "Returns "
          + SC_SERVICE_UNAVAILABLE
          + " status code if set to true and no proposers have been prepared";
  public static final String REQUIRE_VALIDATOR_REGISTRATIONS = "require_validator_registrations";
  public static final String REQUIRE_VALIDATOR_REGISTRATIONS_DESCRIPTION =
      "Returns "
          + SC_SERVICE_UNAVAILABLE
          + " status code if set to true and no validators have been registered with the builder";
  public static final String SKIP_RANDAO_VERIFICATION_PARAM_DESCRIPTION =
      "Skip verification of the `randao_reveal` value. Ignored in the Teku implementation.";

  public static final String INCLUDE_PAYLOAD_PARAM_DESCRIPTION =
      """
              Controls whether the execution payload envelope and blobs are included in the response
              when self-building (using local execution payload).

              When `true`, the response includes the full block contents: beacon block,
              execution payload envelope, blobs, and KZG proofs. This enables stateless operation
              where the validator client can use multiple beacon nodes (multi-BN setups, distributed validators, failover).

              When `false`, only the beacon block is returned and the beacon node caches the execution
              payload envelope and blobs internally. The validator client must then fetch them separately
              via `GET /eth/v1/validator/execution_payload_envelopes/{slot}/{beacon_block_root}`. This saves
              bandwidth but requires the validator client to publish via the same beacon node that
              produced the block (stateful operation).

              This parameter affects the self-built case only. When a builder bid wins, the beacon node
              does not hold that payload, so it returns only the beacon block regardless of this
              parameter.""";

  public static final String BUILDER_BOOST_FACTOR = "builder_boost_factor";
  public static final String BUILDER_BOOST_FACTOR_DESCRIPTION =
      """
          Percentage multiplier to apply to the builder's payload value when choosing between a
          builder payload header and payload from the paired execution node. This parameter is only
          relevant if the beacon node is connected to a builder, deems it safe to produce a builder
          payload, and receives valid responses from both the builder endpoint _and_ the paired
          execution node. When these preconditions are met, the server MUST act as follows:

          * if `exec_node_payload_value >= builder_boost_factor * (builder_payload_value // 100)`,
            then return a full (unblinded) block containing the execution node payload.
          * otherwise, return a blinded block containing the builder payload header.

          Servers must support the following values of the boost factor which encode common
          preferences:

          * `builder_boost_factor=0`: prefer the execution node payload unless an error makes it
            unviable.
          * `builder_boost_factor=100`: default profit maximization mode; choose whichever
            payload pays more.
          * `builder_boost_factor=2**64 - 1`: prefer the builder payload unless an error or
            beacon node health check makes it unviable.

          Servers should use saturating arithmetic or another technique to ensure that large values of
          the `builder_boost_factor` do not trigger overflows or errors. If this parameter is
          provided and the beacon node is not configured with a builder then the beacon node MUST
          respond with a full block, which the caller can choose to reject if it wishes. If this
          parameter is **not** provided then it should be treated as having the default value of 100.
          If the value is provided but out of range for a 64-bit unsigned integer, then an error
          response with status code 400 MUST be returned.""";

  public static final String HEADER_ACCEPT = "Accept";

  public static final String HEADER_CONSENSUS_VERSION = "Eth-Consensus-Version";
  public static final String HEADER_EXECUTION_PAYLOAD_BLINDED = "Eth-Execution-Payload-Blinded";
  public static final String HEADER_BLOB_DATA_INCLUDED = "Eth-Blob-Data-Included";
  public static final String HEADER_EXECUTION_PAYLOAD_VALUE = "Eth-Execution-Payload-Value";
  public static final String HEADER_CONSENSUS_BLOCK_VALUE = "Eth-Consensus-Block-Value";
  public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
  public static final String HEADER_CONTENT_ENCODING = "Content-Encoding";
  public static final String HEADER_INCLUDE_PAYLOAD = "Eth-Execution-Payload-Included";
  public static final String HEADER_BUILDER_URL = "Eth-Builder-Url";

  public static final String CACHE_NONE = "max-age=0";

  public static final String PUBKEY = "pubkey";
}
