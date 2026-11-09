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

package tech.pegasys.teku.validator.coordinator;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.time.TimeUtilities.secondsToMillis;

import org.hyperledger.besu.plugin.services.MetricsSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import tech.pegasys.teku.infrastructure.metrics.MetricsCountersByIntervals;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.Validator.ValidatorDutyMetricUtils;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecMilestone;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.networks.Eth2Network;
import tech.pegasys.teku.storage.client.RecentChainData;

class DutyMetricsTest {

  private static final UInt64 GENESIS_TIME = UInt64.valueOf(100_000);
  private final StubTimeProvider timeProvider =
      StubTimeProvider.withTimeInSeconds(GENESIS_TIME.longValue());
  private final RecentChainData recentChainData = mock(RecentChainData.class);
  private final MetricsCountersByIntervals attestationTimings =
      mock(MetricsCountersByIntervals.class);
  private final MetricsCountersByIntervals blockTimings = mock(MetricsCountersByIntervals.class);

  private final MetricsSystem metricsSystem = new StubMetricsSystem();

  private DutyMetrics createMetrics(final Spec spec) {
    return new DutyMetrics(
        timeProvider,
        recentChainData,
        attestationTimings,
        blockTimings,
        ValidatorDutyMetricUtils.createValidatorDutyMetric(metricsSystem),
        spec);
  }

  @BeforeEach
  void setUp() {
    when(recentChainData.getGenesisTimeMillis()).thenReturn(secondsToMillis(GENESIS_TIME));
  }

  @ParameterizedTest
  @EnumSource(
      value = Eth2Network.class,
      names = {"MAINNET", "MINIMAL", "GNOSIS"})
  void shouldRecordDelayWhenAttestationIsPublishedLate(final Eth2Network eth2Network) {
    Spec spec = getSpec(eth2Network);
    DutyMetrics metrics = createMetrics(spec);

    final UInt64 slotNumber = UInt64.valueOf(30);
    final UInt64 expectedAttestationTime = expectedAttestationTime(slotNumber, spec);
    final int publicationDelay = 575;
    timeProvider.advanceTimeByMillis(expectedAttestationTime.plus(publicationDelay).longValue());

    metrics.onAttestationPublished(UInt64.valueOf(30));

    verify(attestationTimings).recordValue(publicationDelay);
  }

  @ParameterizedTest
  @EnumSource(
      value = Eth2Network.class,
      names = {"MAINNET", "MINIMAL", "GNOSIS"})
  void shouldRecordZeroDelayWhenAttestationIsPublishedEarly(final Eth2Network eth2Network) {
    Spec spec = getSpec(eth2Network);
    DutyMetrics metrics = createMetrics(spec);

    final UInt64 slotNumber = UInt64.valueOf(30);
    final UInt64 expectedAttestationTime = expectedAttestationTime(slotNumber, spec);
    timeProvider.advanceTimeByMillis(expectedAttestationTime.minus(50).longValue());

    metrics.onAttestationPublished(UInt64.valueOf(30));

    verify(attestationTimings).recordValue(0);
  }

  @ParameterizedTest
  @EnumSource(
      value = Eth2Network.class,
      names = {"MAINNET", "MINIMAL", "GNOSIS"})
  void shouldRecordDelayWhenBlockIsPublishedLate(final Eth2Network eth2Network) {
    Spec spec = getSpec(eth2Network);
    DutyMetrics metrics = createMetrics(spec);

    final UInt64 slotNumber = UInt64.valueOf(30);
    final UInt64 expectedAttestationTime = expectedBlockTime(slotNumber, spec);
    final int publicationDelay = 575;
    timeProvider.advanceTimeByMillis(expectedAttestationTime.plus(publicationDelay).longValue());

    metrics.onBlockPublished(UInt64.valueOf(30));

    verify(blockTimings).recordValue(publicationDelay);
  }

  @ParameterizedTest
  @EnumSource(
      value = Eth2Network.class,
      names = {"MAINNET", "MINIMAL", "GNOSIS"})
  void shouldRecordZeroDelayWhenBlockIsPublishedEarly(final Eth2Network eth2Network) {
    Spec spec = getSpec(eth2Network);
    DutyMetrics metrics = createMetrics(spec);

    final UInt64 slotNumber = UInt64.valueOf(30);
    final UInt64 expectedBlockTime = expectedBlockTime(slotNumber, spec);
    timeProvider.advanceTimeByMillis(expectedBlockTime.minus(50).longValue());

    metrics.onBlockPublished(UInt64.valueOf(30));

    verify(blockTimings).recordValue(0);
  }

  private Spec getSpec(final Eth2Network eth2Network) {
    return TestSpecFactory.create(SpecMilestone.PHASE0, eth2Network);
  }

  private UInt64 expectedAttestationTime(final UInt64 slot, final Spec spec) {
    return slot.times(spec.getSlotDurationMillis(slot)).plus(spec.getAttestationDueMillis(slot));
  }

  private UInt64 expectedBlockTime(final UInt64 slot, final Spec spec) {
    return slot.times(spec.getSlotDurationMillis(slot));
  }
}
