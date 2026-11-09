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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.deneb;

import java.util.Optional;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;

public interface MutableBeaconStateDeneb extends MutableBeaconStateCapella, BeaconStateDeneb {
  static MutableBeaconStateDeneb required(final MutableBeaconState state) {
    return state
        .toMutableVersionDeneb()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a Deneb state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  BeaconStateDeneb commitChanges();

  @Override
  default Optional<MutableBeaconStateDeneb> toMutableVersionDeneb() {
    return Optional.of(this);
  }
}
