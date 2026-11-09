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

import static tech.pegasys.teku.ethereum.json.types.validator.AttesterDutyBuilder.ATTESTER_DUTY_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.DEPENDENT_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class AttesterDutiesBuilder {
  public static final DeserializableTypeDefinition<AttesterDuties> ATTESTER_DUTIES_RESPONSE_TYPE =
      DeserializableTypeDefinition.object(AttesterDuties.class, AttesterDutiesBuilder.class)
          .name("GetAttesterDutiesResponse")
          .initializer(AttesterDutiesBuilder::new)
          .finisher(AttesterDutiesBuilder::build)
          .withField(
              DEPENDENT_ROOT,
              BYTES32_TYPE,
              AttesterDuties::getDependentRoot,
              AttesterDutiesBuilder::dependentRoot)
          .withField(
              EXECUTION_OPTIMISTIC,
              BOOLEAN_TYPE,
              AttesterDuties::isExecutionOptimistic,
              AttesterDutiesBuilder::executionOptimistic)
          .withField(
              "data",
              DeserializableTypeDefinition.listOf(ATTESTER_DUTY_TYPE),
              AttesterDuties::getDuties,
              AttesterDutiesBuilder::duties)
          .build();

  private boolean executionOptimistic;
  private Bytes32 dependentRoot;
  private List<AttesterDuty> duties;

  public AttesterDutiesBuilder executionOptimistic(final boolean executionOptimistic) {
    this.executionOptimistic = executionOptimistic;
    return this;
  }

  public AttesterDutiesBuilder dependentRoot(final Bytes32 dependentRoot) {
    this.dependentRoot = dependentRoot;
    return this;
  }

  public AttesterDutiesBuilder duties(final List<AttesterDuty> duties) {
    this.duties = duties;
    return this;
  }

  public AttesterDuties build() {
    return new AttesterDuties(executionOptimistic, dependentRoot, duties);
  }
}
