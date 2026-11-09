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

package tech.pegasys.teku.spec.datastructures.execution;

// Metric - fallback "reason" label values
public enum FallbackReason {
  NOT_NEEDED("not_needed"),
  VALIDATOR_NOT_REGISTERED("validator_not_registered"),
  TRANSITION_NOT_FINALIZED("transition_not_finalized"),
  CIRCUIT_BREAKER_ENGAGED("circuit_breaker_engaged"),
  BUILDER_NOT_AVAILABLE("builder_not_available"),
  BUILDER_NOT_CONFIGURED("builder_not_configured"),
  BUILDER_HEADER_NOT_AVAILABLE("builder_header_not_available"),
  LOCAL_BLOCK_VALUE_WON("local_block_value_won"),
  SHOULD_OVERRIDE_BUILDER_FLAG_IS_TRUE("should_override_builder_flag_is_true"),
  BUILDER_ERROR("builder_error"),
  NONE("");

  private final String displayName;

  FallbackReason(final String displayName) {
    this.displayName = displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
