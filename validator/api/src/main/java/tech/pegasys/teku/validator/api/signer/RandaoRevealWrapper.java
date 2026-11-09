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

package tech.pegasys.teku.validator.api.signer;

import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.UINT64_TYPE;

import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public record RandaoRevealWrapper(UInt64 epoch) {

  public static DeserializableTypeDefinition<RandaoRevealWrapper> getJsonTypeDefinition() {
    return DeserializableTypeDefinition.object(RandaoRevealWrapper.class, Builder.class)
        .initializer(Builder::new)
        .finisher(Builder::build)
        .withField("epoch", UINT64_TYPE, RandaoRevealWrapper::epoch, Builder::epoch)
        .build();
  }

  static class Builder {
    private UInt64 epoch;

    public Builder epoch(final UInt64 epoch) {
      this.epoch = epoch;
      return this;
    }

    public RandaoRevealWrapper build() {
      return new RandaoRevealWrapper(epoch);
    }
  }
}
