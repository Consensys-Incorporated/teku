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

public interface SpecConfigEip8483 extends SpecConfigGloas {

  static SpecConfigEip8483 required(final SpecConfig specConfig) {
    return specConfig
        .toVersionEip8483()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected EIP-8483 spec config but got: "
                        + specConfig.getClass().getSimpleName()));
  }

  int getMaxValidatorPqRegistrationRequestsPerPayload();

  int getMaxBuilderPqRegistrationRequestsPerPayload();

  @Override
  Optional<SpecConfigEip8483> toVersionEip8483();
}
