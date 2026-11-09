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

package tech.pegasys.teku.infrastructure.metrics.Validator;

public enum ValidatorDutyMetricsSteps {
  // Total time to perform the entire duty
  TOTAL("total"),
  // total time to perform the create component of the duty, from the time you call the
  // validatorApiChannel, until you get the result
  CREATE_TOTAL("create_total"),
  // time taken excluding the queued time, to perform the create component of the duty
  CREATE("create"),
  SIGN("sign"),
  SEND("send");

  private final String name;

  ValidatorDutyMetricsSteps(final String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }
}
