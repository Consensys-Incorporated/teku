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

package tech.pegasys.teku.fuzz.input;

import tech.pegasys.teku.infrastructure.ssz.containers.Container2;
import tech.pegasys.teku.infrastructure.ssz.containers.ContainerSchema2;
import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.infrastructure.ssz.tree.TreeNode;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecVersion;
import tech.pegasys.teku.spec.datastructures.execution.versions.electra.ConsolidationRequest;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ConsolidationRequestFuzzInput
    extends Container2<ConsolidationRequestFuzzInput, BeaconState, ConsolidationRequest> {

  public static ContainerSchema2<ConsolidationRequestFuzzInput, BeaconState, ConsolidationRequest>
      createSchema(final SpecVersion spec) {
    return ContainerSchema2.create(
        SszSchema.as(BeaconState.class, spec.getSchemaDefinitions().getBeaconStateSchema()),
        spec.getSchemaDefinitions()
            .toVersionElectra()
            .orElseThrow()
            .getExecutionRequestsSchema()
            .getConsolidationRequestsSchema()
            .getElementSchema(),
        ConsolidationRequestFuzzInput::new);
  }

  public ConsolidationRequestFuzzInput(
      final ContainerSchema2<ConsolidationRequestFuzzInput, BeaconState, ConsolidationRequest> type,
      final TreeNode backingNode) {
    super(type, backingNode);
  }

  public ConsolidationRequestFuzzInput(
      final Spec spec, final BeaconState state, final ConsolidationRequest depositRequest) {
    super(createSchema(spec.atSlot(state.getSlot())), state, depositRequest);
  }

  public BeaconState getState() {
    return getField0();
  }

  public ConsolidationRequest getConsolidationRequest() {
    return getField1();
  }
}
