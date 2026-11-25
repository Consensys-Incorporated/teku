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

package tech.pegasys.teku.spec.logic.versions.pq.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.config.SpecConfigPq;
import tech.pegasys.teku.spec.constants.PqConstants;
import tech.pegasys.teku.spec.logic.common.util.ForkChoiceUtil;

/** The staggered aggregators of consensus-specs 4595f0fc. */
class PqAggregationTimingTest {

  @Test
  void shouldStaggerAggregatorsAsTheSpecificationsTableDoes() {
    // The table in validator.md, with a SLOT_DURATION_MS of 12 seconds
    final ForkChoiceUtil forkChoiceUtil =
        TestSpecFactory.createMainnetPq().getGenesisSpec().getForkChoiceUtil();

    assertThat(IntStream.range(0, 4).map(forkChoiceUtil::getAggregationCutoffMillis))
        .containsExactly(4800, 5400, 6000, 6600);
    assertThat(IntStream.range(0, 4).map(forkChoiceUtil::getAggregateDueMillis))
        .containsExactly(7200, 7800, 8400, 9000);
  }

  @Test
  void shouldMakeTheFirstAggregatorsDueTimeThePqAggregateDueTime() {
    final Spec spec = TestSpecFactory.createMainnetPq();
    final ForkChoiceUtil forkChoiceUtil = spec.getGenesisSpec().getForkChoiceUtil();

    // AGGREGATE_DUE_BPS_PQ replaces AGGREGATE_DUE_BPS_GLOAS (50%)
    assertThat(forkChoiceUtil.getAggregateDueMillis()).isEqualTo(7200);
    assertThat(spec.getAggregateDueMillis(UInt64.ZERO)).isEqualTo(7200);
    assertThat(spec.getAggregationCutoffMillis(UInt64.ZERO, 1)).isEqualTo(5400);
    assertThat(spec.getAggregateDueMillis(UInt64.ZERO, 1)).isEqualTo(7800);
  }

  @Test
  void shouldKeepTheLastAggregatorWithinTheSlot() {
    final SpecConfigPq config =
        SpecConfigPq.required(TestSpecFactory.createMinimalPq().getGenesisSpecConfig());

    assertThat(config.getAggregationCutoffBps()).isLessThan(config.getAggregateDueBpsPq());
    assertThat(
            config.getAggregateDueBpsPq()
                + (PqConstants.AGGREGATORS_PER_COMMITTEE - 1) * config.getAggregatorDelayBps())
        .isLessThanOrEqualTo(10_000);
  }

  @Test
  void shouldRejectACutoffAtOrAfterTheDueTime() {
    assertThatThrownBy(
            () ->
                TestSpecFactory.createMinimalPq(
                    builder ->
                        builder.pqBuilder(
                            pq -> pq.aggregationCutoffBps(6000).aggregateDueBpsPq(6000))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AGGREGATION_CUTOFF_BPS must be less than AGGREGATE_DUE_BPS_PQ");
  }

  @Test
  void shouldRejectALastAggregatorDueAfterTheSlot() {
    assertThatThrownBy(
            () ->
                TestSpecFactory.createMinimalPq(
                    builder ->
                        builder.pqBuilder(
                            pq -> pq.aggregateDueBpsPq(8000).aggregatorDelayBps(1000))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("within the slot");
  }
}
