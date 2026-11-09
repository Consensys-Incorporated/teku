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

package tech.pegasys.teku.spec.config.builder;

import static tech.pegasys.teku.spec.config.SpecConfig.FAR_FUTURE_EPOCH;

import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class BaseForkBuilder {
  private UInt64 forkEpoch = FAR_FUTURE_EPOCH;

  public void setForkEpoch(final UInt64 epoch) {
    this.forkEpoch = epoch;
  }

  public UInt64 getForkEpoch() {
    return forkEpoch;
  }

  public void defaultValuesIfRequired(final ForkConfigBuilder<?, ?> builder) {
    if (getForkEpoch().equals(FAR_FUTURE_EPOCH)) {
      SpecBuilderUtil.fillMissingValuesWithZeros(builder);
    }
  }
}
