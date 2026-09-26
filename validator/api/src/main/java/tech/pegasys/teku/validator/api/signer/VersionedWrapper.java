/*
 * Copyright Consensys Software Inc., 2026
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

package tech.pegasys.teku.validator.api.signer;

import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.enumOf;

import tech.pegasys.teku.infrastructure.json.types.SerializableTypeDefinition;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.spec.SpecMilestone;

/**
 * Fork-versioned payload as expected by the remote signing API for Gloas message types, i.e. {@code
 * {"version": "GLOAS", "data": {...}}}.
 */
public record VersionedWrapper<T extends SszData>(SpecMilestone version, T data) {

  @SuppressWarnings("unchecked")
  public SerializableTypeDefinition<VersionedWrapper<T>> getJsonTypeDefinition() {
    return SerializableTypeDefinition.<VersionedWrapper<T>>object()
        .withField("version", enumOf(SpecMilestone.class), VersionedWrapper::version)
        .withField(
            "data",
            (SerializableTypeDefinition<T>) data.getSchema().getJsonTypeDefinition(),
            VersionedWrapper::data)
        .build();
  }
}
