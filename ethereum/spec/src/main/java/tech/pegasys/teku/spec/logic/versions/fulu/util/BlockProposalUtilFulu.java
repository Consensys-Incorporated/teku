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

package tech.pegasys.teku.spec.logic.versions.fulu.util;

import static com.google.common.base.Preconditions.checkArgument;

import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.logic.common.block.BlockProcessor;
import tech.pegasys.teku.spec.logic.versions.phase0.util.BlockProposalUtilPhase0;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

public class BlockProposalUtilFulu extends BlockProposalUtilPhase0 {
  private static final Logger LOG = LogManager.getLogger();
  private final Optional<UInt64> forkEpoch;

  public BlockProposalUtilFulu(
      final SchemaDefinitions schemaDefinitions,
      final BlockProcessor blockProcessor,
      final UInt64 forkEpoch) {
    super(schemaDefinitions, blockProcessor);
    this.forkEpoch = forkEpoch.equals(UInt64.MAX_VALUE) ? Optional.empty() : Optional.of(forkEpoch);
  }

  @Override
  public int getProposerLookAheadEpochs() {
    return 1;
  }

  @Override
  public UInt64 getStateSlotForProposerDuties(
      final Spec spec, final UInt64 stateEpoch, final UInt64 dutiesEpoch) {
    if (stateEpoch.isLessThan(dutiesEpoch) && stateEpoch.increment().isLessThan(dutiesEpoch)) {
      return spec.computeStartSlotAtEpoch(dutiesEpoch.minusMinZero(1));
    }
    return dutiesEpoch.isGreaterThan(stateEpoch)
        ? spec.computeStartSlotAtEpoch(stateEpoch)
        : spec.computeStartSlotAtEpoch(dutiesEpoch);
  }

  @Override
  public Bytes32 getBlockProposalDependentRoot(
      final Bytes32 headBlockRoot,
      final Bytes32 previousTargetRoot,
      final Bytes32 currentTargetRoot,
      final UInt64 stateEpoch,
      final UInt64 dutyEpoch) {
    if (forkEpoch.isEmpty() || dutyEpoch.minusMinZero(1).isLessThan(forkEpoch.get())) {
      return super.getBlockProposalDependentRoot(
          headBlockRoot, previousTargetRoot, currentTargetRoot, stateEpoch, dutyEpoch);
    }
    checkArgument(
        dutyEpoch.isGreaterThanOrEqualTo(stateEpoch),
        "Attempting to calculate dependent root for duty epoch %s that is before the updated head epoch %s",
        dutyEpoch,
        stateEpoch);
    if (stateEpoch.equals(dutyEpoch)) {
      LOG.debug("headEpoch {} - returning previousDutyDependentRoot", () -> stateEpoch);
      return previousTargetRoot;
    } else if (stateEpoch.increment().equals(dutyEpoch)) {
      LOG.debug("dutyEpoch (next epoch) {} - returning currentDutyDependentRoot", () -> dutyEpoch);
      return currentTargetRoot;
    } else {
      LOG.debug(
          "headBlockRoot returned - dutyEpoch {}, headEpoch {}", () -> dutyEpoch, () -> stateEpoch);
      return headBlockRoot;
    }
  }
}
