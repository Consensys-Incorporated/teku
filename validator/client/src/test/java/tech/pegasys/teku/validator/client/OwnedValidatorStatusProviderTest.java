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

package tech.pegasys.teku.validator.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.BLS_WITHDRAWAL_BYTE;
import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.COMPOUNDING_WITHDRAWAL_BYTE;
import static tech.pegasys.teku.spec.constants.WithdrawalPrefixes.ETH1_ADDRESS_WITHDRAWAL_BYTE;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.response.ValidatorStatus;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.ethereum.json.types.beacon.StateValidatorData;
import tech.pegasys.teku.infrastructure.async.AsyncRunner;
import tech.pegasys.teku.infrastructure.async.StubAsyncRunner;
import tech.pegasys.teku.infrastructure.metrics.StubLabelledGauge;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.TekuMetricCategory;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.util.DataStructureUtil;
import tech.pegasys.teku.validator.api.ValidatorApiChannel;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

class OwnedValidatorStatusProviderTest {

  private final StubMetricsSystem metricSystem = new StubMetricsSystem();
  private final OwnedValidators ownedValidators = new OwnedValidators();
  private final Spec spec = TestSpecFactory.createDefault();
  private final AsyncRunner asyncRunner = new StubAsyncRunner();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);

  private ValidatorApiChannel validatorApiChannel;
  private OwnedValidatorStatusProvider ownedValidatorStatusProvider;

  @BeforeEach
  public void setUp() {
    validatorApiChannel = mock(ValidatorApiChannel.class);

    ownedValidatorStatusProvider =
        new OwnedValidatorStatusProvider(
            metricSystem, ownedValidators, validatorApiChannel, spec, asyncRunner);
  }

  @Test
  public void testLocalValidatorBalancesMetric() {
    final List<Validator> blsCredsValidators =
        IntStream.range(0, 2)
            .mapToObj(
                __ ->
                    dataStructureUtil
                        .validatorBuilder()
                        .effectiveBalance(UInt64.THIRTY_TWO_ETH)
                        .withRandomBlsWithdrawalCredentials()
                        .build())
            .toList();

    final List<Validator> eth1CredsValidators =
        IntStream.range(0, 2)
            .mapToObj(
                __ ->
                    dataStructureUtil
                        .validatorBuilder()
                        .effectiveBalance(UInt64.THIRTY_TWO_ETH)
                        .withRandomEth1WithdrawalCredentials()
                        .build())
            .toList();

    final List<Validator> compoundingCredsValidators =
        IntStream.range(0, 2)
            .mapToObj(
                __ ->
                    dataStructureUtil
                        .validatorBuilder()
                        .effectiveBalance(UInt64.THIRTY_TWO_ETH)
                        .withRandomCompoundingWithdrawalCredentials()
                        .build())
            .toList();

    final Map<BLSPublicKey, StateValidatorData> validatorDataMap =
        Stream.of(
                blsCredsValidators.stream(),
                eth1CredsValidators.stream(),
                compoundingCredsValidators.stream())
            .flatMap(Function.identity())
            .collect(
                Collectors.toMap(
                    Validator::getPublicKey,
                    validator ->
                        new StateValidatorData(
                            dataStructureUtil.randomValidatorIndex(),
                            validator.getEffectiveBalance(),
                            ValidatorStatus.active_ongoing,
                            validator)));

    ownedValidatorStatusProvider.updateValidatorBalanceMetrics(validatorDataMap);

    final StubLabelledGauge validatorBalancesMetric =
        metricSystem.getLabelledGauge(TekuMetricCategory.VALIDATOR, "local_validator_balances");

    assertThat(validatorBalancesMetric.getValue(String.valueOf(BLS_WITHDRAWAL_BYTE)))
        .hasValue(UInt64.THIRTY_TWO_ETH.times(2).longValue());
    assertThat(validatorBalancesMetric.getValue(String.valueOf(ETH1_ADDRESS_WITHDRAWAL_BYTE)))
        .hasValue(UInt64.THIRTY_TWO_ETH.times(2).longValue());
    assertThat(validatorBalancesMetric.getValue(String.valueOf(COMPOUNDING_WITHDRAWAL_BYTE)))
        .hasValue(UInt64.THIRTY_TWO_ETH.times(2).longValue());
  }
}
