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
import static tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory.VALIDATOR_PERFORMANCE;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;

public class ValidatorPerformanceMetricsTest {

  private static final UInt64 EPOCH = UInt64.ONE;
  private static final int NUMBER_OF_EXPECTED_MESSAGES = 64;
  private static final int NUMBER_OF_PRODUCED_MESSAGES = 60;
  private static final int NUMBER_OF_CORRECT_MESSAGES = 55;
  private static final int NUMBER_OF_INCLUDED_MESSAGES = 52;
  private final StubMetricsSystem metricsSystem = new StubMetricsSystem();
  public final ValidatorPerformanceMetrics validatorPerformanceMetrics =
      new ValidatorPerformanceMetrics(metricsSystem);

  private static final int NUMBER_OF_EXPECTED_ATTESTATIONS = 55;
  private static final int NUMBER_OF_PRODUCED_ATTESTATIONS = 50;
  private static final int NUMBER_OF_INCLUDED_ATTESTATIONS = 30;
  private static final int INCLUSION_DISTANCE_MAX = 15;
  private static final int INCLUSION_DISTANCE_MIN = 1;
  private static final double INCLUSION_DISTANCE_AVERAGE = 1.14;
  private static final int CORRECT_TARGET_COUNT = 20;
  private static final int CORRECT_HEAD_BLOCK_COUNT = 14;

  private static final int NUMBER_OF_EXPECTED_BLOCKS = 51;
  private static final int NUMBER_OF_INCLUDED_BLOCKS = 3;
  private static final int NUMBER_OF_PRODUCED_BLOCKS = 5;

  private final AttestationPerformance attestationPerformance =
      new AttestationPerformance(
          EPOCH,
          NUMBER_OF_EXPECTED_ATTESTATIONS,
          NUMBER_OF_PRODUCED_ATTESTATIONS,
          NUMBER_OF_INCLUDED_ATTESTATIONS,
          INCLUSION_DISTANCE_MAX,
          INCLUSION_DISTANCE_MIN,
          INCLUSION_DISTANCE_AVERAGE,
          CORRECT_TARGET_COUNT,
          CORRECT_HEAD_BLOCK_COUNT);

  private final BlockPerformance blockPerformance =
      new BlockPerformance(
          EPOCH, NUMBER_OF_EXPECTED_BLOCKS, NUMBER_OF_INCLUDED_BLOCKS, NUMBER_OF_PRODUCED_BLOCKS);

  private final SyncCommitteePerformance syncCommitteePerformance =
      new SyncCommitteePerformance(
          EPOCH,
          NUMBER_OF_EXPECTED_MESSAGES,
          NUMBER_OF_PRODUCED_MESSAGES,
          NUMBER_OF_CORRECT_MESSAGES,
          NUMBER_OF_INCLUDED_MESSAGES);

  @BeforeEach
  void setUp() {
    validatorPerformanceMetrics.updateAttestationPerformanceMetrics(attestationPerformance);
    validatorPerformanceMetrics.updateBlockPerformanceMetrics(blockPerformance);
    validatorPerformanceMetrics.updateSyncCommitteePerformance(syncCommitteePerformance);
  }

  @Test
  void getExpectedAttestations() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "expected_attestations").getValue())
        .isEqualTo(NUMBER_OF_EXPECTED_ATTESTATIONS);
  }

  @Test
  void getProducedAttestations() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "produced_attestations").getValue())
        .isEqualTo(NUMBER_OF_PRODUCED_ATTESTATIONS);
  }

  @Test
  void getIncludedAttestations() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "included_attestations").getValue())
        .isEqualTo(NUMBER_OF_INCLUDED_ATTESTATIONS);
  }

  @Test
  void getInclusionDistanceMax() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "inclusion_distance_max").getValue())
        .isEqualTo(INCLUSION_DISTANCE_MAX);
  }

  @Test
  void getInclusionDistanceMin() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "inclusion_distance_min").getValue())
        .isEqualTo(INCLUSION_DISTANCE_MIN);
  }

  @Test
  void getInclusionDistanceAverage() {
    assertThat(
            metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "inclusion_distance_average").getValue())
        .isEqualTo(INCLUSION_DISTANCE_AVERAGE);
  }

  @Test
  void getCorrectTargetCount() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "correct_target_count").getValue())
        .isEqualTo(CORRECT_TARGET_COUNT);
  }

  @Test
  void getCorrectHeadBlockCount() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "correct_head_block_count").getValue())
        .isEqualTo(CORRECT_HEAD_BLOCK_COUNT);
  }

  @Test
  void getExpectedBlocks() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "expected_blocks").getValue())
        .isEqualTo(NUMBER_OF_EXPECTED_BLOCKS);
  }

  @Test
  void getProducedBlocks() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "produced_blocks").getValue())
        .isEqualTo(NUMBER_OF_PRODUCED_BLOCKS);
  }

  @Test
  void getIncludedBlocks() {
    assertThat(metricsSystem.getGauge(VALIDATOR_PERFORMANCE, "included_blocks").getValue())
        .isEqualTo(NUMBER_OF_INCLUDED_BLOCKS);
  }

  @Test
  void getExpectedMessages() {
    assertThat(
            metricsSystem
                .getGauge(VALIDATOR_PERFORMANCE, "expected_sync_committee_messages")
                .getValue())
        .isEqualTo(NUMBER_OF_EXPECTED_MESSAGES);
  }

  @Test
  void getProducedMessages() {
    assertThat(
            metricsSystem
                .getGauge(VALIDATOR_PERFORMANCE, "produced_sync_committee_messages")
                .getValue())
        .isEqualTo(NUMBER_OF_PRODUCED_MESSAGES);
  }

  @Test
  void getCorrectMessages() {
    assertThat(
            metricsSystem
                .getGauge(VALIDATOR_PERFORMANCE, "correct_sync_committee_messages")
                .getValue())
        .isEqualTo(NUMBER_OF_CORRECT_MESSAGES);
  }

  @Test
  void getIncludedMessages() {
    assertThat(
            metricsSystem
                .getGauge(VALIDATOR_PERFORMANCE, "included_sync_committee_messages")
                .getValue())
        .isEqualTo(NUMBER_OF_INCLUDED_MESSAGES);
  }
}
