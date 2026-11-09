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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;

import java.util.List;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class StateValidatorRequestBodyType {

  public static final DeserializableTypeDefinition<StateValidatorRequestBodyType>
      STATE_VALIDATOR_REQUEST_TYPE =
          DeserializableTypeDefinition.object(StateValidatorRequestBodyType.class)
              .name("PostStateValidatorsRequestBody")
              .initializer(StateValidatorRequestBodyType::new)
              .withOptionalField(
                  "ids",
                  DeserializableTypeDefinition.listOf(STRING_TYPE),
                  StateValidatorRequestBodyType::getMaybeIds,
                  StateValidatorRequestBodyType::setIds)
              .withOptionalField(
                  "statuses",
                  DeserializableTypeDefinition.listOf(STRING_TYPE),
                  StateValidatorRequestBodyType::getMaybeStringStatuses,
                  StateValidatorRequestBodyType::setStatuses)
              .build();

  private List<String> ids = List.of();
  private List<StatusParameter> statuses = List.of();

  public StateValidatorRequestBodyType() {}

  public StateValidatorRequestBodyType(
      final List<String> ids, final List<StatusParameter> statuses) {
    this.ids = ids;
    this.statuses = statuses;
  }

  public List<String> getIds() {
    return ids;
  }

  public Optional<List<String>> getMaybeIds() {
    return ids.isEmpty() ? Optional.empty() : Optional.of(ids);
  }

  public void setIds(final Optional<List<String>> ids) {
    ids.ifPresent(i -> this.ids = i);
  }

  public List<StatusParameter> getStatuses() {
    return statuses;
  }

  public Optional<List<String>> getMaybeStringStatuses() {
    return statuses.isEmpty()
        ? Optional.empty()
        : Optional.of(statuses.stream().map(Enum::name).toList());
  }

  public void setStatuses(final Optional<List<String>> statuses) {
    statuses.ifPresent(s -> this.statuses = s.stream().map(StatusParameter::parse).toList());
  }
}
