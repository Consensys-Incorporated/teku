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

package tech.pegasys.teku.spec.schemas.api;

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.ssz.schema.SszListSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.schemas.ApiSchemas;

public class StateValidatorBalanceData
    extends Container2<StateValidatorBalanceData, SszUInt64, SszUInt64> {
  public static final StateValidatorBalanceDataSchema SSZ_SCHEMA =
      new StateValidatorBalanceDataSchema();

  @SuppressWarnings("unchecked")
  public static final SszListSchema<StateValidatorBalanceData, SszList<StateValidatorBalanceData>>
      SSZ_LIST_SCHEMA =
          (SszListSchema<StateValidatorBalanceData, SszList<StateValidatorBalanceData>>)
              SszListSchema.create(SSZ_SCHEMA, ApiSchemas.MAX_VALIDATOR_REGISTRATIONS_SIZE);

  public StateValidatorBalanceData(final UInt64 index, final UInt64 balance) {
    this(SSZ_SCHEMA, SszUInt64.of(index), SszUInt64.of(balance));
  }

  protected StateValidatorBalanceData(
      final ContainerSchema2<StateValidatorBalanceData, SszUInt64, SszUInt64> schema,
      final SszUInt64 index,
      final SszUInt64 balance) {
    super(schema, index, balance);
  }

  protected StateValidatorBalanceData(
      final ContainerSchema2<StateValidatorBalanceData, SszUInt64, SszUInt64> schema,
      final TreeNode node) {
    super(schema, node);
  }

  public static Optional<StateValidatorBalanceData> fromState(
      final BeaconState state, final Integer index) {
    if (index >= state.getValidators().size()) {
      return Optional.empty();
    }
    return Optional.of(
        new StateValidatorBalanceData(
            UInt64.valueOf(index), state.getBalances().getElement(index)));
  }

  public UInt64 getIndex() {
    return getField0().get();
  }

  public UInt64 getBalance() {
    return getField1().get();
  }

  public static SerializableTypeDefinition<StateValidatorBalanceData> getJsonTypeDefinition() {
    return SSZ_SCHEMA.getJsonTypeDefinition();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    StateValidatorBalanceData that = (StateValidatorBalanceData) o;
    return Objects.equals(getIndex(), that.getIndex())
        && Objects.equals(getBalance(), that.getBalance());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getIndex(), getBalance());
  }
}
