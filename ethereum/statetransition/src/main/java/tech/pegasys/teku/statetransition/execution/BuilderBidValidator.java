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

package tech.pegasys.teku.statetransition.execution;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.ProposerPreferences;
import tech.pegasys.teku.spec.datastructures.epbs.versions.gloas.SignedExecutionPayloadBid;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.BeaconStateAccessorsGloas;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.statetransition.validation.ExecutionPayloadBidGossipValidator;
import tech.pegasys.teku.storage.client.RecentChainData;

public class BuilderBidValidator {

  private static final Logger LOG = LogManager.getLogger();

  private final Spec spec;
  private final ProposerPreferencesManager proposerPreferencesManager;
  private final RecentChainData recentChainData;

  public BuilderBidValidator(
      final Spec spec,
      final ProposerPreferencesManager proposerPreferencesManager,
      final RecentChainData recentChainData) {
    this.spec = spec;
    this.proposerPreferencesManager = proposerPreferencesManager;
    this.recentChainData = recentChainData;
  }

  /**
   * Validates a bid coming from the Builder API
   *
   * <p><a
   * href="https://github.com/ethereum/builder-specs/blob/main/specs/gloas/validator.md#validating-a-signedexecutionpayloadbid">Validating
   * a SignedExecutionPayloadBid</a>
   *
   * @param signedBid the signed bid to validate
   * @param state the current beacon state
   * @return true if the bid is valid, false otherwise
   */
  public boolean validateBid(final SignedExecutionPayloadBid signedBid, final BeaconState state) {
    final ExecutionPayloadBid bid = signedBid.getMessage();
    final UInt64 slot = bid.getSlot();
    final SpecVersion specVersion = spec.atSlot(slot);

    final PredicatesGloas predicates = PredicatesGloas.required(specVersion.predicates());
    final BeaconStateAccessorsGloas beaconStateAccessors =
        BeaconStateAccessorsGloas.required(specVersion.beaconStateAccessors());
    final BeaconStateGloas stateGloas = BeaconStateGloas.required(state);

    if (!predicates.isActiveBuilder(state, bid.getBuilderIndex())) {
      LOG.warn("Bid rejected: builder {} is not active", bid.getBuilderIndex());
      return false;
    }

    if (!slot.equals(state.getSlot())) {
      LOG.warn("Bid rejected: bid slot {} does not match state slot {}", slot, state.getSlot());
      return false;
    }

    if (!bid.getParentBlockHash().equals(stateGloas.getLatestExecutionPayloadBid().getBlockHash())
        && !bid.getParentBlockHash().equals(stateGloas.getLatestBlockHash())) {
      LOG.warn("Bid rejected: parent block hash does not extend a known parent");
      return false;
    }

    if (!bid.getParentBlockRoot().equals(state.getLatestBlockHeader().hashTreeRoot())) {
      LOG.warn("Bid rejected: parent block root mismatch");
      return false;
    }

    if (!bid.getPrevRandao()
        .equals(
            beaconStateAccessors.getRandaoMix(
                state, beaconStateAccessors.getCurrentEpoch(state)))) {
      LOG.warn("Bid rejected: prev_randao mismatch");
      return false;
    }

    final Optional<ProposerPreferences> proposerPreferences =
        proposerPreferencesManager.getProposerPreferences(slot);

    if (proposerPreferences.isPresent()) {
      if (!bid.getFeeRecipient().equals(proposerPreferences.get().getFeeRecipient())) {
        LOG.warn("Bid rejected: fee recipient mismatch");
        return false;
      }
      final UInt64 parentGasLimit =
          recentChainData
              .getExecutionGasLimitForBlockRootAndHash(
                  bid.getParentBlockRoot(), bid.getParentBlockHash())
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          String.format(
                              "Parent gas limit for block root %s and hash %s is not available",
                              bid.getParentBlockRoot(), bid.getParentBlockHash())));
      if (!ExecutionPayloadBidGossipValidator.isGasLimitTargetCompatible(
          parentGasLimit, bid.getGasLimit(), proposerPreferences.get().getTargetGasLimit())) {
        LOG.warn("Bid rejected: gas limit {} is not compatible with target", bid.getGasLimit());
        return false;
      }
    }

    if (bid.getValue().isGreaterThan(UInt64.ZERO)
        && !beaconStateAccessors.canBuilderCoverBid(state, bid.getBuilderIndex(), bid.getValue())) {
      LOG.warn("Bid rejected: builder {} cannot cover bid value", bid.getBuilderIndex());
      return false;
    }

    if (!specVersion
        .operationSignatureVerifier()
        .verifyExecutionPayloadBidSignature(
            state, signedBid, specVersion.getConfig().getBLSSignatureVerifier())) {
      LOG.debug("Bid rejected: invalid signature");
      return false;
    }

    return true;
  }
}
