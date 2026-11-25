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

package tech.pegasys.teku.spec.logic.versions.pq.helpers;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.constants.PqConstants.VRF_TREE_DEPTH;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_ATTESTATION_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_BUILDER_BID_LEAVES;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_DEPOSIT_PREFIX;
import static tech.pegasys.teku.spec.constants.PqConstants.XMSS_RESERVED_LEAVES;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.bytes.Bytes48;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.gloas.BeaconStateGloas;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.BuilderPreregistration;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPreregistration;
import tech.pegasys.teku.spec.logic.versions.gloas.helpers.PredicatesGloas;
import tech.pegasys.teku.spec.signatures.xmss.VrfTree;

public class PredicatesPq extends PredicatesGloas {

  public PredicatesPq(final SpecConfig specConfig) {
    super(specConfig);
  }

  /**
   * is_legacy_pubkey: a request made before the upgrade names a compressed BLS public key; one made
   * after it names 16 zero bytes followed by an XMSS public key.
   */
  public boolean isLegacyPubkey(final Bytes48 pubkey) {
    return !pubkey.slice(0, XMSS_DEPOSIT_PREFIX.size()).equals(XMSS_DEPOSIT_PREFIX);
  }

  /** is_builder_bid_leaf_index */
  public boolean isBuilderBidLeafIndex(final UInt64 leafIndex) {
    final UInt64 start = XMSS_RESERVED_LEAVES.plus(XMSS_ATTESTATION_LEAVES);
    return leafIndex.isGreaterThanOrEqualTo(start)
        && leafIndex.isLessThan(start.plus(XMSS_BUILDER_BID_LEAVES));
  }

  /**
   * is_valid_vrf_proof: {@code proof} reveals the secret that the leaf at index {@code slot} of the
   * tree under the validator's VRF root, a Keccak-256 Merkle tree, commits to. Only the low {@code
   * VRF_TREE_DEPTH} bits of the slot select the leaf.
   */
  public boolean isValidVrfProof(
      final BeaconState state,
      final int validatorIndex,
      final UInt64 slot,
      final List<Bytes32> proof) {
    checkArgument(
        proof.size() == VRF_TREE_DEPTH + 1, "A VRF proof has %s elements", VRF_TREE_DEPTH + 1);
    final Bytes32 vrfRoot = ((ValidatorPq) state.getValidators().get(validatorIndex)).getVrfRoot();
    return VrfTree.computeRootFromProof(proof, slot).equals(vrfRoot);
  }

  /**
   * is_active_validator_preregistration: activity is evaluated against the slot of the outstanding
   * parent payload, whose execution requests are the ones being applied.
   */
  public boolean isActiveValidatorPreregistration(
      final BeaconState state, final ValidatorPreregistration preregistration) {
    return getParentPayloadSlot(state).isLessThan(preregistration.getExpirySlot());
  }

  /** is_active_builder_preregistration */
  public boolean isActiveBuilderPreregistration(
      final BeaconState state, final BuilderPreregistration preregistration) {
    return getParentPayloadSlot(state).isLessThan(preregistration.getExpirySlot());
  }

  private static UInt64 getParentPayloadSlot(final BeaconState state) {
    return BeaconStateGloas.required(state).getLatestExecutionPayloadBid().getSlot();
  }
}
