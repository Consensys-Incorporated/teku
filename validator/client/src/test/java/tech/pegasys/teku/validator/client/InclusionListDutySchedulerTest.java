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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;

class InclusionListDutySchedulerTest {

  private final Map<UInt64, SafeFuture<Optional<SlotBasedScheduledDuties<?, ?>>>>
      requestedDutiesByEpoch = new HashMap<>();
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  private final DutyLoader<?> dutyLoader = mock(DutyLoader.class);

  @BeforeEach
  void setUp() {
    when(dutyLoader.loadDutiesForEpoch(any()))
        .thenAnswer(
            invocation -> {
              final UInt64 epoch = invocation.getArgument(0);
              return requestedDutiesByEpoch.computeIfAbsent(epoch, __ -> new SafeFuture<>());
            });
  }

  @Test
  void shouldNotRequestDutiesBeforeHeze() {
    final Spec spec = TestSpecFactory.createMinimalWithHezeForkEpoch(UInt64.valueOf(3));
    final InclusionListDutyScheduler dutyScheduler = createDutyScheduler(spec);

    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));

    assertThat(requestedDutiesByEpoch).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 3})
  void shouldOnlyRequestHezeDutiesWhenLookaheadIncludesHeze(final int forkEpoch) {
    final UInt64 hezeForkEpoch = UInt64.valueOf(forkEpoch);
    final Spec spec = TestSpecFactory.createMinimalWithHezeForkEpoch(hezeForkEpoch);
    final InclusionListDutyScheduler dutyScheduler = createDutyScheduler(spec);

    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(hezeForkEpoch.decrement()));

    assertThat(requestedDutiesByEpoch).containsOnlyKeys(hezeForkEpoch);
  }

  @ParameterizedTest
  @ValueSource(ints = {0, 1, 3})
  void shouldRequestCurrentAndLookaheadDutiesAtHeze(final int forkEpoch) {
    final UInt64 hezeForkEpoch = UInt64.valueOf(forkEpoch);
    final Spec spec = TestSpecFactory.createMinimalWithHezeForkEpoch(hezeForkEpoch);
    final InclusionListDutyScheduler dutyScheduler = createDutyScheduler(spec);

    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(hezeForkEpoch));

    assertThat(requestedDutiesByEpoch).containsOnlyKeys(hezeForkEpoch, hezeForkEpoch.increment());
  }

  private InclusionListDutyScheduler createDutyScheduler(final Spec spec) {
    return new InclusionListDutyScheduler(metricsSystem, dutyLoader, spec);
  }
}
