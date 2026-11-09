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

import java.util.Optional;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container3;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema3;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.type.SszPublicKey;

public class StateValidatorIdentity
    extends Container3<StateValidatorIdentity, SszUInt64, SszPublicKey, SszUInt64> {
  public static final StateValidatorIdentitySchema SSZ_SCHEMA = new StateValidatorIdentitySchema();

  @SuppressWarnings("unchecked")
  public static final SszListSchema<StateValidatorIdentity, SszList<StateValidatorIdentity>>
      SSZ_LIST_SCHEMA =
          (SszListSchema<StateValidatorIdentity, SszList<StateValidatorIdentity>>)
              SszListSchema.create(StateValidatorIdentity.SSZ_SCHEMA, Integer.MAX_VALUE);

  protected StateValidatorIdentity(
      final ContainerSchema3<StateValidatorIdentity, SszUInt64, SszPublicKey, SszUInt64> schema,
      final SszUInt64 arg0,
      final SszPublicKey arg1,
      final SszUInt64 arg2) {
    super(schema, arg0, arg1, arg2);
  }

  protected StateValidatorIdentity(
      final ContainerSchema3<StateValidatorIdentity, SszUInt64, SszPublicKey, SszUInt64> schema,
      final TreeNode node) {
    super(schema, node);
  }

  public static StateValidatorIdentity create(
      final UInt64 index, final BLSPublicKey publicKey, final UInt64 activationEpoch) {
    return new StateValidatorIdentity(
        SSZ_SCHEMA,
        SszUInt64.of(index),
        new SszPublicKey(publicKey),
        SszUInt64.of(activationEpoch));
  }

  public UInt64 getActivationEpoch() {
    return getField2().get();
  }

  public BLSPublicKey getPublicKey() {
    return getField1().getBLSPublicKey();
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public static Optional<StateValidatorIdentity> fromState(
      final BeaconState state, final Integer index) {
    if (index >= state.getValidators().size()) {
      return Optional.empty();
    }
    final Validator validator = state.getValidators().get(index);
    return Optional.of(
        new StateValidatorIdentity(
            SSZ_SCHEMA,
            SszUInt64.of(UInt64.valueOf(index)),
            new SszPublicKey(validator.getPublicKey()),
            SszUInt64.of(validator.getActivationEpoch())));
  }
}
