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

package tech.pegasys.teku.api.migrated;

import java.util.Objects;

public class GetAttestationRewardsResponse {

  private final boolean executionOptimistic;
  private final boolean finalized;
  private final AttestationRewardsData attestationRewardsData;

  public GetAttestationRewardsResponse(
      final boolean executionOptimistic,
      final boolean finalized,
      final AttestationRewardsData attestationRewardsData) {
    this.executionOptimistic = executionOptimistic;
    this.finalized = finalized;
    this.attestationRewardsData = attestationRewardsData;
  }

  public boolean isExecutionOptimistic() {
    return executionOptimistic;
  }

  public boolean isFinalized() {
    return finalized;
  }

  public AttestationRewardsData getAttestationRewardsData() {
    return attestationRewardsData;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }

    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    GetAttestationRewardsResponse that = (GetAttestationRewardsResponse) o;
    return executionOptimistic == that.executionOptimistic
        && finalized == that.finalized
        && Objects.equals(attestationRewardsData, that.attestationRewardsData);
  }

  @Override
  public int hashCode() {
    return Objects.hash(executionOptimistic, finalized, attestationRewardsData);
  }
}
