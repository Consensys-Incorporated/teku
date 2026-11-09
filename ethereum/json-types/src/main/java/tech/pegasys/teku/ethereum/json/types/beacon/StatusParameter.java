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

package tech.pegasys.teku.ethereum.json.types.beacon;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import tech.pegasys.teku.api.response.ValidatorStatus;

public enum StatusParameter {
  PENDING_INITIALIZED("pending_initialized"),
  PENDING_QUEUED("pending_queued"),
  ACTIVE_ONGOING("active_ongoing"),
  ACTIVE_EXITING("active_exiting"),
  ACTIVE_SLASHED("active_slashed"),
  EXITED_UNSLASHED("exited_unslashed"),
  EXITED_SLASHED("exited_slashed"),
  WITHDRAWAL_POSSIBLE("withdrawal_possible"),
  WITHDRAWAL_DONE("withdrawal_done"),
  ACTIVE("active"),
  PENDING("pending"),
  EXITED("exited"),
  WITHDRAWAL("withdrawal");

  private final String value;

  StatusParameter(final String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static StatusParameter parse(final String value) {
    return StatusParameter.valueOf(value.toUpperCase(Locale.ROOT));
  }

  public static Set<ValidatorStatus> getApplicableValidatorStatuses(
      final List<StatusParameter> statusParameters) {
    return statusParameters.stream()
        .flatMap(
            statusParameter ->
                Arrays.stream(ValidatorStatus.values())
                    .filter(
                        validatorStatus ->
                            validatorStatus.name().contains(statusParameter.getValue())))
        .collect(Collectors.toSet());
  }
}
