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

package tech.pegasys.teku.spec.generator;

import static com.google.common.base.Preconditions.checkArgument;
import static tech.pegasys.teku.spec.constants.PqConstants.VRF_TREE_DEPTH;

import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.crypto.Hash;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.BeaconStateEip8483;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.eip8483.BeaconStateSchemaEip8483;
import tech.pegasys.teku.spec.datastructures.state.versions.eip8483.ValidatorPqRegistrationSchema;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignature;
import tech.pegasys.teku.spec.datastructures.type.SszXmssSignatureSchema;

/**
 * Deterministic PQ identities for test validators, cheap enough to build chains with.
 *
 * <ul>
 *   <li>Each validator's XMSS public key is a hash of its index. Tests that import these chains run
 *       with XMSS verification disabled, as the spec's own tests do, so signatures are stubs that
 *       only carry their leaf index.
 *   <li>Each validator's VRF tree is the placeholder of the spec's own test keys (consensus-specs
 *       {@code pq} 4c296784, helpers/keys.py): a subtree of real leaves for the first 2^{@link
 *       #VRF_SUBTREE_DEPTH} slots, shared by every validator, then siblings of the validator's own
 *       up to its root. Its root and proofs take one subtree, 2^13 hashes, rather than 2^33. The
 *       proofs are real and pass process_randao for slots in the subtree.
 * </ul>
 */
public final class PqTestKeys {

  /** The placeholder VRF trees commit to secrets for the first 2^12 slots only. */
  public static final int VRF_SUBTREE_DEPTH = 12;

  // The shared subtree's levels, from its leaves up to its root
  private static final List<Bytes32[]> VRF_SUBTREE_LEVELS = vrfSubtreeLevels();

  private PqTestKeys() {}

  public static Bytes32 xmssPublicKey(final int validatorIndex) {
    return Hash.sha256(
        Bytes.concatenate(
            Bytes.wrap("xmss".getBytes(StandardCharsets.UTF_8)), index(validatorIndex)));
  }

  /** The VRF root registered for the validator: the root of its placeholder VRF tree. */
  public static Bytes32 vrfRoot(final int validatorIndex) {
    Bytes32 node = VRF_SUBTREE_LEVELS.getLast()[0];
    for (final Bytes32 sibling : vrfUpperBranch(validatorIndex)) {
      node = Hash.keccak256(Bytes.concatenate(node, sibling));
    }
    return node;
  }

  /**
   * The validator's VRF proof for {@code slot}, its RANDAO reveal: the secret for the slot, then
   * the Merkle branch of the slot's leaf.
   *
   * @throws IllegalArgumentException for a slot beyond the shared subtree
   */
  public static List<Bytes32> vrfProof(final int validatorIndex, final UInt64 slot) {
    checkArgument(
        slot.isLessThan(1L << VRF_SUBTREE_DEPTH),
        "Test VRF trees only commit to the first %s slots",
        1L << VRF_SUBTREE_DEPTH);
    final int leaf = slot.intValue();
    final List<Bytes32> proof = new ArrayList<>(VRF_TREE_DEPTH + 1);
    proof.add(vrfSecret(leaf));
    for (int height = 0; height < VRF_SUBTREE_DEPTH; height++) {
      proof.add(VRF_SUBTREE_LEVELS.get(height)[(leaf >>> height) ^ 1]);
    }
    proof.addAll(vrfUpperBranch(validatorIndex));
    return proof;
  }

  /** The EIP-8483 {@code pq_data}: the XMSS public key followed by the VRF root. */
  public static Bytes pqData(final int validatorIndex) {
    return Bytes.concatenate(xmssPublicKey(validatorIndex), vrfRoot(validatorIndex));
  }

  /** A stub XMSS signature at {@code leafIndex}: the 4-byte leaf index, then zeros. */
  public static SszXmssSignature stubSignature(final UInt64 leafIndex) {
    final Bytes leaf = Bytes.ofUnsignedInt(leafIndex.longValue(), ByteOrder.LITTLE_ENDIAN);
    return new SszXmssSignature(
        Bytes.concatenate(
            leaf, Bytes.wrap(new byte[SszXmssSignatureSchema.XMSS_SIGNATURE_SIZE - leaf.size()])));
  }

  /**
   * Registers every validator of an EIP-8483 state, so none is exited at the PQ fork. Returns other
   * states unchanged.
   */
  public static BeaconState registerAllValidators(final BeaconState state) {
    return state.toVersionEip8483().map(PqTestKeys::registerAll).orElse(state);
  }

  private static BeaconState registerAll(final BeaconStateEip8483 state) {
    final BeaconStateSchemaEip8483 stateSchema =
        BeaconStateSchemaEip8483.required(state.getBeaconStateSchema());
    final ValidatorPqRegistrationSchema registrationSchema = new ValidatorPqRegistrationSchema();
    return state.updatedEip8483(
        mutable ->
            mutable.setValidatorPqRegistrations(
                stateSchema
                    .getValidatorPqRegistrationsSchema()
                    .createFromElements(
                        IntStream.range(0, state.getValidators().size())
                            .mapToObj(
                                index ->
                                    registrationSchema.create(UInt64.valueOf(index), pqData(index)))
                            .toList())));
  }

  /** The secret for {@code slot} of every placeholder VRF tree. */
  private static Bytes32 vrfSecret(final long slot) {
    return Hash.keccak256(
        Bytes.concatenate(Bytes.wrap("vrf secret".getBytes(StandardCharsets.UTF_8)), index(slot)));
  }

  /** The subtree every placeholder VRF tree has on its left: each leaf commits to its secret. */
  private static List<Bytes32[]> vrfSubtreeLevels() {
    final List<Bytes32[]> levels = new ArrayList<>(VRF_SUBTREE_DEPTH + 1);
    Bytes32[] level = new Bytes32[1 << VRF_SUBTREE_DEPTH];
    for (int slot = 0; slot < level.length; slot++) {
      level[slot] =
          Hash.keccak256(
              Bytes.concatenate(
                  Bytes.wrap("VRF_LEAF".getBytes(StandardCharsets.UTF_8)),
                  vrfSecret(slot),
                  index(slot)));
    }
    levels.add(level);
    while (level.length > 1) {
      final Bytes32[] parents = new Bytes32[level.length / 2];
      for (int i = 0; i < parents.length; i++) {
        parents[i] = Hash.keccak256(Bytes.concatenate(level[2 * i], level[2 * i + 1]));
      }
      levels.add(parents);
      level = parents;
    }
    return levels;
  }

  /** The siblings above the shared subtree, which give each validator its own VRF root. */
  private static List<Bytes32> vrfUpperBranch(final int validatorIndex) {
    final List<Bytes32> branch = new ArrayList<>(VRF_TREE_DEPTH - VRF_SUBTREE_DEPTH);
    for (int height = VRF_SUBTREE_DEPTH; height < VRF_TREE_DEPTH; height++) {
      branch.add(
          Hash.keccak256(
              Bytes.concatenate(
                  Bytes.wrap("vrf node".getBytes(StandardCharsets.UTF_8)),
                  index(validatorIndex),
                  index(height))));
    }
    return branch;
  }

  private static Bytes index(final long value) {
    return Bytes.ofUnsignedLong(value, ByteOrder.LITTLE_ENDIAN);
  }
}
