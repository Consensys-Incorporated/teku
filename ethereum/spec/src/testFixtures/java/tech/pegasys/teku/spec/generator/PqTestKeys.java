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
 *   <li>Each validator's VRF tree has identical leaves, so its root and proofs take {@code
 *       VRF_TREE_DEPTH} hashes rather than 2^32: every node at a height is the same. The proofs are
 *       real and pass process_randao.
 * </ul>
 */
public final class PqTestKeys {

  private PqTestKeys() {}

  public static Bytes32 xmssPublicKey(final int validatorIndex) {
    return Hash.sha256(
        Bytes.concatenate(
            Bytes.wrap("xmss".getBytes(StandardCharsets.UTF_8)), index(validatorIndex)));
  }

  /** The VRF root registered for the validator: the root of a tree whose leaves are all equal. */
  public static Bytes32 vrfRoot(final int validatorIndex) {
    return vrfTreeLevels(validatorIndex).getLast();
  }

  /** The validator's VRF proof for {@code slot}, its RANDAO reveal: the leaf, then its branch. */
  public static List<Bytes32> vrfProof(final int validatorIndex, final UInt64 slot) {
    // Every leaf is equal, so the proof is the same for every slot: the leaf, then the sibling at
    // each height, which is the node at that height
    final List<Bytes32> levels = vrfTreeLevels(validatorIndex);
    final List<Bytes32> proof = new ArrayList<>(VRF_TREE_DEPTH + 1);
    proof.add(levels.getFirst());
    proof.addAll(levels.subList(0, VRF_TREE_DEPTH));
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

  /** The leaf, then the node at each height up to the root. */
  private static List<Bytes32> vrfTreeLevels(final int validatorIndex) {
    final List<Bytes32> levels = new ArrayList<>(VRF_TREE_DEPTH + 1);
    Bytes32 node = Hash.sha256(index(validatorIndex));
    levels.add(node);
    for (int height = 0; height < VRF_TREE_DEPTH; height++) {
      node = Hash.keccak256(Bytes.concatenate(node, node));
      levels.add(node);
    }
    return levels;
  }

  private static Bytes index(final int validatorIndex) {
    return Bytes.ofUnsignedLong(validatorIndex, ByteOrder.LITTLE_ENDIAN);
  }
}
