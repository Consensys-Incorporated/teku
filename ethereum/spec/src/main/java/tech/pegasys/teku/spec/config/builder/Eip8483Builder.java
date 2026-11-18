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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import tech.pegasys.teku.spec.config.SpecConfigAndParent;
import tech.pegasys.teku.spec.config.SpecConfigEip8483;
import tech.pegasys.teku.spec.config.SpecConfigEip8483Impl;
import tech.pegasys.teku.spec.config.SpecConfigGloas;

public class Eip8483Builder extends BaseForkBuilder
    implements ForkConfigBuilder<SpecConfigGloas, SpecConfigEip8483> {

  // eip8483 preset
  private Integer maxValidatorPqRegistrationRequestsPerPayload;
  private Integer maxBuilderPqRegistrationRequestsPerPayload;

  Eip8483Builder() {}

  @Override
  public SpecConfigAndParent<SpecConfigEip8483> build(
      final SpecConfigAndParent<SpecConfigGloas> specConfigAndParent) {
    return SpecConfigAndParent.of(
        new SpecConfigEip8483Impl(
            specConfigAndParent.specConfig(),
            maxValidatorPqRegistrationRequestsPerPayload,
            maxBuilderPqRegistrationRequestsPerPayload),
        specConfigAndParent);
  }

  public Eip8483Builder maxValidatorPqRegistrationRequestsPerPayload(
      final Integer maxValidatorPqRegistrationRequestsPerPayload) {
    checkNotNull(maxValidatorPqRegistrationRequestsPerPayload);
    this.maxValidatorPqRegistrationRequestsPerPayload =
        maxValidatorPqRegistrationRequestsPerPayload;
    return this;
  }

  public Eip8483Builder maxBuilderPqRegistrationRequestsPerPayload(
      final Integer maxBuilderPqRegistrationRequestsPerPayload) {
    checkNotNull(maxBuilderPqRegistrationRequestsPerPayload);
    this.maxBuilderPqRegistrationRequestsPerPayload = maxBuilderPqRegistrationRequestsPerPayload;
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
    constants.put(
        "maxValidatorPqRegistrationRequestsPerPayload",
        maxValidatorPqRegistrationRequestsPerPayload);
    constants.put(
        "maxBuilderPqRegistrationRequestsPerPayload", maxBuilderPqRegistrationRequestsPerPayload);
    return constants;
  }

  @Override
  public void addOverridableItemsToRawConfig(final BiConsumer<String, Object> rawConfig) {}
}
