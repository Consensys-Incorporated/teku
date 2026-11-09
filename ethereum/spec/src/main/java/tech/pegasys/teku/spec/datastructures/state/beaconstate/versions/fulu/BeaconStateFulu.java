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

import com.google.common.base.MoreObjects;
import java.util.Optional;
import tech.pegasys.teku.infrastructure.ssz.SszCollection;
import tech.pegasys.teku.infrastructure.ssz.SszData;
import tech.pegasys.teku.infrastructure.ssz.collections.SszUInt64Vector;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.electra.BeaconStateElectra;

public interface BeaconStateFulu extends BeaconStateElectra {
  static BeaconStateFulu required(final BeaconState state) {
    return state
        .toVersionFulu()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Expected a Fulu state but got: " + state.getClass().getSimpleName()));
  }

  private static <T extends SszData> void addItems(
      final MoreObjects.ToStringHelper stringBuilder,
      final String keyPrefix,
      final SszCollection<T> items) {
    for (int i = 0; i < items.size(); i++) {
      stringBuilder.add(keyPrefix + "[" + i + "]", items.get(i));
    }
  }

  static void describeCustomFuluFields(
      final MoreObjects.ToStringHelper stringBuilder, final BeaconStateFulu state) {
    BeaconStateElectra.describeCustomElectraFields(stringBuilder, state);
    addItems(stringBuilder, "proposer_lookahead", state.getProposerLookahead());
  }

  @Override
  MutableBeaconStateFulu createWritableCopy();

  default <E1 extends Exception, E2 extends Exception, E3 extends Exception>
      BeaconStateFulu updatedFulu(final Mutator<MutableBeaconStateFulu, E1, E2, E3> mutator)
          throws E1, E2, E3 {
    MutableBeaconStateFulu writableCopy = createWritableCopy();
    mutator.mutate(writableCopy);
    return writableCopy.commitChanges();
  }

  @Override
  default Optional<BeaconStateFulu> toVersionFulu() {
    return Optional.of(this);
  }

  default SszUInt64Vector getProposerLookahead() {
    final int index = getSchema().getFieldIndex(PROPOSER_LOOKAHEAD);
    return getAny(index);
  }
}
