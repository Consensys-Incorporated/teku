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

package tech.pegasys.teku.ethereum.json.types.beacon;

import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.FINALIZED;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.List;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaData;
import tech.pegasys.teku.spec.datastructures.metadata.ObjectAndMetaDataBuilder;
import tech.pegasys.teku.spec.datastructures.state.Validator;

public class StateValidatorDataBuilder {
  private static final DeserializableTypeDefinition<ValidatorStatus> STATUS_TYPE =
      DeserializableTypeDefinition.enumOf(ValidatorStatus.class);

  public static final DeserializableTypeDefinition<StateValidatorData> STATE_VALIDATOR_DATA_TYPE =
      DeserializableTypeDefinition.object(StateValidatorData.class, StateValidatorDataBuilder.class)
          .initializer(StateValidatorDataBuilder::new)
          .finisher(StateValidatorDataBuilder::build)
          .withField(
              "index", UINT64_TYPE, StateValidatorData::getIndex, StateValidatorDataBuilder::index)
          .withField(
              "balance",
              UINT64_TYPE,
              StateValidatorData::getBalance,
              StateValidatorDataBuilder::balance)
          .withField(
              "status",
              STATUS_TYPE,
              StateValidatorData::getStatus,
              StateValidatorDataBuilder::status)
          .withField(
              "validator",
              Validator.SSZ_SCHEMA.getJsonTypeDefinition(),
              StateValidatorData::getValidator,
              StateValidatorDataBuilder::validator)
          .build();

  public static final DeserializableTypeDefinition<ObjectAndMetaData<List<StateValidatorData>>>
      STATE_VALIDATORS_RESPONSE_TYPE =
          DeserializableTypeDefinition
              .<ObjectAndMetaData<List<StateValidatorData>>,
                  ObjectAndMetaDataBuilder<List<StateValidatorData>>>
                  object()
              .name("GetStateValidatorsResponse")
              .initializer(ObjectAndMetaDataBuilder::new)
              .finisher(ObjectAndMetaDataBuilder::build)
              .withField(
                  EXECUTION_OPTIMISTIC,
                  BOOLEAN_TYPE,
                  ObjectAndMetaData::isExecutionOptimistic,
                  ObjectAndMetaDataBuilder::executionOptimistic)
              .withField(
                  FINALIZED,
                  BOOLEAN_TYPE,
                  ObjectAndMetaData::isFinalized,
                  ObjectAndMetaDataBuilder::finalized)
              .withField(
                  "data",
                  listOf(STATE_VALIDATOR_DATA_TYPE),
                  ObjectAndMetaData::getData,
                  ObjectAndMetaDataBuilder::data)
              .build();

  private UInt64 index;
  private UInt64 balance;
  private ValidatorStatus status;
  private Validator validator;

  public StateValidatorDataBuilder index(final UInt64 index) {
    this.index = index;
    return this;
  }

  public StateValidatorDataBuilder balance(final UInt64 balance) {
    this.balance = balance;
    return this;
  }

  public StateValidatorDataBuilder status(final ValidatorStatus status) {
    this.status = status;
    return this;
  }

  public StateValidatorDataBuilder validator(final Validator validator) {
    this.validator = validator;
    return this;
  }

  public StateValidatorData build() {
    return new StateValidatorData(index, balance, status, validator);
  }
}
