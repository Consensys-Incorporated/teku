/*
 * Copyright (c) 2026 Consensys Incorporated. All rights reserved.
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

package tech.pegasys.teku.spec.logic.versions.heze.util;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntList;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigHeze;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.InclusionList;
import tech.pegasys.teku.spec.datastructures.execution.versions.heze.SignedInclusionList;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.util.AsyncBLSSignatureVerifier;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.MiscHelpersGloas;
import tech.pegasys.teku.spec.logic.versions.heze.helpers.BeaconStateAccessorsHeze;

public class InclusionListUtil {

  private final SpecConfigHeze specConfig;
  private final BeaconStateAccessorsHeze beaconStateAccessors;
  private final MiscHelpersGloas miscHelpers;

  public InclusionListUtil(
      final SpecConfigHeze specConfig,
      final BeaconStateAccessorsHeze beaconStateAccessors,
      final MiscHelpersGloas miscHelpers) {
    this.specConfig = specConfig;
    this.beaconStateAccessors = beaconStateAccessors;
    this.miscHelpers = miscHelpers;
  }

  public IntList getInclusionListCommittee(final BeaconState state, final UInt64 slot) {
    return beaconStateAccessors.getInclusionListCommittee(state, slot);
  }

  public boolean validatorIndexWithinCommittee(
      final BeaconState state, final UInt64 slot, final UInt64 validatorIndex) {
    return getInclusionListCommittee(state, slot).contains(validatorIndex.intValue());
  }

  /**
   * Verifies that {@code signedInclusionList} has a valid signature per spec: <code>
   * def is_valid_inclusion_list_signature(state, signed_inclusion_list):
   *     message = signed_inclusion_list.message
   *     index = message.validator_index
   *     pubkey = state.validators[index].pubkey
   *     domain = compute_domain(
   *         DOMAIN_INCLUSION_LIST_COMMITTEE,
   *         compute_fork_version(compute_epoch_at_slot(message.slot)),
   *         state.genesis_validators_root,
   *     )
   *     signing_root = compute_signing_root(message, domain)
   *     return bls.Verify(pubkey, signing_root, signed_inclusion_list.signature)
   * </code>
   */
  public SafeFuture<Boolean> isValidInclusionListSignature(
      final Fork fork,
      final BeaconState state,
      final SignedInclusionList signedInclusionList,
      final AsyncBLSSignatureVerifier signatureVerifier) {
    final InclusionList message = signedInclusionList.getMessage();
    final UInt64 index = message.getValidatorIndex();
    final BLSPublicKey pubkey = state.getValidators().get(index.intValue()).getPublicKey();
    final Bytes32 domain =
        beaconStateAccessors.getDomain(
            Domain.INCLUSION_LIST_COMMITTEE,
            miscHelpers.computeEpochAtSlot(message.getSlot()),
            fork,
            state.getGenesisValidatorsRoot());
    final Bytes signingRoot = miscHelpers.computeSigningRoot(message, domain);
    final BLSSignature signature = signedInclusionList.getSignature();
    return signatureVerifier.verify(pubkey, signingRoot, signature);
  }

  public Int2ObjectMap<UInt64> getValidatorIndexToSlotAssignmentMap(
      final BeaconState state, final UInt64 epoch) {
    final Int2ObjectMap<UInt64> assignmentMap = new Int2ObjectOpenHashMap<>();
    final int slotsPerEpoch = specConfig.getSlotsPerEpoch();
    final UInt64 startSlot = miscHelpers.computeStartSlotAtEpoch(epoch);
    for (int slotOffset = 0; slotOffset < slotsPerEpoch; slotOffset++) {
      final UInt64 slot = startSlot.plus(slotOffset);
      final IntList committee = beaconStateAccessors.getInclusionListCommittee(state, slot);
      committee.forEach(idx -> assignmentMap.put(idx, slot));
    }
    return assignmentMap;
  }
}
