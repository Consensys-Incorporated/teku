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

package tech.pegasys.teku.validator.client;

import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.validator.api.InteropConfig;
import tech.pegasys.teku.validator.api.ValidatorConfig;
import tech.pegasys.teku.validator.client.restapi.ValidatorRestApiConfig;

public class ValidatorClientConfiguration {
  private final ValidatorConfig validatorConfig;
  private final ValidatorRestApiConfig validatorRestApiConfig;
  private final InteropConfig interopConfig;
  private final Spec spec;

  public ValidatorClientConfiguration(
      final ValidatorConfig validatorConfig,
      final InteropConfig interopConfig,
      final ValidatorRestApiConfig validatorRestApiConfig,
      final Spec spec) {
    this.validatorConfig = validatorConfig;
    this.interopConfig = interopConfig;
    this.validatorRestApiConfig = validatorRestApiConfig;
    this.spec = spec;
  }

  public ValidatorConfig getValidatorConfig() {
    return validatorConfig;
  }

  public InteropConfig getInteropConfig() {
    return interopConfig;
  }

  public ValidatorRestApiConfig getValidatorRestApiConfig() {
    return validatorRestApiConfig;
  }

  public Spec getSpec() {
    return spec;
  }
}
