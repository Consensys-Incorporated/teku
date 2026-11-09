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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;

interface ForkConfigBuilder<ParentType extends SpecConfig, ForkType extends ParentType> {

  SpecConfigAndParent<ForkType> build(SpecConfigAndParent<ParentType> specConfigAndParent);

  void setForkEpoch(UInt64 epoch);

  void validate();

  Map<String, Object> getValidationMap();

  default void validateConstants() {
    final List<Optional<String>> maybeErrors = new ArrayList<>();
    final Map<String, Object> constants = getValidationMap();

    constants.forEach((k, v) -> maybeErrors.add(SpecBuilderUtil.validateConstant(k, v)));

    final List<String> fieldsFailingValidation =
        maybeErrors.stream().filter(Optional::isPresent).map(Optional::get).toList();

    if (!fieldsFailingValidation.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "The specified network configuration had missing or invalid values for constants %s",
              String.join(", ", fieldsFailingValidation)));
    }
  }

  void addOverridableItemsToRawConfig(BiConsumer<String, Object> rawConfig);
}
