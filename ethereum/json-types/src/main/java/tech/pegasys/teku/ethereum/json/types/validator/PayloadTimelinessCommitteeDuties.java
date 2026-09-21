/*
 * Copyright Consensys Software Inc., 2026
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

import static tech.pegasys.teku.ethereum.json.types.validator.PtcDuty.PTC_DUTY_TYPE_DEFINITION;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.DEPENDENT_ROOT;
import static tech.pegasys.teku.infrastructure.http.RestApiConstants.EXECUTION_OPTIMISTIC;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BOOLEAN_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.CoreTypes.BYTES32_TYPE;
import static tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition.listOf;

import java.util.List;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.json.types.DeserializableTypeDefinition;

public record PayloadTimelinessCommitteeDuties(
    boolean executionOptimistic, Bytes32 dependentRoot, List<PtcDuty> duties) {

  public static final DeserializableTypeDefinition<PayloadTimelinessCommitteeDuties>
      PTC_DUTIES_TYPE_DEFINITION =
          DeserializableTypeDefinition.object(
                  PayloadTimelinessCommitteeDuties.class,
                  PayloadTimelinessCommitteeDuties.Builder.class)
              .name("GetPtcDutiesResponse")
              .initializer(PayloadTimelinessCommitteeDuties.Builder::new)
              .finisher(PayloadTimelinessCommitteeDuties.Builder::build)
              .withField(
                  DEPENDENT_ROOT,
                  BYTES32_TYPE,
                  PayloadTimelinessCommitteeDuties::dependentRoot,
                  PayloadTimelinessCommitteeDuties.Builder::dependentRoot)
              .withField(
                  EXECUTION_OPTIMISTIC,
                  BOOLEAN_TYPE,
                  PayloadTimelinessCommitteeDuties::executionOptimistic,
                  PayloadTimelinessCommitteeDuties.Builder::executionOptimistic)
              .withField(
                  "data",
                  listOf(PTC_DUTY_TYPE_DEFINITION),
                  PayloadTimelinessCommitteeDuties::duties,
                  PayloadTimelinessCommitteeDuties.Builder::duties)
              .build();

  public static class Builder {

    private boolean executionOptimistic;
    private Bytes32 dependentRoot;
    private List<PtcDuty> duties;

    public Builder executionOptimistic(final boolean executionOptimistic) {
      this.executionOptimistic = executionOptimistic;
      return this;
    }

    public Builder dependentRoot(final Bytes32 dependentRoot) {
      this.dependentRoot = dependentRoot;
      return this;
    }

    public Builder duties(final List<PtcDuty> duties) {
      this.duties = duties;
      return this;
    }

    public PayloadTimelinessCommitteeDuties build() {
      return new PayloadTimelinessCommitteeDuties(executionOptimistic, dependentRoot, duties);
    }
  }
}
