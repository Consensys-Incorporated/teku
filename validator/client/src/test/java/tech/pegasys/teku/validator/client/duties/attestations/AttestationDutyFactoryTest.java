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

package tech.pegasys.teku.validator.client.duties.attestations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.ForkProvider;
import tech.pegasys.teku.validator.client.Validator;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;

class AttestationDutyFactoryTest {

  private final Spec spec = TestSpecFactory.createMinimalElectra();
  private final ValidatorApiChannel validatorApiChannel = mock(ValidatorApiChannel.class);
  private final ForkProvider forkProvider = mock(ForkProvider.class);
  private final ValidatorDutyMetrics validatorDutyMetrics =
      ValidatorDutyMetrics.create(new StubMetricsSystem());
  private final Validator validator = mock(Validator.class);
  private final UInt64 slot = UInt64.ONE;

  @ParameterizedTest
  @MethodSource("aggregatorTypeArgs")
  public void shouldUseCorrectAggregatorTypeWhenCreatingAggregationDuty(
      final boolean dvtEnabled, final Class<?> expectedAggregatorClass) {
    final AttestationDutyFactory attestationDutyFactory =
        new AttestationDutyFactory(
            spec, forkProvider, validatorApiChannel, validatorDutyMetrics, dvtEnabled);
    final AggregationDuty aggregationDuty =
        attestationDutyFactory.createAggregationDuty(slot, validator);

    assertThat(aggregationDuty.getAggregators()).isInstanceOf(expectedAggregatorClass);
  }

  private static Stream<Arguments> aggregatorTypeArgs() {
    return Stream.of(
        Arguments.of(false, AggregatorsGroupedByCommittee.class),
        Arguments.of(true, UngroupedAggregators.class));
  }
}
