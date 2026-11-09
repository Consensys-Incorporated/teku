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

package tech.pegasys.teku.spec.logic.versions.deneb.util;

import static tech.pegasys.teku.spec.SpecMilestone.DENEB;

import java.util.Optional;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.datastructures.blobs.versions.deneb.BlobSidecar;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.forkchoice.ReadOnlyStore;
import tech.pegasys.teku.spec.logic.common.helpers.BeaconStateAccessors;
import tech.pegasys.teku.spec.logic.common.helpers.MiscHelpers;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityChecker;
import tech.pegasys.teku.spec.logic.common.statetransition.availability.AvailabilityCheckerFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.EpochProcessor;
import tech.pegasys.teku.spec.logic.common.util.AttestationUtil;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;

public class ForkChoiceUtilDeneb extends ForkChoiceUtil {

  private volatile AvailabilityCheckerFactory<BlobSidecar> blobSidecarAvailabilityCheckerFactory;

  public ForkChoiceUtilDeneb(
      final SpecConfig specConfig,
      final BeaconStateAccessors beaconStateAccessors,
      final EpochProcessor epochProcessor,
      final AttestationUtil attestationUtil,
      final MiscHelpers miscHelpers) {
    super(specConfig, beaconStateAccessors, epochProcessor, attestationUtil, miscHelpers);
  }

  public void setBlobSidecarAvailabilityCheckerFactory(
      final AvailabilityCheckerFactory<BlobSidecar> factory) {
    this.blobSidecarAvailabilityCheckerFactory = factory;
  }

  @Override
  public Optional<UInt64> getEarliestAvailabilityWindowSlotBeforeBlock(
      final Spec spec, final ReadOnlyStore store, final UInt64 slot) {
    final UInt64 firstAvailabilityWindowSlot = computeFirstAvailabilityWindowSlot(store, spec);
    if (firstAvailabilityWindowSlot.isLessThanOrEqualTo(slot)) {
      return Optional.of(firstAvailabilityWindowSlot);
    } else {
      return Optional.empty();
    }
  }

  private UInt64 computeFirstAvailabilityWindowSlot(final ReadOnlyStore store, final Spec spec) {
    final UInt64 currentEpoch = spec.getCurrentEpoch(store);
    final SpecConfigDeneb specConfigDeneb = SpecConfigDeneb.required(specConfig);
    final UInt64 maybeAvailabilityWindowStartSlot =
        spec.computeStartSlotAtEpoch(
            currentEpoch.minusMinZero(specConfigDeneb.getMinEpochsForBlobSidecarsRequests()));
    final UInt64 firstDenebSlot =
        spec.getForkSchedule()
            .streamMilestoneBoundarySlots()
            .filter(pair -> pair.getLeft().isGreaterThanOrEqualTo(DENEB))
            .findFirst()
            .orElseThrow()
            .getRight();

    return maybeAvailabilityWindowStartSlot.max(firstDenebSlot);
  }

  @Override
  public AvailabilityChecker<?> createAvailabilityCheckerOnBlock(final SignedBeaconBlock block) {
    final AvailabilityCheckerFactory<BlobSidecar> factory =
        this.blobSidecarAvailabilityCheckerFactory;
    if (factory == null) {
      throw new IllegalStateException("blobSidecarAvailabilityCheckerFactory not initialized");
    }
    return factory.createAvailabilityChecker(block);
  }

  @Override
  public Optional<ForkChoiceUtilDeneb> toVersionDeneb() {
    return Optional.of(this);
  }
}
