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

import static tech.pegasys.teku.ethereum.json.types.validator.ProposerDutyBuilder.PROPOSER_DUTY_TYPE;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public class ProposerDutiesBuilder {
  public static final DeserializableTypeDefinition<ProposerDuties> PROPOSER_DUTIES_TYPE =
      DeserializableTypeDefinition.object(ProposerDuties.class, ProposerDutiesBuilder.class)
          .name("GetProposerDutiesResponse")
          .initializer(ProposerDutiesBuilder::new)
          .finisher(ProposerDutiesBuilder::build)
          .withField(
              "dependent_root",
              BYTES32_TYPE,
              ProposerDuties::getDependentRoot,
              ProposerDutiesBuilder::dependentRoot)
          .withField(
              EXECUTION_OPTIMISTIC,
              BOOLEAN_TYPE,
              ProposerDuties::isExecutionOptimistic,
              ProposerDutiesBuilder::executionOptimistic)
          .withField(
              "data",
              listOf(PROPOSER_DUTY_TYPE),
              ProposerDuties::getDuties,
              ProposerDutiesBuilder::duties)
          .build();

  private Bytes32 dependentRoot;
  private boolean executionOptimistic;
  private List<ProposerDuty> duties;

  public ProposerDutiesBuilder dependentRoot(final Bytes32 dependentRoot) {
    this.dependentRoot = dependentRoot;
    return this;
  }

  public ProposerDutiesBuilder executionOptimistic(final boolean executionOptimistic) {
    this.executionOptimistic = executionOptimistic;
    return this;
  }

  public ProposerDutiesBuilder duties(final List<ProposerDuty> duties) {
    this.duties = duties;
    return this;
  }

  public ProposerDuties build() {
    return new ProposerDuties(dependentRoot, duties, executionOptimistic);
  }
}
