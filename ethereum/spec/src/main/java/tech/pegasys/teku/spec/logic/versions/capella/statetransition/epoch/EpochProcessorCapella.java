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

package tech.pegasys.teku.spec.logic.versions.capella.statetransition.epoch;

import com.google.common.annotations.VisibleForTesting;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszBytes32;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigBellatrix;
import tech.pegasys.teku.spec.config.SpecConfigCapella;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.MutableBeaconState;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.versions.capella.MutableBeaconStateCapella;
import tech.pegasys.teku.spec.datastructures.state.versions.capella.HistoricalSummary;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateMutators;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.util.BeaconStateUtil;
import tech.pegasys.teku.spec.logic.common.util.ValidatorsUtil;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.BeaconStateAccessorsAltair;
import tech.pegasys.teku.spec.logic.versions.altair.helpers.MiscHelpersAltair;
import tech.pegasys.teku.spec.logic.versions.bellatrix.statetransition.epoch.EpochProcessorBellatrix;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;
import tech.pegasys.teku.spec.schemas.SchemaDefinitionsCapella;

public class EpochProcessorCapella extends EpochProcessorBellatrix {
  private final SchemaDefinitions schemaDefinitions;

  public EpochProcessorCapella(
      final SpecConfigCapella specConfig,
      final MiscHelpersAltair miscHelpers,
      final BeaconStateAccessorsAltair beaconStateAccessors,
      final BeaconStateMutators beaconStateMutators,
      final ValidatorsUtil validatorsUtil,
      final BeaconStateUtil beaconStateUtil,
      final ValidatorStatusFactory validatorStatusFactory,
      final SchemaDefinitions schemaDefinitions,
      final TimeProvider timeProvider) {
    super(
        specConfig,
        miscHelpers,
        beaconStateAccessors,
        beaconStateMutators,
        validatorsUtil,
        beaconStateUtil,
        validatorStatusFactory,
        schemaDefinitions,
        timeProvider);
    this.schemaDefinitions = schemaDefinitions;
  }

  @VisibleForTesting
  public EpochProcessorCapella(
      final EpochProcessorCapella processor, final TimeProvider timeProvider) {
    super(
        SpecConfigBellatrix.required(processor.specConfig),
        processor.miscHelpersAltair,
        processor.beaconStateAccessorsAltair,
        processor.beaconStateMutators,
        processor.validatorsUtil,
        processor.beaconStateUtil,
        processor.validatorStatusFactory,
        processor.schemaDefinitions,
        timeProvider);
    this.schemaDefinitions = processor.schemaDefinitions;
  }

  @Override
  public void processHistoricalRootsUpdate(final MutableBeaconState state) {
    // no longer used in capella
  }

  @Override
  public void processHistoricalSummariesUpdate(final MutableBeaconState state) {
    final UInt64 nextEpoch = beaconStateAccessors.getCurrentEpoch(state).plus(1);
    if (nextEpoch
        .mod(specConfig.getSlotsPerHistoricalRoot() / specConfig.getSlotsPerEpoch())
        .equals(UInt64.ZERO)) {
      final HistoricalSummary summary =
          SchemaDefinitionsCapella.required(schemaDefinitions)
              .getHistoricalSummarySchema()
              .create(
                  SszBytes32.of(state.getBlockRoots().hashTreeRoot()),
                  SszBytes32.of(state.getStateRoots().hashTreeRoot()));
      MutableBeaconStateCapella.required(state).getHistoricalSummaries().append(summary);
    }
  }
}
