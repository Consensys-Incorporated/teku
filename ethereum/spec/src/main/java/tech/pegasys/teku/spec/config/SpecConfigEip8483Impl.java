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

import java.util.Objects;
import java.util.Optional;
import tech.pegasys.teku.spec.SpecMilestone;

public class SpecConfigEip8483Impl extends DelegatingSpecConfigGloas implements SpecConfigEip8483 {

  private final int maxValidatorPqRegistrationRequestsPerPayload;
  private final int maxBuilderPqRegistrationRequestsPerPayload;

  public SpecConfigEip8483Impl(
      final SpecConfigGloas specConfig,
      final int maxValidatorPqRegistrationRequestsPerPayload,
      final int maxBuilderPqRegistrationRequestsPerPayload) {
    super(specConfig);
    this.maxValidatorPqRegistrationRequestsPerPayload =
        maxValidatorPqRegistrationRequestsPerPayload;
    this.maxBuilderPqRegistrationRequestsPerPayload = maxBuilderPqRegistrationRequestsPerPayload;
  }

  @Override
  public SpecMilestone getMilestone() {
    return SpecMilestone.EIP8483;
  }

  @Override
  public int getMaxValidatorPqRegistrationRequestsPerPayload() {
    return maxValidatorPqRegistrationRequestsPerPayload;
  }

  @Override
  public int getMaxBuilderPqRegistrationRequestsPerPayload() {
    return maxBuilderPqRegistrationRequestsPerPayload;
  }

  @Override
  public Optional<SpecConfigEip8483> toVersionEip8483() {
    return Optional.of(this);
  }

  @Override
  public boolean equals(final Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final SpecConfigEip8483Impl that = (SpecConfigEip8483Impl) o;
    return maxValidatorPqRegistrationRequestsPerPayload
            == that.maxValidatorPqRegistrationRequestsPerPayload
        && maxBuilderPqRegistrationRequestsPerPayload
            == that.maxBuilderPqRegistrationRequestsPerPayload;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        super.hashCode(),
        maxValidatorPqRegistrationRequestsPerPayload,
        maxBuilderPqRegistrationRequestsPerPayload);
  }
}
