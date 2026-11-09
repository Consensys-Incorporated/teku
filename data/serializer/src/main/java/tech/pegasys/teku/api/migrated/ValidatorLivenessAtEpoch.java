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

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record ValidatorLivenessAtEpoch(UInt64 index, boolean isLive) {

  public static DeserializableTypeDefinition<ValidatorLivenessAtEpoch> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(
            ValidatorLivenessAtEpoch.class, ValidatorLivenessAtEpoch.Builder.class)
        .initializer(Builder::new)
        .finisher(Builder::build)
        .withField("index", UINT64_TYPE, ValidatorLivenessAtEpoch::index, Builder::index)
        .withField("is_live", BOOLEAN_TYPE, ValidatorLivenessAtEpoch::isLive, Builder::isLive)
        .build();
  }

  @Override
  public String toString() {
    return "ValidatorLivenessAtEpoch{" + "index=" + index + ", isLive=" + isLive + '}';
  }

  public static class Builder {
    private UInt64 index;
    private boolean isLive;

    public Builder() {}

    public Builder isLive(final boolean isLive) {
      this.isLive = isLive;
      return this;
    }

    public Builder index(final UInt64 index) {
      this.index = index;
      return this;
    }

    public ValidatorLivenessAtEpoch build() {
      return new ValidatorLivenessAtEpoch(index, isLive);
    }
  }
}
