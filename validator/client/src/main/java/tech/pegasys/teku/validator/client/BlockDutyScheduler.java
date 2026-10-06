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

package tech.pegasys.teku.validator.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.plugin.services.MetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;

public class BlockDutyScheduler extends AbstractDutyScheduler {
  private static final Logger LOG = LogManager.getLogger();

  public BlockDutyScheduler(
      final MetricsSystem metricsSystem, final DutyLoader<?> dutyLoader, final Spec spec) {
    super(metricsSystem, "block", dutyLoader, spec);

    metricsSystem.createIntegerGauge(
        TekuMetricCategory.VALIDATOR,
        "scheduled_block_duties_current",
        "Current number of pending block duties that have been scheduled",
        () -> dutiesByEpoch.values().stream().mapToInt(PendingDuties::countDuties).sum());
  }

  @Override
  public void onBlockProductionDue(final UInt64 slot) {
    onProductionDue(slot);
  }

  @Override
  protected Bytes32 getExpectedDependentRoot(
      final Bytes32 headBlockRoot,
      final Bytes32 previousTargetRoot,
      final Bytes32 currentTargetRoot,
      final UInt64 headEpoch,
      final UInt64 dutyEpoch) {
    return spec.atEpoch(dutyEpoch)
        .getBlockProposalUtil()
        .getBlockProposalDependentRoot(
            headBlockRoot, previousTargetRoot, currentTargetRoot, headEpoch, dutyEpoch);
  }

  @Override
  public int getLookAheadEpochs(final UInt64 epoch) {
    final int lookAheadEpochs =
        spec.atEpoch(epoch).getBlockProposalUtil().getProposerLookAheadEpochs();
    LOG.trace(
        "LookAhead period for block duty at milestone {} is {}",
        () -> spec.atEpoch(epoch).getMilestone(),
        () -> lookAheadEpochs);
    return lookAheadEpochs;
  }
}
