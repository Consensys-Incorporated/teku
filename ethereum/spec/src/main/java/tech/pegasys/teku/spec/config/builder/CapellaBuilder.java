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

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.config.SpecConfigCapellaImpl;

public class CapellaBuilder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigBellatrix, SpecConfigCapella> {

  private Integer maxBlsToExecutionChanges;
  private Integer maxWithdrawalsPerPayload;
  private Integer maxValidatorsPerWithdrawalSweep;

  CapellaBuilder() {}

  @Override
  public SpecConfigAndParent<SpecConfigCapella> build(
      final SpecConfigAndParent<SpecConfigBellatrix> specConfig) {
    return SpecConfigAndParent.of(
        new SpecConfigCapellaImpl(
            specConfig.specConfig(),
            maxBlsToExecutionChanges,
            maxWithdrawalsPerPayload,
            maxValidatorsPerWithdrawalSweep),
        specConfig);
  }

  public CapellaBuilder maxBlsToExecutionChanges(final Integer maxBlsToExecutionChanges) {
    this.maxBlsToExecutionChanges = maxBlsToExecutionChanges;
    return this;
  }

  public CapellaBuilder maxWithdrawalsPerPayload(final Integer maxWithdrawalsPerPayload) {
    this.maxWithdrawalsPerPayload = maxWithdrawalsPerPayload;
    return this;
  }

  public CapellaBuilder maxValidatorsPerWithdrawalsSweep(
      final Integer maxValidatorsPerWithdrawalSweep) {
    this.maxValidatorsPerWithdrawalSweep = maxValidatorsPerWithdrawalSweep;
    return this;
  }

  @Override
  public void validate() {
    defaultValuesIfRequired(this);
    validateConstants();
  }

  @Override
  public Map<String, Object> getValidationMap() {
    final Map<String, Object> constants = new HashMap<>();
    constants.put("maxBlsToExecutionChanges", maxBlsToExecutionChanges);
    constants.put("maxWithdrawalsPerPayload", maxWithdrawalsPerPayload);
    constants.put("maxValidatorsPerWithdrawalSweep", maxValidatorsPerWithdrawalSweep);

    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
