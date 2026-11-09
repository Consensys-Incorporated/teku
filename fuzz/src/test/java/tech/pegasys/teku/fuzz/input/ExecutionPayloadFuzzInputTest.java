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

import tech.pegasys.teku.infrastructure.ssz.schema.SszSchema;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.versions.electra.BeaconBlockBodyElectra;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class ExecutionPayloadFuzzInputTest extends AbstractFuzzInputTest<BeaconBlockBodyFuzzInput> {

  @Override
  protected SszSchema<BeaconBlockBodyFuzzInput> getInputType() {
    return BeaconBlockBodyFuzzInput.createSchema(spec.getGenesisSpec());
  }

  @Override
  protected BeaconBlockBodyFuzzInput createInput() {
    final BeaconState state = dataStructureUtil.randomBeaconState();
    final BeaconBlockBodyElectra beaconBlockBody =
        BeaconBlockBodyElectra.required(dataStructureUtil.randomBeaconBlockBody());
    return new BeaconBlockBodyFuzzInput(spec, state, beaconBlockBody);
  }
}
