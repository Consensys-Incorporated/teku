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

package tech.pegasys.teku.validator.api;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.STRING_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record SubmitDataError(UInt64 index, String message) {

  public static DeserializableTypeDefinition<SubmitDataError> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(SubmitDataError.class, SubmitDataErrorBuilder.class)
        .name("SubmitDataError")
        .initializer(SubmitDataErrorBuilder::new)
        .finisher(SubmitDataErrorBuilder::build)
        .withField("index", UINT64_TYPE, SubmitDataError::index, SubmitDataErrorBuilder::index)
        .withField(
            "message", STRING_TYPE, SubmitDataError::message, SubmitDataErrorBuilder::message)
        .build();
  }

  @Override
  public String toString() {
    final StringBuilder sb = new StringBuilder("SubmitDataError{");
    sb.append("index=").append(index);
    sb.append(", message='").append(message).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
