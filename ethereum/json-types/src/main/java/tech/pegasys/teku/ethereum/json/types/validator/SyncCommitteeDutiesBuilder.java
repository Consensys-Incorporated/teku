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

package tech.pegasys.teku.ethereum.json.types.validator;

import static tech.pegasys.teku.ethereum.json.types.validator.SyncCommitteeDutyBuilder.SYNC_COMMITTEE_DUTY_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.List;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class SyncCommitteeDutiesBuilder {
  public static final DeserializableTypeDefinition<SyncCommitteeDuties> SYNC_COMMITTEE_DUTIES_TYPE =
      DeserializableTypeDefinition.object(
              SyncCommitteeDuties.class, SyncCommitteeDutiesBuilder.class)
          .name("GetSyncCommitteeDutiesResponse")
          .initializer(SyncCommitteeDutiesBuilder::new)
          .finisher(SyncCommitteeDutiesBuilder::build)
          .withField(
              EXECUTION_OPTIMISTIC,
              BOOLEAN_TYPE,
              SyncCommitteeDuties::isExecutionOptimistic,
              SyncCommitteeDutiesBuilder::executionOptimistic)
          .withField(
              "data",
              listOf(SYNC_COMMITTEE_DUTY_TYPE),
              SyncCommitteeDuties::getDuties,
              SyncCommitteeDutiesBuilder::duties)
          .build();

  private boolean executionOptimistic;
  private List<SyncCommitteeDuty> duties;

  public SyncCommitteeDutiesBuilder executionOptimistic(final boolean executionOptimistic) {
    this.executionOptimistic = executionOptimistic;
    return this;
  }

  public SyncCommitteeDutiesBuilder duties(final List<SyncCommitteeDuty> duties) {
    this.duties = duties;
    return this;
  }

  public SyncCommitteeDuties build() {
    return new SyncCommitteeDuties(executionOptimistic, duties);
  }
}
