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

package tech.pegasys.teku.statetransition.attestation;

import java.util.HashSet;
import java.util.Set;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.constants.Domain;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;

public class AttestationForkChecker {

  private final Set<Bytes32> validCommitteeShufflingSeeds = new HashSet<>();

  public AttestationForkChecker(final Spec spec, final BeaconState state) {

    UInt64 epoch = spec.computeEpochAtSlot(state.getSlot());
    Bytes32 currentEpochSeed = spec.getSeed(state, epoch, Domain.BEACON_ATTESTER);
    validCommitteeShufflingSeeds.add(currentEpochSeed);

    if (!epoch.equals(UInt64.ZERO)) {
      Bytes32 previousEpochSeed =
          spec.getSeed(state, epoch.minus(UInt64.ONE), Domain.BEACON_ATTESTER);
      validCommitteeShufflingSeeds.add(previousEpochSeed);
    }
  }

  public boolean areAttestationsFromCorrectForkV2(
      final MatchingDataAttestationGroupV2 attestationGroup) {
    return attestationGroup.matchesCommitteeShufflingSeed(validCommitteeShufflingSeeds);
  }
}
