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

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.infrastructure.async.SafeFuture.completedFuture;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ONE;
import static tech.pegasys.teku.infrastructure.unsigned.UInt64.ZERO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuties;
import tech.pegasys.teku.ethereum.json.types.validator.ProposerDuty;
import tech.pegasys.teku.infrastructure.async.SafeFuture;
import tech.pegasys.teku.infrastructure.metrics.StubMetricsSystem;
import tech.pegasys.teku.infrastructure.metrics.Validator.DutyType;
import tech.pegasys.teku.infrastructure.time.StubTimeProvider;
import tech.pegasys.teku.infrastructure.time.TimeProvider;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.TestSpecFactory;
import tech.pegasys.teku.validator.api.ValidatorTimingChannel;
import tech.pegasys.teku.validator.client.duties.BlockDutyFactory;
import tech.pegasys.teku.validator.client.duties.BlockProductionDuty;
import tech.pegasys.teku.validator.client.duties.Duty;
import tech.pegasys.teku.validator.client.duties.DutyResult;
import tech.pegasys.teku.validator.client.duties.SlotBasedScheduledDuties;
import tech.pegasys.teku.validator.client.duties.ValidatorDutyMetrics;
import tech.pegasys.teku.validator.client.loader.OwnedValidators;

public class BlockDutySchedulerTest extends AbstractDutySchedulerTest {
  private BlockDutyScheduler dutyScheduler;

  private Spec spec = TestSpecFactory.createMinimalPhase0();
  private final TimeProvider timeProvider = StubTimeProvider.withTimeInSeconds(1000);

  private final BlockDutyFactory blockDutyFactory = mock(BlockDutyFactory.class);

  @SuppressWarnings("unchecked")
  final SlotBasedScheduledDuties<BlockProductionDuty, Duty> scheduledDuties =
      mock(SlotBasedScheduledDuties.class);

  final ValidatorTimingChannel validatorTimingChannelPubisher = mock(ValidatorTimingChannel.class);

  final StubMetricsSystem metricsSystem2 = new StubMetricsSystem();

  @Test
  public void shouldFetchDutiesForCurrentEpoch() {
    createDutySchedulerWithRealDuties();
    when(validatorApiChannel.getProposerDuties(any(), anyBoolean()))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new ProposerDuties(dataStructureUtil.randomBytes32(), emptyList(), false))));

    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(UInt64.ONE));

    verify(validatorApiChannel).getProposerDuties(eq(UInt64.ONE), eq(true));
    verify(validatorApiChannel, never()).getProposerDuties(eq(UInt64.valueOf(2)), anyBoolean());
  }

  @Test
  public void shouldNotPerformDutiesForSameSlotTwice() {
    createDutySchedulerWithRealDuties();
    final UInt64 blockProposerSlot = UInt64.valueOf(5);
    final ProposerDuty validator1Duties = new ProposerDuty(VALIDATOR1_KEY, 5, blockProposerSlot);
    when(validatorApiChannel.getProposerDuties(eq(ZERO), eq(true)))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new ProposerDuties(
                        dataStructureUtil.randomBytes32(),
                        List.of(
                            validator1Duties,
                            new ProposerDuty(
                                dataStructureUtil.randomPublicKey(), 6, UInt64.valueOf(4))),
                        false))));

    final BlockProductionDuty blockCreationDuty =
        createBlockProductionDutyAtSlot(blockProposerSlot);

    // Load duties
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(ZERO));

    // Execute
    dutyScheduler.onBlockProductionDue(blockProposerSlot);
    verify(blockCreationDuty).getType();
    verify(blockCreationDuty).performDuty();

    // Somehow we triggered the same slot again.
    dutyScheduler.onBlockProductionDue(blockProposerSlot);
    // But shouldn't produce another block and get ourselves slashed.
    verifyNoMoreInteractions(blockCreationDuty);
  }

  @Test
  public void lookaheadIsCurrentEpochBeforeFulu() {
    spec = TestSpecFactory.createMinimalElectra();
    createDutySchedulerWithRealDuties();
    assertThat(dutyScheduler.getLookAheadEpochs(UInt64.ONE)).isEqualTo(0);
  }

  @Test
  public void shouldScheduleBlockProposalDuty() {
    createDutySchedulerWithRealDuties();
    final UInt64 blockProposerSlot = UInt64.valueOf(5);
    final ProposerDuty validator1Duties = new ProposerDuty(VALIDATOR1_KEY, 5, blockProposerSlot);
    when(validatorApiChannel.getProposerDuties(eq(ZERO), eq(true)))
        .thenReturn(
            completedFuture(
                Optional.of(
                    new ProposerDuties(
                        dataStructureUtil.randomBytes32(), List.of(validator1Duties), false))));

    final BlockProductionDuty blockCreationDuty =
        createBlockProductionDutyAtSlot(blockProposerSlot);

    // Load duties
    dutyScheduler.onSlot(spec.computeStartSlotAtEpoch(ZERO));

    // Execute
    dutyScheduler.onBlockProductionDue(blockProposerSlot);
    verify(blockCreationDuty).performDuty();
  }

  private BlockProductionDuty createBlockProductionDutyAtSlot(final UInt64 blockProposerSlot) {
    final BlockProductionDuty blockCreationDuty = mock(BlockProductionDuty.class);
    when(blockCreationDuty.getType()).thenReturn(DutyType.BLOCK_PRODUCTION);
    when(blockCreationDuty.performDuty()).thenReturn(new SafeFuture<>());
    when(blockDutyFactory.createProductionDuty(blockProposerSlot, validator1))
        .thenReturn(blockCreationDuty);
    return blockCreationDuty;
  }

  @Test
  public void shouldDelayExecutingDutiesUntilSchedulingIsComplete() {
    createDutySchedulerWithRealDuties();
    createDutySchedulerWithMockDuties();
    final SafeFuture<Optional<ProposerDuties>> epoch0Duties = new SafeFuture<>();

    when(validatorApiChannel.getProposerDuties(eq(ZERO), anyBoolean())).thenReturn(epoch0Duties);
    dutyScheduler.onSlot(ZERO);

    dutyScheduler.onBlockProductionDue(ZERO);
    // Duties haven't been loaded yet.
    verify(scheduledDuties, never()).performProductionDuty(ZERO);

    epoch0Duties.complete(
        Optional.of(new ProposerDuties(dataStructureUtil.randomBytes32(), emptyList(), false)));

    verify(scheduledDuties).performProductionDuty(ZERO);
    verify(validatorApiChannel).getProposerDuties(eq(ZERO), anyBoolean());
    verify(validatorApiChannel, never()).getProposerDuties(eq(ONE), anyBoolean());
  }

  @Test
  public void shouldRefetchDutiesWhenHeadUpdateHasDifferentCurrentDependentRoot() {
    createDutySchedulerWithRealDuties();
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final Bytes32 previousDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDependentRoot = dataStructureUtil.randomBytes32();
    when(validatorApiChannel.getProposerDuties(any(), anyBoolean()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new ProposerDuties(currentDependentRoot, emptyList(), false))));
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getProposerDuties(currentEpoch, true);

    dutyScheduler.onHeadUpdate(
        currentSlot,
        previousDependentRoot,
        dataStructureUtil.randomBytes32(),
        dataStructureUtil.randomBytes32());

    verify(validatorApiChannel, times(2)).getProposerDuties(currentEpoch, true);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldNotRefetchDutiesWhenHeadUpdateHasSameCurrentDependentRoot() {
    createDutySchedulerWithRealDuties();
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    final Bytes32 previousDependentRoot = dataStructureUtil.randomBytes32();
    final Bytes32 currentDependentRoot = dataStructureUtil.randomBytes32();
    when(validatorApiChannel.getProposerDuties(any(), anyBoolean()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(new ProposerDuties(currentDependentRoot, emptyList(), false))));
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getProposerDuties(currentEpoch, true);

    dutyScheduler.onHeadUpdate(
        currentSlot,
        previousDependentRoot,
        currentDependentRoot,
        dataStructureUtil.randomBytes32());

    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldRefetchAllDutiesOnMissedEvents() {
    createDutySchedulerWithRealDuties();
    final UInt64 currentEpoch = UInt64.valueOf(5);
    final UInt64 currentSlot = spec.computeStartSlotAtEpoch(currentEpoch);
    when(validatorApiChannel.getProposerDuties(any(), anyBoolean())).thenReturn(new SafeFuture<>());
    dutyScheduler.onSlot(currentSlot);

    verify(validatorApiChannel).getProposerDuties(currentEpoch, true);

    dutyScheduler.onPossibleMissedEvents();

    // Remembers the previous latest epoch and uses it to recalculate duties
    verify(validatorApiChannel, times(2)).getProposerDuties(currentEpoch, true);
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  void shouldRefetchAllDutiesOnMissedEventsWithNoPreviousEpoch() {
    createDutySchedulerWithRealDuties();
    dutyScheduler.onPossibleMissedEvents();

    // Latest epoch is unknown so can't recalculate duties
    verifyNoMoreInteractions(validatorApiChannel);
  }

  @Test
  public void shouldNotProduceBlockIfEpochIsUnknown() {
    createDutySchedulerWithMockDuties();
    dutyScheduler.onBlockProductionDue(ONE);
    verify(scheduledDuties, never()).performProductionDuty(ZERO);
  }

  @Test
  public void shouldNotProduceBlockIfCurrentEpochIsTooFarBeforeSlotEpoch() {
    createDutySchedulerWithMockDuties();
    // first slot of epoch 1
    final int lookAheadEpochs = dutyScheduler.getLookAheadEpochs(ONE);
    final UInt64 slot = spec.computeStartSlotAtEpoch(UInt64.valueOf(lookAheadEpochs + 1));
    dutyScheduler.onSlot(ONE); // epoch 0
    dutyScheduler.onBlockProductionDue(slot);
    verify(scheduledDuties, never()).performProductionDuty(slot);
  }

  @Test
  public void shouldProduceBlockIfCurrentEpochIsAtBoundaryOfLookaheadEpoch() {
    createDutySchedulerWithMockDuties();
    // last slot of epoch 0
    final int lookAheadEpochs = dutyScheduler.getLookAheadEpochs(ZERO);
    final UInt64 slot =
        spec.computeStartSlotAtEpoch(UInt64.valueOf(lookAheadEpochs + 1)).decrement();
    when(scheduledDuties.performProductionDuty(slot))
        .thenReturn(SafeFuture.completedFuture(DutyResult.success(Bytes32.ZERO)));

    when(validatorApiChannel.getProposerDuties(eq(ZERO), anyBoolean()))
        .thenReturn(
            SafeFuture.completedFuture(
                Optional.of(
                    new ProposerDuties(dataStructureUtil.randomBytes32(), emptyList(), false))));

    dutyScheduler.onSlot(ZERO); // epoch 0
    dutyScheduler.onBlockProductionDue(slot);
    verify(scheduledDuties).performProductionDuty(slot);
  }

  @Test
  void shouldUseCurrentDependentRootWhenDutyFromCurrentEpoch() {
    createDutySchedulerWithRealDuties();
    final Bytes32 currentDutyDependentRoot = Bytes32.fromHexString("0x2222");
    final Bytes32 result =
        dutyScheduler.getExpectedDependentRoot(
            Bytes32.fromHexString("0x3333"),
            Bytes32.fromHexString("0x1111"),
            currentDutyDependentRoot,
            ONE,
            ONE);

    assertThat(result).isEqualTo(currentDutyDependentRoot);
  }

  @Test
  void shouldUseHeadRootWhenDutyIsFromNextEpoch() {
    createDutySchedulerWithRealDuties();
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x3333");
    final Bytes32 result =
        dutyScheduler.getExpectedDependentRoot(
            headBlockRoot,
            Bytes32.fromHexString("0x1111"),
            Bytes32.fromHexString("0x2222"),
            ZERO,
            ONE);

    assertThat(result).isEqualTo(headBlockRoot);
  }

  @Test
  void shouldUseHeadRootWhenDutyIsFromBeyondNextEpoch() {
    createDutySchedulerWithRealDuties();
    final Bytes32 headBlockRoot = Bytes32.fromHexString("0x3333");
    final Bytes32 result =
        dutyScheduler.getExpectedDependentRoot(
            headBlockRoot,
            Bytes32.fromHexString("0x1111"),
            Bytes32.fromHexString("0x2222"),
            ZERO,
            UInt64.valueOf(2));

    assertThat(result).isEqualTo(headBlockRoot);
  }

  private void createDutySchedulerWithRealDuties() {
    final ValidatorDutyMetrics validatorDutyMetrics = ValidatorDutyMetrics.create(metricsSystem);

    dutyScheduler =
        new BlockDutyScheduler(
            metricsSystem,
            new RetryingDutyLoader<>(
                asyncRunner,
                timeProvider,
                new BlockProductionDutyLoader(
                    validatorApiChannel,
                    dependentRoot ->
                        new SlotBasedScheduledDuties<>(
                            blockDutyFactory,
                            dependentRoot,
                            validatorDutyMetrics::performDutyWithMetrics),
                    new OwnedValidators(
                        Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2)),
                    validatorIndexProvider,
                    validatorTimingChannelPubisher)),
            spec);
  }

  private void createDutySchedulerWithMockDuties() {
    dutyScheduler =
        new BlockDutyScheduler(
            metricsSystem2,
            new RetryingDutyLoader<>(
                asyncRunner,
                timeProvider,
                new BlockProductionDutyLoader(
                    validatorApiChannel,
                    dependentRoot -> scheduledDuties,
                    new OwnedValidators(
                        Map.of(VALIDATOR1_KEY, validator1, VALIDATOR2_KEY, validator2)),
                    validatorIndexProvider,
                    validatorTimingChannelPubisher)),
            spec);
  }
}
