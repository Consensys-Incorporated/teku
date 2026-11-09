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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.bellatrix;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.execution.ExecutionPayloadHeader;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;

public interface MutableBeaconStateBellatrix
    extends MutableBeaconStateAltair, BeaconStateBellatrix {

  static MutableBeaconStateBellatrix required(final MutableBeaconState state) {
    return state
        .toMutableVersionBellatrix()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a bellatrix state but got: " + state.getClass().getSimpleName()));
  }

  default void setLatestExecutionPayloadHeader(
      final ExecutionPayloadHeader executionPayloadHeader) {
    final int fieldIndex =
        getSchema().getFieldIndex(BeaconStateFields.LATEST_EXECUTION_PAYLOAD_HEADER);
    set(fieldIndex, executionPayloadHeader);
  }

  @Override
  BeaconStateBellatrix commitChanges();

  @Override
  default Optional<MutableBeaconStateBellatrix> toMutableVersionBellatrix() {
    return Optional.of(this);
  }

  @Override
  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateBellatrix updatedBellatrix(
          final Mutator<MutableBeaconStateBellatrix, E1, E2, E3> mutator) throws E1, E2, E3 {
    throw new UnsupportedOperationException();
  }
}
