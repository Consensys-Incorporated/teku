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

public enum DutyType {
  ATTESTATION_AGGREGATION("attestation_aggregation"),
  ATTESTATION_PRODUCTION("attestation_production"),
  BLOCK_PRODUCTION("block_production"),
  PAYLOAD_ATTESTATION_PRODUCTION("payload_attestation_production");

  private final String name;

  DutyType(final String type) {
    this.name = type;
  }

  public String getName() {
    return name;
  }
}
