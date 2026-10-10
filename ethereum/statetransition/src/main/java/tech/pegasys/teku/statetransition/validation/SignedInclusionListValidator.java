/*
 * Copyright Consensys Software Inc., 2025
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

package tech.pegasys.teku.statetransition.validation;

import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.heze.util.InclusionListUtil;
import tech.pegasys.teku.storage.client.RecentChainData;

public class SignedInclusionListValidator {

  /** Spec: a validator may have at most two valid inclusion lists per slot and dependent root. */
  private static final int MAX_VALID_INCLUSION_LISTS_PER_VALIDATOR = 2;

  private final Spec spec;
  private final RecentChainData recentChainData;
  private final GossipValidationHelper gossipValidationHelper;
  private final AsyncBLSSignatureVerifier signatureVerifier;

  /**
   * {@code seen.inclusion_list_counts}: valid inclusion lists per slot, then per dependent root and
   * validator index. Keyed by slot first so that past slots can be pruned.
   */
  private final NavigableMap<UInt64, ConcurrentMap<SeenInclusionListKey, Integer>>
      seenInclusionListCounts = new ConcurrentSkipListMap<>();

  public SignedInclusionListValidator(
      final Spec spec,
      final RecentChainData recentChainData,
      final GossipValidationHelper gossipValidationHelper,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    this.spec = spec;
    this.recentChainData = recentChainData;
    this.gossipValidationHelper = gossipValidationHelper;
    this.signatureVerifier = signatureVerifier;
  }

  public SafeFuture<InternalValidationResult> validate(
      final SignedInclusionList signedInclusionList) {

    final InclusionList inclusionList = signedInclusionList.getMessage();
    final UInt64 slot = inclusionList.getSlot();
    final SeenInclusionListKey seenKey =
        new SeenInclusionListKey(
            inclusionList.getDependentRoot(), inclusionList.getValidatorIndex());

    /*
     * [IGNORE] This is the first or second valid message from this validator for the slot and
     * dependent root.
     */
    if (getSeenCount(slot, seenKey) >= MAX_VALID_INCLUSION_LISTS_PER_VALIDATOR) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore(
              "Already received %d valid Inclusion Lists from validator with index %d",
              MAX_VALID_INCLUSION_LISTS_PER_VALIDATOR,
              inclusionList.getValidatorIndex().intValue()));
    }

    /*
     * [IGNORE] The inclusion list's slot is for the current slot (with
     * MAXIMUM_GOSSIP_CLOCK_DISPARITY allowance).
     */
    if (!gossipValidationHelper.isSlotCurrent(slot)) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore("Inclusion List is not for the current slot"));
    }
    // Only current slot inclusion lists can be valid, so earlier slots' counts are no longer needed
    seenInclusionListCounts.headMap(slot.minusMinZero(UInt64.ONE)).clear();

    final Fork fork = spec.fork(spec.computeEpochAtSlot(slot));
    final SpecConfigHeze specConfigHeze =
        spec.atSlot(slot).getConfig().toVersionHeze().orElseThrow();
    final int maxTransactionsBytesPerInclusionList =
        specConfigHeze.getMaxTransactionsBytesPerInclusionList();
    final int transactionsBytesSize =
        inclusionList.getTransactions().stream()
            .map(transaction -> transaction.getBytes().size())
            .reduce(0, Integer::sum);

    /*
     * [IGNORE] The size of inclusion list transactions must be non-empty
     */
    if (transactionsBytesSize == 0) {
      return SafeFuture.completedFuture(
          InternalValidationResult.ignore("Inclusion List contains no transactions"));
    }

    /*
     * [REJECT] The size of message is within upperbound MAX_TRANSACTIONS_BYTES_PER_INCLUSION_LIST
     */
    if (transactionsBytesSize > maxTransactionsBytesPerInclusionList) {
      return SafeFuture.completedFuture(
          InternalValidationResult.reject(
              "Inclusion List's transactions size %d (bytes) exceeds max allowed size %d (bytes)",
              transactionsBytesSize, maxTransactionsBytesPerInclusionList));
    }

    /*
     * [REJECT] Every transaction in message.transactions is non-empty
     */
    if (inclusionList.getTransactions().stream()
        .anyMatch(transaction -> transaction.getBytes().isEmpty())) {
      return SafeFuture.completedFuture(
          InternalValidationResult.reject("Inclusion List contains an empty transaction"));
    }

    final InclusionListUtil inclusionListUtil =
        spec.atSlot(slot).getInclusionListUtil().orElseThrow();

    return recentChainData
        .retrieveStateInEffectAtSlot(slot)
        .thenCompose(
            maybeState -> {
              if (maybeState.isEmpty()) {
                // We know the block is imported but now don't have a state to validate against
                // Must have got pruned between checks
                return SafeFuture.completedFuture(InternalValidationResult.IGNORE);
              }
              final BeaconState state = maybeState.get();
              /*
               * [IGNORE] The dependent root for the inclusion-list epoch on the current branch
               * corresponds to message.dependent_root.
               */
              if (!spec.getInclusionListDependentRoot(state, slot)
                  .equals(inclusionList.getDependentRoot())) {
                return SafeFuture.completedFuture(
                    InternalValidationResult.ignore("Inclusion List dependent root mismatch."));
              }
              /*
               * [REJECT] The validator index message.validator_index is within the inclusion list
               * committee corresponding to message.dependent_root.
               */
              if (!inclusionListUtil.validatorIndexWithinCommittee(
                  state, slot, inclusionList.getValidatorIndex())) {
                return SafeFuture.completedFuture(
                    InternalValidationResult.reject(
                        "Validator index is not within the inclusion list committee."));
              }

              /*
               * [REJECT] The signature is valid with respect to the validator's public key.
               */
              return inclusionListUtil
                  .isValidInclusionListSignature(
                      fork, state, signedInclusionList, signatureVerifier)
                  .thenApply(
                      isValidInclusionListSignature -> {
                        if (!isValidInclusionListSignature) {
                          return InternalValidationResult.reject(
                              "Invalid inclusion list signature.");
                        }
                        // Another valid list from this validator may have been counted while
                        // this one was being validated
                        return markSeen(slot, seenKey)
                            ? InternalValidationResult.ACCEPT
                            : InternalValidationResult.ignore(
                                "Already received %d valid Inclusion Lists from validator with"
                                    + " index %d",
                                MAX_VALID_INCLUSION_LISTS_PER_VALIDATOR,
                                inclusionList.getValidatorIndex().intValue());
                      });
            });
  }

  private int getSeenCount(final UInt64 slot, final SeenInclusionListKey seenKey) {
    final Map<SeenInclusionListKey, Integer> countsForSlot = seenInclusionListCounts.get(slot);
    return countsForSlot == null ? 0 : countsForSlot.getOrDefault(seenKey, 0);
  }

  /** Counts a valid inclusion list, returning false if the validator already had the maximum. */
  private boolean markSeen(final UInt64 slot, final SeenInclusionListKey seenKey) {
    final AtomicBoolean counted = new AtomicBoolean(false);
    seenInclusionListCounts
        .computeIfAbsent(slot, __ -> new ConcurrentHashMap<>())
        .compute(
            seenKey,
            (__, count) -> {
              final int currentCount = count == null ? 0 : count;
              if (currentCount >= MAX_VALID_INCLUSION_LISTS_PER_VALIDATOR) {
                return currentCount;
              }
              counted.set(true);
              return currentCount + 1;
            });
    return counted.get();
  }

  private record SeenInclusionListKey(Bytes32 dependentRoot, UInt64 validatorIndex) {}
}
