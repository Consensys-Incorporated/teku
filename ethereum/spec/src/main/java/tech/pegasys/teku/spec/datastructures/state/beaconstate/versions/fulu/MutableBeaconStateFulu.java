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

package tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.fulu;

import static tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields.PROPOSER_LOOKAHEAD;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.collections.SszMutableUInt64Vector;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.MutableBeaconStateElectra;

public interface MutableBeaconStateFulu extends MutableBeaconStateElectra, BeaconStateFulu {
  static MutableBeaconStateFulu required(final MutableBeaconState state) {
    return state
        .toMutableVersionFulu()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a Fulu state but got: " + state.getClass().getSimpleName()));
  }

  @Override
  BeaconStateFulu commitChanges();

  @Override
  default Optional<MutableBeaconStateFulu> toMutableVersionFulu() {
    return Optional.of(this);
  }

  default void setProposerLookahead(final SszUInt64Vector proposerLookahead) {
    final int fieldIndex = getSchema().getFieldIndex(BeaconStateFields.PROPOSER_LOOKAHEAD);
    set(fieldIndex, proposerLookahead);
  }

  @Override
  default SszMutableUInt64Vector getProposerLookahead() {
    final int index = getSchema().getFieldIndex(PROPOSER_LOOKAHEAD);
    return getAnyByRef(index);
  }
}
