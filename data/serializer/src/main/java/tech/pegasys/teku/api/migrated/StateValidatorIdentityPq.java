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

package tech.pegasys.teku.api.migrated;

import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.BYTES32_SCHEMA;
import static tech.pegasys.teku.infrastructure.ssz.schema.SszPrimitiveSchemas.UINT64_SCHEMA;

import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.versions.pq.ValidatorPq;

/** A PQ validator's identity: like {@link StateValidatorIdentity}, with a 32-byte XMSS key. */
public class StateValidatorIdentityPq
    extends Container3<StateValidatorIdentityPq, SszUInt64, SszBytes32, SszUInt64> {

  public static class StateValidatorIdentityPqSchema
      extends ContainerSchema3<StateValidatorIdentityPq, SszUInt64, SszBytes32, SszUInt64> {

    private StateValidatorIdentityPqSchema() {
      super(
          "StateValidatorIdentityPq",
          namedSchema("index", UINT64_SCHEMA),
          namedSchema("pubkey", BYTES32_SCHEMA),
          namedSchema("activation_epoch", UINT64_SCHEMA));
    }

    @Override
    public StateValidatorIdentityPq createFromBackingNode(final TreeNode node) {
      return new StateValidatorIdentityPq(this, node);
    }
  }

  public static final StateValidatorIdentityPqSchema SSZ_SCHEMA =
      new StateValidatorIdentityPqSchema();

  @SuppressWarnings("unchecked")
  public static final SszListSchema<StateValidatorIdentityPq, SszList<StateValidatorIdentityPq>>
      SSZ_LIST_SCHEMA =
          (SszListSchema<StateValidatorIdentityPq, SszList<StateValidatorIdentityPq>>)
              SszListSchema.create(SSZ_SCHEMA, Integer.MAX_VALUE);

  private StateValidatorIdentityPq(
      final StateValidatorIdentityPqSchema schema, final TreeNode node) {
    super(schema, node);
  }

  private StateValidatorIdentityPq(
      final UInt64 index, final Bytes32 xmssPublicKey, final UInt64 activationEpoch) {
    super(
        SSZ_SCHEMA,
        SszUInt64.of(index),
        SszBytes32.of(xmssPublicKey),
        SszUInt64.of(activationEpoch));
  }

  public static StateValidatorIdentityPq create(
      final UInt64 index, final Bytes32 xmssPublicKey, final UInt64 activationEpoch) {
    return new StateValidatorIdentityPq(index, xmssPublicKey, activationEpoch);
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public Bytes32 getXmssPublicKey() {
    return getField1().get();
  }

  public UInt64 getActivationEpoch() {
    return getField2().get();
  }

  public static Optional<StateValidatorIdentityPq> fromState(
      final BeaconState state, final Integer index) {
    if (index >= state.getValidators().size()) {
      return Optional.empty();
    }
    final ValidatorPq validator = (ValidatorPq) state.getValidators().get(index);
    return Optional.of(
        new StateValidatorIdentityPq(
            UInt64.valueOf(index), validator.getXmssPublicKey(), validator.getActivationEpoch()));
  }
}
