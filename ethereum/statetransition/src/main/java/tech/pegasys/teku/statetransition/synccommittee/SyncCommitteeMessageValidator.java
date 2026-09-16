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

package tech.pegasys.teku.statetransition.synccommittee;

import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.spec.config.Constants.VALID_SYNC_COMMITTEE_MESSAGE_SET_SIZE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ACCEPT;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.IGNORE;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.ignore;
import static tech.pegasys.teku.statetransition.validation.InternalValidationResult.reject;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.collections.LimitedSet;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.SyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.operations.versions.altair.ValidatableSyncCommitteeMessage;
import tech.pegasys.teku.spec.datastructures.state.ForkInfo;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.util.SyncSubcommitteeAssignments;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.common.util.SyncCommitteeUtil;
import tech.pegasys.teku.statetransition.validation.GossipValidationHelper;
import tech.pegasys.teku.statetransition.validation.InternalValidationResult;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SyncCommitteeMessageValidator {
  private static final Logger LOG = LogManager.getLogger();
  private final Set<UniquenessKey> seenIndices =
      LimitedSet.createSynchronizedIterable(VALID_SYNC_COMMITTEE_MESSAGE_SET_SIZE * 2);
  private final Spec spec;
  private final SyncCommitteeStateUtils syncCommitteeStateUtils;
  private final AsyncBLSSignatureVerifier signatureVerifier;
  private final GossipValidationHelper gossipValidationHelper;

  private final RecentChainData recentChainData;

  public SyncCommitteeMessageValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final SyncCommitteeStateUtils syncCommitteeStateUtils,
      final AsyncBLSSignatureVerifier signatureVerifier,
      final GossipValidationHelper gossipValidationHelper) {
    this.spec = spec;
    this.syncCommitteeStateUtils = syncCommitteeStateUtils;
    this.signatureVerifier = signatureVerifier;
    this.gossipValidationHelper = gossipValidationHelper;
    this.recentChainData = recentChainData;
  }

  public SafeFuture<InternalValidationResult> validate(
      final ValidatableSyncCommitteeMessage validatableMessage) {

    final SyncCommitteeMessage message = validatableMessage.getMessage();

    final Optional<SyncCommitteeUtil> maybeSyncCommitteeUtil =
        spec.getSyncCommitteeUtil(message.getSlot());
    if (maybeSyncCommitteeUtil.isEmpty()) {
      return completedFuture(
          reject(
              "Rejecting sync committee message because the fork active at slot %s does not support sync committees",
              message.getSlot()));
    }
    final SyncCommitteeUtil syncCommitteeUtil = maybeSyncCommitteeUtil.get();

    /*
     * [IGNORE] The message's slot is for the current slot(with a MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance),
     * i.e. sync_committee_message.slot == current_slot
     */
    if (!gossipValidationHelper.isSlotCurrent(message.getSlot())) {
      LOG.trace(
          "Ignoring sync committee message from validator {}, "
              + "because it is not from the current slot "
              + "(message slot: {}, current slot: {})",
          message::getValidatorIndex,
          message::getSlot,
          recentChainData::getCurrentSlot);
      return completedFuture(IGNORE);
    }

    /*
     * [IGNORE] There has been no other valid sync committee message for the declared slot for
     * the validator referenced by sync_committee_message.validator_index for this subnet.
     * Note this validation is _per topic_ so that for a given `slot`, multiple messages could be
     * forwarded with the same `validator_index` as long as the `subnet_id`s are distinct.
     */
    final Optional<UniquenessKey> uniquenessKey;
    if (validatableMessage.getReceivedSubnetId().isPresent()) {
      final UniquenessKey key =
          getUniquenessKey(message, validatableMessage.getReceivedSubnetId().getAsInt());
      if (seenIndices.contains(key)) {
        return completedFuture(IGNORE);
      }
      uniquenessKey = Optional.of(key);
    } else {
      uniquenessKey = Optional.empty();
    }

    return syncCommitteeStateUtils
        .getStateForSyncCommittee(message.getSlot())
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                LOG.trace(
                    "Ignoring sync committee message because state is not available or not from Altair fork");
                return completedFuture(IGNORE);
              }
              final BeaconStateAltair state = maybeState.get();
              return validateWithState(
                  validatableMessage, message, syncCommitteeUtil, state, uniquenessKey);
            });
  }

  private SafeFuture<InternalValidationResult> validateWithState(
      final ValidatableSyncCommitteeMessage validatableMessage,
      final SyncCommitteeMessage message,
      final SyncCommitteeUtil syncCommitteeUtil,
      final BeaconStateAltair state,
      final Optional<UniquenessKey> maybeUniquenessKey) {
    final UInt64 messageEpoch = spec.computeEpochAtSlot(message.getSlot());

    // Always calculate the applicable subcommittees to ensure they are cached and can be used to
    // send the gossip.
    final SyncSubcommitteeAssignments assignedSubcommittees =
        validatableMessage.calculateAssignments(spec, state);

    /*
     * [REJECT] The validator producing this sync_committee_message is in the current sync
     * committee, i.e. state.validators[sync_committee_message.validator_index].pubkey in
     * state.current_sync_committee.pubkeys.
     */
    if (assignedSubcommittees.isEmpty()) {
      return completedFuture(
          reject(
              "Rejecting sync committee message because validator is not in the sync committee"));
    }

    // For messages received via gossip, it has to be unique based on the subnet it was on
    // For locally produced messages we should accept it if it hasn't been seen on any subnet
    final List<UniquenessKey> uniquenessKeys =
        maybeUniquenessKey
            .map(List::of)
            .orElseGet(
                () ->
                    assignedSubcommittees
                        .getAssignedSubcommittees()
                        .intStream()
                        .mapToObj(subnetId -> getUniquenessKey(message, subnetId))
                        .toList());

    /*
     * [IGNORE] There has been no other valid sync committee message for the declared slot for the
     * validator referenced by sync_committee_message.validator_index.
     */
    if (seenIndices.containsAll(uniquenessKeys)) {
      return completedFuture(IGNORE);
    }

    /*
     * [REJECT] The subnet_id is correct, i.e. subnet_id in compute_subnets_for_sync_committee(state, sync_committee_message.validator_index).
     */
    if (validatableMessage.getReceivedSubnetId().isPresent()
        && !assignedSubcommittees
            .getAssignedSubcommittees()
            .contains(validatableMessage.getReceivedSubnetId().getAsInt())) {
      return completedFuture(
          reject("Rejecting sync committee message because subnet id is incorrect"));
    }

    final Optional<BLSPublicKey> maybeValidatorPublicKey =
        spec.getValidatorPubKey(state, message.getValidatorIndex());
    if (maybeValidatorPublicKey.isEmpty()) {
      return completedFuture(
          reject("Rejecting sync committee message because the validator index is unknown"));
    }

    final ForkInfo forkInfo =
        new ForkInfo(spec.fork(messageEpoch), state.getGenesisValidatorsRoot());
    final Bytes32 signingRoot =
        syncCommitteeUtil.getSyncCommitteeMessageSigningRoot(
            message.getBeaconBlockRoot(), messageEpoch, forkInfo);
    /*
     * [REJECT] The signature is valid
     */
    return signatureVerifier
        .verify(maybeValidatorPublicKey.get(), signingRoot, message.getSignature())
        .thenApply(
            signatureValid -> {
              if (!signatureValid) {
                return reject("Rejecting sync committee message because the signature is invalid");
              }
              if (!seenIndices.addAll(uniquenessKeys)) {
                return ignore(
                    "Ignoring sync committee message as a duplicate was processed during validation");
              }
              return ACCEPT;
            });
  }

  private UniquenessKey getUniquenessKey(final SyncCommitteeMessage message, final int subnetId) {
    return new UniquenessKey(message.getSlot(), message.getValidatorIndex(), subnetId);
  }

  private record UniquenessKey(UInt64 slot, UInt64 validatorIndex, int subnetId) {}
}
