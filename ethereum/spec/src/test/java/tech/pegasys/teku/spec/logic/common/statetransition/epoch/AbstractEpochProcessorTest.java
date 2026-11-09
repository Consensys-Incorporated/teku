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

package tech.pegasys.teku.spec.logic.common.statetransition.epoch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;
import org.junit.platform.commons.util.ReflectionUtils.HierarchyTraversalMode;
import tech.pegasys.teku.infrastructure.ssz.collections.SszPrimitiveVector;
import tech.pegasys.teku.infrastructure.ssz.primitive.SszUInt64;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.Throttler;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.spec.datastructures.state.Validator;
import tech.pegasys.teku.spec.datastructures.state.beaconstate.BeaconState;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatusFactory;
import tech.pegasys.teku.spec.logic.common.statetransition.epoch.status.ValidatorStatuses;
import tech.pegasys.teku.spec.logic.versions.capella.statetransition.epoch.EpochProcessorCapella;
import tech.pegasys.teku.spec.util.DataStructureUtil;

class AbstractEpochProcessorTest {

  private final Spec spec = TestSpecFactory.createMinimalCapella();
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil(spec);
  private final EpochProcessor epochProcessor = spec.getGenesisSpec().getEpochProcessor();
  private final int throttlingPeriod = 1; // expect maximum of one call per second
  private static final Logger LOGGER = mock(Logger.class);
  private final Throttler<Logger> loggerThrottler = spyLogThrottler(LOGGER, throttlingPeriod);
  private final BeaconState state = createStateInInactivityLeak();
  private final int slotsPerEpoch = spec.getSlotsPerEpoch(state.getSlot());

  @Test
  public void shouldThrottleInactivityLeakLogs() throws Exception {
    final StubTimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(100_000L);
    final EpochProcessor epochProcessor = spec.getGenesisSpec().getEpochProcessor();
    FieldUtils.writeField(epochProcessor, "timeProvider", timeProvider, true);

    // First two processEpoch calls within the same second
    epochProcessor.processEpoch(state);
    epochProcessor.processEpoch(advanceNSlots(state, 1));
    timeProvider.advanceTimeBy(Duration.ofSeconds(1));
    epochProcessor.processEpoch(advanceNSlots(state, slotsPerEpoch));

    // Logger throttler called 3 times
    verify(loggerThrottler, times(3)).invoke(any(), any());

    // Real logger only called 2 times (one per second)
    verify(LOGGER, times(2)).info(anyString());
  }

  private BeaconState advanceNSlots(final BeaconState state, final long slotsToAdvance) {
    return state.updated(s -> s.setSlot(s.getSlot().plus(slotsToAdvance)));
  }

  @SuppressWarnings("MockNotUsedInProduction")
  private BeaconState createStateInInactivityLeak() {
    final UInt64 maxEffectiveBalance = spec.getGenesisSpecConfig().getMaxEffectiveBalance();
    final Validator validator =
        spy(dataStructureUtil.randomValidator().withEffectiveBalance(maxEffectiveBalance));
    when(validator.getEffectiveBalance()).thenReturn(maxEffectiveBalance);

    // Prevent uin64 overflow when processing epoch
    final SszPrimitiveVector<UInt64, SszUInt64> noSlashings =
        dataStructureUtil.randomSszPrimitiveVector(
            dataStructureUtil.getBeaconStateSchema().getSlashingsSchema(), () -> UInt64.ZERO);

    return dataStructureUtil
        .stateBuilder(spec.getGenesisSpec().getMilestone(), 1, 0)
        .slashings(noSlashings)
        .finalizedCheckpoint(dataStructureUtil.randomCheckpoint(UInt64.ZERO))
        .build();
  }

  private Throttler<Logger> spyLogThrottler(final Logger logger, final int throttlingPeriod) {
    Throttler<Logger> loggerThrottler =
        spy(new Throttler<>(logger, UInt64.valueOf(throttlingPeriod)));
    Field field =
        ReflectionUtils.findFields(
                EpochProcessorCapella.class,
                f -> f.getName().equals("loggerThrottler"),
                HierarchyTraversalMode.TOP_DOWN)
            .get(0);

    field.setAccessible(true);
    try {
      field.set(epochProcessor, loggerThrottler);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }

    return loggerThrottler;
  }

  @Test
  public void shouldCheckNewValidatorsDuringEpochProcessingReturnsFalse() {
    assertThat(
            ((AbstractEpochProcessor) epochProcessor)
                .shouldCheckNewValidatorsDuringEpochProcessing())
        .isFalse();
  }

  @Test
  public void recreateValidatorStatusWithNoNewValidators() {
    final BeaconState preState =
        dataStructureUtil.stateBuilder(spec.getGenesisSpec().getMilestone(), 10, 3).build();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(preState.getSlot());
    final ValidatorStatusFactory validatorStatusFactory =
        spec.atSlot(preState.getSlot()).getValidatorStatusFactory();

    final ValidatorStatuses validatorStatuses =
        validatorStatusFactory.createValidatorStatuses(preState);

    final ValidatorStatuses newValidatorStatuses =
        ((AbstractEpochProcessor) epochProcessor)
            .recreateValidatorStatusIfNewValidatorsAreFound(
                preState, validatorStatuses, currentEpoch);

    assertThat(preState.getValidators().size())
        .isEqualTo(newValidatorStatuses.getStatuses().size());
  }

  @Test
  public void recreateValidatorStatusWithNewValidators() {
    final BeaconState preState =
        dataStructureUtil.stateBuilder(spec.getGenesisSpec().getMilestone(), 10, 3).build();
    final UInt64 currentEpoch = spec.computeEpochAtSlot(preState.getSlot());
    final ValidatorStatusFactory validatorStatusFactory =
        spec.atSlot(preState.getSlot()).getValidatorStatusFactory();

    final ValidatorStatuses validatorStatuses =
        validatorStatusFactory.createValidatorStatuses(preState);

    final List<Validator> newValidators =
        List.of(
            dataStructureUtil.randomValidator(),
            dataStructureUtil.randomValidator(),
            dataStructureUtil.randomValidator());
    final BeaconState postState =
        preState.updated(state -> newValidators.forEach(state.getValidators()::append));

    final ValidatorStatuses newValidatorStatuses =
        ((AbstractEpochProcessor) epochProcessor)
            .recreateValidatorStatusIfNewValidatorsAreFound(
                postState, validatorStatuses, currentEpoch);

    assertThat(preState.getValidators().size() + newValidators.size())
        .isEqualTo(newValidatorStatuses.getStatuses().size());
    assertThat(postState.getValidators().size())
        .isEqualTo(newValidatorStatuses.getStatuses().size());
  }
}
