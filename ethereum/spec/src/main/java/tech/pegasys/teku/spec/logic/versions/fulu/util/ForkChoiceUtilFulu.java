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

package tech.pegasys.teku.spec.logic.versions.fulu.util;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.versions.deneb.util.ForkChoiceUtilDeneb;

public class ForkChoiceUtilFulu extends ForkChoiceUtilDeneb {

  protected volatile AvailabilityCheckerFactory<UInt64> dataColumnSidecarAvailabilityCheckerFactory;

  public ForkChoiceUtilFulu(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      final EpochProcessor epochProcessor,
      final AttestationUtil attestationUtil,
      final MiscHelpers miscHelpers) {
    super(specConfig, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
  }

  public void setDataColumnSidecarAvailabilityCheckerFactory(
      final AvailabilityCheckerFactory<UInt64> factory) {
    this.dataColumnSidecarAvailabilityCheckerFactory = factory;
  }

  @Override
  public AvailabilityChecker<?> createAvailabilityCheckerOnBlock(final SignedBeaconBlock block) {
    final AvailabilityCheckerFactory<UInt64> factory =
        this.dataColumnSidecarAvailabilityCheckerFactory;
    if (factory == null) {
      throw new IllegalStateException(
          "DataColumnSidecarAvailabilityCheckerFactory not initialized");
    }
    return factory.createAvailabilityChecker(block);
  }

  @Override
  public boolean isDataAvailabilityRequiredForTimeliness() {
    return true;
  }

  /**
   * From Fulu (EIP-7917) the proposer lookahead fixes proposer assignments before the epoch
   * boundary, so re-orging a late block at the end of the previous epoch cannot change the current
   * slot's proposer. The proposer is therefore always stable.
   */
  @Override
  protected boolean isProposerStable(final UInt64 slot) {
    return true;
  }

  @Override
  public Optional<ForkChoiceUtilFulu> toVersionFulu() {
    return Optional.of(this);
  }
}
