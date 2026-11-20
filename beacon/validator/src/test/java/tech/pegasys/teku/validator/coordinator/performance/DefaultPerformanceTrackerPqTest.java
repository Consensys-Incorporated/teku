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

package tech.pegasys.teku.validator.coordinator.performance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.validator.coordinator.performance.DefaultPerformanceTracker.ATTESTATION_INCLUSION_RANGE;

import org.junit.jupiter.api.Test;
import tech.pegasys.infrastructure.logging.LogCaptor;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.logging.StatusLogger;
import tech.pegasys.teku.infrastructure.metrics.SettableGauge;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBlockAndState;
import tech.pegasys.teku.spec.datastructures.operations.AttestationData;
import tech.pegasys.teku.spec.datastructures.operations.versions.pq.SingleAttestationPq;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.generator.PqTestKeys;
import tech.pegasys.teku.storage.storageSystem.InMemoryStorageSystemBuilder;
import tech.pegasys.teku.storage.storageSystem.StorageSystem;
import tech.pegasys.teku.validator.api.ValidatorPerformanceTrackingMode;
import tech.pegasys.teku.validator.coordinator.ActiveValidatorTracker;

/** The performance tracker with PQ single attestations, which carry XMSS signatures. */
class DefaultPerformanceTrackerPqTest {

  private static final UInt64 PQ_FORK_EPOCH = UInt64.ONE;

  private final Spec spec = TestSpecFactory.createMinimalWithPqForkEpoch(PQ_FORK_EPOCH);
  private final StatusLogger log = mock(StatusLogger.class);
  private final ActiveValidatorTracker validatorTracker = mock(ActiveValidatorTracker.class);
  private final SyncCommitteePerformanceTracker syncCommitteePerformanceTracker =
      mock(SyncCommitteePerformanceTracker.class);

  @Test
  void shouldReportPerformanceOfPqSingleAttestations() {
    final StorageSystem storageSystem = InMemoryStorageSystemBuilder.buildDefault(spec);
    storageSystem.chainUpdater().initializeGenesis();
    final SignedBlockAndState head =
        storageSystem
            .chainUpdater()
            .advanceChain(spec.computeStartSlotAtEpoch(PQ_FORK_EPOCH).plus(1));
    storageSystem.chainUpdater().updateBestBlock(head);
    assertThat(spec.atSlot(head.getSlot()).getMilestone()).isEqualTo(SpecMilestone.PQ);
    final DefaultPerformanceTracker performanceTracker =
        new DefaultPerformanceTracker(
            storageSystem.combinedChainDataClient(),
            log,
            mock(ValidatorPerformanceMetrics.class),
            ValidatorPerformanceTrackingMode.ALL,
            validatorTracker,
            syncCommitteePerformanceTracker,
            spec,
            mock(SettableGauge.class));
    when(validatorTracker.getNumberOfValidatorsForEpoch(any())).thenReturn(1);
    when(syncCommitteePerformanceTracker.calculatePerformance(any()))
        .thenReturn(
            SafeFuture.completedFuture(new SyncCommitteePerformance(UInt64.ZERO, 0, 0, 0, 0)));
    performanceTracker.start(UInt64.ZERO);

    performanceTracker.saveProducedAttestation(singleAttestation(head));

    try (final LogCaptor logCaptor = LogCaptor.forClass(DefaultPerformanceTracker.class)) {
      performanceTracker.onSlot(
          spec.computeStartSlotAtEpoch(
              spec.computeEpochAtSlot(head.getSlot()).plus(ATTESTATION_INCLUSION_RANGE)));

      assertThat(logCaptor.getErrorLogs()).isEmpty();
    }
    verify(log).performance(contains("Attestation performance"));
  }

  private SingleAttestationPq singleAttestation(final SignedBlockAndState head) {
    final BeaconState state = head.getState();
    final UInt64 committeeIndex = UInt64.ZERO;
    final AttestationData data =
        spec.getGenericAttestationData(head.getSlot(), state, head, committeeIndex);
    final int attester = spec.getBeaconCommittee(state, head.getSlot(), committeeIndex).getInt(0);
    return new SingleAttestationPq(
        committeeIndex,
        UInt64.valueOf(attester),
        data,
        PqTestKeys.stubSignature(UInt64.ZERO).getBytes());
  }
}
