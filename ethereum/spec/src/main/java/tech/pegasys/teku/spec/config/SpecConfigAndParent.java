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

package tech.pegasys.teku.spec.config;

import java.util.Optional;
import tech.pegasys.teku.spec.SpecMilestone;

public record SpecConfigAndParent<TConfig extends SpecConfig>(
    TConfig specConfig, Optional<SpecConfigAndParent<? extends SpecConfig>> parentSpecConfig) {

  public static <TConfig extends TParentConfig, TParentConfig extends SpecConfig>
      SpecConfigAndParent<TConfig> of(
          final TConfig spec, final SpecConfigAndParent<TParentConfig> parentSpec) {
    return new SpecConfigAndParent<>(spec, Optional.of(parentSpec));
  }

  public static <TConfig extends SpecConfig> SpecConfigAndParent<TConfig> of(final TConfig spec) {
    return new SpecConfigAndParent<>(spec, Optional.empty());
  }

  public SpecConfig forMilestone(final SpecMilestone milestone) {
    if (specConfig.getMilestone() == milestone) {
      return specConfig;
    }
    if (parentSpecConfig.isEmpty()) {
      throw new IllegalArgumentException("No config available for milestone " + milestone);
    }
    return parentSpecConfig.get().forMilestone(milestone);
  }
}
