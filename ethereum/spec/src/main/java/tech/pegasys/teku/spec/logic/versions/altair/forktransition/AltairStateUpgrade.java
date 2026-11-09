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

package tech.pegasys.teku.spec.logic.versions.altair.forktransition;

import java.util.List;
import tech.pegasys.teku.infrastructure.ssz.SszList;
import tech.pegasys.teku.infrastructure.ssz.SszMutableList;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszByte;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.state.Fork;
import tech.pegasys.teku.spec.datastructures.state.PendingAttestation;
import tech.pegasys.teku.spec.datastructures.state.SyncCommittee;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.common.BeaconStateFields;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.BeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.altair.MutableBeaconStateAltair;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.phase0.BeaconStatePhase0;
import tech.pegasys.teku.spec.logic.common.forktransition.StateUpgrade;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsAltair;

public class AltairStateUpgrade implements StateUpgrade<BeaconStateAltair> {
  final SpecConfigAltair specConfig;
  final SchemaDefinitionsAltair schemaDefinitions;
  final BeaconStateAccessorsAltair beaconStateAccessors;
  private final AttestationUtil attestationUtil;
  private final MiscHelpersAltair miscHelpersAltair;

  public AltairStateUpgrade(
      final SpecConfigAltair specConfig,
      final SchemaDefinitionsAltair schemaDefinitions,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final AttestationUtil attestationUtil,
      final MiscHelpersAltair miscHelpersAltair) {
    this.specConfig = specConfig;
    this.schemaDefinitions = schemaDefinitions;
    this.beaconStateAccessors = beaconStateAccessors;
    this.attestationUtil = attestationUtil;
    this.miscHelpersAltair = miscHelpersAltair;
  }

  @Override
  public BeaconStateAltair upgrade(final BeaconState preState) {
    final UInt64 epoch = beaconStateAccessors.getCurrentEpoch(preState);
    final int validatorCount = preState.getValidators().size();

    return BeaconStateAltair.required(schemaDefinitions.getBeaconStateSchema().createEmpty())
        .updatedAltair(
            state -> {
              BeaconStateFields.copyCommonFieldsFromSource(state, preState);

              state.setFork(
                  new Fork(
                      preState.getFork().getCurrentVersion(),
                      specConfig.getAltairForkVersion(),
                      epoch));
              state.getPreviousEpochParticipation().setAll(SszByte.ZERO, validatorCount);
              state.getCurrentEpochParticipation().setAll(SszByte.ZERO, validatorCount);
              state.getInactivityScores().setAll(SszUInt64.ZERO, validatorCount);

              // Fill in previous epoch participation from the pre state's pending attestations
              translateParticipation(
                  state, BeaconStatePhase0.required(preState).getPreviousEpochAttestations());

              // Fill in sync committees
              // Note: A duplicate committee is assigned for the current and next committee at the
              // fork boundary
              final SyncCommittee committee = beaconStateAccessors.getNextSyncCommittee(state);
              state.setCurrentSyncCommittee(committee);
              state.setNextSyncCommittee(committee);
            });
  }

  private void translateParticipation(
      final MutableBeaconStateAltair state, final SszList<PendingAttestation> pendingAttestations) {
    for (PendingAttestation attestation : pendingAttestations) {
      final AttestationData data = attestation.getData();
      final UInt64 inclusionDelay = attestation.getInclusionDelay();

      // Translate attestation inclusion info to flag indices
      final List<Integer> participationFlagIndices =
          beaconStateAccessors.getAttestationParticipationFlagIndices(state, data, inclusionDelay);

      // Apply flags to all attesting validators
      final SszMutableList<SszByte> epochParticipation = state.getPreviousEpochParticipation();
      attestationUtil
          .streamAttestingIndices(state, attestation.getData(), attestation.getAggregationBits())
          .forEach(
              index -> {
                final byte previousFlags = epochParticipation.get(index).get();
                byte newFlags = previousFlags;
                for (int flagIndex : participationFlagIndices) {
                  newFlags = miscHelpersAltair.addFlag(newFlags, flagIndex);
                }
                if (previousFlags != newFlags) {
                  epochParticipation.set(index, SszByte.of(newFlags));
                }
              });
    }
  }
}
